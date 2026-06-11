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

/**
 * WebAssembly JavaScript implementation of DefaultRecompositionLogger that uses println.
 *
 * Example output:
 * ```
 * [Recomposition #3] UserProfile (tag: user-screen)
 *   ├─ user: User changed (User@abc123 → User@def456)
 *   ├─ count: Int stable (42)
 *   ├─ onClick: () -> Unit stable
 *   └─ Unstable parameters: [user]
 * ```
 */
public actual class DefaultRecompositionLogger : RecompositionLogger {
  public actual constructor()

  actual override fun log(event: RecompositionEvent) {
    val tagSuffix = if (event.tag.isNotEmpty()) " (tag: ${event.tag})" else ""
    val durationStr = if (event.durationNanos > 0) {
      val ms = event.durationNanos / 1_000_000
      val fraction = (event.durationNanos % 1_000_000) / 10_000
      " ($ms.${fraction.toString().padStart(2, '0')}ms)"
    } else {
      ""
    }
    // Trailing tokens only: older parsers match the header groups in strict order and simply
    // ignore anything after the duration, so (fq:) and (auto) must never appear earlier.
    val fqSuffix = if (event.fqName.isNotEmpty()) " (fq: ${event.fqName})" else ""
    val autoSuffix = if (event.isAutoTraced) " (auto)" else ""

    println(
      "[Recomposition #${event.recompositionCount}] " +
        "${event.composableName}$tagSuffix$durationStr$fqSuffix$autoSuffix",
    )

    val lines = buildTreeLines(event)
    lines.forEachIndexed { index, line ->
      val prefix = if (index == lines.size - 1) "  └─" else "  ├─"
      println("$prefix $line")
    }
  }

  private fun buildTreeLines(event: RecompositionEvent): List<String> {
    val lines = mutableListOf<String>()

    event.parameterChanges.forEach { change ->
      val status = when {
        change.changed -> {
          val oldStr = safeToString(change.oldValue)
          val newStr = safeToString(change.newValue)
          "changed ($oldStr → $newStr)"
        }
        // Equals-equal but a new instance: emit the old → new arrow on the existing
        // stable/unstable token (no new token) so the IDE can detect a reference-only
        // change while staying backward/forward compatible with older parsers.
        change.referenceChanged -> {
          val oldStr = safeToString(change.oldValue)
          val newStr = safeToString(change.newValue)
          val token = if (change.stable) "stable" else "unstable"
          "$token ($oldStr → $newStr)"
        }
        change.stable -> "stable (${safeToString(change.newValue)})"
        else -> "unstable (${safeToString(change.newValue)})"
      }
      lines.add("[param] ${change.name}: ${change.type} $status")
    }

    event.stateChanges.filter { it.changed }.forEach { change ->
      val oldStr = safeToString(change.oldValue)
      val newStr = safeToString(change.newValue)
      val site = change.writeSite?.let { " ← $it" } ?: ""
      lines.add("[state] ${change.name}: ${change.type} changed ($oldStr → $newStr)$site")
    }

    if (event.unstableParameters.isNotEmpty()) {
      lines.add("Unstable parameters: ${event.unstableParameters}")
    }

    val changedStates = event.stateChanges.filter { it.changed }.map { it.name }
    if (changedStates.isNotEmpty()) {
      lines.add("State changes: $changedStates")
    }

    return lines
  }

  /**
   * Safely converts a value to string, handling function types and reflection errors.
   * Falls back to a simple representation if toString() throws an exception.
   */
  private fun safeToString(value: Any?): String {
    if (value == null) return "null"

    return try {
      value.toString()
    } catch (e: Throwable) {
      // Fallback for any toString() failures (including reflection errors)
      // On Wasm, we don't have javaClass.simpleName, so use a simpler approach
      "Object@${value.hashCode().toString(16)}"
    }
  }
}
