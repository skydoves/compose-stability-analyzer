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

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

public object StabilityAnalyzerConfigurationKeys {
  public val KEY_ENABLED: CompilerConfigurationKey<Boolean> =
    CompilerConfigurationKey<Boolean>("enabled")

  public val KEY_STABILITY_OUTPUT_DIR: CompilerConfigurationKey<String> =
    CompilerConfigurationKey<String>("stabilityOutputDir")

  public val KEY_PROJECT_DEPENDENCIES: CompilerConfigurationKey<String> =
    CompilerConfigurationKey<String>("projectDependencies")
}

@OptIn(ExperimentalCompilerApi::class)
public class StabilityAnalyzerCommandLineProcessor : CommandLineProcessor {

  public companion object {
    public const val PLUGIN_ID: String = "com.skydoves.compose.stability.compiler"

    public val OPTION_ENABLED: CliOption = CliOption(
      optionName = "enabled",
      valueDescription = "<true|false>",
      description = "Enable stability analyzer",
      required = false,
    )

    public val OPTION_STABILITY_OUTPUT_DIR: CliOption = CliOption(
      optionName = "stabilityOutputDir",
      valueDescription = "<path>",
      description = "Output directory for stability information",
      required = false,
    )

    public val OPTION_PROJECT_DEPENDENCIES: CliOption = CliOption(
      optionName = "projectDependencies",
      valueDescription = "<path>",
      description = "Path to file containing project module names (one per line)",
      required = false,
    )
  }

  override val pluginId: String = PLUGIN_ID

  override val pluginOptions: Collection<AbstractCliOption> = listOf(
    OPTION_ENABLED,
    OPTION_STABILITY_OUTPUT_DIR,
    OPTION_PROJECT_DEPENDENCIES,
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option) {
      OPTION_ENABLED -> configuration.put(
        StabilityAnalyzerConfigurationKeys.KEY_ENABLED,
        value.toBoolean(),
      )

      OPTION_STABILITY_OUTPUT_DIR -> configuration.put(
        StabilityAnalyzerConfigurationKeys.KEY_STABILITY_OUTPUT_DIR,
        value,
      )

      OPTION_PROJECT_DEPENDENCIES -> configuration.put(
        StabilityAnalyzerConfigurationKeys.KEY_PROJECT_DEPENDENCIES,
        value,
      )
    }
  }
}
