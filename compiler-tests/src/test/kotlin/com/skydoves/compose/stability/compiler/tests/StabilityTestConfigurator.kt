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

import com.skydoves.compose.stability.compiler.StabilityAnalyzerFirExtensionRegistrar
import com.skydoves.compose.stability.compiler.StabilityAnalyzerIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

/**
 * Test configurator that registers the Stability Analyzer compiler plugin
 * for use in compiler tests.
 *
 * This is used by test base classes to ensure the plugin is active during test compilation.
 */
class StabilityTestConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {

  @OptIn(ExperimentalCompilerApi::class)
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    // Register FIR extensions for frontend analysis (K2)
    FirExtensionRegistrarAdapter.registerExtension(
      StabilityAnalyzerFirExtensionRegistrar(),
    )

    // Register IR generation extension for backend transformations
    IrGenerationExtension.registerExtension(
      StabilityAnalyzerIrGenerationExtension(
        stabilityOutputDir = "",
        projectDependencies = "",
      ),
    )
  }
}
