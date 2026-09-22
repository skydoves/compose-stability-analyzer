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

import org.jetbrains.kotlin.gradle.tasks.CompilerPluginOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the build cache relocatability of Kotlin compilations in a consumer's project (issue #212).
 *
 * `CompilerPluginConfig.getAsTaskInputArgs()` is annotated `@Input`, so anything it returns is part
 * of the build cache key. A plain `SubpluginOption` carrying an absolute path therefore makes the
 * checkout location a cache input, and compilation stops being shareable between machines or
 * worktrees. Only `InternalSubpluginOption` and `FilesSubpluginOption` are excluded.
 *
 * The oracle is KGP's own [CompilerPluginOptions], not a reimplementation of its filtering rule, so
 * these tests keep working if KGP changes which subtypes it excludes. It exposes both halves at
 * once: `getAsTaskInputArgs()` is the cache key surface, and `arguments` is what the compiler
 * actually receives.
 *
 * Note this does not cover Kotlin/Native. `KotlinNativeCompile` declares `compilerPluginCommandLine`
 * (which is `arguments`, unfiltered) as a plain `@Input`, so no option subtype can keep a path out
 * of its cache key.
 */
class StabilityOutputDirCacheKeyTest {

  private val marker = "CHECKOUT_PATH_MARKER"
  private val outputDir = File("/$marker/build/stability/compileKotlin")

  /** The keys KGP is expected to expose as task inputs, without the plugin id prefix. */
  private val expectedInputKeys =
    setOf("enabled", "traceAll", "traceAllThreshold", "strongSkipping")

  private fun options(): CompilerPluginOptions {
    val config = CompilerPluginOptions()
    StabilityAnalyzerGradlePlugin.subpluginOptions(
      enabled = true,
      stabilityOutputDir = outputDir,
      traceAllEnabled = true,
      traceAllThreshold = 3,
      strongSkipping = false,
      stabilityConfigurationFiles = listOf(File("/$marker/stability_config.conf")),
    ).forEach { config.addPluginArgument("com.skydoves.compose.stability.compiler", it) }
    return config
  }

  @Test
  fun noTaskInputCarriesTheCheckoutPath() {
    val leaked = options().getAsTaskInputArgs().filterValues { it.contains(marker) }
    assertTrue(
      leaked.isEmpty(),
      "no task input may carry the checkout path, or Kotlin compilation stops being cacheable " +
        "across machines and worktrees. Leaked: $leaked",
    )
  }

  /**
   * Set equality, not membership. Membership alone would stay green if `stabilityOutputDir` were
   * deleted outright, which silently stops the report being written, and would also miss a newly
   * added path-valued option whose path happens not to contain the marker above.
   */
  @Test
  fun taskInputsAreExactlyTheNonPathOptions() {
    val actual = options().getAsTaskInputArgs().keys.map { it.substringAfterLast('.') }.toSet()
    assertEquals(
      expectedInputKeys,
      actual,
      "a new task input means a new cache key component. If it is path valued it must be a " +
        "FilesSubpluginOption instead, and if it is not, add it here deliberately.",
    )
  }

  /**
   * The counterpart to the assertions above: keeping the path out of the cache key is only correct
   * if the compiler still receives it. Without this, emitting `InternalSubpluginOption(key, "")`,
   * or dropping the option, would make every other test in this class greener while silently
   * disabling report generation on every platform.
   */
  @Test
  fun compilerStillReceivesTheOutputDirectory() {
    val arguments = options().arguments
    assertTrue(
      arguments.any {
        it == "plugin:com.skydoves.compose.stability.compiler:stabilityOutputDir=$outputDir"
      },
      "the compiler must still be given the output directory, got: $arguments",
    )
  }

  /** Positive control: the value options really are registered, so the map is never empty. */
  @Test
  fun valueOptionsAreStillRegisteredAsTaskInputs() {
    val args = options().getAsTaskInputArgs().mapKeys { it.key.substringAfterLast('.') }
    assertEquals("true", args["enabled"], "inputs: $args")
    assertEquals("true", args["traceAll"], "inputs: $args")
    assertEquals("3", args["traceAllThreshold"], "inputs: $args")
    assertEquals("false", args["strongSkipping"], "inputs: $args")
  }

  /**
   * The configuration file path must stay out of the task inputs for the same reason as the output
   * directory. `contains`, not `endsWith`: KGP suffixes repeated keys with `.0`/`.1` and flattens
   * composite options into `parent.child`, so an `endsWith` check would miss both shapes.
   */
  @Test
  fun configurationFilePathIsNotPartOfTheTaskInputs() {
    val keys = options().getAsTaskInputArgs().keys
    assertTrue(
      keys.none { it.contains("stabilityConfigurationFile") },
      "stabilityConfigurationFile must not appear in task inputs, got keys: $keys",
    )
  }
}
