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

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Gradle plugin for Compose Stability Analyzer.
 * Automatically configures the Kotlin compiler plugin for stability analysis.
 *
 * This plugin follows the KotlinCompilerPluginSupportPlugin pattern for proper
 * integration with the Kotlin Gradle Plugin.
 */
public class StabilityAnalyzerGradlePlugin : KotlinCompilerPluginSupportPlugin {

  public companion object {
    // Plugin IDs
    private const val COMPILER_PLUGIN_ID = "com.skydoves.compose.stability.compiler"
    internal const val MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

    // Artifact coordinates
    internal const val GROUP_ID = "com.github.skydoves"
    private const val COMPILER_ARTIFACT_ID = "compose-stability-compiler"
    internal const val RUNTIME_ARTIFACT_ID = "compose-stability-runtime"

    // This version should match the version in gradle.properties (VERSION_NAME).
    // Update this when bumping the library version — it pins the compiler/runtime
    // artifacts the Gradle plugin pulls onto the Kotlin compile classpath.
    internal const val VERSION = "0.12.0"

    // Compiler option keys
    private const val OPTION_ENABLED = "enabled"
    private const val OPTION_STABILITY_OUTPUT_DIR = "stabilityOutputDir"
    private const val OPTION_TRACE_ALL = "traceAll"
    private const val OPTION_TRACE_ALL_THRESHOLD = "traceAllThreshold"
    private const val OPTION_STABILITY_CONFIGURATION_FILE = "stabilityConfigurationFile"

    /** Maven coordinate of the runtime this plugin version pairs with. */
    internal const val RUNTIME_DEPENDENCY: String = "$GROUP_ID:$RUNTIME_ARTIFACT_ID:$VERSION"
  }

  override fun apply(target: Project) {
    // Create extension for user configuration
    val extension = target.extensions.create(
      "composeStabilityAnalyzer",
      StabilityAnalyzerExtension::class.java,
      target.layout,
    )

    // Add runtime to compiler plugin classpath for all compilations
    addRuntimeToCompilerClasspath(target)

    val registrar =
      if (target.plugins.hasPlugin("com.android.base")) {
        AndroidStabilityTaskRegistrar()
      } else {
        JvmStabilityTaskRegistrar()
      }
    registrar.registerStabilityTasks(target, extension)

    // Per-task output directory to avoid shared output conflicts with other plugins (Issue #153)
    target.tasks.withType(KotlinCompile::class.java).configureEach {
      val stabilityDir = target.layout.buildDirectory.dir("stability/$name")
      outputs.dir(stabilityDir).optional(true)
    }

    // Disable incremental compilation when this project's stability tasks are in the graph
    // (Issue #156). Kotlin IC may skip recompiling files when dependency changes are
    // binary-compatible, but stability can still change (e.g., val → var makes a type UNSTABLE).
    //
    // Only this project's own task paths are probed: `TaskExecutionGraph.allTasks` observes tasks
    // created by other projects, which Isolated Projects rejects ("Project … cannot access the
    // tasks in the task graph that were created by other projects" — issue #107), whereas
    // `hasTask(path)` is clean for own-project paths. The scoping is per project as a result:
    // `./gradlew :app:stabilityCheck` no longer disables incremental compilation in `:core`, which
    // is also more correct, since that task only reads `:app`'s own stability-info.json.
    val projectPathPrefix = if (target.path.endsWith(":")) target.path else "${target.path}:"
    val dumpTasks = target.tasks.withType(StabilityDumpTask::class.java)
    val checkTasks = target.tasks.withType(StabilityCheckTask::class.java)
    val allowIncrementalDisabling = extension.stabilityValidation.allowIncrementalDisabling
    target.gradle.taskGraph.whenReady {
      if (!allowIncrementalDisabling.get()) {
        return@whenReady
      }
      // `names` merges realized and pending registrations, so no task is realized here.
      val hasStabilityTasks = (dumpTasks.names + checkTasks.names).any {
        hasTask("$projectPathPrefix$it")
      }
      if (hasStabilityTasks) {
        target.tasks.withType(KotlinCompile::class.java).configureEach {
          incremental = false
        }
      }
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    val project = kotlinCompilation.target.project
    val extension = project.extensions.findByType(StabilityAnalyzerExtension::class.java)
      ?: return false

    // Check if project is ignored
    val ignoredProjects = extension.stabilityValidation.ignoredProjects.get()
    if (ignoredProjects.contains(project.name)) {
      return false
    }

    // Check if this is a test compilation
    val includeTests = extension.stabilityValidation.includeTests.get()
    if (!includeTests && isTestCompilation(kotlinCompilation)) {
      return false
    }

    return true
  }

  override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = GROUP_ID,
    artifactId = COMPILER_ARTIFACT_ID,
    version = VERSION,
  )

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val extension = project.extensions.getByType(StabilityAnalyzerExtension::class.java)

    // Everything the provider needs is resolved to a plain value or a Provider here, so the lambda
    // below closes over neither Project nor KotlinCompilation (the latter transitively holds the
    // Project). That matters on Kotlin/Native, where KGP realizes this provider eagerly during
    // task configuration rather than at configuration-cache store time.
    // Per-compilation output directory avoids shared output conflicts (Issue #153).
    val stabilityDir = project.layout.buildDirectory
      .dir("stability/${kotlinCompilation.compileTaskProvider.name}")
    val compilationName = kotlinCompilation.name
    val isTestCompilation = isTestCompilation(kotlinCompilation)

    return project.providers.provider {
      val traceAllEnabled = extension.traceAll.enabled.get() &&
        compilationAcceptsTraceAll(
          compilationName,
          isTestCompilation,
          extension.traceAll.variants.get(),
        )

      val stabilityConfigurationFiles = extension
        .stabilityConfigurationFiles
        .getOrElse(emptyList())

      listOf(
        SubpluginOption(
          key = OPTION_ENABLED,
          value = extension.enabled.get().toString(),
        ),
        SubpluginOption(
          key = OPTION_STABILITY_OUTPUT_DIR,
          value = stabilityDir.get().asFile.absolutePath,
        ),
        SubpluginOption(
          key = OPTION_TRACE_ALL,
          value = traceAllEnabled.toString(),
        ),
        SubpluginOption(
          key = OPTION_TRACE_ALL_THRESHOLD,
          value = extension.traceAll.threshold.get().toString(),
        ),
      ) + stabilityConfigurationFiles.map { file ->
        // FilesSubpluginOption (one per file, so each option value is a single path) registers the
        // configuration file as a compile-task input, so editing its contents invalidates the
        // Kotlin compilation and regenerates stability-info.json. A plain SubpluginOption would only
        // track the path string, leaving stale results when the file changes in place (issue #176).
        FilesSubpluginOption(
          key = OPTION_STABILITY_CONFIGURATION_FILE,
          files = listOf(file.asFile),
        )
      }
    }
  }

  /**
   * Decides whether trace-all instruments this compilation. Test compilations never qualify;
   * the rest is delegated to [traceAllMatchesCompilationName].
   */
  internal fun compilationAcceptsTraceAll(
    compilationName: String,
    isTestCompilation: Boolean,
    variantTokens: List<String>,
  ): Boolean {
    if (isTestCompilation) {
      return false
    }
    return traceAllMatchesCompilationName(compilationName, variantTokens)
  }

  /**
   * Add runtime to compiler plugin classpath.
   * This ensures the compiler plugin can access runtime classes during compilation.
   */
  private fun addRuntimeToCompilerClasspath(project: Project) {
    // Add runtime to all compiler plugin classpath configurations
    project.configurations.configureEach {
      if (name.contains("CompilerPluginClasspath", ignoreCase = true)) {
        project.dependencies.add(name, RUNTIME_DEPENDENCY)
      }
    }
  }

  /**
   * Check if a compilation is a test compilation.
   */
  private fun isTestCompilation(compilation: KotlinCompilation<*>): Boolean {
    val compilationName = compilation.name.lowercase()
    return compilationName.contains("test") ||
      compilationName.contains("androidtest") ||
      compilationName.contains("unittest")
  }
}

/**
 * Pure variant-matching rule for trace-all (extracted for unit testing).
 *
 * Android compilations are named after their variant (`debug`, `stagingDebug`, ...), so they
 * must match one of the configured variant tokens (equals or endsWith, case-insensitive).
 * Non-Android main compilations (KMP `main`, jvm, js, native) have no variant dimension and
 * always qualify — the runtime `ComposeStabilityAnalyzer.setEnabled(...)` gate is the
 * production safety net there.
 */
internal fun traceAllMatchesCompilationName(
  compilationName: String,
  variantTokens: List<String>,
): Boolean {
  val normalizedName = compilationName.lowercase()
  if (normalizedName == "main") {
    return true
  }
  return variantTokens.any { token ->
    val normalizedToken = token.lowercase()
    normalizedName == normalizedToken || normalizedName.endsWith(normalizedToken)
  }
}
