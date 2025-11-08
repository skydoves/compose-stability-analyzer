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
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

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
    private const val MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

    // Artifact coordinates
    private const val GROUP_ID = "com.github.skydoves"
    private const val COMPILER_ARTIFACT_ID = "compose-stability-compiler"
    private const val RUNTIME_ARTIFACT_ID = "compose-stability-runtime"

    // This version should match the version in gradle.properties
    // Update this when bumping the library version
    private const val VERSION = "0.5.0"

    // Compiler option keys
    private const val OPTION_ENABLED = "enabled"
    private const val OPTION_STABILITY_OUTPUT_DIR = "stabilityOutputDir"
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

    // Register stability dump task
    val stabilityDumpTask = target.tasks.register(
      "stabilityDump",
      StabilityDumpTask::class.java,
    ) {
      stabilityInputFile.set(
        target.layout.buildDirectory.file("stability/stability-info.json"),
      )
      outputDir.set(extension.stabilityValidation.outputDir)
      ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
      ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
    }

    // Register stability check task
    val stabilityCheckTask = target.tasks.register(
      "stabilityCheck",
      StabilityCheckTask::class.java,
    ) {
      stabilityInputFile.set(
        target.layout.buildDirectory.file("stability/stability-info.json"),
      )
      stabilityDir.set(extension.stabilityValidation.outputDir)
      ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
      ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
    }

    // Make check task depend on stabilityCheck if enabled
    target.tasks.named("check") {
      dependsOn(stabilityCheckTask)
    }

    // Configure after project evaluation
    target.afterEvaluate {
      configureTaskDependencies(target, extension, stabilityDumpTask, stabilityCheckTask)
      addRuntimeDependency(target)
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

  override fun getPluginArtifact(): SubpluginArtifact {
    return SubpluginArtifact(
      groupId = GROUP_ID,
      artifactId = COMPILER_ARTIFACT_ID,
      version = VERSION,
    )
  }

  override fun getPluginArtifactForNative(): SubpluginArtifact {
    return SubpluginArtifact(
      groupId = GROUP_ID,
      artifactId = COMPILER_ARTIFACT_ID,
      version = VERSION,
    )
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val extension = project.extensions.getByType(StabilityAnalyzerExtension::class.java)

    return project.provider {
      listOf(
        SubpluginOption(
          key = OPTION_ENABLED,
          value = extension.enabled.get().toString(),
        ),
        SubpluginOption(
          key = OPTION_STABILITY_OUTPUT_DIR,
          value = project.layout.buildDirectory.dir("stability").get().asFile.absolutePath,
        ),
      )
    }
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

      // Add runtime to all compiler plugin classpath configurations (not general compiler classpath)
      project.configurations.configureEach {
        if (name.contains("CompilerPluginClasspath", ignoreCase = true)) {
          project.dependencies.add(name, runtimeDependency)
        }
      }
    }
  }

  /**
   * Add runtime dependency to the project.
   * For multiplatform projects, adds to commonMain sourceSet.
   * For JVM/Android projects, adds to implementation configuration.
   */
  private fun addRuntimeDependency(project: Project) {
    val runtimeProject = getRuntimeProject(project)
    val lintProject = getLintProject(project)

    val runtimeDependency = runtimeProject
      ?: "$GROUP_ID:$RUNTIME_ARTIFACT_ID:$VERSION"

    // Check if this is a multiplatform project
    if (project.plugins.hasPlugin(MULTIPLATFORM_PLUGIN_ID)) {
      // For multiplatform projects, add dependency to commonMain sourceSet
      val kotlinExtension = project.extensions.getByName("kotlin") as KotlinSourceSetContainer
      val commonMain = kotlinExtension.sourceSets.getByName(COMMON_MAIN_SOURCE_SET_NAME)
      commonMain.dependencies {
        implementation(runtimeDependency)
      }
    } else {
      // For JVM/Android projects, add to implementation configuration
      project.dependencies.add("implementation", runtimeDependency)

      // Lint is only supported for Android projects
      if (lintProject != null) {
        project.dependencies.add("lintChecks", lintProject)
      }
    }
  }

  /**
   * Configure task dependencies for stability dump and check tasks.
   */
  private fun configureTaskDependencies(
    project: Project,
    extension: StabilityAnalyzerExtension,
    stabilityDumpTask: org.gradle.api.tasks.TaskProvider<StabilityDumpTask>,
    stabilityCheckTask: org.gradle.api.tasks.TaskProvider<StabilityCheckTask>,
  ) {
    val includeTests = extension.stabilityValidation.includeTests.get()

    project.tasks.matching { task ->
      val isKotlinCompile = task.name.startsWith("compile") && task.name.contains("Kotlin")
      val isTestTask = task.name.lowercase().let {
        it.contains("test") || it.contains("androidtest") || it.contains("unittest")
      }

      // Include task if it's a Kotlin compile task and either:
      // 1. includeTests is true, OR
      // 2. it's not a test task
      isKotlinCompile && (includeTests || !isTestTask)
    }.all {
      stabilityDumpTask.get().dependsOn(this)
      stabilityCheckTask.get().dependsOn(this)
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
   * Get the compiler plugin project if available.
   */
  private fun getCompilerProject(): Project? {
    return null // Will be resolved from current project's rootProject in actual usage
  }

  /**
   * Get the runtime project if available.
   */
  private fun getRuntimeProject(project: Project): Project? {
    return project.rootProject.findProject(":stability-runtime")
  }

  /**
   * Get the lint project if available.
   */
  private fun getLintProject(project: Project): Project? {
    return project.rootProject.findProject(":stability-lint")
  }
}
