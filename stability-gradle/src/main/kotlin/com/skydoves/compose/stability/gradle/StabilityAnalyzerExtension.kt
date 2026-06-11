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

  /**
   * Configuration for trace-all auto-instrumentation.
   */
  public val traceAll: TraceAllConfig =
    objects.newInstance(TraceAllConfig::class.java, objects)

  /**
   * Configure trace-all auto-instrumentation.
   */
  public fun traceAll(action: Action<TraceAllConfig>) {
    action.execute(traceAll)
  }
}

/**
 * Configuration for trace-all auto-instrumentation.
 *
 * When enabled, the compiler plugin instruments every restartable composable in the module for
 * recomposition tracing — as if each one carried `@TraceRecomposition` — so tools like the IDE
 * plugin's Live Heatmap and Stability Doctor receive module-wide runtime data without manual
 * annotations. Explicit `@TraceRecomposition` annotations still win (their tag/threshold apply).
 *
 * Tracing remains gated at runtime by `ComposeStabilityAnalyzer.setEnabled(...)`; with the
 * analyzer disabled, the residual overhead per composition is a map lookup plus early-returned
 * calls. Logging volume is debug-oriented by design: raise [threshold] if very active
 * composables (e.g. animations) produce too many log lines.
 *
 * Example:
 * ```
 * composeStabilityAnalyzer {
 *   traceAll {
 *     enabled.set(true)
 *     threshold.set(2)
 *     variants.set(listOf("debug"))
 *   }
 * }
 * ```
 */
public abstract class TraceAllConfig @Inject constructor(objects: ObjectFactory) {
  /**
   * Whether trace-all auto-instrumentation is enabled.
   * Default: false (opt-in)
   */
  public val enabled: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(false)

  /**
   * Recomposition count threshold for auto-traced composables: logging starts at this
   * recomposition number. The default of 2 silences the initial-composition burst — only
   * composables that actually RE-compose emit events.
   *
   * Default: 2
   */
  public val threshold: Property<Int> =
    objects.property(Int::class.javaObjectType).convention(2)

  /**
   * Android variant/build-type name filters deciding which compilations get trace-all.
   * A compilation is instrumented when its lowercased name equals or ends with one of these
   * tokens — so "debug" matches `debug`, `stagingDebug`, and `fullDebug` but not `release`.
   * Non-Android (KMP/JVM) main compilations are always instrumented while [enabled] is true,
   * since they have no variant dimension; test compilations are never instrumented.
   *
   * Default: ["debug"]
   */
  public val variants: ListProperty<String> =
    objects.listProperty(String::class.java).convention(listOf("debug"))
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
   * When true, only unstable composables (not skippable) are included in the baseline file.
   * This reduces baseline file size in large projects and lets you focus on fixing stability issues.
   *
   * Default: false
   */
  public val unstableOnly: Property<Boolean> =
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

  /**
   * When true, plugin may disable Kotlin's incremental compilation in some cases
   * to improve accuracy.
   *
   * See https://github.com/skydoves/compose-stability-analyzer/issues/156
   *
   * Default: true
   */
  public val allowIncrementalDisabling: Property<Boolean> =
    objects.property(Boolean::class.javaObjectType).convention(true)
}
