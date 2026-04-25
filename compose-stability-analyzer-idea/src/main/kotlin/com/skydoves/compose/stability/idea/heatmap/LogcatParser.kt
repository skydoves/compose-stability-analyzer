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
 * Stateful parser for logcat recomposition messages.
 *
 * Parses the structured output from [DefaultRecompositionLogger] into
 * [ParsedRecompositionEvent] objects. Lines are fed one at a time;
 * when a new header arrives the previous accumulated event is emitted.
 *
 * Expected format:
 * ```
 * [Recomposition #3] UserProfile (tag: user-screen) (2.30ms)
 *   ├─ [param] user: User changed (User@abc123 → User@def456)
 *   ├─ [param] count: Int stable (42)
 *   ├─ [state] counter: Int changed (5 → 6)
 *   └─ Unstable parameters: [user]
 * ```
 *
 * Also supports the legacy format without `[param]`/`[state]` prefixes
 * and without duration.
 */
internal class LogcatParser(
  private val onEvent: (ParsedRecompositionEvent) -> Unit,
) {

  private companion object {
    /** Header: `[Recomposition #N] Name (tag: t) (2.30ms)` */
    val HEADER_REGEX =
      """\[Recomposition #(\d+)] (\S+)(?:\s+\(tag:\s+(.+?)\))?(?:\s+\((\d+\.?\d*)ms\))?"""
        .toRegex()

    /**
     * Parameter line with optional `[param]` prefix:
     *   `├─ [param] user: User changed (User@abc → User@def)`
     *   `├─ user: User changed (User@abc → User@def)`
     */
    val PARAM_REGEX =
      ("""\s*[├└]─\s+(?:\[param]\s+)?""" +
        """(\w+):\s+(.+?)\s+(changed|stable|unstable)""" +
        """(?:\s+\((.+)\))?""").toRegex()

    /**
     * State change line:
     *   `├─ [state] counter: Int changed (5 → 6)`
     */
    val STATE_REGEX =
      """\s*[├└]─\s+\[state]\s+(.+)"""
        .toRegex()

    val UNSTABLE_SUMMARY_REGEX =
      """\s*[├└]─\s+Unstable parameters:\s+\[(.+)]""".toRegex()

    val STATE_SUMMARY_REGEX =
      """\s*[├└]─\s+State changes:\s+\[(.+)]""".toRegex()
  }

  private var currentName: String? = null
  private var currentTag: String = ""
  private var currentCount: Int = 0
  private var currentDurationMs: Double = 0.0
  private var currentParams: MutableList<ParsedParameterEntry> = mutableListOf()
  private var currentUnstable: MutableList<String> = mutableListOf()
  private var currentStateEntries: MutableList<String> = mutableListOf()

  /**
   * Feed a single logcat message line (after stripping the `D/Recomposition: ` prefix).
   */
  fun feedLine(line: String) {
    val headerMatch = HEADER_REGEX.find(line)
    if (headerMatch != null) {
      emitCurrent()
      currentCount =
        headerMatch.groupValues[1].toIntOrNull() ?: 0
      currentName = headerMatch.groupValues[2]
      currentTag = headerMatch.groupValues[3]
      currentDurationMs =
        headerMatch.groupValues[4].toDoubleOrNull() ?: 0.0
      currentParams = mutableListOf()
      currentUnstable = mutableListOf()
      currentStateEntries = mutableListOf()
      return
    }

    if (currentName == null) return

    val unstableMatch = UNSTABLE_SUMMARY_REGEX.find(line)
    if (unstableMatch != null) {
      currentUnstable = unstableMatch.groupValues[1]
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
      return
    }

    // Skip state summary lines (not individual state entries)
    if (STATE_SUMMARY_REGEX.find(line) != null) return

    // [state] lines: capture the raw detail text
    val stateMatch = STATE_REGEX.find(line)
    if (stateMatch != null) {
      currentStateEntries.add(stateMatch.groupValues[1])
      return
    }

    val paramMatch = PARAM_REGEX.find(line)
    if (paramMatch != null) {
      val status = when (paramMatch.groupValues[3]) {
        "changed" -> ParameterStatus.CHANGED
        "stable" -> ParameterStatus.STABLE
        else -> ParameterStatus.UNSTABLE
      }
      currentParams.add(
        ParsedParameterEntry(
          name = paramMatch.groupValues[1],
          type = paramMatch.groupValues[2],
          status = status,
          detail = paramMatch.groupValues[4],
        ),
      )
    }
  }

  /**
   * Flush any partially accumulated event. Call when the logcat stream ends.
   */
  fun flush() {
    emitCurrent()
  }

  private fun emitCurrent() {
    val name = currentName ?: return
    onEvent(
      ParsedRecompositionEvent(
        composableName = name,
        tag = currentTag,
        recompositionCount = currentCount,
        parameterEntries = currentParams.toList(),
        unstableParameters = currentUnstable.toList(),
        timestampMs = System.currentTimeMillis(),
        durationMs = currentDurationMs,
        stateEntries = currentStateEntries.toList(),
      ),
    )
    currentName = null
  }
}
