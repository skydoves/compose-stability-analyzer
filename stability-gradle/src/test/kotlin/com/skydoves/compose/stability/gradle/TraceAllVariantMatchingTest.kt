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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the trace-all variant gating rule.
 */
class TraceAllVariantMatchingTest {

  private val defaultTokens = listOf("debug")

  @Test
  fun testTraceAll_debugVariantMatches() {
    assertTrue(traceAllMatchesCompilationName("debug", defaultTokens))
  }

  @Test
  fun testTraceAll_flavoredDebugVariantsMatch() {
    assertTrue(traceAllMatchesCompilationName("stagingDebug", defaultTokens))
    assertTrue(traceAllMatchesCompilationName("fullDebug", defaultTokens))
    assertTrue(traceAllMatchesCompilationName("FreeDebug", defaultTokens))
  }

  @Test
  fun testTraceAll_releaseVariantsDoNotMatch() {
    assertFalse(traceAllMatchesCompilationName("release", defaultTokens))
    assertFalse(traceAllMatchesCompilationName("stagingRelease", defaultTokens))
    assertFalse(traceAllMatchesCompilationName("benchmark", defaultTokens))
  }

  @Test
  fun testTraceAll_kmpMainCompilationAlwaysMatches() {
    // KMP/JVM main compilations have no variant dimension; the runtime gate is the safety net.
    assertTrue(traceAllMatchesCompilationName("main", defaultTokens))
    assertTrue(traceAllMatchesCompilationName("main", emptyList()))
  }

  @Test
  fun testTraceAll_customBuildTypeViaTokens() {
    val tokens = listOf("debug", "internal")
    assertTrue(traceAllMatchesCompilationName("internal", tokens))
    assertTrue(traceAllMatchesCompilationName("stagingInternal", tokens))
    assertFalse(traceAllMatchesCompilationName("release", tokens))
  }

  @Test
  fun testTraceAll_caseInsensitiveTokenMatching() {
    assertTrue(traceAllMatchesCompilationName("StagingDEBUG", listOf("Debug")))
  }

  @Test
  fun testTraceAll_emptyTokensMatchNothingButMain() {
    assertFalse(traceAllMatchesCompilationName("debug", emptyList()))
    assertTrue(traceAllMatchesCompilationName("main", emptyList()))
  }

  @Test
  fun testTraceAll_midStringOccurrenceDoesNotMatch() {
    // The token must be a suffix, not just contained anywhere in the compilation name.
    assertFalse(traceAllMatchesCompilationName("debuggableRelease", defaultTokens))
  }
}
