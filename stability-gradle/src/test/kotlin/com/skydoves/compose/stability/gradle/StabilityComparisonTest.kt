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
package com.skydoves.compose.stability.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StabilityComparisonTest {

  @Test
  fun testCompareStability_noDifferences() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val differences = compareStability(current, reference)
    assertTrue(differences.isEmpty())
  }

  @Test
  fun testCompareStability_newFunction() {
    val current = mapOf(
      "com.example.Old" to createEntry("com.example.Old"),
      "com.example.New" to createEntry("com.example.New"),
    )

    val reference = mapOf(
      "com.example.Old" to createEntry("com.example.Old"),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.NewFunction)
    assertEquals("com.example.New", (differences[0] as StabilityDifference.NewFunction).name)
  }

  @Test
  fun testCompareStability_newFunctionWithRegressionFiltering() {
    val current = mapOf(
      "com.example.New1" to createEntry("com.example.New1", skippable = true),
      "com.example.New2" to createEntry("com.example.New2", skippable = false),
    )

    val reference = emptyMap<String, StabilityEntry>()

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.NewFunction)
    assertEquals("com.example.New2", (differences[0] as StabilityDifference.NewFunction).name)
  }

  @Test
  fun testCompareStability_removedFunction() {
    val current = mapOf(
      "com.example.Remaining" to createEntry("com.example.Remaining"),
    )

    val reference = mapOf(
      "com.example.Remaining" to createEntry("com.example.Remaining"),
      "com.example.Removed" to createEntry("com.example.Removed"),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.RemovedFunction)
    assertEquals(
      "com.example.Removed",
      (differences[0] as StabilityDifference.RemovedFunction).name,
    )
  }

  @Test
  fun testCompareStability_removedFunctionWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Remaining" to createEntry("com.example.Remaining"),
    )

    val reference = mapOf(
      "com.example.Remaining" to createEntry("com.example.Remaining"),
      "com.example.Removed" to createEntry("com.example.Removed"),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_skippabilityChanged_trueToFalse() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = false),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.SkippabilityChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals(true, diff.from)
    assertEquals(false, diff.to)
  }

  @Test
  fun testCompareStability_skippabilityChanged_trueToFalse_withRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = false),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.SkippabilityChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals(true, diff.from)
    assertEquals(false, diff.to)
  }

  @Test
  fun testCompareStability_skippabilityChanged_falseToTrue() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = false),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.SkippabilityChanged
    assertEquals(false, diff.from)
    assertEquals(true, diff.to)
  }

  @Test
  fun testCompareStability_skippabilityChanged_falseToTrue_withRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = true),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test", skippable = false),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_parameterCountChanged() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
          ParameterInfo("c", "Boolean", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterCountChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals(2, diff.from)
    assertEquals(3, diff.to)
  }

  @Test
  fun testCompareStability_parameterCountChanged_withRegressionFilteringAllStable() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
          ParameterInfo("c", "Boolean", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_parameterCountChanged_withRegressionFilteringSomeUnstable() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
          ParameterInfo("c", "Boolean", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterCountChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals(2, diff.from)
    assertEquals(3, diff.to)
  }

  @Test
  fun testCompareStability_parameterStabilityChanged() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterStabilityChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals("user", diff.parameter)
    assertEquals("STABLE", diff.from)
    assertEquals("UNSTABLE", diff.to)
  }

  @Test
  fun testCompareStability_parameterStabilityChangedToStableWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "UNSTABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_parameterStabilityChangedToUnstableWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("user", "User", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterStabilityChanged
    assertEquals("com.example.Test", diff.function)
    assertEquals("user", diff.parameter)
    assertEquals("STABLE", diff.from)
    assertEquals("UNSTABLE", diff.to)
  }

  @Test
  fun testCompareStability_multipleParameterStabilityChanges() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "UNSTABLE"),
          ParameterInfo("b", "B", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "STABLE"),
          ParameterInfo("b", "B", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference)

    assertEquals(2, differences.size)
    assertTrue(differences.all { it is StabilityDifference.ParameterStabilityChanged })
  }

  @Test
  fun testCompareStability_multipleParameterStabilityChangesToUnstableWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "UNSTABLE"),
          ParameterInfo("b", "B", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "STABLE"),
          ParameterInfo("b", "B", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(2, differences.size)
    assertTrue(differences.all { it is StabilityDifference.ParameterStabilityChanged })
  }

  @Test
  fun testCompareStability_multipleParameterStabilityChangesToStableWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "STABLE"),
          ParameterInfo("b", "B", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "UNSTABLE"),
          ParameterInfo("b", "B", "UNSTABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_multipleParameterStabilityChangesToMixedWithRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "STABLE"),
          ParameterInfo("b", "B", "UNSTABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "A", "UNSTABLE"),
          ParameterInfo("b", "B", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterStabilityChanged
    assertEquals("b", diff.parameter)
  }

  @Test
  fun testCompareStability_multipleDifferenceTypes() {
    val current = mapOf(
      "com.example.Changed" to createEntry("com.example.Changed", skippable = false),
      "com.example.New" to createEntry("com.example.New"),
    )

    val reference = mapOf(
      "com.example.Changed" to createEntry("com.example.Changed", skippable = true),
      "com.example.Removed" to createEntry("com.example.Removed"),
    )

    val differences = compareStability(current, reference)

    assertEquals(3, differences.size)

    val types = differences.map { it::class.simpleName }.toSet()
    assertTrue(types.contains("NewFunction"))
    assertTrue(types.contains("RemovedFunction"))
    assertTrue(types.contains("SkippabilityChanged"))
  }

  @Test
  fun testCompareStability_onlyParameterCountChange_noStabilityChanges() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
          ParameterInfo("c", "Boolean", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference)

    // Should only report parameter count change, not individual parameter changes
    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.ParameterCountChanged)
  }

  @Test
  fun testCompareStability_onlyParameterCountChange_noStabilityChanges_withRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
          ParameterInfo("b", "Int", "STABLE"),
          ParameterInfo("c", "Boolean", "STABLE"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("a", "String", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    // Should only report parameter count change, not individual parameter changes
    assertEquals(0, differences.size)
  }

  @Test
  fun testCompareStability_stableToRuntime() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("value", "T", "RUNTIME"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("value", "T", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterStabilityChanged
    assertEquals("STABLE", diff.from)
    assertEquals("RUNTIME", diff.to)
  }

  @Test
  fun testCompareStability_stableToRuntime_withRegressionFiltering() {
    val current = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("value", "T", "RUNTIME"),
        ),
      ),
    )

    val reference = mapOf(
      "com.example.Test" to createEntry(
        "com.example.Test",
        params = listOf(
          ParameterInfo("value", "T", "STABLE"),
        ),
      ),
    )

    val differences = compareStability(current, reference, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    val diff = differences[0] as StabilityDifference.ParameterStabilityChanged
    assertEquals("STABLE", diff.from)
    assertEquals("RUNTIME", diff.to)
  }

  @Test
  fun testCompareStability_bothEmpty() {
    val current = emptyMap<String, StabilityEntry>()
    val reference = emptyMap<String, StabilityEntry>()

    val differences = compareStability(current, reference)
    assertTrue(differences.isEmpty())
  }

  @Test
  fun testCompareStability_currentEmpty() {
    val current = emptyMap<String, StabilityEntry>()
    val reference = mapOf(
      "com.example.Test" to createEntry("com.example.Test"),
    )

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.RemovedFunction)
  }

  @Test
  fun testCompareStability_referenceEmpty() {
    val current = mapOf(
      "com.example.Test" to createEntry("com.example.Test"),
    )
    val reference = emptyMap<String, StabilityEntry>()

    val differences = compareStability(current, reference)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.NewFunction)
  }

  // Helper methods
  private fun createEntry(
    qualifiedName: String,
    skippable: Boolean = true,
    params: List<ParameterInfo> = emptyList(),
  ): StabilityEntry {
    return StabilityEntry(
      qualifiedName = qualifiedName,
      simpleName = qualifiedName.substringAfterLast("."),
      visibility = "public",
      parameters = params,
      returnType = "kotlin.Unit",
      skippable = skippable,
      restartable = true,
    )
  }

  private fun compareStability(
    current: Map<String, StabilityEntry>,
    reference: Map<String, StabilityEntry>,
    ignoreNonRegressiveChanges: Boolean = false,

  ): List<StabilityDifference> {
    val differences = mutableListOf<StabilityDifference>()

    // Check for new functions
    current.keys.subtract(reference.keys).forEach { functionName ->
      if (!ignoreNonRegressiveChanges || !current.getValue(functionName).skippable) {
        differences.add(StabilityDifference.NewFunction(functionName))
      }
    }

    // Check for removed functions
    if (!ignoreNonRegressiveChanges) {
      reference.keys.subtract(current.keys).forEach { functionName ->
        differences.add(StabilityDifference.RemovedFunction(functionName))
      }
    }

    // Check for changed stability
    current.keys.intersect(reference.keys).forEach { functionName ->
      val currentEntry = current[functionName]!!
      val referenceEntry = reference[functionName]!!

      // Check skippability change
      if (currentEntry.skippable != referenceEntry.skippable &&
        (!ignoreNonRegressiveChanges || !currentEntry.skippable)
      ) {
        differences.add(
          StabilityDifference.SkippabilityChanged(
            functionName,
            referenceEntry.skippable,
            currentEntry.skippable,
          ),
        )
      }

      // Check if parameter count changed
      if (currentEntry.parameters.size != referenceEntry.parameters.size) {
        if (!ignoreNonRegressiveChanges ||
          currentEntry.parameters.any { it.stability != "STABLE" }
        ) {
          differences.add(
            StabilityDifference.ParameterCountChanged(
              functionName,
              referenceEntry.parameters.size,
              currentEntry.parameters.size,
            ),
          )
        }
      } else {
        // Check parameter stability changes (only if count is the same)
        currentEntry.parameters.zip(referenceEntry.parameters).forEach { (current, ref) ->
          if (current.stability != ref.stability &&
            (!ignoreNonRegressiveChanges || current.stability != "STABLE")
          ) {
            differences.add(
              StabilityDifference.ParameterStabilityChanged(
                functionName,
                current.name,
                ref.stability,
                current.stability,
              ),
            )
          }
        }
      }
    }

    return differences
  }
}
