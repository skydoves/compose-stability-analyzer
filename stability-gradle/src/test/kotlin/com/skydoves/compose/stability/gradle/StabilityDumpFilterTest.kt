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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the `unstableOnly` baseline filter.
 */
class StabilityDumpFilterTest {

  @Test
  fun testFilter_keepsSkippableComposableWithUnstableParameter() {
    // The regression this filter exists to avoid: the compiler can still mark a composable
    // skippable while a parameter is unstable, and `compareStability` reports it as a new unstable
    // composable. Filtering by skippability alone dropped it from the baseline, so it was reported
    // on every run with no way to accept it.
    val entry = entry(
      "com.example.Skippable",
      skippable = true,
      params = listOf(ParameterInfo("value", "com.example.Mutable", "UNSTABLE")),
    )

    assertTrue(entry.isStabilityIssue(emptyList()))
  }

  @Test
  fun testFilter_keepsSkippableComposableWithRuntimeOrUnknownParameter() {
    val runtime = entry(
      "com.example.Runtime",
      skippable = true,
      params = listOf(ParameterInfo("node", "com.example.Node", "RUNTIME")),
    )
    val unknown = entry(
      "com.example.Unknown",
      skippable = true,
      params = listOf(ParameterInfo("scope", "com.example.Scope", "UNKNOWN")),
    )

    assertTrue(runtime.isStabilityIssue(emptyList()))
    assertTrue(unknown.isStabilityIssue(emptyList()))
  }

  @Test
  fun testFilter_keepsNonSkippableAndNonRestartableComposables() {
    val nonSkippable = entry("com.example.NonSkippable", skippable = false)
    val nonRestartable = entry("com.example.NonRestartable", restartable = false)

    assertTrue(nonSkippable.isStabilityIssue(emptyList()))
    assertTrue(nonRestartable.isStabilityIssue(emptyList()))
  }

  @Test
  fun testFilter_dropsFullyStableComposables() {
    val withStableParams = entry(
      "com.example.Stable",
      params = listOf(ParameterInfo("text", "kotlin.String", "STABLE")),
    )
    val parameterless = entry("com.example.Parameterless")

    assertFalse(withStableParams.isStabilityIssue(emptyList()))
    assertFalse(parameterless.isStabilityIssue(emptyList()))
  }

  @Test
  fun testFilter_dropsComposableRescuedByStabilityConfiguration() {
    val entry = entry(
      "com.example.Configured",
      params = listOf(ParameterInfo("model", "com.example.Model", "UNSTABLE")),
    )

    assertTrue(entry.isStabilityIssue(emptyList()))
    assertFalse(entry.isStabilityIssue(listOf(FqNameMatcher("com.example.Model"))))
  }

  @Test
  fun testFilter_baselineNeverReportsDroppedEntriesAsRegressions() {
    // The property that makes `unstableOnly` usable at all: a baseline filtered by this predicate
    // must not make `stabilityCheck` fail against the very code it was dumped from. Anything the
    // filter drops has to be something `compareStability` would not report under regression
    // filtering — otherwise the build fails permanently and no `stabilityDump` can fix it.
    val all = listOf(
      entry(
        "com.example.Stable",
        params = listOf(ParameterInfo("text", "kotlin.String", "STABLE")),
      ),
      entry("com.example.Parameterless"),
      entry(
        "com.example.SkippableButUnstable",
        skippable = true,
        params = listOf(ParameterInfo("value", "com.example.Mutable", "UNSTABLE")),
      ),
      entry("com.example.NonSkippable", skippable = false),
      entry("com.example.NonRestartable", skippable = false, restartable = false),
    )

    val current = all.associateBy { it.qualifiedName }
    val baseline = all.filter { it.isStabilityIssue(emptyList()) }.associateBy { it.qualifiedName }

    assertEquals(3, baseline.size)

    val differences = compareStability(current, baseline, ignoreNonRegressiveChanges = true)
    assertTrue(
      differences.isEmpty(),
      "an unstableOnly baseline must not report its own code: $differences",
    )
  }

  @Test
  fun testFilter_stillReportsANewlyUnstableComposable() {
    // The other half: reducing the baseline must not cost the gate its teeth. A composable that
    // gains an unstable parameter is absent from the reduced baseline, so it reads as new — and a
    // new composable with an unstable parameter is a regression.
    val baseline = listOf(
      entry(
        "com.example.Stable",
        params = listOf(ParameterInfo("text", "kotlin.String", "STABLE")),
      ),
    ).filter { it.isStabilityIssue(emptyList()) }.associateBy { it.qualifiedName }

    assertTrue(baseline.isEmpty())

    val current = mapOf(
      "com.example.Stable" to entry(
        "com.example.Stable",
        params = listOf(ParameterInfo("text", "com.example.Mutable", "UNSTABLE")),
      ),
    )

    val differences = compareStability(current, baseline, ignoreNonRegressiveChanges = true)

    assertEquals(1, differences.size)
    assertTrue(differences[0] is StabilityDifference.NewFunction)
  }

  private fun entry(
    qualifiedName: String,
    skippable: Boolean = true,
    restartable: Boolean = true,
    params: List<ParameterInfo> = emptyList(),
  ): StabilityEntry = StabilityEntry(
    qualifiedName = qualifiedName,
    simpleName = qualifiedName.substringAfterLast("."),
    visibility = "public",
    parameters = params,
    returnType = "kotlin.Unit",
    skippable = skippable,
    restartable = restartable,
  )
}
