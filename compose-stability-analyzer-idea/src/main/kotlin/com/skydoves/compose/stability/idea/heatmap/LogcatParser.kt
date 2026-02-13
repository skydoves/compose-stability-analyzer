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
 * [Recomposition #3] UserProfile (tag: user-screen)
 *   ├─ user: User changed (User@abc123 → User@def456)
 *   ├─ count: Int stable (42)
 *   └─ Unstable parameters: [user]
 * ```
 */
internal class LogcatParser(
  private val onEvent: (ParsedRecompositionEvent) -> Unit,
) {

  private companion object {
    val HEADER_REGEX =
      """\[Recomposition #(\d+)] (\S+)(?:\s+\(tag:\s+(.+?)\))?""".toRegex()
    val PARAM_REGEX =
      """\s*[├└]─\s+(\w+):\s+(.+?)\s+(changed|stable|unstable)(?:\s+\((.+)\))?""".toRegex()
    val UNSTABLE_SUMMARY_REGEX =
      """\s*[├└]─\s+Unstable parameters:\s+\[(.+)]""".toRegex()
  }

  private var currentName: String? = null
  private var currentTag: String = ""
  private var currentCount: Int = 0
  private var currentParams: MutableList<ParsedParameterEntry> = mutableListOf()
  private var currentUnstable: MutableList<String> = mutableListOf()

  /**
   * Feed a single logcat message line (after stripping the `D/Recomposition: ` prefix).
   */
  fun feedLine(line: String) {
    val headerMatch = HEADER_REGEX.find(line)
    if (headerMatch != null) {
      emitCurrent()
      currentCount = headerMatch.groupValues[1].toIntOrNull() ?: 0
      currentName = headerMatch.groupValues[2]
      currentTag = headerMatch.groupValues[3]
      currentParams = mutableListOf()
      currentUnstable = mutableListOf()
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
      ),
    )
    currentName = null
  }
}
