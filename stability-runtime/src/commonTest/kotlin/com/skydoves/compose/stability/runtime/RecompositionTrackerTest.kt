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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecompositionTrackerTest {

  private lateinit var testLogger: TestRecompositionLogger

  @BeforeTest
  fun setup() {
    testLogger = TestRecompositionLogger()
    ComposeStabilityAnalyzer.setLogger(testLogger)
    ComposeStabilityAnalyzer.setEnabled(true)
  }

  @AfterTest
  fun tearDown() {
    ComposeStabilityAnalyzer.setEnabled(true)
    ComposeStabilityAnalyzer.setLogger(DefaultRecompositionLogger())
  }

  @Test
  fun testRecompositionTracker_firstRecomposition() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackParameter("count", "Int", 0, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]
    assertEquals("TestComposable", event.composableName)
    assertEquals(1, event.recompositionCount)
    assertEquals(1, event.parameterChanges.size)

    val param = event.parameterChanges[0]
    assertEquals("count", param.name)
    assertEquals("Int", param.type)
    assertNull(param.oldValue)
    assertEquals(0, param.newValue)
    // First recomposition: no previous value, so changed is false
    assertFalse(param.changed)
    assertTrue(param.stable)
  }

  @Test
  fun testRecompositionTracker_parameterChanged() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    // First recomposition
    tracker.trackParameter("count", "Int", 0, isStable = true)
    tracker.logIfThresholdMet()

    // Second recomposition with changed value
    tracker.trackParameter("count", "Int", 1, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)

    val firstEvent = testLogger.events[0]
    // First recomposition: no previous value, so changed is false
    assertFalse(firstEvent.parameterChanges[0].changed)
    assertNull(firstEvent.parameterChanges[0].oldValue)
    assertEquals(0, firstEvent.parameterChanges[0].newValue)

    val secondEvent = testLogger.events[1]
    assertTrue(secondEvent.parameterChanges[0].changed)
    assertEquals(0, secondEvent.parameterChanges[0].oldValue)
    assertEquals(1, secondEvent.parameterChanges[0].newValue)
  }

  @Test
  fun testRecompositionTracker_parameterNotChanged() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    // First recomposition
    tracker.trackParameter("count", "Int", 5, isStable = true)
    tracker.logIfThresholdMet()

    // Second recomposition with same value
    tracker.trackParameter("count", "Int", 5, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)

    val secondEvent = testLogger.events[1]
    assertFalse(secondEvent.parameterChanges[0].changed)
    assertEquals(5, secondEvent.parameterChanges[0].oldValue)
    assertEquals(5, secondEvent.parameterChanges[0].newValue)
  }

  @Test
  fun testRecompositionTracker_multipleParameters() {
    val tracker = RecompositionTracker("UserCard", "", 1)

    tracker.trackParameter("name", "String", "John", isStable = true)
    tracker.trackParameter("age", "Int", 30, isStable = true)
    tracker.trackParameter("data", "MutableData", "data1", isStable = false)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]

    assertEquals(3, event.parameterChanges.size)
    assertEquals(listOf("name", "age", "data"), event.parameterChanges.map { it.name })
    assertEquals(listOf("data"), event.unstableParameters)
  }

  @Test
  fun testRecompositionTracker_threshold() {
    val tracker = RecompositionTracker("TestComposable", "", 3)

    // First recomposition - below threshold
    tracker.trackParameter("count", "Int", 0, isStable = true)
    tracker.logIfThresholdMet()
    assertEquals(0, testLogger.events.size)

    // Second recomposition - still below threshold
    tracker.trackParameter("count", "Int", 1, isStable = true)
    tracker.logIfThresholdMet()
    assertEquals(0, testLogger.events.size)

    // Third recomposition - meets threshold
    tracker.trackParameter("count", "Int", 2, isStable = true)
    tracker.logIfThresholdMet()
    assertEquals(1, testLogger.events.size)

    // Fourth recomposition - above threshold, should log
    tracker.trackParameter("count", "Int", 3, isStable = true)
    tracker.logIfThresholdMet()
    assertEquals(2, testLogger.events.size)

    assertEquals(3, testLogger.events[0].recompositionCount)
    assertEquals(4, testLogger.events[1].recompositionCount)
  }

  @Test
  fun testRecompositionTracker_withTag() {
    val tracker = RecompositionTracker("UserProfile", "user-screen", 1)

    tracker.trackParameter("user", "User", "user1", isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]

    assertEquals("UserProfile", event.composableName)
    assertEquals("user-screen", event.tag)
  }

  @Test
  fun testRecompositionTracker_unstableParameters() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackParameter("stable", "Int", 1, isStable = true)
    tracker.trackParameter("unstable1", "MutableData", "data", isStable = false)
    tracker.trackParameter("unstable2", "MutableList", listOf("a"), isStable = false)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]

    assertEquals(3, event.parameterChanges.size)
    assertEquals(2, event.unstableParameters.size)
    assertTrue(event.unstableParameters.contains("unstable1"))
    assertTrue(event.unstableParameters.contains("unstable2"))
    assertFalse(event.unstableParameters.contains("stable"))
  }

  @Test
  fun testRecompositionTracker_objectReferenceChange() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    val obj1 = object {}
    val obj2 = object {}

    // First recomposition
    tracker.trackParameter("obj", "Object", obj1, isStable = true)
    tracker.logIfThresholdMet()

    // Second recomposition with different object
    tracker.trackParameter("obj", "Object", obj2, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)

    val secondEvent = testLogger.events[1]
    assertTrue(secondEvent.parameterChanges[0].changed)
  }

  @Test
  fun testRecompositionTracker_nullValues() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    // First with null
    tracker.trackParameter("data", "String?", null, isStable = true)
    tracker.logIfThresholdMet()

    // Second with value
    tracker.trackParameter("data", "String?", "value", isStable = true)
    tracker.logIfThresholdMet()

    // Third with null again
    tracker.trackParameter("data", "String?", null, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(3, testLogger.events.size)

    assertNull(testLogger.events[0].parameterChanges[0].newValue)
    assertEquals("value", testLogger.events[1].parameterChanges[0].newValue)
    assertTrue(testLogger.events[1].parameterChanges[0].changed)
    assertNull(testLogger.events[2].parameterChanges[0].newValue)
    assertTrue(testLogger.events[2].parameterChanges[0].changed)
  }

  @Test
  fun testCreateRecompositionTracker() {
    val tracker = createRecompositionTracker(
      composableName = "TestComposable",
      tag = "test-tag",
      threshold = 5,
    )

    tracker.trackParameter("param", "String", "value", isStable = true)
    tracker.logIfThresholdMet()

    // Should not log because threshold is 5
    assertEquals(0, testLogger.events.size)
  }

  @Test
  fun testRecompositionTracker_consecutiveRecompositions() {
    val tracker = RecompositionTracker("CounterDisplay", "counter", 1)

    for (i in 0..9) {
      tracker.trackParameter("count", "Int", i, isStable = true)
      tracker.logIfThresholdMet()
    }

    assertEquals(10, testLogger.events.size)
    for (i in 0..9) {
      assertEquals(i + 1, testLogger.events[i].recompositionCount)
    }
  }

  @Test
  fun testRecompositionTracker_loggingDisabled() {
    ComposeStabilityAnalyzer.setEnabled(false)

    val tracker = RecompositionTracker("TestComposable", "", 1)
    tracker.trackParameter("count", "Int", 0, isStable = true)
    tracker.logIfThresholdMet()

    // Should not log when disabled
    assertEquals(0, testLogger.events.size)
  }

  @Test
  fun testRecompositionTracker_trackState_firstRecomposition() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackState("count", "Int", 0)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]
    assertEquals(1, event.stateChanges.size)

    val state = event.stateChanges[0]
    assertEquals("count", state.name)
    assertEquals("Int", state.type)
    assertNull(state.oldValue)
    assertEquals(0, state.newValue)
    // First recomposition: no previous value, changed is false
    assertFalse(state.changed)
  }

  @Test
  fun testRecompositionTracker_trackState_valueChanged() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    // First recomposition
    tracker.trackState("count", "Int", 0)
    tracker.logIfThresholdMet()

    // Second recomposition with changed value
    tracker.trackState("count", "Int", 5)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)

    val secondEvent = testLogger.events[1]
    assertTrue(secondEvent.stateChanges[0].changed)
    assertEquals(0, secondEvent.stateChanges[0].oldValue)
    assertEquals(5, secondEvent.stateChanges[0].newValue)
  }

  @Test
  fun testRecompositionTracker_trackState_valueNotChanged() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackState("count", "Int", 3)
    tracker.logIfThresholdMet()

    tracker.trackState("count", "Int", 3)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)

    val secondEvent = testLogger.events[1]
    assertFalse(secondEvent.stateChanges[0].changed)
  }

  @Test
  fun testRecompositionTracker_mixedParameterAndState() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackParameter("title", "String", "Hello", isStable = true)
    tracker.trackState("count", "Int", 0)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    val event = testLogger.events[0]
    assertEquals(1, event.parameterChanges.size)
    assertEquals(1, event.stateChanges.size)
    assertEquals("title", event.parameterChanges[0].name)
    assertEquals("count", event.stateChanges[0].name)
  }

  @Test
  fun testRecompositionTracker_stateChangeSummary() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    // First recomposition (setup)
    tracker.trackState("count", "Int", 0)
    tracker.trackState("flag", "Boolean", true)
    tracker.logIfThresholdMet()

    // Second recomposition (count changed, flag same)
    tracker.trackState("count", "Int", 1)
    tracker.trackState("flag", "Boolean", true)
    tracker.logIfThresholdMet()

    assertEquals(2, testLogger.events.size)
    val secondEvent = testLogger.events[1]
    assertEquals(2, secondEvent.stateChanges.size)
    assertTrue(secondEvent.stateChanges[0].changed) // count
    assertFalse(secondEvent.stateChanges[1].changed) // flag
  }

  @Test
  fun testRecompositionTracker_noStateChanges_emptyByDefault() {
    val tracker = RecompositionTracker("TestComposable", "", 1)

    tracker.trackParameter("x", "Int", 1, isStable = true)
    tracker.logIfThresholdMet()

    assertEquals(1, testLogger.events.size)
    assertTrue(testLogger.events[0].stateChanges.isEmpty())
  }

  /**
   * Test logger that captures events for verification.
   */
  private class TestRecompositionLogger : RecompositionLogger {
    val events = mutableListOf<RecompositionEvent>()

    override fun log(event: RecompositionEvent) {
      events.add(event)
    }
  }
}
