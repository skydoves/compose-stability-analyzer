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
package com.skydoves.compose.stability.idea.reality

import com.skydoves.compose.stability.idea.heatmap.ComposableHeatmapData
import com.skydoves.compose.stability.idea.heatmap.ParameterStatus
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability

/**
 * The reconciled verdict for a single parameter, comparing the compile-time stability
 * prediction against what actually happened at runtime.
 */
internal enum class RealityGrade {
  /** Predicted stable and runtime agrees (or RUNTIME param that behaves stably). */
  CONFIRMED,

  /** Predicted unstable, but the instance stayed referentially stable, so Compose skips fine. */
  FALSE_ALARM,

  /**
   * Equals-equal but a new instance each time: under strong skipping an unstable param compared
   * by identity recomposes when a stable type would have skipped. The real, hidden problem.
   */
  SILENT_WASTE,

  /** The value genuinely changes (equals-different), so the recomposition is necessary. */
  JUSTIFIED,

  /** Not enough runtime observations yet to grade; show the static prediction only. */
  OBSERVING,
}

/** Per-parameter reconciliation result. */
internal data class ParameterReality(
  val name: String,
  val type: String,
  val predicted: ParameterStability,
  val grade: RealityGrade,
  val equalsChangedCount: Int,
  val refChangedCount: Int,
  val observationCount: Int,
)

/** Per-composable reconciliation result. */
internal data class ComposableReality(
  val composableName: String,
  val parameters: List<ParameterReality>,
  /**
   * Number of recent recompositions that were caused purely by a reference-only change of an
   * unstable param (no genuine value change, no internal state change) — i.e. recompositions a
   * stable type would have skipped. Counted over the recent-events window (capped), NOT lifetime.
   */
  val wastedRecompositions: Int,
  val totalObservedRecompositions: Int,
) {
  val hasSilentWaste: Boolean get() = parameters.any { it.grade == RealityGrade.SILENT_WASTE }
  val hasFalseAlarm: Boolean get() = parameters.any { it.grade == RealityGrade.FALSE_ALARM }
  val isObserving: Boolean get() = parameters.all { it.grade == RealityGrade.OBSERVING }

  fun gradeFor(paramName: String): RealityGrade? =
    parameters.find { it.name == paramName }?.grade
}

/**
 * Joins the static stability verdict with live runtime data to grade each parameter.
 *
 * The grading is deliberately gated on the STATIC verdict (not runtime alone): a reference-only
 * change is only "silent waste" for params the compiler considers unstable, because strong
 * skipping compares stable params with `equals()` (which still skips) and unstable params with
 * identity `===` (which recomposes). This is a pure function — safe to call off the EDT.
 */
internal object RealityClassifier {

  /**
   * Minimum number of logged observations of a parameter before we trust a grade.
   * Below this we report [RealityGrade.OBSERVING] to avoid early-noise verdicts.
   */
  const val MIN_OBSERVATIONS: Int = 3

  fun classify(
    static: ComposableStabilityInfo,
    live: ComposableHeatmapData?,
  ): ComposableReality {
    if (live == null) {
      return ComposableReality(
        composableName = static.name,
        parameters = static.parameters.map { p ->
          ParameterReality(
            name = p.name,
            type = p.type,
            predicted = p.stability,
            grade = RealityGrade.OBSERVING,
            equalsChangedCount = 0,
            refChangedCount = 0,
            observationCount = 0,
          )
        },
        wastedRecompositions = 0,
        totalObservedRecompositions = 0,
      )
    }

    val parameters = static.parameters.map { p ->
      val observed = live.observationCounts[p.name] ?: 0
      val equalsChanged = live.changedParameters[p.name] ?: 0
      val refChanged = live.refChangedParameters[p.name] ?: 0
      ParameterReality(
        name = p.name,
        type = p.type,
        predicted = p.stability,
        grade = gradeParameter(p.stability, observed, equalsChanged, refChanged),
        equalsChangedCount = equalsChanged,
        refChangedCount = refChanged,
        observationCount = observed,
      )
    }

    return ComposableReality(
      composableName = static.name,
      parameters = parameters,
      wastedRecompositions = countWasted(live),
      totalObservedRecompositions = live.totalRecompositionCount,
    )
  }

  private fun gradeParameter(
    predicted: ParameterStability,
    observed: Int,
    equalsChanged: Int,
    refChanged: Int,
  ): RealityGrade {
    if (observed < MIN_OBSERVATIONS) return RealityGrade.OBSERVING

    return when (predicted) {
      // Stable params are compared by equals() under strong skipping, so reference churn is
      // irrelevant — they always skip on equal content. Only a genuine value change matters.
      ParameterStability.STABLE ->
        if (equalsChanged > 0) RealityGrade.JUSTIFIED else RealityGrade.CONFIRMED

      // Unstable params are compared by identity (===): a fresh equals-equal instance recomposes.
      ParameterStability.UNSTABLE -> when {
        equalsChanged > 0 -> RealityGrade.JUSTIFIED
        refChanged > 0 -> RealityGrade.SILENT_WASTE
        else -> RealityGrade.FALSE_ALARM
      }

      // RUNTIME/UNKNOWN had no firm prediction, so there is no "false alarm" to call —
      // grade on behavior. (UNKNOWN: interfaces / non-final classes, per Compose 2.4.0.)
      ParameterStability.RUNTIME, ParameterStability.UNKNOWN -> when {
        equalsChanged > 0 -> RealityGrade.JUSTIFIED
        refChanged > 0 -> RealityGrade.SILENT_WASTE
        else -> RealityGrade.CONFIRMED
      }
    }
  }

  /**
   * Counts recompositions whose only trigger was a reference-only change of a param — excluding
   * recompositions explained by a genuine value change or an internal state change (which would
   * be legitimate, or attributable to state rather than a parameter).
   */
  private fun countWasted(live: ComposableHeatmapData): Int {
    return live.recentEvents.count { event ->
      val hasGenuineChange = event.parameterEntries.any { it.status == ParameterStatus.CHANGED }
      val hasRefChange = event.parameterEntries.any { it.status == ParameterStatus.REF_CHANGED }
      val hasStateChange = event.stateEntries.isNotEmpty()
      hasRefChange && !hasGenuineChange && !hasStateChange
    }
  }
}
