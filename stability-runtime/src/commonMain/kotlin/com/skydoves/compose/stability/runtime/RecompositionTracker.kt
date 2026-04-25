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
 * Tracker for monitoring recomposition of a single composable function.
 *
 * This class is instantiated by compiler-generated code and should not be used directly.
 * It tracks parameter values across recompositions and logs changes when threshold is met.
 *
 * Generated code example:
 * ```kotlin
 * @TraceRecomposition(tag = "profile", threshold = 3)
 * @Composable
 * fun UserProfile(user: User, count: Int) {
 *   val _tracker = remember {
 *     createRecompositionTracker(
 *       composableName = "UserProfile",
 *       tag = "profile",
 *       threshold = 3
 *     )
 *   }
 *   _tracker.trackParameter("user", "User", user, isStable = false)
 *   _tracker.trackParameter("count", "Int", count, isStable = true)
 *   _tracker.logIfThresholdMet()
 *
 *   // Original function body...
 * }
 * ```
 *
 * @property composableName The name of the composable function being tracked
 * @property tag Custom tag from @TraceRecomposition annotation
 * @property threshold Only log after this many recompositions
 */
public class RecompositionTracker(
  private val composableName: String,
  private val tag: String = "",
  private val threshold: Int = 1,
) {
  private var recompositionCount = 0
  private var lastDurationNanos: Long = 0L
  private val currentParameters = mutableMapOf<String, TrackedParameter>()
  private val previousParameters = mutableMapOf<String, Any?>()
  private val currentStates = mutableMapOf<String, TrackedState>()
  private val previousStateValues = mutableMapOf<String, Any?>()

  /**
   * Tracks a parameter value for this recomposition.
   *
   * This method is called by generated code for each parameter of the composable.
   *
   * @param name Parameter name
   * @param type Parameter type as string
   * @param value Current parameter value
   * @param isStable Whether this parameter type is stable according to Compose
   */
  public fun trackParameter(name: String, type: String, value: Any?, isStable: Boolean) {
    val hasPrevious = name in previousParameters
    val previousValue = previousParameters[name]
    val changed = hasPrevious && previousValue != value

    currentParameters[name] = TrackedParameter(
      name = name,
      type = type,
      oldValue = previousValue,
      newValue = value,
      changed = changed,
      stable = isStable,
    )
  }

  /**
   * Tracks an internal state variable value for this recomposition.
   *
   * This method is called by generated code for each detected state variable
   * when `@TraceRecomposition(traceStates = true)` is used.
   *
   * @param name State variable name
   * @param type State value type as string
   * @param value Current state value
   */
  public fun trackState(name: String, type: String, value: Any?) {
    val hasPrevious = name in previousStateValues
    val previousValue = previousStateValues[name]
    val changed = hasPrevious && previousValue != value

    currentStates[name] = TrackedState(
      name = name,
      type = type,
      oldValue = previousValue,
      newValue = value,
      changed = changed,
    )
  }

  /**
   * Records the duration of the current recomposition.
   * Called by generated code with the start time from System.nanoTime().
   *
   * @param startTimeNanos The System.nanoTime() value captured at composable entry
   */
  public fun recordDuration(startTimeNanos: Long) {
    lastDurationNanos = currentNanoTime() - startTimeNanos
  }

  /**
   * Increments recomposition count and logs if threshold is met.
   *
   * This method should be called after all parameters are tracked.
   */
  public fun logIfThresholdMet() {
    recompositionCount++

    if (recompositionCount >= threshold) {
      val parameterChanges = currentParameters.values.map { tracked ->
        ParameterChange(
          name = tracked.name,
          type = tracked.type,
          oldValue = tracked.oldValue,
          newValue = tracked.newValue,
          changed = tracked.changed,
          stable = tracked.stable,
        )
      }

      val unstableParameters = currentParameters.values
        .filter { !it.stable }
        .map { it.name }

      val stateChanges = currentStates.values.map { tracked ->
        StateChange(
          name = tracked.name,
          type = tracked.type,
          oldValue = tracked.oldValue,
          newValue = tracked.newValue,
          changed = tracked.changed,
        )
      }

      val event = RecompositionEvent(
        composableName = composableName,
        tag = tag,
        recompositionCount = recompositionCount,
        parameterChanges = parameterChanges,
        unstableParameters = unstableParameters,
        stateChanges = stateChanges,
        durationNanos = lastDurationNanos,
      )

      ComposeStabilityAnalyzer.logEvent(event)
    }

    lastDurationNanos = 0L

    // Update previous values for next recomposition
    currentParameters.forEach { (name, tracked) ->
      previousParameters[name] = tracked.newValue
    }
    currentParameters.clear()
    currentStates.forEach { (name, tracked) ->
      previousStateValues[name] = tracked.newValue
    }
    currentStates.clear()
  }

  /**
   * Internal data class for tracking a parameter during a single recomposition.
   */
  private data class TrackedParameter(
    val name: String,
    val type: String,
    val oldValue: Any?,
    val newValue: Any?,
    val changed: Boolean,
    val stable: Boolean,
  )

  private data class TrackedState(
    val name: String,
    val type: String,
    val oldValue: Any?,
    val newValue: Any?,
    val changed: Boolean,
  )
}

/**
 * Factory function to create a RecompositionTracker instance.
 *
 * This function is called by compiler-generated code and should not be used directly.
 *
 * @param composableName The name of the composable function being tracked
 * @param tag Custom tag from @TraceRecomposition annotation
 * @param threshold Only log after this many recompositions
 * @return A new RecompositionTracker instance
 */
public fun createRecompositionTracker(
  composableName: String,
  tag: String = "",
  threshold: Int = 1,
): RecompositionTracker = RecompositionTracker(composableName, tag, threshold)
