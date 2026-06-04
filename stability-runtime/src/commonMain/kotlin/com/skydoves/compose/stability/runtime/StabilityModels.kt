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
 * Represents the stability of a parameter or type.
 */
public enum class ParameterStability {
  /**
   * The type is known to be stable at compile time.
   */
  STABLE,

  /**
   * The type is known to be unstable at compile time.
   */
  UNSTABLE,

  /**
   * The stability can only be determined at runtime.
   */
  RUNTIME,

  /**
   * The stability cannot be determined statically because the concrete type is
   * unknown — e.g. an interface or a non-final (open/abstract) class whose actual
   * implementation could be anything. Mirrors Compose 2.4.0's `Stability.Unknown`,
   * which the compiler now infers for interfaces and non-final classes by default.
   * Treated as not statically stable (a composable with an UNKNOWN parameter is not
   * skippable unless strong skipping applies).
   */
  UNKNOWN,
}

/**
 * Contains stability information about a composable function parameter.
 */
public data class ParameterStabilityInfo(
  val name: String,
  val type: String,
  val stability: ParameterStability,
  val reason: String? = null,
)

/**
 * Types of receivers that can affect composable stability.
 */
public enum class ReceiverKind {
  /**
   * Extension receiver (e.g., String in "fun String.show()")
   */
  EXTENSION,

  /**
   * Dispatch receiver (e.g., MyClass in "class MyClass { fun show() }")
   */
  DISPATCH,

  /**
   * Context receiver (e.g., MyContext in "context(MyContext) fun show()")
   */
  CONTEXT,
}

/**
 * Contains stability information about a receiver.
 */
public data class ReceiverStabilityInfo(
  val type: String,
  val stability: ParameterStability,
  val reason: String? = null,
  val receiverKind: ReceiverKind,
)

/**
 * Contains stability analysis information about a composable function.
 */
public data class ComposableStabilityInfo(
  val name: String,
  val fqName: String,
  val isRestartable: Boolean,
  val isSkippable: Boolean,
  val isReadonly: Boolean,
  val parameters: List<ParameterStabilityInfo>,
  /**
   * Indicates if this composable is only skippable due to strong skipping mode.
   * When true, the composable has unstable parameters but is considered skippable
   * because strong skipping mode treats all parameters as stable.
   */
  val isSkippableInStrongSkippingMode: Boolean = false,
  /**
   * List of receivers (extension, dispatch, context) and their stability.
   * Empty if the composable has no receivers.
   */
  val receivers: List<ReceiverStabilityInfo> = emptyList(),
) {
  /**
   * Returns true if any parameter is unstable.
   */
  public fun hasUnstableParameters(): Boolean =
    parameters.any { it.stability == ParameterStability.UNSTABLE }

  /**
   * Returns a list of unstable parameters.
   */
  public fun getUnstableParameters(): List<ParameterStabilityInfo> =
    parameters.filter { it.stability == ParameterStability.UNSTABLE }

  /**
   * Returns true if any receiver is unstable.
   */
  public fun hasUnstableReceivers(): Boolean =
    receivers.any { it.stability == ParameterStability.UNSTABLE }

  /**
   * Returns a list of unstable receivers.
   */
  public fun getUnstableReceivers(): List<ReceiverStabilityInfo> =
    receivers.filter { it.stability == ParameterStability.UNSTABLE }

  /**
   * Returns a human-readable summary of the stability status.
   */
  public fun getSummary(): String = buildString {
    append(name)
    append(": ")
    when {
      isSkippable -> append("✅ Skippable")
      isRestartable -> append("⚠️ Restartable but not skippable")
      else -> append("❌ Not restartable")
    }

    if (hasUnstableParameters()) {
      append(" (unstable: ")
      append(getUnstableParameters().joinToString { it.name })
      append(")")
    }
  }
}

/**
 * Annotation to explicitly mark a class as stable for the analyzer.
 *
 * Note: This annotation is currently not implemented in the compiler plugin.
 * It is reserved for future use.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class StableForAnalysis
