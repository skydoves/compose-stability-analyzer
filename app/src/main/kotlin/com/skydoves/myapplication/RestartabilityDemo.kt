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
package com.skydoves.myapplication

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Regression fixtures for issue #184: composables that the Compose compiler does not wrap in a
 * restart group must be reported as `restartable: false` (and therefore `skippable: false`), while
 * `@NonSkippableComposable` stays restartable but opts out of skipping. These entries lock in that
 * behavior in the generated `.stability` baseline; reverting the fix flips them back and fails
 * `stabilityCheck`.
 */

/** `@NonRestartableComposable` has no restart group, so it is neither restartable nor skippable. */
@Composable
@NonRestartableComposable
fun NonRestartableDemo(text: String) {
  Text(text)
}

/** `@NonSkippableComposable` keeps its restart group but opts out of skipping. */
@Composable
@NonSkippableComposable
fun NonSkippableDemo(text: String) {
  Text(text)
}

/** `@ReadOnlyComposable` is not restartable, so it can never be skipped. */
@Composable
@ReadOnlyComposable
fun readOnlyDemo(): String = "read-only"

/** A composable that returns a non-`Unit` value gets no restart group either. */
@Composable
fun nonUnitReturnDemo(): Int = 42

/** An `inline` composable is inlined into the caller and is never restartable on its own. */
@Composable
inline fun InlineWrapperDemo(content: @Composable () -> Unit) {
  content()
}
