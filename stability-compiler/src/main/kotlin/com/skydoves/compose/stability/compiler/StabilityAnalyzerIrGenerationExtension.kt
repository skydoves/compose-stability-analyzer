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
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import java.io.File

public class StabilityAnalyzerIrGenerationExtension(
  private val stabilityOutputDir: String,
  private val projectDependencies: String,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    // Create stability info collector if output directory is specified
    val collector = if (stabilityOutputDir.isNotEmpty()) {
      val outputFile = File(stabilityOutputDir, "stability-info.json")
      StabilityInfoCollector(outputFile)
    } else {
      null
    }

    // Parse project dependencies from comma-separated string
    val dependencyModules = if (projectDependencies.isNotEmpty()) {
      projectDependencies.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
      emptyList()
    }

    // Create and run the stability analyzer transformer
    val transformer = StabilityAnalyzerTransformer(
      pluginContext = pluginContext,
      stabilityCollector = collector,
      projectDependencies = dependencyModules,
    )

    moduleFragment.transformChildrenVoid(transformer)

    // Export collected stability information
    collector?.export()
  }
}
