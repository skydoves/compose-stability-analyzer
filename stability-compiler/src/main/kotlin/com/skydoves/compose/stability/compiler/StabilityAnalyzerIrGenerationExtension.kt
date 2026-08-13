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

import com.skydoves.compose.stability.compiler.lower.StabilityAnalyzerTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import java.io.File

public class StabilityAnalyzerIrGenerationExtension(
  private val stabilityOutputDir: String,
  private val traceAll: Boolean = false,
  private val traceAllThreshold: Int = 2,
  private val stabilityConfigurationFiles: List<File> = emptyList(),
  private val messageCollector: MessageCollector = MessageCollector.NONE,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    // Create stability info collector if output directory is specified
    val collector = if (stabilityOutputDir.isNotEmpty()) {
      val outputFile = File(stabilityOutputDir, "stability-info.json")
      StabilityInfoCollector(outputFile)
    } else {
      null
    }

    // Construct matchers out of stability configuration files. A malformed or unreadable config
    // file is skipped (with a warning) instead of aborting compilation, and the remaining valid
    // files still contribute their matchers. Unlike a silent skip, the warning makes a
    // misconfiguration debuggable instead of silently reverting configured types to unstable.
    val stabilityConfigurationMatchers = stabilityConfigurationFiles.flatMap { file ->
      try {
        if (file.exists()) {
          StabilityConfigParser.fromFile(file.absolutePath).stableTypeMatchers
        } else {
          messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "Stability configuration file not found, ignoring it: ${file.path}",
          )
          emptyList()
        }
      } catch (e: Exception) {
        messageCollector.report(
          CompilerMessageSeverity.WARNING,
          "Failed to parse stability configuration file, ignoring it: ${file.path} (${e.message})",
        )
        emptyList()
      }
    }

    // Create and run the stability analyzer transformer
    val transformer = StabilityAnalyzerTransformer(
      pluginContext = pluginContext,
      stabilityCollector = collector,
      traceAll = traceAll,
      traceAllThreshold = traceAllThreshold,
      stabilityConfigurationMatchers = stabilityConfigurationMatchers,
    )

    moduleFragment.transformChildrenVoid(transformer)

    // Export collected stability information
    collector?.export()
  }
}
