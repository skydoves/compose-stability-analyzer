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
package com.skydoves.compose.stability.idea.heatmap

/**
 * A parsed recomposition event from logcat output.
 */
internal data class ParsedRecompositionEvent(
  val composableName: String,
  val tag: String,
  val recompositionCount: Int,
  val parameterEntries: List<ParsedParameterEntry>,
  val unstableParameters: List<String>,
  val timestampMs: Long,
  val durationMs: Double = 0.0,
  val stateEntries: List<String> = emptyList(),
)

/**
 * A single parameter entry from a recomposition log.
 */
internal data class ParsedParameterEntry(
  val name: String,
  val type: String,
  val status: ParameterStatus,
  val detail: String,
)

/**
 * Status of a parameter in a recomposition event.
 */
internal enum class ParameterStatus {
  CHANGED,
  STABLE,
  UNSTABLE,

  /**
   * Structurally equal to the previous value but a new instance — the runtime logged a
   * `stable`/`unstable` token carrying an `old → new` arrow. Under strong skipping an unstable
   * param compared by identity (`===`) recomposes here even though `equals` matched, which the
   * Reality Check surfaces as silent waste.
   */
  REF_CHANGED,
}

/**
 * Aggregated heatmap data for a single composable function.
 */
internal data class ComposableHeatmapData(
  val composableName: String,
  val totalRecompositionCount: Int,
  val maxSingleCount: Int,
  val recentEvents: List<ParsedRecompositionEvent>,
  val lastSeenTimestampMs: Long,
  val changedParameters: Map<String, Int>,
  val unstableParameters: Set<String>,
  val lastDurationMs: Double = 0.0,
  val totalDurationMs: Double = 0.0,
  val lastParameterChanges: List<String> = emptyList(),
  val lastStateChanges: List<String> = emptyList(),
  /** Per-parameter count of equals-equal-but-new-instance observations (REF_CHANGED). */
  val refChangedParameters: Map<String, Int> = emptyMap(),
  /** Per-parameter count of total appearances across recompositions (lifetime, uncapped). */
  val observationCounts: Map<String, Int> = emptyMap(),
)

/**
 * Represents a connected ADB device.
 */
internal data class AdbDevice(
  val serial: String,
  val description: String,
) {
  override fun toString(): String = "$serial  $description"
}
