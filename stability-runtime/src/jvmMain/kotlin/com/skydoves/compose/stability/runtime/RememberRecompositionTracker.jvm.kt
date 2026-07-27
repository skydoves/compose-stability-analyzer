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

/*
 * Binary compatibility only. `rememberRecompositionTracker` moved to commonMain in 0.12.0, which
 * moved its JVM file facade from `RememberRecompositionTracker_jvmKt` to
 * `RememberRecompositionTrackerKt`. Code instrumented by 0.11.x and earlier still calls the old
 * facade, so this file keeps that class name (derived from the file name, as before) with the old
 * signatures delegating to the common implementation. Hidden from Kotlin resolution: callers get
 * the commonMain function.
 *
 * Nothing new should be added here.
 */

@Deprecated(
  message = "Binary compatibility with 0.11.x instrumentation. Use rememberRecompositionTracker.",
  level = DeprecationLevel.HIDDEN,
)
@JvmName("rememberRecompositionTracker")
public fun rememberRecompositionTrackerLegacyFacade(
  composableName: String,
  tag: String,
  threshold: Int,
  fqName: String,
  isAutoTraced: Boolean,
): RecompositionTracker =
  rememberRecompositionTracker(composableName, tag, threshold, fqName, isAutoTraced)

@Deprecated(
  message = "Binary compatibility with 0.11.x instrumentation. Use rememberRecompositionTracker.",
  level = DeprecationLevel.HIDDEN,
)
@JvmName("rememberRecompositionTracker")
public fun rememberRecompositionTrackerLegacyFacade(
  composableName: String,
  tag: String,
  threshold: Int,
): RecompositionTracker = rememberRecompositionTracker(composableName, tag, threshold)
