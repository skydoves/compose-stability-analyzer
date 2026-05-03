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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Collects stability information about composable functions during compilation
 * and exports it to a JSON file for use by Gradle tasks.
 */
public class StabilityInfoCollector(private val outputFile: File) {

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
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  public fun export() {
    // Filter out anonymous composables (compiler-generated functions)
    // Check the qualified name to catch cases like "Foo.<anonymous>.Bar"
    val filteredComposables = composables.filter { !it.qualifiedName.contains("<anonymous>") }

    // Don't create file if there are no entries
    if (filteredComposables.isEmpty()) {
      return
    }

    outputFile.parentFile?.mkdirs()

    val report = StabilityReport(
      composables = filteredComposables.sortedBy { it.qualifiedName },
    )

    val json = Json {
      prettyPrint = true
      prettyPrintIndent = "  "
    }

    outputFile.writeText(json.encodeToString(report))
  }
}

/**
 * Root stability report containing all composables.
 */
@Serializable
public data class StabilityReport(val composables: List<ComposableStabilityInfo>)

/**
 * Stability information for a single composable function.
 */
@Serializable
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
@Serializable
public data class ParameterStabilityInfo(
  val name: String,
  val type: String,
  val stability: String, // STABLE, UNSTABLE, RUNTIME
  val reason: String? = null,
)
