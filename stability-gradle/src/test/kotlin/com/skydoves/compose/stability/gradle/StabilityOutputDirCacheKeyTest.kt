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

import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the build cache relocatability of every Kotlin compilation in a consumer's project
 * (issue #212).
 *
 * `CompilerPluginConfig.getAsTaskInputArgs()` is annotated `@Input`, so anything it returns is part
 * of the build cache key. A plain `SubpluginOption` carrying an absolute path therefore makes the
 * checkout location a cache input, and Kotlin compilation stops being shareable between machines
 * or worktrees. Only `InternalSubpluginOption` and `FilesSubpluginOption` are excluded.
 *
 * The oracle here is KGP's own `CompilerPluginConfig`, not a reimplementation of its filtering
 * rule, so this test keeps working if KGP changes which subtypes it excludes.
 */
class StabilityOutputDirCacheKeyTest {

  private val checkoutMarker = "CHECKOUT_PATH_MARKER"

  private fun inputArgs(): Map<String, String> {
    val options = StabilityAnalyzerGradlePlugin.subpluginOptions(
      enabled = true,
      stabilityOutputDir = File("/$checkoutMarker/build/stability/compileKotlin"),
      traceAllEnabled = true,
      traceAllThreshold = 3,
      strongSkipping = false,
      stabilityConfigurationFiles = listOf(File("/$checkoutMarker/stability_config.conf")),
    )
    val config = CompilerPluginConfig()
    options.forEach { config.addPluginArgument("com.skydoves.compose.stability.compiler", it) }
    return config.getAsTaskInputArgs()
  }

  @Test
  fun outputDirectoryIsNotPartOfTheTaskInputs() {
    val args = inputArgs()
    val leaked = args.filterValues { it.contains(checkoutMarker) }
    assertTrue(
      leaked.isEmpty(),
      "no task input may carry the checkout path, or Kotlin compilation stops being cacheable " +
        "across machines and worktrees. Leaked: $leaked",
    )
    assertTrue(
      args.keys.none { it.endsWith(".stabilityOutputDir") },
      "stabilityOutputDir must not appear in task inputs, got keys: ${args.keys}",
    )
  }

  /**
   * Positive control. Without this, the assertions above would pass just as happily against an
   * empty map, which is exactly what a broken call to `getAsTaskInputArgs()` would produce.
   */
  @Test
  fun valueOptionsAreStillRegisteredAsTaskInputs() {
    val args = inputArgs()
    val suffixed = args.mapKeys { it.key.substringAfterLast('.') }
    assertEquals("true", suffixed["enabled"], "inputs: $args")
    assertEquals("true", suffixed["traceAll"], "inputs: $args")
    assertEquals("3", suffixed["traceAllThreshold"], "inputs: $args")
    assertEquals("false", suffixed["strongSkipping"], "inputs: $args")
  }

  /**
   * The configuration file path must stay out of the task inputs for the same reason as the output
   * directory. Note this says nothing about its contents being tracked: they are not, see the note
   * at the call site in [StabilityAnalyzerGradlePlugin].
   */
  @Test
  fun configurationFilePathIsNotPartOfTheTaskInputs() {
    val args = inputArgs()
    assertTrue(
      args.keys.none { it.contains("stabilityConfigurationFile") },
      "stabilityConfigurationFile must not appear in task inputs, got keys: ${args.keys}",
    )
  }
}
