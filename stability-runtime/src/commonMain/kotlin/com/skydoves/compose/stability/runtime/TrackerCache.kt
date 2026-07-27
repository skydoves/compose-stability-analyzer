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

/**
 * Returns the tracker cached under [key], creating and caching it with [create] on first use.
 *
 * The cache is what makes a [RecompositionTracker] survive across recompositions, so it is
 * process-global. Implementations must return the same instance for a repeated [key] and must be
 * safe against a concurrent insert wherever the platform can actually run threads. Only the
 * storage differs:
 * - Android/JVM: a `ConcurrentHashMap`, since trace-all lets several composition threads (or
 *   Previews) reach the same key at once.
 * - Native: a copy-on-write map behind an atomic compare-and-set, for the same reason.
 * - JS/Wasm: a plain map, because those runtimes execute Kotlin on a single thread.
 */
internal expect fun getOrCreateTracker(
  key: String,
  create: () -> RecompositionTracker,
): RecompositionTracker
