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
package com.skydoves.compose.stability.idea

import java.util.concurrent.atomic.AtomicInteger

internal object StabilityAnalyzerTestHelpers {
  /**
   * Forces the analyzer onto its "fallback scan" path by making reference resolution fail.
   *
   * Why do we force failure?
   * - In production IDE usage, `resolveMainReference()` can throw or return unexpected results:
   *   - in K2 mode,
   *   - during indexing ("dumb mode"),
   *   - or in partially loaded projects.
   * - The fallback scan exists specifically to keep analysis robust in those states.
   *
   * What this helper guarantees:
   * - The override is actually invoked at least once (otherwise the test could silently
   *   exercise the normal resolution path and give a false sense of coverage).
   *
   * Return value:
   * - Pair of (block result, number of forced-resolution calls).
   */
  internal inline fun <T> withForcedResolveFailure(block: () -> T): Pair<T, Int> {
    val calls = AtomicInteger(0)
    val prev = StabilityAnalyzer.resolveMainReferenceOverride

    // Replace the resolver with an implementation that always fails.
    StabilityAnalyzer.resolveMainReferenceOverride = {
      calls.incrementAndGet()
      throw RuntimeException("force resolveMainReference() failure")
    }

    return try {
      block() to calls.get()
    } finally {
      // Always restore the resolver, even if assertions fail.
      StabilityAnalyzer.resolveMainReferenceOverride = prev
    }
  }
}
