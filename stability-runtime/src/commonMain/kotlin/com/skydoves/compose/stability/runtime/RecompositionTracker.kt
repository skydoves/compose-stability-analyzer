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
 *       threshold = 3,
 *       fqName = "com.example.UserProfile",
 *       isAutoTraced = false
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
 * @property fqName Fully qualified name of the composable (empty when unavailable)
 * @property isAutoTraced True when instrumented by the trace-all compiler mode
 */
public class RecompositionTracker(
  private val composableName: String,
  private val tag: String = "",
  private val threshold: Int = 1,
  private val fqName: String = "",
  private val isAutoTraced: Boolean = false,
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
    // Trace-all instruments every composable, so the disabled path must allocate nothing.
    if (!ComposeStabilityAnalyzer.isEnabled()) return
    val hasPrevious = name in previousParameters
    val previousValue = previousParameters[name]
    val changed = hasPrevious && previousValue != value
    // Identity comparison mirrors strong skipping's `===` check, which only applies to UNSTABLE
    // params (stable params are compared by equals(), so their identity is irrelevant). Gating on
    // !isStable also avoids autoboxing false positives for stable primitives like Int/String,
    // where a boxed value outside the small-integer cache is a new instance despite being equal.
    // Guarded by hasPrevious + !changed so the first recomposition and genuine changes never report it.
    val referenceChanged = !isStable && hasPrevious && !changed && previousValue !== value

    currentParameters[name] = TrackedParameter(
      name = name,
      type = type,
      oldValue = previousValue,
      newValue = value,
      changed = changed,
      stable = isStable,
      referenceChanged = referenceChanged,
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
  public fun trackState(name: String, type: String, value: Any?, stateObject: Any? = null) {
    if (!ComposeStabilityAnalyzer.isEnabled()) return
    if (stateObject != null) {
      // Activates the Snapshot write observer's stack capture (kept off until a traceStates
      // consumer actually exists, to avoid overhead for the common traceStates=false case).
      ComposeStabilityAnalyzer.markStateTracingActive()
    }
    val hasPrevious = name in previousStateValues
    val previousValue = previousStateValues[name]
    val changed = hasPrevious && previousValue != value

    currentStates[name] = TrackedState(
      name = name,
      type = type,
      oldValue = previousValue,
      newValue = value,
      changed = changed,
      stateObject = stateObject,
    )
  }

  /**
   * Records the duration of the current recomposition.
   * Called by generated code with the start time from System.nanoTime().
   *
   * @param startTimeNanos The System.nanoTime() value captured at composable entry
   */
  public fun recordDuration(startTimeNanos: Long) {
    if (!ComposeStabilityAnalyzer.isEnabled()) return
    lastDurationNanos = currentNanoTime() - startTimeNanos
  }

  /**
   * Increments recomposition count and logs if threshold is met.
   *
   * This method should be called after all parameters are tracked.
   */
  public fun logIfThresholdMet() {
    recompositionCount++

    // Check before building any ParameterChange/RecompositionEvent objects: with trace-all,
    // this runs for every composable on every recomposition even when logging is off.
    if (!ComposeStabilityAnalyzer.isEnabled()) {
      lastDurationNanos = 0L
      currentParameters.clear()
      currentStates.clear()
      return
    }

    if (recompositionCount >= threshold) {
      val parameterChanges = currentParameters.values.map { tracked ->
        ParameterChange(
          name = tracked.name,
          type = tracked.type,
          oldValue = tracked.oldValue,
          newValue = tracked.newValue,
          changed = tracked.changed,
          stable = tracked.stable,
          referenceChanged = tracked.referenceChanged,
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
          // Only attribute a write-site to states that actually changed this cycle, so a stale
          // site from a prior cycle is never shown.
          writeSite = if (tracked.changed) writeSiteFor(tracked.stateObject) else null,
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
        fqName = fqName,
        isAutoTraced = isAutoTraced,
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
    val referenceChanged: Boolean,
  )

  private data class TrackedState(
    val name: String,
    val type: String,
    val oldValue: Any?,
    val newValue: Any?,
    val changed: Boolean,
    val stateObject: Any?,
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
 * @param fqName Fully qualified name of the composable (empty when unavailable)
 * @param isAutoTraced True when instrumented by the trace-all compiler mode
 * @return A new RecompositionTracker instance
 */
public fun createRecompositionTracker(
  composableName: String,
  tag: String = "",
  threshold: Int = 1,
  fqName: String = "",
  isAutoTraced: Boolean = false,
): RecompositionTracker = RecompositionTracker(composableName, tag, threshold, fqName, isAutoTraced)
