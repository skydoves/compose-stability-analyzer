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
 * Annotation to enable runtime recomposition tracing for a @Composable function.
 *
 * When applied to a composable function, the compiler plugin will inject code to track:
 * - Recomposition count
 * - Parameter changes (what changed and why)
 * - Unstable parameters
 *
 * Example:
 * ```kotlin
 * @TraceRecomposition(tag = "user-profile", threshold = 3)
 * @Composable
 * fun UserProfile(user: User, onClick: () -> Unit) {
 *   // Function body
 * }
 * ```
 *
 * Output in Logcat after 3rd recomposition:
 * ```
 * [Recomposition #3] UserProfile (tag: user-profile)
 *   ├─ user: User changed (User@abc → User@def)
 *   ├─ onClick: () -> Unit stable
 *   └─ Unstable parameters: [user]
 * ```
 *
 * @param tag Optional custom tag for filtering logs. Useful for grouping related composables.
 * @param threshold Only log after this many recompositions. Default is 1 (log from first recomposition).
 *               Use higher values (e.g., 5 or 10) to reduce noise in frequently recomposing screens.
 * @param traceStates When true, also tracks internal state changes (mutableStateOf, derivedStateOf, etc.)
 *               that cause recomposition, not just parameter changes. Default is false.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class TraceRecomposition(
  val tag: String = "",
  val threshold: Int = 1,
  val traceStates: Boolean = false,
)
