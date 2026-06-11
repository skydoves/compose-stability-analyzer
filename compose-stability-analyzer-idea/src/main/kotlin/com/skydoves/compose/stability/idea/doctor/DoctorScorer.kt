/*
 * Designed and developed by 2025 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skydoves.compose.stability.idea.doctor

import com.skydoves.compose.stability.idea.heatmap.ComposableHeatmapData
import com.skydoves.compose.stability.idea.reality.ComposableReality
import com.skydoves.compose.stability.idea.reality.RealityClassifier
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min

/**
 * Pure scoring functions for the Stability Doctor. No PSI, no services — unit-testable.
 *
 * Both score kinds share one 0..100 scale. Estimated (static-only) scores are capped at
 * [ESTIMATED_CAP] so a composable with confirmed, measured waste always outranks speculation;
 * conversely a measured-but-healthy composable (only false alarms) deliberately sinks below
 * high-value estimates — that demotion is the point of hybrid scoring.
 */
internal object DoctorScorer {

  const val ESTIMATED_CAP: Double = 60.0
  const val MEASURED_CAP: Double = 100.0

  /** Static inputs for the estimated score. */
  internal data class StaticInputs(
    /** Count of parameters with UNSTABLE stability. */
    val unstableParams: Int,
    /** Count of parameters with UNKNOWN or RUNTIME stability. */
    val unknownOrRuntimeParams: Int,
    /** Count of UNSTABLE params whose type is fixable (declared in project sources). */
    val fixableParams: Int,
    /** True when the composable is not statically skippable (non-strong-skipping setups). */
    val notSkippable: Boolean,
    /** Downstream unskippable count from the cascade summary; null = not computed (treated 0). */
    val blastRadius: Int?,
  )

  /**
   * `min(60, 10*U + 3*K + 2*F + SK + 6*log2(1+B))` — one unstable param ~ 10-12 points;
   * fixability is a small actionability boost; blast radius is log-compressed so a huge
   * subtree doesn't dwarf everything else.
   */
  fun estimated(inputs: StaticInputs): Double {
    val b = (inputs.blastRadius ?: 0).coerceAtLeast(0)
    val raw = 10.0 * inputs.unstableParams +
      3.0 * inputs.unknownOrRuntimeParams +
      2.0 * inputs.fixableParams +
      (if (inputs.notSkippable) 8.0 else 0.0) +
      6.0 * log2(1.0 + b)
    return min(ESTIMATED_CAP, raw)
  }

  /**
   * `min(100, 22*log10(1+wasteMs) + 10*S + 6*log2(1+B))` where S = SILENT_WASTE param count.
   * JUSTIFIED recompositions carry no penalty — they are legitimate work.
   */
  fun measured(wasteMs: Double, silentWasteParams: Int, blastRadius: Int?): Double {
    val b = (blastRadius ?: 0).coerceAtLeast(0)
    val raw = 22.0 * log10(1.0 + wasteMs.coerceAtLeast(0.0)) +
      10.0 * silentWasteParams +
      6.0 * log2(1.0 + b)
    return min(MEASURED_CAP, raw)
  }

  /**
   * Computes the full [DoctorScore] for one composable. The score is MEASURED only when live
   * data exists AND at least one parameter has enough observations to be graded
   * ([RealityClassifier.MIN_OBSERVATIONS]); otherwise it stays ESTIMATED. Parameterless
   * composables with observed recompositions also count as measured (nothing to grade, but
   * the waste counter is real).
   */
  fun score(
    staticInputs: StaticInputs,
    reality: ComposableReality?,
    live: ComposableHeatmapData?,
  ): DoctorScore {
    val estimatedValue = estimated(staticInputs)
    val graded = reality != null && live != null && hasGradedObservations(reality)

    if (!graded) {
      return DoctorScore(
        kind = ScoreKind.ESTIMATED,
        value = estimatedValue,
        estimatedComponent = estimatedValue,
        measuredWasteMs = 0.0,
        blastRadius = staticInputs.blastRadius,
      )
    }

    val wasteMs = measuredWasteMs(reality!!, live!!)
    val silentWaste = reality.parameters.count { it.grade == RealityGrade.SILENT_WASTE }
    val measuredValue = measured(wasteMs, silentWaste, staticInputs.blastRadius)
    return DoctorScore(
      kind = ScoreKind.MEASURED,
      value = measuredValue,
      estimatedComponent = estimatedValue,
      measuredWasteMs = wasteMs,
      blastRadius = staticInputs.blastRadius,
    )
  }

  /**
   * Observed wasted time: wasted recompositions x average duration per recomposition.
   * Uses a 1ms floor when the device logged no durations so waste still registers.
   */
  fun measuredWasteMs(reality: ComposableReality, live: ComposableHeatmapData): Double {
    val avgDurationMs = if (live.totalRecompositionCount > 0 && live.totalDurationMs > 0.0) {
      live.totalDurationMs / live.totalRecompositionCount
    } else {
      1.0
    }
    return reality.wastedRecompositions * avgDurationMs
  }

  /**
   * Ranking comparator: score desc; ties broken by MEASURED first, then wasted ms,
   * then unstable param count (encoded in estimatedComponent as the dominant term).
   */
  val ranking: Comparator<Prescription> =
    compareByDescending<Prescription> { it.score.value }
      .thenByDescending { it.score.kind == ScoreKind.MEASURED }
      .thenByDescending { it.score.measuredWasteMs }
      .thenByDescending { it.score.estimatedComponent }

  /** True when at least one parameter reached the observation threshold, or a parameterless
   * composable has observed recompositions. */
  private fun hasGradedObservations(reality: ComposableReality): Boolean {
    if (reality.parameters.isEmpty()) return reality.totalObservedRecompositions > 0
    return reality.parameters.any { it.grade != RealityGrade.OBSERVING }
  }

  /** Builds [StaticInputs] from a static verdict (blast radius supplied separately). */
  fun staticInputs(
    info: ComposableStabilityInfo,
    fixableParams: Int,
    strongSkipping: Boolean,
    blastRadius: Int?,
  ): StaticInputs {
    val unstable = info.parameters.count { it.stability == ParameterStability.UNSTABLE }
    val unknownOrRuntime = info.parameters.count {
      it.stability == ParameterStability.UNKNOWN || it.stability == ParameterStability.RUNTIME
    }
    return StaticInputs(
      unstableParams = unstable,
      unknownOrRuntimeParams = unknownOrRuntime,
      fixableParams = fixableParams,
      // Under strong skipping everything is skippable, so the SK term only fires when
      // strong skipping is off AND the function is not skippable.
      notSkippable = !strongSkipping && !info.isSkippable,
      blastRadius = blastRadius,
    )
  }

  private fun log2(x: Double): Double = ln(x) / ln(2.0)
}
