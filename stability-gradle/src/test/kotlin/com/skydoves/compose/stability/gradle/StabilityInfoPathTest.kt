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
package com.skydoves.compose.stability.gradle

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [stabilityInfoRelativePaths].
 *
 * Issue #183: in a Kotlin Multiplatform project the Android compile task is
 * `compile<Variant>KotlinAndroid`, so `stability-info.json` lands under that directory. The Android
 * task registrar must look there in addition to the pure-Android `compile<Variant>Kotlin` path.
 */
class StabilityInfoPathTest {

  @Test
  fun includesBothAgpAndKmpAndroidCandidatePaths() {
    val paths = stabilityInfoRelativePaths("debug")
    assertTrue(
      paths.contains("stability/compileDebugKotlin/stability-info.json"),
      "must include the pure-Android (AGP) compile task path",
    )
    assertTrue(
      paths.contains("stability/compileDebugKotlinAndroid/stability-info.json"),
      "must include the KMP Android target compile task path (issue #183)",
    )
  }

  @Test
  fun capitalizesTheVariantName() {
    val paths = stabilityInfoRelativePaths("freeRelease")
    assertTrue(paths.contains("stability/compileFreeReleaseKotlin/stability-info.json"))
    assertTrue(paths.contains("stability/compileFreeReleaseKotlinAndroid/stability-info.json"))
  }
}
