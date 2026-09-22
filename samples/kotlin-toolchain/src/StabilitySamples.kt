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
package com.skydoves.stability.sample

import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition

/** All properties are `val` of a stable type, so this reports STABLE. */
data class StableUser(val name: String)

/** A `var` property makes this UNSTABLE. */
class UnstableUser(var name: String)

/** A standard collection defers to a runtime check, so this reports RUNTIME. */
data class UserPage(val users: List<StableUser>)

@Composable
fun ShowStable(user: StableUser) {
  println(user.name)
}

@Composable
fun ShowUnstable(user: UnstableUser) {
  println(user.name)
}

@Composable
fun ShowRuntime(page: UserPage) {
  println(page.users.size)
}

@TraceRecomposition(threshold = 1)
@Composable
fun Traced(user: StableUser) {
  println(user.name)
}
