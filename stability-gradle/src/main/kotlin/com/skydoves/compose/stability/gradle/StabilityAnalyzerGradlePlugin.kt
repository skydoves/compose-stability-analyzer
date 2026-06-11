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
    internal const val VERSION = "0.9.0"

    // Compiler option keys
    private const val OPTION_ENABLED = "enabled"
    private const val OPTION_STABILITY_OUTPUT_DIR = "stabilityOutputDir"
    private const val OPTION_PROJECT_DEPENDENCIES = "projectDependencies"
    private const val OPTION_TRACE_ALL = "traceAll"
    private const val OPTION_TRACE_ALL_THRESHOLD = "traceAllThreshold"

    /**
     * Get the runtime project if available.
     */
    internal fun getRuntimeProject(project: Project): Project? =
      project.rootProject.findProject(":stability-runtime")

    /**
     * Get the lint project if available.
     */
    internal fun getLintProject(project: Project): Project? =
      project.rootProject.findProject(":stability-lint")
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

    // Disable incremental compilation when stability tasks are in the graph (Issue #156)
    // Kotlin IC may skip recompiling files when dependency changes are binary-compatible,
    // but stability can still change (e.g., val → var makes a type UNSTABLE).
    target.gradle.taskGraph.whenReady {
      if (extension.stabilityValidation.allowIncrementalDisabling.get()) {
        val hasStabilityTasks = allTasks.any {
          it is StabilityDumpTask || it is StabilityCheckTask
        }
        if (hasStabilityTasks) {
          target.tasks.withType(KotlinCompile::class.java).configureEach {
            incremental = false
          }
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

    return project.provider {
      val projectDependencies = collectProjectDependencies(project)

      // Per-compilation output directory to avoid shared output conflicts (Issue #153)
      val compileTaskName = kotlinCompilation.compileTaskProvider.name
      val stabilityDir = project.layout.buildDirectory
        .dir("stability/$compileTaskName").get().asFile
      stabilityDir.mkdirs()
      val dependenciesFile = java.io.File(stabilityDir, "project-dependencies.txt")
      dependenciesFile.writeText(projectDependencies.joinToString("\n"))

      val traceAllEnabled = extension.traceAll.enabled.get() &&
        compilationAcceptsTraceAll(kotlinCompilation, extension.traceAll.variants.get())

      listOf(
        SubpluginOption(
          key = OPTION_ENABLED,
          value = extension.enabled.get().toString(),
        ),
        SubpluginOption(
          key = OPTION_STABILITY_OUTPUT_DIR,
          value = stabilityDir.absolutePath,
        ),
        SubpluginOption(
          key = OPTION_PROJECT_DEPENDENCIES,
          value = dependenciesFile.absolutePath,
        ),
        SubpluginOption(
          key = OPTION_TRACE_ALL,
          value = traceAllEnabled.toString(),
        ),
        SubpluginOption(
          key = OPTION_TRACE_ALL_THRESHOLD,
          value = extension.traceAll.threshold.get().toString(),
        ),
      )
    }
  }

  /**
   * Decides whether trace-all instruments this compilation. Test compilations never qualify;
   * the rest is delegated to [traceAllMatchesCompilationName].
   */
  internal fun compilationAcceptsTraceAll(
    compilation: KotlinCompilation<*>,
    variantTokens: List<String>,
  ): Boolean {
    if (isTestCompilation(compilation)) {
      return false
    }
    return traceAllMatchesCompilationName(compilation.name, variantTokens)
  }

  /**
   * Add runtime to compiler plugin classpath.
   * This ensures the compiler plugin can access runtime classes during compilation.
   */
  private fun addRuntimeToCompilerClasspath(project: Project) {
    project.afterEvaluate {
      val runtimeProject = getRuntimeProject(project)
      val runtimeDependency = runtimeProject
        ?: "$GROUP_ID:$RUNTIME_ARTIFACT_ID:$VERSION"

      // Add runtime to all compiler plugin classpath configurations
      project.configurations.configureEach {
        if (name.contains("CompilerPluginClasspath", ignoreCase = true)) {
          project.dependencies.add(name, runtimeDependency)
        }
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

  /**
   * Collects package names from project dependencies for cross-module detection.
   * Used by compiler plugin to identify classes from other Gradle modules.
   */
  private fun collectProjectDependencies(project: Project): List<String> = try {
    val dependencies = mutableSetOf<String>()

    // Collect packages from all other subprojects
    // This is a conservative approach: mark all cross-module classes as requiring annotations
    project.rootProject.allprojects.forEach { subproject ->
      if (subproject != project && subproject.name != project.rootProject.name) {
        val packageName = extractPackageName(subproject)
        if (packageName.isNotEmpty()) {
          dependencies.add(packageName)
        }
      }
    }

    dependencies.toList()
  } catch (e: Exception) {
    emptyList()
  }

  /**
   * Extracts package name from a project using: group property, source files, or project path.
   */
  private fun extractPackageName(project: Project): String {
    return try {
      // Strategy 1: Use the group property if set
      val group = project.group.toString()
      if (group.isNotEmpty() && group != "unspecified") {
        return group
      }

      // Strategy 2: Try to find package from source files
      val kotlinSources = project.projectDir.resolve("src/main/kotlin")
      if (kotlinSources.exists()) {
        val packageFromSource = findPackageFromSourceDir(kotlinSources)
        if (packageFromSource.isNotEmpty()) {
          return packageFromSource
        }
      }

      // Strategy 3: Use project path as fallback (e.g., :app-model -> app.model)
      val projectPath = project.path
        .removePrefix(":")
        .replace("-", ".")
        .replace("/", ".")
      projectPath
    } catch (e: Exception) {
      ""
    }
  }

  /**
   * Finds the base package name from a source directory by reading the first .kt file.
   */
  private fun findPackageFromSourceDir(dir: java.io.File): String = try {
    dir.walkTopDown()
      .filter { it.extension == "kt" }
      .firstOrNull()
      ?.let { file ->
        file.readLines()
          .firstOrNull { it.trim().startsWith("package ") }
          ?.removePrefix("package ")
          ?.trim()
          ?.removeSuffix(";")
      } ?: ""
  } catch (e: Exception) {
    ""
  }

  /**
   * Get the compiler plugin project if available.
   */
  private fun getCompilerProject(): Project? {
    return null // Will be resolved from current project's rootProject in actual usage
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
