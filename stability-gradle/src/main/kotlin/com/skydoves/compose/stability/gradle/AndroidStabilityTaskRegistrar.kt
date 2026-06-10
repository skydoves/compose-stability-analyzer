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
import com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin.Companion.getLintProject
import org.gradle.api.Project

/**
 * Registers stability tasks for Android projects, per variant.
 */
internal class AndroidStabilityTaskRegistrar : StabilityTaskRegistrar() {

  override fun registerStabilityTasks(target: Project, extension: StabilityAnalyzerExtension) {
    val androidComponents =
      target.extensions.getByType(AndroidComponentsExtension::class.java)

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
          target.layout.buildDirectory.file(
            "stability/compile${variantNameUpperCase}Kotlin/stability-info.json",
          ),
        )
        outputDir.set(extension.stabilityValidation.outputDir)
        ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
        ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
        stabilityFileSuffix.set(variant.name)
        stabilityConfigurationFiles.set(extension.stabilityValidation.stabilityConfigurationFiles)
        unstableOnly.set(extension.stabilityValidation.unstableOnly)
      }

      // Register stability check task
      val stabilityCheckTask = target.tasks.register(
        "${variantNameLowerCase}StabilityCheck",
        StabilityCheckTask::class.java,
      ) {
        projectName.set(target.name)
        stabilityInputFiles.from(
          target.layout.buildDirectory.file(
            "stability/compile${variantNameUpperCase}Kotlin/stability-info.json",
          ),
        )
        stabilityReferenceFiles.from(extension.stabilityValidation.outputDir)
        ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
        ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
        failOnStabilityChange.set(extension.stabilityValidation.failOnStabilityChange)
        quietCheck.set(extension.stabilityValidation.quietCheck)
        stabilityFileSuffix.set(variant.name)
        ignoreNonRegressiveChanges.set(extension.stabilityValidation.ignoreNonRegressiveChanges)
        allowMissingBaseline.set(extension.stabilityValidation.allowMissingBaseline)
        stabilityConfigurationFiles.set(extension.stabilityValidation.stabilityConfigurationFiles)
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

  override fun registerLintingTask(target: Project) {
    getLintProject(target) ?.let { lintProject ->
      target.dependencies.add("lintChecks", lintProject)
    }
  }
}
