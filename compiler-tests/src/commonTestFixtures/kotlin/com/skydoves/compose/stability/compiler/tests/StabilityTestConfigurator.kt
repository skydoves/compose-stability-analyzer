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
package com.skydoves.compose.stability.compiler.tests

import com.skydoves.compose.stability.compiler.StabilityAnalyzerConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure

/**
 * Test configurator that registers compiler configuration for the Stability Analyzer compiler plugin
 */
class StabilityTestConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {

  override val directiveContainers: List<DirectivesContainer> =
    listOf(StabilityTestDirectives)

  override fun configureCompilerConfiguration(
    configuration: CompilerConfiguration,
    module: TestModule,
  ) {
    configuration.put(
      StabilityAnalyzerConfigurationKeys.KEY_TRACE_ALL,
      StabilityTestDirectives.ENABLE_TRACE_ALL in module.directives,
    )
    val parentDirectory = testServices.moduleStructure.originalTestDataFiles.first().parentFile
    configuration.put(
      StabilityAnalyzerConfigurationKeys.KEY_STABILITY_CONFIGURATION_FILES,
      module
        .directives[StabilityTestDirectives.STABILITY_CONFIGURATION_FILES]
        .map { parentDirectory.resolve(it) },
    )
  }
}
