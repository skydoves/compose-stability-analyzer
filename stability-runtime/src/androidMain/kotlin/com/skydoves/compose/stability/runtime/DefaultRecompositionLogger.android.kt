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

    Log.d(
      tag,
      "[Recomposition #${event.recompositionCount}] ${event.composableName}$tagSuffix",
    )

    // Determine what summary lines we'll have after parameter changes
    val internalState = event.unstableParameters.contains("_internal_state")
    val regularUnstable = event.unstableParameters.filter { it != "_internal_state" }
    val hasUnstableSummary = regularUnstable.isNotEmpty()
    val hasInternalState = internalState

    // Log parameter changes
    event.parameterChanges.forEachIndexed { index, change ->
      val isLastParam = index == event.parameterChanges.size - 1
      // Only use └─ if this is the last param AND there are no summary lines after
      val isLast = isLastParam && !hasUnstableSummary && !hasInternalState
      val prefix = if (isLast) "  └─" else "  ├─"

      val status = when {
        change.changed -> {
          val oldStr = safeToString(change.oldValue)
          val newStr = safeToString(change.newValue)
          "changed ($oldStr → $newStr)"
        }

        change.stable -> "stable (${safeToString(change.newValue)})"
        else -> "unstable (${safeToString(change.newValue)})"
      }

      Log.d(tag, "$prefix ${change.name}: ${change.type} $status")
    }

    // Log unstable parameters summary
    if (hasUnstableSummary) {
      // Use └─ only if this is the last line (no internal state message follows)
      val prefix = if (hasInternalState) "  ├─" else "  └─"
      Log.d(tag, "$prefix Unstable parameters: $regularUnstable")
    }
    if (hasInternalState) {
      Log.d(
        tag,
        "  └─ ⚡ Internal state change (remember/derivedState) triggered recomposition",
      )
    }
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
