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

import kotlin.concurrent.AtomicReference

// Global cache to persist trackers across recompositions. Kotlin/Native has real threads and
// nothing stops a Recomposer from running off the main one, so this cannot be a plain map: a
// concurrent insert would corrupt it. Copy-on-write behind a CAS keeps reads allocation-free and
// makes every caller converge on one tracker per key, like ConcurrentHashMap.putIfAbsent does on
// the JVM. Writes only happen once per composable, so copying the map is not on the hot path.
private val trackerCache =
  AtomicReference<Map<String, RecompositionTracker>>(emptyMap())

internal actual fun getOrCreateTracker(
  key: String,
  create: () -> RecompositionTracker,
): RecompositionTracker {
  while (true) {
    val current = trackerCache.value
    current[key]?.let { return it }

    val created = create()
    if (trackerCache.compareAndSet(current, current + (key to created))) {
      return created
    }
    // Lost the race: another thread published a tracker (for this key or another one). Retry, so
    // the next pass either returns the winner's instance or re-applies this insert.
  }
}
