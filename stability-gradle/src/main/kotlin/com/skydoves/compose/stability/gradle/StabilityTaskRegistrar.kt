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

import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.GROUP_ID
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.MULTIPLATFORM_PLUGIN_ID
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.RUNTIME_ARTIFACT_ID
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.VERSION
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.getLintProject
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.getRuntimeProject
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer

/**
 * Base class to register stability tasks.
 */
internal abstract class StabilityTaskRegistrar {

  /**
   * Registers stability tasks for the [target] project.
   */
  abstract fun register(target: Project, extension: StabilityAnalyzerExtension)

  /**
   * Add runtime dependency to the project.
   * For multiplatform projects, adds to commonMain sourceSet.
   * For JVM/Android projects, adds to implementation configuration.
   */
  protected fun addRuntimeDependency(project: Project) {
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
  protected fun configureTaskDependencies(
    project: Project,
    extension: StabilityAnalyzerExtension,
    filter: String? = null,
    stabilityDumpTask: TaskProvider<StabilityDumpTask>,
    stabilityCheckTask: TaskProvider<StabilityCheckTask>,
  ) {
    // Get the includeTests provider for lazy evaluation
    val includeTestsProvider = extension.stabilityValidation.includeTests

    // Configure dependencies lazily using TaskProvider
    stabilityDumpTask.configure {
      dependsOn(
        includeTestsProvider.map { includeTests ->
          project.tasks.matching { task ->
            isKotlinTaskApplicable(task.name, includeTests) &&
              (filter == null || task.name.contains(filter))
          }
        },
      )
    }

    stabilityCheckTask.configure {
      dependsOn(
        includeTestsProvider.map { includeTests ->
          project.tasks.matching { task ->
            isKotlinTaskApplicable(task.name, includeTests) &&
              (filter == null || task.name.contains(filter))
          }
        },
      )
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
}
