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
package com.skydoves.compose.stability.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
public class StabilityAnalyzerPluginRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean = true

  public val pluginId: String = StabilityAnalyzerCommandLineProcessor.PLUGIN_ID

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val enabled = configuration.get(StabilityAnalyzerConfigurationKeys.KEY_ENABLED, true)

    if (!enabled) {
      return
    }

    val stabilityOutputDir = configuration.get(
      StabilityAnalyzerConfigurationKeys.KEY_STABILITY_OUTPUT_DIR,
      "",
    )

    val projectDependencies = configuration.get(
      StabilityAnalyzerConfigurationKeys.KEY_PROJECT_DEPENDENCIES,
      "",
    )

    // Register FIR extensions for frontend analysis (K2)
    FirExtensionRegistrarAdapter.registerExtension(
      StabilityAnalyzerFirExtensionRegistrar(),
    )

    // Register IR generation extension for backend transformations
    IrGenerationExtension.registerExtension(
      StabilityAnalyzerIrGenerationExtension(
        stabilityOutputDir = stabilityOutputDir,
        projectDependencies = projectDependencies,
      ),
    )
  }
}
