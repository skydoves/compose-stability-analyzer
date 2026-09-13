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

/** Non-restartable because it returns a `String`, not because of `@ReadOnlyComposable`. */
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

/**
 * A `@ReadOnlyComposable` that returns `Unit`. The annotation alone does not remove the restart
 * group; [readOnlyDemo] above is non-restartable because of its return type, not its annotation.
 */
@Composable
@ReadOnlyComposable
fun ReadOnlyUnitDemo(text: String) {
  println(text)
}

/**
 * An `open` member composable. Restart logic makes a virtual call, so the Compose compiler does not
 * give these a restart group (b/329477544) even though the function itself looks ordinary.
 */
open class OpenRestartabilityDemo {
  @Composable
  open fun OpenMemberDemo(text: String) {
    Text(text)
  }

  /** A `final` member of the same open class keeps its restart group. */
  @Composable
  fun FinalMemberDemo(text: String) {
    Text(text)
  }
}

/** An interface method with a default body is open by definition, so it is not restartable. */
interface RestartabilityScreen {
  @Composable
  fun Content(text: String) {
    Text(text)
  }
}

/** A local composable declared inside another function is not restartable. */
@Composable
fun LocalFunctionHostDemo(text: String) {
  @Composable
  fun LocalContent(inner: String) {
    Text(inner)
  }
  LocalContent(text)
}
