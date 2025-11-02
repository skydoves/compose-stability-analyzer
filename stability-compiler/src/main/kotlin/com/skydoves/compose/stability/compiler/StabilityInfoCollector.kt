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

import java.io.File

/**
 * Collects stability information about composable functions during compilation
 * and exports it to a JSON file for use by Gradle tasks.
 */
public class StabilityInfoCollector(
  private val outputFile: File,
) {

  private val composables = mutableListOf<ComposableStabilityInfo>()

  /**
   * Record a composable function's stability information.
   */
  public fun recordComposable(info: ComposableStabilityInfo) {
    composables.add(info)
  }

  /**
   * Export collected stability information to JSON file.
   * Does nothing if no composables were collected.
   * Filters out anonymous composables (compiler-generated lambda functions).
   */
  public fun export() {
    // Filter out anonymous composables (compiler-generated functions)
    // Check the qualified name to catch cases like "Foo.<anonymous>.Bar"
    val filteredComposables = composables.filter { !it.qualifiedName.contains("<anonymous>") }

    // Don't create file if there are no entries
    if (filteredComposables.isEmpty()) {
      return
    }

    outputFile.parentFile?.mkdirs()

    val json = buildString {
      appendLine("{")
      appendLine("  \"composables\": [")

      filteredComposables.sortedBy { it.qualifiedName }.forEachIndexed { index, info ->
        appendLine("    {")
        appendLine("      \"qualifiedName\": \"${info.qualifiedName.escapeJson()}\",")
        appendLine("      \"simpleName\": \"${info.simpleName.escapeJson()}\",")
        appendLine("      \"visibility\": \"${info.visibility}\",")
        appendLine("      \"skippable\": ${info.skippable},")
        appendLine("      \"restartable\": ${info.restartable},")
        appendLine("      \"returnType\": \"${info.returnType.escapeJson()}\",")
        appendLine("      \"parameters\": [")

        info.parameters.forEachIndexed { paramIndex, param ->
          appendLine("        {")
          appendLine("          \"name\": \"${param.name.escapeJson()}\",")
          appendLine("          \"type\": \"${param.type.escapeJson()}\",")
          appendLine("          \"stability\": \"${param.stability}\",")
          if (param.reason != null) {
            appendLine("          \"reason\": \"${param.reason.escapeJson()}\"")
          } else {
            appendLine("          \"reason\": null")
          }
          if (paramIndex < info.parameters.size - 1) {
            appendLine("        },")
          } else {
            appendLine("        }")
          }
        }

        appendLine("      ]")
        if (index < filteredComposables.size - 1) {
          appendLine("    },")
        } else {
          appendLine("    }")
        }
      }

      appendLine("  ]")
      appendLine("}")
    }

    outputFile.writeText(json)
  }

  private fun String.escapeJson(): String {
    return this
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }
}

/**
 * Stability information for a single composable function.
 */
public data class ComposableStabilityInfo(
  val qualifiedName: String,
  val simpleName: String,
  val visibility: String,
  val skippable: Boolean,
  val restartable: Boolean,
  val returnType: String,
  val parameters: List<ParameterStabilityInfo>,
)

/**
 * Stability information for a single parameter.
 */
public data class ParameterStabilityInfo(
  val name: String,
  val type: String,
  val stability: String, // STABLE, UNSTABLE, RUNTIME
  val reason: String? = null,
)
