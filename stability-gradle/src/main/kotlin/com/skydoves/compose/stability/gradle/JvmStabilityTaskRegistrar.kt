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

/**
 * Registers stability tasks for non-Android (JVM/KMP) projects.
 */
internal class JvmStabilityTaskRegistrar : StabilityTaskRegistrar() {

  override fun register(target: Project, extension: StabilityAnalyzerExtension) {
    // Register stability dump task
    val stabilityDumpTask = target.tasks.register(
      "stabilityDump",
      StabilityDumpTask::class.java,
    ) {
      projectName.set(target.name)
      stabilityInputFiles.setFrom(
        target.fileTree(target.layout.buildDirectory.dir("stability")) {
          include("*/stability-info.json")
        },
      )
      outputDir.set(extension.stabilityValidation.outputDir)
      ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
      ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
      stabilityConfigurationFiles.set(extension.stabilityValidation.stabilityConfigurationFiles)
      unstableOnly.set(extension.stabilityValidation.unstableOnly)
    }

    // Register stability check task
    val stabilityCheckTask = target.tasks.register(
      "stabilityCheck",
      StabilityCheckTask::class.java,
    ) {
      projectName.set(target.name)
      stabilityInputFiles.from(
        target.fileTree(target.layout.buildDirectory.dir("stability")) {
          include("*/stability-info.json")
        },
      )
      stabilityReferenceFiles.from(extension.stabilityValidation.outputDir)
      ignoredPackages.set(extension.stabilityValidation.ignoredPackages)
      ignoredClasses.set(extension.stabilityValidation.ignoredClasses)
      failOnStabilityChange.set(extension.stabilityValidation.failOnStabilityChange)
      quietCheck.set(extension.stabilityValidation.quietCheck)
      ignoreNonRegressiveChanges.set(extension.stabilityValidation.ignoreNonRegressiveChanges)
      allowMissingBaseline.set(extension.stabilityValidation.allowMissingBaseline)
      stabilityConfigurationFiles.set(extension.stabilityValidation.stabilityConfigurationFiles)
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
}
