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

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Extension for configuring the Compose Stability Analyzer plugin.
 */
public abstract class StabilityAnalyzerExtension @Inject constructor(
  layout: ProjectLayout,
  objects: ObjectFactory,
  providers: ProviderFactory,
) {
  /**
   * Whether the stability analyzer is enabled.
   * Default: true
   */
  public val enabled: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(true)

  /**
   * Configuration for stability validation (dump and check).
   */
  public val stabilityValidation: StabilityValidationConfig =
    objects.newInstance(StabilityValidationConfig::class.java, layout, objects)

  /**
   * Configure stability validation.
   */
  public fun stabilityValidation(action: Action<StabilityValidationConfig>) {
    action.execute(stabilityValidation)
  }
}

/**
 * Configuration for stability validation.
 */
public abstract class StabilityValidationConfig @Inject constructor(
  layout: ProjectLayout,
  objects: ObjectFactory,
) {
  /**
   * Whether stability validation is enabled.
   * Default: true
   */
  public val enabled: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(true)

  /**
   * Output directory for stability files.
   * Default: <module>/stability/
   */
  public val outputDir: DirectoryProperty =
    objects.directoryProperty().convention(layout.projectDirectory.dir("stability"))

  /**
   * Whether to include test sources in stability analysis.
   * When false, only main source sets will be analyzed (e.g., src/main).
   * When true, test source sets will also be analyzed (e.g., src/test, src/androidTest).
   *
   * Default: false (test code is excluded)
   *
   * Example:
   * ```
   * composeStabilityAnalyzer {
   *   stabilityValidation {
   *     includeTests.set(false) // Exclude test code (default)
   *   }
   * }
   * ```
   */
  public val includeTests: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(false)

  /**
   * Sub-projects/modules that are excluded from stability validation.
   * Specify project names (not paths) to exclude entire modules from analysis.
   *
   * This is useful in multi-module projects to exclude:
   * - Benchmark modules
   * - Example/sample modules
   * - Internal test modules
   * - Documentation modules
   *
   * Default: empty (all projects included)
   *
   * Example:
   * ```
   * composeStabilityAnalyzer {
   *   stabilityValidation {
   *     ignoredProjects.set(listOf("benchmarks", "examples", "samples"))
   *   }
   * }
   * ```
   *
   * Note: Use project names, not paths. For example, use "examples" not ":examples"
   */
  public val ignoredProjects: ListProperty<String> =
    objects.listProperty(String::class.java).convention(emptyList())

  /**
   * Packages to ignore during stability validation.
   * Functions in these packages will not be included in stability files.
   */
  public val ignoredPackages: ListProperty<String> =
    objects.listProperty(String::class.java).convention(emptyList())

  /**
   * Classes to ignore during stability validation.
   * Composables in these classes will not be included in stability files.
   */
  public val ignoredClasses: ListProperty<String> =
    objects.listProperty(String::class.java).convention(emptyList())

  /**
   * Whether to fail the build when stability changes are detected.
   * When false, stability changes will be logged as warnings instead.
   * Default: true
   */
  public val failOnStabilityChange: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(true)

  /**
   * Whether to suppress success messages from stability checks.
   * When true, "✅ Stability check passed." messages will be hidden for modules that pass.
   * Errors and warnings will still be shown.
   *
   * This is useful in multi-module projects to reduce log noise when many modules pass checks.
   * Default: false (success messages shown)
   *
   * Example:
   * ```
   * composeStabilityAnalyzer {
   *   stabilityValidation {
   *     quietCheck.set(true) // Suppress success messages
   *   }
   * }
   * ```
   */
  public val quietCheck: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(false)

  /**
   * Whether to ignore any stable changes from the baseline.
   *
   * When set to true, any new stable parameters, stable functions or removed parameters/functions
   * will not be reported.
   *
   * Default: false
   */
  public val ignoreNonRegressiveChanges: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(false)

  /**
   * When true, plugin will still run normally even with the baseline missing, treating it
   * as an empty baseline
   *
   * Default: false
   */
  public val allowMissingBaseline: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(false)

  /**
   * List of paths to stability configuration files.
   *
   * For more information, see this link:
   *  - [AndroidX stability configuration file](https://developer.android.com/develop/ui/compose/performance/stability/fix#configuration-file)
   *
   * Default: empty
   */
  public val stabilityConfigurationFiles: ListProperty<RegularFile> = objects
    .listProperty(RegularFile::class.java)
    .convention(emptyList())
}
