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
 * Returns the current time in nanoseconds from a monotonic clock source.
 *
 * This is used internally by [RecompositionTracker.recordDuration] to compute
 * recomposition duration from a start time captured at composable entry.
 *
 * Platform implementations:
 * - Android/JVM: delegates to `System.nanoTime()`
 * - Everything else: elapsed nanoseconds from `kotlin.time.TimeSource.Monotonic`
 *
 * Readings are only ever subtracted from each other, so the origin does not matter as long as
 * both readings come from this same clock. That is why compiler-generated code captures its
 * start time through [recompositionNanoTime] rather than reading a platform clock directly.
 */
internal expect fun currentNanoTime(): Long

/**
 * Public entry point for the monotonic clock behind [RecompositionTracker.recordDuration].
 *
 * Compiler-generated code calls this at composable entry and hands the value back to
 * [RecompositionTracker.recordDuration], which subtracts it from its own reading. Both sides
 * therefore share one clock on every target, including those without `System.nanoTime()`.
 */
public fun recompositionNanoTime(): Long = currentNanoTime()
