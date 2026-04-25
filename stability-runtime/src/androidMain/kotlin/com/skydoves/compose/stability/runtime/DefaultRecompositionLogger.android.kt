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
package com.skydoves.compose.stability.runtime

import android.util.Log

/**
 * Android implementation of DefaultRecompositionLogger that uses android.util.Log.
 *
 * Logs appear in Logcat with tag "Recomposition".
 *
 * Example output:
 * ```
 * D/Recomposition: [Recomposition #3] UserProfile (tag: user-screen)
 * D/Recomposition:   ├─ user: User changed (User@abc123 → User@def456)
 * D/Recomposition:   ├─ count: Int stable (42)
 * D/Recomposition:   ├─ onClick: () -> Unit stable
 * D/Recomposition:   └─ Unstable parameters: [user]
 * ```
 */
public actual class DefaultRecompositionLogger : RecompositionLogger {
  public actual constructor()

  private val tag = "Recomposition"

  actual override fun log(event: RecompositionEvent) {
    val tagSuffix = if (event.tag.isNotEmpty()) " (tag: ${event.tag})" else ""
    val durationStr = if (event.durationNanos > 0) {
      " (%.2fms)".format(event.durationNanos / 1_000_000.0)
    } else {
      ""
    }

    Log.d(
      tag,
      "[Recomposition #${event.recompositionCount}] " +
        "${event.composableName}$tagSuffix$durationStr",
    )

    // Collect all tree lines, then apply └─ to the last one
    val lines = buildTreeLines(event)
    lines.forEachIndexed { index, line ->
      val prefix = if (index == lines.size - 1) "  └─" else "  ├─"
      Log.d(tag, "$prefix $line")
    }
  }

  private fun buildTreeLines(event: RecompositionEvent): List<String> {
    val lines = mutableListOf<String>()

    // Parameter changes
    event.parameterChanges.forEach { change ->
      val status = when {
        change.changed -> {
          val oldStr = safeToString(change.oldValue)
          val newStr = safeToString(change.newValue)
          "changed ($oldStr → $newStr)"
        }
        change.stable -> "stable (${safeToString(change.newValue)})"
        else -> "unstable (${safeToString(change.newValue)})"
      }
      lines.add("[param] ${change.name}: ${change.type} $status")
    }

    // State changes
    event.stateChanges.filter { it.changed }.forEach { change ->
      val oldStr = safeToString(change.oldValue)
      val newStr = safeToString(change.newValue)
      lines.add("[state] ${change.name}: ${change.type} changed ($oldStr → $newStr)")
    }

    // Unstable parameters summary
    if (event.unstableParameters.isNotEmpty()) {
      lines.add("Unstable parameters: ${event.unstableParameters}")
    }

    // State changes summary
    val changedStates = event.stateChanges.filter { it.changed }.map { it.name }
    if (changedStates.isNotEmpty()) {
      lines.add("State changes: $changedStates")
    }

    return lines
  }

  /**
   * Safely converts a value to string, handling reflection errors.
   * Falls back to a simple representation if toString() throws an exception.
   */
  private fun safeToString(value: Any?): String {
    if (value == null) return "null"

    return try {
      value.toString()
    } catch (e: Throwable) {
      // Fallback for any toString() failures (including reflection errors)
      "${value.javaClass.simpleName}@${value.hashCode().toString(16)}"
    }
  }
}
