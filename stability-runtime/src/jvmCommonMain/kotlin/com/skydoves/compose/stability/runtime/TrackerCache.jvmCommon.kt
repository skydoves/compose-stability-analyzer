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

import java.util.concurrent.ConcurrentHashMap

// Global cache to persist trackers across recompositions. Concurrent because trace-all lets
// multiple composition threads (or Previews) hit this map simultaneously.
private val trackerCache = ConcurrentHashMap<String, RecompositionTracker>()

// Resolves to the ConcurrentMap.getOrPut extension (putIfAbsent-based), so concurrent callers
// always converge on one tracker instance. computeIfAbsent is avoided: it requires API 24+.
internal actual fun getOrCreateTracker(
  key: String,
  create: () -> RecompositionTracker,
): RecompositionTracker = trackerCache.getOrPut(key, create)
