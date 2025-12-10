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
package com.skydoves.compose.stability.idea.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import java.io.File

/**
 * Collects all composable functions in the project and analyzes their stability.
 */
public class ComposableStabilityCollector(private val project: Project) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  /**
   * Scans the entire project for composable functions by reading from pre-generated
   * stability-info.json files.
   */
  public fun collectAll(): ComposableStabilityResults {
    if (!settings.isStabilityCheckEnabled) {
      return ComposableStabilityResults(emptyList(), StabilityStats())
    }

    val composables = mutableListOf<ComposableInfo>()
    val gson = Gson()

    // Find all modules and their build directories
    val modules = ModuleManager.getInstance(project).modules
    for (module in modules) {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      val contentRoots = moduleRootManager.contentRoots

      for (contentRoot in contentRoots) {
        // Look for build/stability/stability-info.json
        val jsonFile = File(contentRoot.path, "build/stability/stability-info.json")
        if (!jsonFile.exists()) {
          continue
        }

        try {
          val jsonContent = jsonFile.readText()
          val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
          val composablesArray = jsonObject.getAsJsonArray("composables")

          for (composableElement in composablesArray) {
            val composableJson = composableElement.asJsonObject

            // Skip anonymous composables
            val simpleName = composableJson.get("simpleName").asString
            if (simpleName == "<anonymous>") {
              continue
            }

            val qualifiedName = composableJson.get("qualifiedName").asString
            val skippable = composableJson.get("skippable").asBoolean
            val restartable = composableJson.get("restartable").asBoolean

            // Parse parameters
            val parametersArray = composableJson.getAsJsonArray("parameters")
            val parameters = parametersArray.map { paramElement ->
              val paramJson = paramElement.asJsonObject
              val stability = paramJson.get("stability").asString
              ParameterInfo(
                name = paramJson.get("name").asString,
                type = paramJson.get("type").asString,
                isStable = stability == "STABLE",
                isRuntime = stability == "RUNTIME",
              )
            }

            // Try to find the source file and line number
            val (filePath, fileName, line) =
              ReadAction.compute<Triple<String, String, Int>, Exception> {
                findSourceLocation(qualifiedName, simpleName)
              }

            val packageName = qualifiedName.substringBeforeLast(".$simpleName", "")

            composables.add(
              ComposableInfo(
                functionName = simpleName,
                moduleName = module.name,
                packageName = packageName.ifEmpty { "<default>" },
                fileName = fileName,
                filePath = filePath,
                line = line,
                isSkippable = skippable,
                isRestartable = restartable,
                isRuntime = !skippable && restartable,
                parameters = parameters,
              ),
            )
          }
        } catch (e: Exception) {
          // Skip modules that fail to parse
        }
      }
    }

    // Calculate statistics
    val stats = StabilityStats(
      totalCount = composables.size,
      skippableCount = composables.count { it.isSkippable },
      unskippableCount = composables.count { !it.isSkippable },
    )

    return ComposableStabilityResults(composables, stats)
  }

  /**
   * Finds the source file location for a composable function.
   */
  private fun findSourceLocation(
    qualifiedName: String,
    simpleName: String,
  ): Triple<String, String, Int> {
    try {
      val scope = GlobalSearchScope.projectScope(project)
      val psiManager = PsiManager.getInstance(project)

      val packageName = qualifiedName.substringBeforeLast(".$simpleName", "")
      val allFiles = FileTypeIndex.getFiles(
        KotlinFileType.INSTANCE,
        scope,
      )

      for (virtualFile in allFiles) {
        val ktFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
        if (ktFile.packageFqName.asString() != packageName) {
          continue
        }

        // Search for named functions
        ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { function ->
          if (function.name == simpleName) {
            val line = try {
              val document = com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getDocument(ktFile)
              document?.getLineNumber(function.textOffset)?.plus(1) ?: 0
            } catch (e: Exception) {
              0
            }
            return Triple(virtualFile.path, virtualFile.name, line)
          }
        }

        // Search for properties (for composable properties/getters)
        ktFile.declarations.filterIsInstance<KtProperty>().forEach { property ->
          if (property.name == simpleName) {
            val line = try {
              val document = com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getDocument(ktFile)
              document?.getLineNumber(property.textOffset)?.plus(1) ?: 0
            } catch (e: Exception) {
              0
            }
            return Triple(virtualFile.path, virtualFile.name, line)
          }
        }
      }

      return Triple("Unknown", "Unknown.kt", 0)
    } catch (e: Exception) {
      return Triple("Unknown", "Unknown.kt", 0)
    }
  }
}
