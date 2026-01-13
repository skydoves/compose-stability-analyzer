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

class StabilityDifferenceTest {

  @Test
  fun testNewFunction_format() {
    val diff = StabilityDifference.NewFunction(
      "com.example.NewComposable",
      listOf(
        ParameterInfo("a", "String", "STABLE"),
        ParameterInfo("b", "Int", "UNSTABLE"),
      ),
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("+"))
    assertTrue(formatted.contains("com.example.NewComposable"))
    assertTrue(formatted.contains("new composable"))
    assertTrue(formatted.contains(":"))
    assertTrue(formatted.contains("a: STABLE"))
    assertTrue(formatted.contains("b: UNSTABLE"))
  }

  @Test
  fun testNewFunction_format_noColon() {
    val diff = StabilityDifference.NewFunction(
      "com.example.NewComposable",
emptyList(),
    )

    val formatted = diff.format()
    assertFalse(formatted.contains(":"))
  }

  @Test
  fun testRemovedFunction_format() {
    val diff = StabilityDifference.RemovedFunction("com.example.OldComposable")

    val formatted = diff.format()
    assertTrue(formatted.contains("-"))
    assertTrue(formatted.contains("com.example.OldComposable"))
    assertTrue(formatted.contains("removed composable"))
  }

  @Test
  fun testSkippabilityChanged_format_fromTrueToFalse() {
    val diff = StabilityDifference.SkippabilityChanged(
      function = "com.example.UserCard",
      from = true,
      to = false,
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("~"))
    assertTrue(formatted.contains("com.example.UserCard"))
    assertTrue(formatted.contains("skippable changed from true to false"))
  }

  @Test
  fun testSkippabilityChanged_format_fromFalseToTrue() {
    val diff = StabilityDifference.SkippabilityChanged(
      function = "com.example.UserCard",
      from = false,
      to = true,
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("skippable changed from false to true"))
  }

  @Test
  fun testParameterCountChanged_format() {
    val diff = StabilityDifference.ParameterCountChanged(
      function = "com.example.ProductCard",
      from = 2,
      to = 3,
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("~"))
    assertTrue(formatted.contains("com.example.ProductCard"))
    assertTrue(formatted.contains("parameter count changed from 2 to 3"))
  }

  @Test
  fun testParameterStabilityChanged_format() {
    val diff = StabilityDifference.ParameterStabilityChanged(
      function = "com.example.UserProfile",
      parameter = "user",
      from = "STABLE",
      to = "UNSTABLE",
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("~"))
    assertTrue(formatted.contains("com.example.UserProfile"))
    assertTrue(formatted.contains("user"))
    assertTrue(formatted.contains("stability changed from STABLE to UNSTABLE"))
  }

  @Test
  fun testParameterStabilityChanged_format_fromUnstableToStable() {
    val diff = StabilityDifference.ParameterStabilityChanged(
      function = "com.example.DataCard",
      parameter = "data",
      from = "UNSTABLE",
      to = "STABLE",
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("stability changed from UNSTABLE to STABLE"))
  }

  @Test
  fun testParameterStabilityChanged_format_runtime() {
    val diff = StabilityDifference.ParameterStabilityChanged(
      function = "com.example.GenericCard",
      parameter = "value",
      from = "RUNTIME",
      to = "STABLE",
    )

    val formatted = diff.format()
    assertTrue(formatted.contains("stability changed from RUNTIME to STABLE"))
  }

  @Test
  fun testNewFunction_dataClass() {
    val diff1 = StabilityDifference.NewFunction("com.example.Test", emptyList())
    val diff2 = StabilityDifference.NewFunction("com.example.Test", emptyList())
    val diff3 = StabilityDifference.NewFunction("com.example.Other", emptyList())

    assertEquals(diff1, diff2)
    assertEquals(diff1.hashCode(), diff2.hashCode())
    assertTrue(diff1 != diff3)
  }

  @Test
  fun testNewFunction_dataClass_parameters() {
    val diff1 = StabilityDifference.NewFunction(
      "com.example.Test",
      listOf(ParameterInfo("a", "String", "STABLE")),
    )
    val diff2 = StabilityDifference.NewFunction(
      "com.example.Test",
      listOf(ParameterInfo("a", "String", "STABLE")),
    )
    val diff3 = StabilityDifference.NewFunction(
      "com.example.Test",
      listOf(ParameterInfo("b", "String", "STABLE")),
    )

    assertEquals(diff1, diff2)
    assertEquals(diff1.hashCode(), diff2.hashCode())
    assertTrue(diff1 != diff3)
  }

  @Test
  fun testRemovedFunction_dataClass() {
    val diff1 = StabilityDifference.RemovedFunction("com.example.Test")
    val diff2 = StabilityDifference.RemovedFunction("com.example.Test")

    assertEquals(diff1, diff2)
  }

  @Test
  fun testSkippabilityChanged_dataClass() {
    val diff1 = StabilityDifference.SkippabilityChanged("com.example.Test", true, false)
    val diff2 = StabilityDifference.SkippabilityChanged("com.example.Test", true, false)
    val diff3 = StabilityDifference.SkippabilityChanged("com.example.Test", false, true)

    assertEquals(diff1, diff2)
    assertTrue(diff1 != diff3)
  }

  @Test
  fun testParameterCountChanged_dataClass() {
    val diff1 = StabilityDifference.ParameterCountChanged("com.example.Test", 2, 3)
    val diff2 = StabilityDifference.ParameterCountChanged("com.example.Test", 2, 3)
    val diff3 = StabilityDifference.ParameterCountChanged("com.example.Test", 3, 2)

    assertEquals(diff1, diff2)
    assertTrue(diff1 != diff3)
  }

  @Test
  fun testParameterStabilityChanged_dataClass() {
    val diff1 = StabilityDifference.ParameterStabilityChanged(
      "com.example.Test",
      "param",
      "STABLE",
      "UNSTABLE",
    )
    val diff2 = StabilityDifference.ParameterStabilityChanged(
      "com.example.Test",
      "param",
      "STABLE",
      "UNSTABLE",
    )
    val diff3 = StabilityDifference.ParameterStabilityChanged(
      "com.example.Test",
      "other",
      "STABLE",
      "UNSTABLE",
    )

    assertEquals(diff1, diff2)
    assertTrue(diff1 != diff3)
  }

  @Test
  fun testDifferenceTypes_polymorphism() {
    val differences: List<StabilityDifference> = listOf(
      StabilityDifference.NewFunction("com.example.New", emptyList()),
      StabilityDifference.RemovedFunction("com.example.Old"),
      StabilityDifference.SkippabilityChanged("com.example.Changed", true, false),
      StabilityDifference.ParameterCountChanged("com.example.Params", 1, 2),
      StabilityDifference.ParameterStabilityChanged("com.example.Stability", "p", "S", "U"),
    )

    assertEquals(5, differences.size)
    differences.forEach { diff ->
      val formatted = diff.format()
      assertTrue(formatted.isNotEmpty())
    }
  }
}
