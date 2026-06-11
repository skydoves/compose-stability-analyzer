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
package com.skydoves.compose.stability.idea.heatmap

import junit.framework.TestCase

/**
 * Tests for [LogcatParser], covering both the legacy header format (runtime < 1.0, no
 * `(fq:)`/`(auto)` tokens) and the extended format emitted by trace-all-capable runtimes.
 */
class LogcatParserTest : TestCase() {

  private fun parse(vararg lines: String): List<ParsedRecompositionEvent> {
    val events = mutableListOf<ParsedRecompositionEvent>()
    val parser = LogcatParser { events.add(it) }
    lines.forEach { parser.feedLine(it) }
    parser.flush()
    return events
  }

  fun testLegacyHeader_noFqNoAuto() {
    val events = parse("[Recomposition #3] UserProfile (tag: user-screen) (2.30ms)")

    assertEquals(1, events.size)
    val event = events[0]
    assertEquals("UserProfile", event.composableName)
    assertEquals("user-screen", event.tag)
    assertEquals(3, event.recompositionCount)
    assertEquals(2.30, event.durationMs)
    assertEquals("", event.fqName)
    assertFalse(event.isAutoTraced)
  }

  fun testHeader_withFqToken() {
    val events = parse(
      "[Recomposition #5] UserProfile (tag: t) (1.20ms) (fq: com.example.profile.UserProfile)",
    )

    val event = events.single()
    assertEquals("UserProfile", event.composableName)
    assertEquals("t", event.tag)
    assertEquals(5, event.recompositionCount)
    assertEquals(1.20, event.durationMs)
    assertEquals("com.example.profile.UserProfile", event.fqName)
    assertFalse(event.isAutoTraced)
  }

  fun testHeader_withFqAndAutoTokens() {
    val events = parse(
      "[Recomposition #2] ProductCard (0.45ms) (fq: com.example.ProductCard) (auto)",
    )

    val event = events.single()
    assertEquals("ProductCard", event.composableName)
    assertEquals("", event.tag)
    assertEquals("com.example.ProductCard", event.fqName)
    assertTrue(event.isAutoTraced)
  }

  fun testHeader_fqWithoutTagOrDuration() {
    val events = parse("[Recomposition #1] Card (fq: com.example.Card) (auto)")

    val event = events.single()
    assertEquals("Card", event.composableName)
    assertEquals("", event.tag)
    assertEquals(0.0, event.durationMs)
    assertEquals("com.example.Card", event.fqName)
    assertTrue(event.isAutoTraced)
  }

  fun testHeader_legacyMinimal() {
    val events = parse("[Recomposition #1] Card")

    val event = events.single()
    assertEquals("Card", event.composableName)
    assertEquals("", event.fqName)
    assertFalse(event.isAutoTraced)
  }

  fun testParamLines_parsedWithNewHeaderTokens() {
    val events = parse(
      "[Recomposition #4] ProductCard (fq: com.example.ProductCard) (auto)",
      "  ├─ [param] product: Product unstable (Product@abc → Product@def)",
      "  ├─ [param] onClick: () -> Unit stable (Function@xyz)",
      "  └─ Unstable parameters: [product]",
    )

    val event = events.single()
    assertEquals(2, event.parameterEntries.size)
    // unstable token with an old → new arrow = reference-only change (silent waste signal)
    assertEquals(ParameterStatus.REF_CHANGED, event.parameterEntries[0].status)
    assertEquals(ParameterStatus.STABLE, event.parameterEntries[1].status)
    assertEquals(listOf("product"), event.unstableParameters)
    assertTrue(event.isAutoTraced)
  }

  fun testStateLines_parsedWithNewHeaderTokens() {
    val events = parse(
      "[Recomposition #2] CounterScreen (1.10ms) (fq: com.example.CounterScreen)",
      "  ├─ [param] title: String stable (Counter)",
      "  ├─ [state] counter: Int changed (0 → 1) ← onClick (Main.kt:42)",
      "  └─ State changes: [counter]",
    )

    val event = events.single()
    assertEquals(1, event.stateEntries.size)
    assertTrue(event.stateEntries[0].contains("counter"))
    assertTrue(event.stateEntries[0].contains("← onClick (Main.kt:42)"))
  }

  fun testConsecutiveEvents_mixedFormats() {
    // A new-format event followed by a legacy event (e.g. mixed library versions).
    val events = parse(
      "[Recomposition #1] NewCard (fq: com.example.NewCard) (auto)",
      "  ├─ [param] x: Int stable (1)",
      "[Recomposition #7] OldCard (tag: legacy)",
      "  ├─ y: String changed (a → b)",
    )

    assertEquals(2, events.size)
    assertEquals("com.example.NewCard", events[0].fqName)
    assertTrue(events[0].isAutoTraced)
    assertEquals("OldCard", events[1].composableName)
    assertEquals("legacy", events[1].tag)
    assertEquals("", events[1].fqName)
    assertFalse(events[1].isAutoTraced)
    assertEquals(ParameterStatus.CHANGED, events[1].parameterEntries[0].status)
  }
}
