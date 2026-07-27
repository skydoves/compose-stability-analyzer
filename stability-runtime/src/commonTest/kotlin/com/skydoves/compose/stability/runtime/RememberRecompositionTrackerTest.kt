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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the entry points compiler-generated code calls. These run on every published target, so
 * a target that cannot resolve them (which used to silently disable `@TraceRecomposition`
 * everywhere except Android/JVM) fails here instead of in a user's project.
 *
 * The tracker cache is process-global, so every test uses its own composable name.
 */
class RememberRecompositionTrackerTest {

  private lateinit var logger: RecordingLogger
  private var wasEnabled: Boolean = true

  @BeforeTest
  fun setup() {
    logger = RecordingLogger()
    wasEnabled = ComposeStabilityAnalyzer.isEnabled()
    ComposeStabilityAnalyzer.setLogger(logger)
    ComposeStabilityAnalyzer.setEnabled(true)
  }

  @AfterTest
  fun tearDown() {
    // Both the logger and the enabled flag are process-global, so restore them for other tests.
    ComposeStabilityAnalyzer.setEnabled(wasEnabled)
    ComposeStabilityAnalyzer.setLogger(DefaultRecompositionLogger())
  }

  @Test
  fun rememberRecompositionTracker_returnsSameInstanceForSameKey() {
    val first = rememberRecompositionTracker("SameKey", "", 1, "com.example.SameKey", false)
    val second = rememberRecompositionTracker("SameKey", "", 1, "com.example.SameKey", false)

    assertSame(first, second, "the cache must survive across recompositions")
  }

  @Test
  fun rememberRecompositionTracker_separatesDifferentTagsAndNames() {
    val tagged = rememberRecompositionTracker("Keys", "a", 1, "com.example.Keys", false)
    val otherTag = rememberRecompositionTracker("Keys", "b", 1, "com.example.Keys", false)
    val otherFqName = rememberRecompositionTracker("Keys", "a", 1, "com.other.Keys", false)

    assertNotSame(tagged, otherTag)
    assertNotSame(tagged, otherFqName)
  }

  @Test
  fun rememberRecompositionTracker_keepsCountingAcrossCalls() {
    repeat(3) {
      rememberRecompositionTracker("Counting", "", 1, "com.example.Counting", false)
        .logIfThresholdMet()
    }

    assertEquals(3, logger.events.size)
    assertEquals(listOf(1, 2, 3), logger.events.map { it.recompositionCount })
  }

  @Test
  fun rememberRecompositionTracker_legacyOverloadResolvesToTheSameCache() {
    val legacy = rememberRecompositionTracker("Legacy", "", 1)
    val explicit = rememberRecompositionTracker("Legacy", "", 1, "", false)

    assertSame(legacy, explicit)
  }

  @Test
  fun recompositionNanoTime_advances() {
    val start = recompositionNanoTime()
    val later = awaitClockAdvance(start)

    assertTrue(later > start, "clock did not advance within $MAX_CLOCK_READS reads")
  }

  @Test
  fun recompositionNanoTime_isMonotonic() {
    var previous = recompositionNanoTime()

    repeat(1000) {
      val next = recompositionNanoTime()
      assertTrue(next >= previous, "clock went backwards: $next < $previous")
      previous = next
    }
  }

  @Test
  fun recordDuration_measuresAgainstTheSameClock() {
    val tracker = RecompositionTracker("Timed", "", 1)

    val startedAt = recompositionNanoTime()
    // Let the clock move first: a positive duration is what proves recordDuration and the
    // generated start-time capture read the same working clock, which `>= 0` would not.
    awaitClockAdvance(startedAt)
    tracker.recordDuration(startedAt)
    tracker.logIfThresholdMet()

    val duration = logger.events.single().durationNanos
    assertTrue(duration > 0, "duration must be positive, was $duration")
  }

  /** Reads the clock until it passes [from], instead of sleeping. Returns the last reading. */
  private fun awaitClockAdvance(from: Long): Long {
    var reads = 0
    var now = recompositionNanoTime()
    while (now <= from && reads < MAX_CLOCK_READS) {
      now = recompositionNanoTime()
      reads++
    }
    return now
  }

  private class RecordingLogger : RecompositionLogger {
    val events = mutableListOf<RecompositionEvent>()

    override fun log(event: RecompositionEvent) {
      events.add(event)
    }
  }

  private companion object {
    /** Generous enough for a 100us-granularity JS clock, small enough to fail fast. */
    const val MAX_CLOCK_READS = 5_000_000
  }
}
