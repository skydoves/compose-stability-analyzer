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
import com.skydoves.compose.stability.idea.reality.RealityClassifier
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import com.skydoves.compose.stability.runtime.ParameterStabilityInfo
import junit.framework.TestCase

/**
 * Pure-function tests for [DoctorScorer]: caps, hybrid ranking semantics (measured waste
 * outranks estimates; healthy-measured sinks), and waste computation.
 */
class DoctorScorerTest : TestCase() {

  private fun staticInfo(vararg stabilities: ParameterStability): ComposableStabilityInfo =
    ComposableStabilityInfo(
      name = "Test",
      fqName = "com.example.Test",
      isRestartable = true,
      isSkippable = stabilities.all { it == ParameterStability.STABLE },
      isReadonly = false,
      parameters = stabilities.mapIndexed { i, s ->
        ParameterStabilityInfo(name = "p$i", type = "T$i", stability = s)
      },
    )

  private fun liveData(
    totalCount: Int,
    totalDurationMs: Double,
    refChanged: Map<String, Int> = emptyMap(),
    changed: Map<String, Int> = emptyMap(),
    observations: Map<String, Int> = emptyMap(),
  ): ComposableHeatmapData = ComposableHeatmapData(
    composableName = "Test",
    totalRecompositionCount = totalCount,
    maxSingleCount = totalCount,
    recentEvents = emptyList(),
    lastSeenTimestampMs = 0L,
    changedParameters = changed,
    unstableParameters = emptySet(),
    totalDurationMs = totalDurationMs,
    refChangedParameters = refChanged,
    observationCounts = observations,
  )

  fun testEstimated_capAt60() {
    val inputs = DoctorScorer.StaticInputs(
      unstableParams = 10,
      unknownOrRuntimeParams = 10,
      fixableParams = 10,
      notSkippable = true,
      blastRadius = 100,
    )
    assertEquals(DoctorScorer.ESTIMATED_CAP, DoctorScorer.estimated(inputs))
  }

  fun testEstimated_singleUnstableParamScoresAroundTen() {
    val inputs = DoctorScorer.StaticInputs(1, 0, 0, false, null)
    assertEquals(10.0, DoctorScorer.estimated(inputs))
  }

  fun testEstimated_blastRadiusLogCompressed() {
    val small = DoctorScorer.StaticInputs(1, 0, 0, false, blastRadius = 3)
    val large = DoctorScorer.StaticInputs(1, 0, 0, false, blastRadius = 50)
    val smallScore = DoctorScorer.estimated(small)
    val largeScore = DoctorScorer.estimated(large)
    assertTrue(largeScore > smallScore)
    // 16x more blast radius must NOT yield 16x the bonus (log compression).
    assertTrue((largeScore - 10.0) < 4 * (smallScore - 10.0))
  }

  fun testMeasured_capAt100() {
    assertEquals(
      DoctorScorer.MEASURED_CAP,
      DoctorScorer.measured(wasteMs = 1e9, silentWasteParams = 10, blastRadius = 1000),
    )
  }

  fun testScore_measuredWasteOutranksAnyEstimate() {
    // Worst possible static-only case is capped at 60.
    val worstEstimate = DoctorScorer.estimated(
      DoctorScorer.StaticInputs(10, 10, 10, true, 1000),
    )

    // A composable with real observed waste: 12 wasted recompositions, ~18ms each.
    val info = staticInfo(ParameterStability.UNSTABLE)
    val live = liveData(
      totalCount = 20,
      totalDurationMs = 20 * 18.0,
      refChanged = mapOf("p0" to 12),
      observations = mapOf("p0" to 20),
    ).copy(
      recentEvents = wastedEvents(12),
    )
    val reality = RealityClassifier.classify(info, live)
    val score = DoctorScorer.score(
      DoctorScorer.staticInputs(info, 0, strongSkipping = true, blastRadius = 12),
      reality,
      live,
    )

    assertEquals(ScoreKind.MEASURED, score.kind)
    assertTrue(
      "measured (${score.value}) should outrank worst estimate ($worstEstimate)",
      score.value > worstEstimate,
    )
  }

  fun testScore_falseAlarmOnlySinksBelowEstimates() {
    // Predicted unstable but referentially stable at runtime -> FALSE_ALARM, no waste.
    val info = staticInfo(ParameterStability.UNSTABLE)
    val live = liveData(
      totalCount = 10,
      totalDurationMs = 50.0,
      observations = mapOf("p0" to 10),
    )
    val reality = RealityClassifier.classify(info, live)
    val score = DoctorScorer.score(
      DoctorScorer.staticInputs(info, 0, strongSkipping = true, blastRadius = null),
      reality,
      live,
    )

    assertEquals(ScoreKind.MEASURED, score.kind)
    // Healthy measured row scores near zero — below its own static estimate (10).
    assertTrue(score.value < score.estimatedComponent)
  }

  fun testScore_noLiveDataStaysEstimated() {
    val info = staticInfo(ParameterStability.UNSTABLE, ParameterStability.UNKNOWN)
    val score = DoctorScorer.score(
      DoctorScorer.staticInputs(info, 0, strongSkipping = true, blastRadius = 4),
      reality = null,
      live = null,
    )
    assertEquals(ScoreKind.ESTIMATED, score.kind)
    assertTrue(score.value > 0)
    assertEquals(score.value, score.estimatedComponent)
  }

  fun testScore_insufficientObservationsStaysEstimated() {
    val info = staticInfo(ParameterStability.UNSTABLE)
    // Below RealityClassifier.MIN_OBSERVATIONS -> all params OBSERVING -> not graded.
    val live = liveData(
      totalCount = 2,
      totalDurationMs = 4.0,
      observations = mapOf("p0" to 2),
    )
    val reality = RealityClassifier.classify(info, live)
    val score = DoctorScorer.score(
      DoctorScorer.staticInputs(info, 0, strongSkipping = true, blastRadius = null),
      reality,
      live,
    )
    assertEquals(ScoreKind.ESTIMATED, score.kind)
  }

  fun testMeasuredWasteMs_usesAverageDurationWithFloor() {
    val info = staticInfo(ParameterStability.UNSTABLE)
    val live = liveData(
      totalCount = 10,
      totalDurationMs = 0.0, // device logged no durations
      refChanged = mapOf("p0" to 5),
      observations = mapOf("p0" to 10),
    ).copy(recentEvents = wastedEvents(5))
    val reality = RealityClassifier.classify(info, live)
    // 5 wasted recompositions x 1ms floor.
    assertEquals(5.0, DoctorScorer.measuredWasteMs(reality, live))
  }

  fun testStaticInputs_skippabilityTermOnlyWithoutStrongSkipping() {
    val info = staticInfo(ParameterStability.UNSTABLE)
    val strong = DoctorScorer.staticInputs(info, 0, strongSkipping = true, blastRadius = null)
    val legacy = DoctorScorer.staticInputs(info, 0, strongSkipping = false, blastRadius = null)
    assertFalse(strong.notSkippable)
    assertTrue(legacy.notSkippable)
  }

  /** Builds N recent events that look like pure reference-only-change recompositions. */
  private fun wastedEvents(count: Int) = List(count) {
    com.skydoves.compose.stability.idea.heatmap.ParsedRecompositionEvent(
      composableName = "Test",
      tag = "",
      recompositionCount = it + 1,
      parameterEntries = listOf(
        com.skydoves.compose.stability.idea.heatmap.ParsedParameterEntry(
          name = "p0",
          type = "T0",
          status = com.skydoves.compose.stability.idea.heatmap.ParameterStatus.REF_CHANGED,
          detail = "T0@a → T0@b",
        ),
      ),
      unstableParameters = listOf("p0"),
      timestampMs = 0L,
    )
  }
}
