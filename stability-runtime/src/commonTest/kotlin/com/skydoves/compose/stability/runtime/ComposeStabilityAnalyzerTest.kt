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

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeStabilityAnalyzerTest {

  private val defaultLogger = DefaultRecompositionLogger()

  @BeforeTest
  fun setup() {
    ComposeStabilityAnalyzer.setLogger(defaultLogger)
    ComposeStabilityAnalyzer.setEnabled(true)
  }

  @AfterTest
  fun tearDown() {
    ComposeStabilityAnalyzer.setLogger(DefaultRecompositionLogger())
    ComposeStabilityAnalyzer.setEnabled(true)
  }

  @Test
  fun testComposeStabilityAnalyzer_defaultLogger() {
    val logger = ComposeStabilityAnalyzer.getLogger()
    assertNotNull(logger)
  }

  @Test
  fun testComposeStabilityAnalyzer_setCustomLogger() {
    val customLogger = TestLogger()
    ComposeStabilityAnalyzer.setLogger(customLogger)

    val retrieved = ComposeStabilityAnalyzer.getLogger()
    assertEquals(customLogger, retrieved)
  }

  @Test
  fun testComposeStabilityAnalyzer_enabledByDefault() {
    assertTrue(ComposeStabilityAnalyzer.isEnabled())
  }

  @Test
  fun testComposeStabilityAnalyzer_setEnabled() {
    ComposeStabilityAnalyzer.setEnabled(false)
    assertFalse(ComposeStabilityAnalyzer.isEnabled())

    ComposeStabilityAnalyzer.setEnabled(true)
    assertTrue(ComposeStabilityAnalyzer.isEnabled())
  }

  @Test
  fun testComposeStabilityAnalyzer_logEvent_whenEnabled() {
    val customLogger = TestLogger()
    ComposeStabilityAnalyzer.setLogger(customLogger)
    ComposeStabilityAnalyzer.setEnabled(true)

    val event = createTestEvent()
    ComposeStabilityAnalyzer.logEvent(event)

    assertEquals(1, customLogger.events.size)
    assertEquals(event, customLogger.events[0])
  }

  @Test
  fun testComposeStabilityAnalyzer_logEvent_whenDisabled() {
    val customLogger = TestLogger()
    ComposeStabilityAnalyzer.setLogger(customLogger)
    ComposeStabilityAnalyzer.setEnabled(false)

    val event = createTestEvent()
    ComposeStabilityAnalyzer.logEvent(event)

    assertEquals(0, customLogger.events.size)
  }

  @Test
  fun testComposeStabilityAnalyzer_multipleEvents() {
    val customLogger = TestLogger()
    ComposeStabilityAnalyzer.setLogger(customLogger)
    ComposeStabilityAnalyzer.setEnabled(true)

    val event1 = createTestEvent("Composable1")
    val event2 = createTestEvent("Composable2")
    val event3 = createTestEvent("Composable3")

    ComposeStabilityAnalyzer.logEvent(event1)
    ComposeStabilityAnalyzer.logEvent(event2)
    ComposeStabilityAnalyzer.logEvent(event3)

    assertEquals(3, customLogger.events.size)
    assertEquals("Composable1", customLogger.events[0].composableName)
    assertEquals("Composable2", customLogger.events[1].composableName)
    assertEquals("Composable3", customLogger.events[2].composableName)
  }

  @Test
  fun testComposeStabilityAnalyzer_changeLogger_midStream() {
    val logger1 = TestLogger()
    val logger2 = TestLogger()

    ComposeStabilityAnalyzer.setLogger(logger1)
    ComposeStabilityAnalyzer.logEvent(createTestEvent("Event1"))

    ComposeStabilityAnalyzer.setLogger(logger2)
    ComposeStabilityAnalyzer.logEvent(createTestEvent("Event2"))

    assertEquals(1, logger1.events.size)
    assertEquals("Event1", logger1.events[0].composableName)

    assertEquals(1, logger2.events.size)
    assertEquals("Event2", logger2.events[0].composableName)
  }

  @Test
  fun testComposeStabilityAnalyzer_toggleEnabled() {
    val customLogger = TestLogger()
    ComposeStabilityAnalyzer.setLogger(customLogger)

    ComposeStabilityAnalyzer.setEnabled(true)
    ComposeStabilityAnalyzer.logEvent(createTestEvent("Event1"))

    ComposeStabilityAnalyzer.setEnabled(false)
    ComposeStabilityAnalyzer.logEvent(createTestEvent("Event2"))

    ComposeStabilityAnalyzer.setEnabled(true)
    ComposeStabilityAnalyzer.logEvent(createTestEvent("Event3"))

    // Only Event1 and Event3 should be logged
    assertEquals(2, customLogger.events.size)
    assertEquals("Event1", customLogger.events[0].composableName)
    assertEquals("Event3", customLogger.events[1].composableName)
  }

  private fun createTestEvent(name: String = "TestComposable"): RecompositionEvent =
    RecompositionEvent(
      composableName = name,
      tag = "test",
      recompositionCount = 1,
      parameterChanges = listOf(
        ParameterChange(
          name = "param",
          type = "String",
          oldValue = null,
          newValue = "value",
          changed = false,
          stable = true,
        ),
      ),
      unstableParameters = emptyList(),
    )

  private class TestLogger : RecompositionLogger {
    val events = mutableListOf<RecompositionEvent>()

    override fun log(event: RecompositionEvent) {
      events.add(event)
    }
  }
}
