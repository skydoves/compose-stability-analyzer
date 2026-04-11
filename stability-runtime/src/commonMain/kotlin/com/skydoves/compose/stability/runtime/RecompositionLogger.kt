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

import kotlin.concurrent.Volatile

/**
 * Logger interface for recomposition tracing.
 *
 * Implement this interface to customize how recomposition information is logged.
 *
 * Example custom logger:
 * ```kotlin
 * class FirebaseRecompositionLogger : RecompositionLogger {
 *   override fun log(event: RecompositionEvent) {
 *     Firebase.analytics.logEvent("recomposition") {
 *       param("name", event.composableName)
 *       param("count", event.recompositionCount.toLong())
 *       param("unstable", event.unstableParameters.joinToString())
 *     }
 *   }
 * }
 *
 * // In Application.onCreate()
 * ComposeStabilityAnalyzer.setLogger(FirebaseRecompositionLogger())
 * ```
 */
public interface RecompositionLogger {
  /**
   * Called when a traced composable recomposes and meets the threshold.
   *
   * @param event The recomposition event containing all tracking information.
   */
  public fun log(event: RecompositionEvent)
}

/**
 * Represents a single recomposition event for a traced composable function.
 *
 * @property composableName The simple name of the composable function (e.g., "UserProfile")
 * @property tag Custom tag from @TraceRecomposition annotation (empty string if not set)
 * @property recompositionCount Number of times this composable has recomposed
 * @property parameterChanges List of all parameters with their change status
 * @property unstableParameters List of parameter names that are unstable
 * @property stateChanges List of internal state variable changes (empty when traceStates is false)
 */
public data class RecompositionEvent(
  val composableName: String,
  val tag: String,
  val recompositionCount: Int,
  val parameterChanges: List<ParameterChange>,
  val unstableParameters: List<String>,
  val stateChanges: List<StateChange> = emptyList(),
)

/**
 * Represents the change status of a single parameter between recompositions.
 *
 * @property name Parameter name
 * @property type Parameter type as string
 * @property oldValue Previous value (null on first recomposition)
 * @property newValue Current value
 * @property changed Whether the value changed from previous recomposition
 * @property stable Whether this parameter type is considered stable by Compose
 */
public data class ParameterChange(
  val name: String,
  val type: String,
  val oldValue: Any?,
  val newValue: Any?,
  val changed: Boolean,
  val stable: Boolean,
)

/**
 * Represents the change status of an internal state variable between recompositions.
 *
 * @property name State variable name
 * @property type State value type as string
 * @property oldValue Previous value (null on first recomposition)
 * @property newValue Current value
 * @property changed Whether the value changed from previous recomposition
 */
public data class StateChange(
  val name: String,
  val type: String,
  val oldValue: Any?,
  val newValue: Any?,
  val changed: Boolean,
)

/**
 * Default logger implementation that logs to platform-specific output.
 *
 * - Android: Uses android.util.Log
 * - JVM: Uses System.out
 *
 * Output format:
 * ```
 * [Recomposition #5] UserProfile (tag: user-screen)
 *   ├─ user: User changed (User@abc123 → User@def456)
 *   ├─ count: Int stable (42)
 *   ├─ onClick: () -> Unit stable
 *   └─ Unstable parameters: [user]
 * ```
 */
public expect class DefaultRecompositionLogger() : RecompositionLogger {
  override fun log(event: RecompositionEvent)
}

/**
 * Singleton provider for the global recomposition logger.
 *
 * This class is thread-safe and can be safely accessed from multiple threads.
 *
 * Usage:
 * ```kotlin
 * // Set custom logger (typically in Application.onCreate() or MainActivity)
 * ComposeStabilityAnalyzer.setLogger(MyCustomLogger())
 *
 * // Enable/disable logging
 * ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
 *
 * // Get current logger
 * val logger = ComposeStabilityAnalyzer.getLogger()
 * ```
 */
public object ComposeStabilityAnalyzer {
  @Volatile
  private var logger: RecompositionLogger = DefaultRecompositionLogger()

  @Volatile
  private var enabled: Boolean = true

  /**
   * Sets a custom logger implementation.
   *
   * Thread-safe: This method uses synchronization to ensure the logger
   * is updated atomically.
   *
   * @param customLogger The logger to use for all recomposition tracing.
   */
  public fun setLogger(customLogger: RecompositionLogger) {
    logger = customLogger
  }

  /**
   * Gets the current logger instance.
   *
   * Thread-safe: Reads from a volatile field.
   *
   * @return The current recomposition logger.
   */
  public fun getLogger(): RecompositionLogger = logger

  /**
   * Enables or disables recomposition logging globally.
   *
   * Thread-safe: This method uses synchronization to ensure the enabled
   * flag is updated atomically.
   *
   * Use this to disable logging in production builds:
   * ```kotlin
   * ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
   * ```
   *
   * @param enabled Whether logging should be enabled.
   */
  public fun setEnabled(enabled: Boolean) {
    this.enabled = enabled
  }

  /**
   * Checks if logging is currently enabled.
   *
   * Thread-safe: Reads from a volatile field.
   *
   * @return True if logging is enabled, false otherwise.
   */
  public fun isEnabled(): Boolean = enabled

  /**
   * Internal method called by generated code to log events.
   * Only logs if enabled.
   *
   * Thread-safe: Reads volatile fields once to ensure consistency
   * and avoid race conditions.
   */
  internal fun logEvent(event: RecompositionEvent) {
    val currentEnabled = enabled
    val currentLogger = logger

    if (currentEnabled) {
      currentLogger.log(event)
    }
  }
}
