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
package com.skydoves.compose.stability.idea.k2

import com.skydoves.compose.stability.idea.StabilityConstants
import com.skydoves.compose.stability.runtime.ParameterStability

/**
 * K2 Analysis API representation of type stability.
 *
 * **Stability Combination Rules:**
 * When combining multiple property stabilities:
 * - Stable + Stable = Stable
 * - Stable + Unstable = Unstable (or vice versa)
 * - Unstable + Unstable = Unstable
 * - Stable + Parameter = Combined([Parameter]) (filter out Stable)
 * - Stable + Runtime = Combined([Runtime]) (filter out Stable)
 * - Parameter + Parameter = Combined([Parameter, Parameter])
 * - Runtime + Parameter = Combined([Runtime, Parameter])
 *
 * Key insight: Stable properties don't affect the outcome in mixed scenarios,
 * so they are filtered out from Combined types.
 */
internal sealed class KtStability {
  /**
   * Stability is certain (known stable or unstable).
   *
   * @param stable true if stable, false if unstable
   * @param reason human-readable reason for this stability
   */
  internal data class Certain(
    val stable: Boolean,
    val reason: String,
  ) : KtStability()

  /**
   * Stability determined at runtime based on @StabilityInferred.
   * The actual stability depends on the runtime class implementation.
   *
   * @param className the fully qualified name of the class
   * @param reason optional custom reason message
   */
  internal data class Runtime(
    val className: String,
    val reason: String? = null,
  ) : KtStability()

  /**
   * Stability is unknown (e.g., interface types, abstract types).
   *
   * @param declarationName the name of the declaration
   */
  internal data class Unknown(
    val declarationName: String,
  ) : KtStability()

  /**
   * Stability depends on a type parameter.
   *
   * @param parameterName the name of the type parameter
   */
  internal data class Parameter(
    val parameterName: String,
  ) : KtStability()

  /**
   * Combined stability from multiple sources (e.g., class with multiple properties).
   * The overall stability is the combination of all element stabilities.
   *
   * @param elements set of individual stabilities to combine
   */
  internal data class Combined(
    val elements: Set<KtStability>,
  ) : KtStability()

  /**
   * Convert K2 stability to runtime stability enum.
   */
  internal fun toParameterStability(): ParameterStability {
    return when (this) {
      is Certain -> if (stable) ParameterStability.STABLE else ParameterStability.UNSTABLE
      is Runtime -> ParameterStability.RUNTIME
      is Unknown -> ParameterStability.UNKNOWN
      is Parameter -> ParameterStability.RUNTIME
      is Combined -> {
        val stabilities = elements.map { it.toParameterStability() }
        when {
          stabilities.all { it == ParameterStability.STABLE } -> ParameterStability.STABLE
          stabilities.any { it == ParameterStability.UNSTABLE } -> ParameterStability.UNSTABLE
          else -> ParameterStability.RUNTIME
        }
      }
    }
  }

  /**
   * Get human-readable reason for this stability.
   */
  internal fun getReasonString(): String {
    return when (this) {
      is Certain -> reason
      is Runtime ->
        reason ?: "${StabilityConstants.Messages.RUNTIME_STABILITY_CHECK} for $className"
      is Unknown ->
        "${StabilityConstants.Messages.UNKNOWN_STABILITY} for " +
          "$declarationName (${StabilityConstants.Messages.INTERFACE_OR_ABSTRACT})"

      is Parameter -> "Stability depends on type parameter $parameterName"
      is Combined -> {
        val reasons = elements.mapNotNull {
          val r = it.getReasonString()
          r.ifBlank { null }
        }
        reasons.joinToString("; ")
      }
    }
  }

  /**
   * Check if this stability is definitely stable.
   */
  internal fun isStable(): Boolean {
    return when (this) {
      is Certain -> stable
      is Combined -> elements.all { it.isStable() }
      else -> false
    }
  }

  /**
   * Check if this stability is definitely unstable.
   */
  internal fun isUnstable(): Boolean {
    return when (this) {
      is Certain -> !stable
      is Combined -> elements.any { it.isUnstable() }
      else -> false
    }
  }
}
