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

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
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
    private const val MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

    // Artifact coordinates
    private const val GROUP_ID = "com.github.skydoves"
    private const val COMPILER_ARTIFACT_ID = "compose-stability-compiler"
    private const val RUNTIME_ARTIFACT_ID = "compose-stability-runtime"

    // This version should match the version in gradle.properties
    // Update this when bumping the library version
    private const val VERSION = "0.7.0"

    // Compiler option keys
    private const val OPTION_ENABLED = "enabled"
    private const val OPTION_STABILITY_OUTPUT_DIR = "stabilityOutputDir"
    private const val OPTION_PROJECT_DEPENDENCIES = "projectDependencies"
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

    val androidComponents = target.extensions.findByType(AndroidComponentsExtension::class.java)
    if (androidComponents == null) {
      registerTasksNonAndroid(target, extension)
    } else {
      registerTasksAndroid(target, extension, androidComponents)
    }

    // Add output parameter to the Kotlin tasks to ensure it is compatible with the Build Cache
    target.tasks.withType(KotlinCompile::class.java).configureEach {
      val stabilityDir = target.layout.buildDirectory.dir("stability").get()
      outputs.dir(stabilityDir).optional(true)
    }
  }

  private fun registerTasksNonAndroid(
    target: Project,
    extension: StabilityAnalyzerExtension,
  ) {
    // Register stability dump task
    val stabilityDumpTask = target.tasks.register(
      "stabilityDump",
      StabilityDumpTask::class.java,
    ) {
      projectName.set(target.name)
      stabilityInputFiles.setFrom(
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
      projectName.set(target.name)
      stabilityInputFiles.from(
        target.layout.buildDirectory.file("stability/stability-info.json"),
      )
      stabilityReferenceFiles.from(extension.stabilityValidation.outputDir)
      ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
      ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
      failOnStabilityChange.set(extension.stabilityValidation.failOnStabilityChange)
      quietCheck.set(extension.stabilityValidation.quietCheck)
      ignoreNonRegressiveChanges.set(extension.stabilityValidation.ignoreNonRegressiveChanges)
      allowMissingBaseline.set(extension.stabilityValidation.allowMissingBaseline)
    }

    // Make check task depend on stabilityCheck if enabled (only if check task exists)
    target.plugins.withId("base") {
      target.tasks.named("check") {
        dependsOn(stabilityCheckTask)
      }
    }

    // Configure after project evaluation
    target.afterEvaluate {
      configureTaskDependencies(target, extension, null, stabilityDumpTask, stabilityCheckTask)
      addRuntimeDependency(target)
    }
  }

  private fun registerTasksAndroid(
    target: Project,
    extension: StabilityAnalyzerExtension,
    androidComponents: AndroidComponentsExtension<*, *, *>,
  ) {
    val aggregateDumpTask = target.tasks.register("stabilityDump") {
      group = "verification"
      description = "Dump composable stability information to stability file"
    }
    val aggregateCheckTask = target.tasks.register("stabilityCheck") {
      group = "verification"
      description = "Check composable stability against reference file"
    }

    androidComponents.onVariants { variant ->
      val variantNameLowerCase = variant.name.replaceFirstChar { it.lowercaseChar() }
      val variantNameUpperCase = variant.name.replaceFirstChar { it.uppercaseChar() }

      // Register stability dump task
      val stabilityDumpTask = target.tasks.register(
        "${variantNameLowerCase}StabilityDump",
        StabilityDumpTask::class.java,
      ) {
        projectName.set(target.name)
        stabilityInputFiles.setFrom(
          target.layout.buildDirectory.file("stability/stability-info.json"),
        )
        outputDir.set(extension.stabilityValidation.outputDir)
        ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
        ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
        stabilityFileSuffix.set(variant.name)
      }

      // Register stability check task
      val stabilityCheckTask = target.tasks.register(
        "${variantNameLowerCase}StabilityCheck",
        StabilityCheckTask::class.java,
      ) {
        projectName.set(target.name)
        stabilityInputFiles.from(
          target.layout.buildDirectory.file("stability/stability-info.json"),
        )
        stabilityReferenceFiles.from(extension.stabilityValidation.outputDir)
        ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
        ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
        failOnStabilityChange.set(extension.stabilityValidation.failOnStabilityChange)
        quietCheck.set(extension.stabilityValidation.quietCheck)
        stabilityFileSuffix.set(variant.name)
        ignoreNonRegressiveChanges.set(extension.stabilityValidation.ignoreNonRegressiveChanges)
        allowMissingBaseline.set(extension.stabilityValidation.allowMissingBaseline)
      }

      aggregateDumpTask.configure {
        dependsOn(stabilityDumpTask)
      }
      aggregateCheckTask.configure {
        dependsOn(stabilityCheckTask)
      }

      // Make check task depend on stabilityCheck if enabled (only if check task exists)
      target.plugins.withId("base") {
        target.tasks.named("check") {
          dependsOn(stabilityCheckTask)
        }
      }

      // Configure after project evaluation
      target.afterEvaluate {
        configureTaskDependencies(
          target,
          extension,
          variantNameUpperCase,
          stabilityDumpTask,
          stabilityCheckTask,
        )
      }
    }

    target.afterEvaluate {
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
      val projectDependencies = collectProjectDependencies(project)

      // Write project dependencies to a file to avoid empty string issues with SubpluginOption
      val stabilityDir = project.layout.buildDirectory.dir("stability").get().asFile
      stabilityDir.mkdirs()
      val dependenciesFile = java.io.File(stabilityDir, "project-dependencies.txt")
      dependenciesFile.writeText(projectDependencies.joinToString("\n"))

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

      // Add runtime to all compiler plugin classpath configurations
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
   * Uses lazy configuration to avoid eager task resolution and Gradle 9.x compatibility issues.
   */
  private fun configureTaskDependencies(
    project: Project,
    extension: StabilityAnalyzerExtension,
    filter: String? = null,
    stabilityDumpTask: org.gradle.api.tasks.TaskProvider<StabilityDumpTask>,
    stabilityCheckTask: org.gradle.api.tasks.TaskProvider<StabilityCheckTask>,

  ) {
    // Get the includeTests provider for lazy evaluation
    val includeTestsProvider = extension.stabilityValidation.includeTests

    // Configure dependencies lazily using TaskProvider
    stabilityDumpTask.configure {
      // Use provider to lazily collect Kotlin compile task names
      dependsOn(
        includeTestsProvider.map { includeTests ->
          project.tasks.matching { task ->
            isKotlinTaskApplicable(task.name, includeTests) &&
              (filter == null || task.name.contains(filter))
          }
        },
      )

      if (filter != null) {
        // For now, stability check compiler plugin still creates the same files
        // for different variant tasks. That means that even though our variant task
        // is not dependent on the other-variant kotlin tasks, it is still implicitly coupled
        // with them as it reads the same files. To mitigate for this,
        // we tell Gradle that this task must run after any kotlin tasks, even
        // if their variant does not match ours
        mustRunAfter(
          includeTestsProvider.map { includeTests ->
            project.tasks.matching { task ->
              isKotlinTaskApplicable(task.name, includeTests) &&
                !task.name.contains(filter)
            }
          },
        )
      }
    }

    stabilityCheckTask.configure {
      // Use provider to lazily collect Kotlin compile task names
      dependsOn(
        includeTestsProvider.map { includeTests ->
          project.tasks.matching { task ->
            isKotlinTaskApplicable(task.name, includeTests) &&
              (filter == null || task.name.contains(filter))
          }
        },
      )

      if (filter != null) {
        // For now, stability check compiler plugin still creates the same files
        // for different variant tasks. That means that even though our variant task
        // is not dependent on the other-variant kotlin tasks, it is still implicitly coupled
        // with them as it reads the same files. To mitigate for this,
        // we tell Gradle that this task must run after any kotlin tasks, even
        // if their variant does not match ours
        mustRunAfter(
          includeTestsProvider.map { includeTests ->
            project.tasks.matching { task ->
              isKotlinTaskApplicable(
                task.name,
                includeTests,
              ) &&
                !task.name.contains(filter)
            }
          },
        )
      }
    }
  }

  private fun isKotlinTaskApplicable(taskName: String, includeTests: Boolean): Boolean {
    val taskNameLower = taskName.lowercase()

    // Match only actual Kotlin compilation tasks, excluding infrastructure tasks
    val isKotlinCompile = taskName.startsWith("compile") &&
      taskName.contains("Kotlin") &&
      // Exclude wasm-specific sync/webpack/executable tasks
      !taskNameLower.contains("sync") &&
      !taskNameLower.contains("webpack") &&
      !taskNameLower.contains("executable") &&
      !taskNameLower.contains("link") &&
      !taskNameLower.contains("assemble")

    val isTestTask = taskNameLower.let {
      it.contains("test") || it.contains("androidtest") || it.contains("unittest")
    }

    // Include task if it's a Kotlin compile task and either:
    // 1. includeTests is true, OR
    // 2. it's not a test task
    return isKotlinCompile && (includeTests || !isTestTask)
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
  private fun collectProjectDependencies(project: Project): List<String> {
    return try {
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
  private fun findPackageFromSourceDir(dir: java.io.File): String {
    return try {
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
