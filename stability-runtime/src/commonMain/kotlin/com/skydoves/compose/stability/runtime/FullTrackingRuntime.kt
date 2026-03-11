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

import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.State
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup

// ── Platform-specific helpers ─────────────────────────────────────────

/**
 * Platform-specific implementation for extracting comparable values from objects.
 * On JVM, uses reflection to access state holder values.
 * On other platforms, returns null.
 */
internal expect fun extractComparableValuePlatform(obj: Any): Any?

/**
 * Platform-specific implementation for snapshotting slot values.
 * On JVM, uses reflection to extract values from state holders.
 * On other platforms, returns a simple string representation.
 */
internal expect fun snapshotSlotValuePlatform(value: Any): Any?

/**
 * Represents a single slot data item extracted from the composition.
 */
public data class SlotDataItem(
  val groupKey: Any?,
  val sourceInfo: String?,
  val slotIndex: Int,
  val value: Any?,
  val depth: Int,
)

/**
 * Represents a change detected between two recompositions.
 */
public sealed class DataChange {
  public data class Added(val item: SlotDataItem) : DataChange()
  public data class Removed(val item: SlotDataItem) : DataChange()
  public data class Modified(val old: SlotDataItem, val new: SlotDataItem) : DataChange()
}

/**
 * Tracker for full recomposition mode.
 *
 * Receives parameter names and values directly from compiler-injected code.
 * Compares parameter values between recompositions to detect what triggered
 * the recomposition.
 *
 * In deep tracking mode, also analyzes CompositionData to detect internal
 * state changes (remember values, derivedState, etc.).
 *
 * @param scopeId The Compose group key (Int) from startRestartGroup, used for scope isolation
 */
public class ScopeDataTracker(
  private val scopeId: Int,
  private val composableName: String,
  private val tag: String,
  private val threshold: Int,
) {
  private var previousParamSnapshot: Map<String, String> = emptyMap()
  private var previousSlotData: List<SlotDataItem> = emptyList()
  private var recompositionCount: Int = 0

  /** Pending parameter changes to be combined with deep tracking results */
  private var pendingParamChanges: List<ParameterChange>? = null

  /** Identity for fast lookup in CompositionData */
  public var identity: Any? = null

  /**
   * Track a recomposition with explicit parameter names and values.
   *
   * @param composer The Composer instance (reserved for future deep tracking)
   * @param paramNames Array of parameter names (injected by compiler)
   * @param paramValues Array of parameter values (injected by compiler)
   */
  public fun trackRecomposition(
    composer: Composer,
    paramNames: Array<String>,
    paramValues: Array<out Any?>,
  ) {
    recompositionCount++

    // Snapshot explicit parameters
    val currentParamSnapshot = mutableMapOf<String, String>()
    for (i in paramNames.indices) {
      val name = paramNames[i]
      val value = paramValues.getOrNull(i)
      currentParamSnapshot[name] = snapshotValue(value)
    }

    // Detect changes after threshold
    if (previousParamSnapshot.isNotEmpty() && recompositionCount >= threshold) {
      val paramChanges = mutableListOf<ParameterChange>()

      for (name in paramNames) {
        val oldVal = previousParamSnapshot[name]
        val newVal = currentParamSnapshot[name]
        val changed = oldVal != newVal
        paramChanges.add(
          ParameterChange(
            name = name,
            type = "param",
            oldValue = oldVal,
            newValue = newVal,
            changed = changed,
            stable = false,
          ),
        )
      }

      // Check for removed params (unlikely but defensive)
      for (name in previousParamSnapshot.keys) {
        if (name !in currentParamSnapshot) {
          paramChanges.add(
            ParameterChange(
              name = name,
              type = "param",
              oldValue = previousParamSnapshot[name],
              newValue = null,
              changed = true,
              stable = false,
            ),
          )
        }
      }

      // Store pending changes - will be combined with deep tracking results
      pendingParamChanges = paramChanges
    }

    previousParamSnapshot = currentParamSnapshot
  }

  /**
   * Track a recomposition with deep slot data analysis.
   *
   * Called after composition completes, with full access
   * to CompositionData for detecting internal state changes.
   *
   * The scopeId is the Compose group key (Int) from startRestartGroup,
   * which uniquely identifies this composable's slot group.
   *
   * @param compositionData The CompositionData from currentComposer
   */
  public fun trackDeepRecomposition(compositionData: CompositionData) {
    // Find our group using the Compose group key (scopeId)
    // This key was extracted from startRestartGroup(key) by the compiler plugin
    val groupByIdentity = identity?.let { compositionData.find(it) }
    val groupByKey = findGroupByKey(compositionData, scopeId)

    val group = groupByIdentity ?: groupByKey

    // Collect slot data from our specific group or fall back to entire tree
    val currentSlotData = if (group != null) {
      collectAllSlotData(group)
    } else {
      // Fallback: collect from entire composition (less precise)
      collectAllSlotData(compositionData)
    }

    // Note: Deep tracking collects slot data but without scope isolation (key() wrapper),
    // it can only detect structural changes in the entire composition tree.
    // Value changes inside State objects aren't directly exposed by CompositionData.

    // Combine parameter changes with slot changes and log once
    val paramChanges = pendingParamChanges ?: emptyList()
    pendingParamChanges = null

    // Only analyze slots after first composition and when threshold is met
    val slotChanges = if (previousSlotData.isNotEmpty() && recompositionCount >= threshold) {
      val changes = detectSlotChanges(previousSlotData, currentSlotData)
      changes.map { change ->
        when (change) {
          is DataChange.Added -> ParameterChange(
            name = formatSlotName(change.item),
            type = "slot_added",
            oldValue = null,
            newValue = formatSlotValue(change.item.value),
            changed = true,
            stable = false,
          )
          is DataChange.Removed -> ParameterChange(
            name = formatSlotName(change.item),
            type = "slot_removed",
            oldValue = formatSlotValue(change.item.value),
            newValue = null,
            changed = true,
            stable = false,
          )
          is DataChange.Modified -> ParameterChange(
            name = formatSlotName(change.new),
            type = "slot_modified",
            oldValue = formatSlotValue(change.old.value),
            newValue = formatSlotValue(change.new.value),
            changed = true,
            stable = false,
          )
        }
      }
    } else {
      emptyList()
    }

    // Log combined event if we have any changes
    val allChanges = paramChanges + slotChanges
    if (allChanges.isNotEmpty() || (paramChanges.isEmpty() && recompositionCount >= threshold)) {
      val hasParamChanges = paramChanges.any { it.changed }
      val unstableParams = when {
        // If we have changed params, list them plus slot changes
        hasParamChanges -> {
          paramChanges.filter { it.changed }.map { it.name } + slotChanges.map { it.name }
        }
        // If we have slot changes but no param changes, just list the slot changes
        slotChanges.isNotEmpty() -> slotChanges.map { it.name }
        // No changes detected at all - mark as internal state change
        else -> listOf("_internal_state")
      }

      val event = RecompositionEvent(
        composableName = composableName,
        tag = tag,
        recompositionCount = recompositionCount,
        parameterChanges = allChanges,
        unstableParameters = unstableParams,
      )
      ComposeStabilityAnalyzer.logEvent(event)
    }

    previousSlotData = currentSlotData
  }

  /**
   * Detect changes between two slot data snapshots.
   */
  private fun detectSlotChanges(
    previous: List<SlotDataItem>,
    current: List<SlotDataItem>,
  ): List<DataChange> {
    val changes = mutableListOf<DataChange>()

    // Index by (depth, slotIndex) for comparison
    val prevMap = previous.associateBy { it.depth to it.slotIndex }
    val currMap = current.associateBy { it.depth to it.slotIndex }

    // Detect modifications and removals
    for ((key, prevItem) in prevMap) {
      val currItem = currMap[key]
      when {
        currItem == null -> changes.add(DataChange.Removed(prevItem))
        !valuesEqual(prevItem.value, currItem.value) -> {
          changes.add(DataChange.Modified(prevItem, currItem))
        }
      }
    }

    // Detect additions
    for ((key, currItem) in currMap) {
      if (key !in prevMap) {
        changes.add(DataChange.Added(currItem))
      }
    }

    return changes
  }

  /**
   * Compare two values for equality, with special handling for state holders.
   * Extracts actual values from State objects for comparison.
   */
  private fun valuesEqual(a: Any?, b: Any?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return a == b

    // For lambdas, only compare by reference
    if (a is Function<*> || b is Function<*>) return a === b

    // For State objects, compare their values
    if (a is State<*> && b is State<*>) {
      return try {
        a.value == b.value
      } catch (_: Throwable) {
        a === b
      }
    }

    // Try to extract comparable values from Compose internal types
    val aValue = extractComparableValue(a)
    val bValue = extractComparableValue(b)
    if (aValue != null && bValue != null && aValue !== a && bValue !== b) {
      return aValue == bValue
    }

    return a == b
  }

  /**
   * Extract a comparable value from a Compose internal object.
   * Handles MutableState, MutableIntState, etc.
   */
  private fun extractComparableValue(obj: Any): Any? {
    return extractComparableValuePlatform(obj)
  }

  private fun formatSlotValue(value: Any?): String {
    return when (value) {
      null -> "null"
      is State<*> -> try {
        "State(${value.value})"
      } catch (_: Throwable) { "State(?)" }
      is Function<*> -> "λ@${value.hashCode().toString(16)}"
      else -> try {
        val str = value.toString()
        if (str.length > 50) str.take(47) + "..." else str
      } catch (_: Throwable) { "?" }
    }
  }

  /**
   * Format a meaningful name for a slot item.
   *
   * Priority:
   * 1. Extract from sourceInfo (e.g., "C(remember)") if available
   * 2. Infer from value type and content
   * 3. Fallback to slot index with depth
   *
   * Note: sourceInfo is only populated by Compose Runtime in "tool mode"
   * (when Layout Inspector or similar tooling is attached). In normal
   * debug/release builds, sourceInfo is always null, so we rely on
   * type inference for meaningful slot names.
   */
  private fun formatSlotName(item: SlotDataItem): String {
    val sourceInfo = item.sourceInfo
    val value = item.value
    val depth = item.depth

    // 1. Try sourceInfo pattern: C(name)
    if (sourceInfo != null) {
      val nameMatch = Regex("""C\(([^)]+)\)""").find(sourceInfo)
      if (nameMatch != null) {
        val name = nameMatch.groupValues[1]
        return if (depth > 0) "$name@d$depth" else name
      }
    }

    // 2. Infer from value type and content
    val inferredName = inferSlotName(value, item.slotIndex)
    return if (depth > 0) "$inferredName@d$depth" else inferredName
  }

  /**
   * Infer a slot name from its value type and content.
   */
  private fun inferSlotName(value: Any?, slotIndex: Int): String {
    if (value == null) return "slot:$slotIndex"

    val className = value::class.simpleName ?: ""
    val valueStr = try { value.toString() } catch (_: Throwable) { "" }

    return when {
      // State holders
      className.contains("MutableIntState") || className.contains("IntState") -> "intState"
      className.contains("MutableLongState") || className.contains("LongState") -> "longState"
      className.contains("MutableFloatState") || className.contains("FloatState") -> "floatState"
      className.contains("MutableDoubleState") || className.contains("DoubleState") -> "doubleState"
      className.contains("MutableState") || className.contains("SnapshotState") -> "state"
      className.contains("DerivedState") -> "derivedState"

      // Derived/computed values (string containing "Derived")
      value is String && valueStr.startsWith("Derived:") -> "derivedValue"

      // UI elements
      className.contains("TextStringSimpleElement") -> "textElement"
      className.contains("LayoutNode") -> "layoutNode"
      className.contains("Modifier") -> "modifier"

      // Primitives with meaningful patterns
      value is Int -> "intSlot:$slotIndex"
      value is String && valueStr.length < 30 -> "strSlot:$slotIndex"

      // Default
      else -> "slot:$slotIndex"
    }
  }

  private fun snapshotValue(value: Any?): String {
    return when (value) {
      null -> "null"
      is State<*> -> try { value.value?.toString() ?: "null" } catch (_: Throwable) { "?" }
      is String -> value
      is Number -> value.toString()
      is Boolean -> value.toString()
      is Char -> value.toString()
      is Enum<*> -> value.toString()
      is Function<*> -> "λ@${value.hashCode().toString(16)}"
      else -> try { value.toString() } catch (_: Throwable) { "?" }
    }
  }
}

// ── Tree traversal helpers ────────────────────────────────────────────

/**
 * Find a [CompositionGroup] by key value.
 */
public fun findGroupByKey(compositionData: CompositionData, targetKey: Any): CompositionGroup? {
  for (group in compositionData.compositionGroups) {
    if (group.key == targetKey) return group
    findGroupByKeyRecursive(group, targetKey)?.let { return it }
  }
  return null
}

private fun findGroupByKeyRecursive(
  group: CompositionGroup,
  targetKey: Any,
): CompositionGroup? {
  for (child in group.compositionGroups) {
    if (child.key == targetKey) return child
    findGroupByKeyRecursive(child, targetKey)?.let { return it }
  }
  return null
}

// ── Public API helpers ────────────────────────────────────────────────

/**
 * Collect all slot data from a [CompositionData] tree.
 */
public fun collectAllSlotData(compositionData: CompositionData): List<SlotDataItem> {
  val result = mutableListOf<SlotDataItem>()
  for (group in compositionData.compositionGroups) {
    collectSlotDataRecursive(group, result, depth = 0)
  }
  return result
}

/**
 * Collect all slot data from a single [CompositionGroup] and its children.
 */
public fun collectAllSlotData(group: CompositionGroup): List<SlotDataItem> {
  val result = mutableListOf<SlotDataItem>()
  collectSlotDataRecursive(group, result, depth = 0)
  return result
}

private fun collectSlotDataRecursive(
  group: CompositionGroup,
  result: MutableList<SlotDataItem>,
  depth: Int,
) {
  if (depth > 30) return
  group.data.forEachIndexed { index, data ->
    // Snapshot the value at this point - extract values from state holders
    val snapshotValue = snapshotSlotValue(data)
    result.add(
      SlotDataItem(
        groupKey = group.key,
        sourceInfo = group.sourceInfo,
        slotIndex = index,
        value = snapshotValue,
        depth = depth,
      ),
    )
  }
  for (child in group.compositionGroups) {
    collectSlotDataRecursive(child, result, depth + 1)
  }
}

/**
 * Snapshot a slot value, extracting actual values from state holders.
 * This ensures we compare values, not object references.
 */
private fun snapshotSlotValue(value: Any?): Any? {
  if (value == null) return null

  // Handle State objects
  if (value is State<*>) {
    return try {
      value.value?.let { snapshotSlotValue(it) } ?: "State(null)"
    } catch (_: Throwable) {
      "State(?)"
    }
  }

  return snapshotSlotValuePlatform(value)
}

// ── Scope tracker cache ───────────────────────────────────────────────

private val scopeTrackerCache = mutableMapOf<Int, ScopeDataTracker>()

// ── Entry point (injected by compiler plugin) ─────────────────────────

/**
 * Entry point for full recomposition tracking, injected by the compiler plugin.
 *
 * NOT @Composable — receives the Composer as a regular parameter.
 * The compiler plugin passes the function's `$composer` parameter directly,
 * along with arrays of parameter names and values.
 *
 * @param scopeId The Compose group key (Int) extracted from startRestartGroup call
 */
public fun TraceFullRecomposition(
  scopeId: Int,
  composableName: String,
  tag: String,
  threshold: Int,
  composer: Composer,
  paramNames: Array<out Any?>,
  paramValues: Array<out Any?>,
) {
  val tracker = scopeTrackerCache.getOrPut(scopeId) {
    ScopeDataTracker(scopeId, composableName, tag, threshold)
  }
  val names = Array(paramNames.size) { paramNames[it]?.toString() ?: "" }
  tracker.trackRecomposition(composer, names, paramValues)
}

/**
 * Entry point for deep slot data tracking, called from SideEffect.
 *
 * This function is called after composition completes, allowing it to
 * analyze the full CompositionData and detect internal state changes.
 *
 * @param scopeId The Compose group key (Int) - same as TraceFullRecomposition
 * @param compositionData The CompositionData from currentComposer
 */
public fun TraceDeepRecomposition(
  scopeId: Int,
  compositionData: CompositionData,
) {
  val tracker = scopeTrackerCache[scopeId] ?: return
  tracker.trackDeepRecomposition(compositionData)
}

/**
 * Schedule deep tracking to run after composition completes.
 *
 * This wrapper function uses Composer.recordSideEffect to ensure the tracking
 * runs after the current composition phase, when all slot data is finalized.
 * This is the correct timing - calling TraceDeepRecomposition directly during
 * composition may capture incomplete state.
 *
 * @param composer The Composer instance (injected by compiler from $composer param)
 * @param scopeId The Compose group key (Int) for scope isolation
 * @param compositionData The CompositionData snapshot to analyze
 */
@OptIn(InternalComposeApi::class)
public fun ScheduleDeepTracking(
  composer: Composer,
  scopeId: Int,
  compositionData: CompositionData,
) {
  // Capture the compositionData at this point in time
  // recordSideEffect will run after composition completes
  composer.recordSideEffect {
    TraceDeepRecomposition(scopeId, compositionData)
  }
}

/**
 * Store identity for a scope, called during composition phase.
 *
 * @param scopeId The Compose group key (Int) for this scope
 * @param identity The identity object for fast lookup in CompositionData
 */
public fun SetScopeIdentity(
  scopeId: Int,
  identity: Any?,
) {
  val tracker = scopeTrackerCache[scopeId] ?: return
  tracker.identity = identity
}
