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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task to check if composable stability matches the dumped stability file.
 *
 * Similar to apiCheck from binary-compatibility-validator.
 */
public abstract class StabilityCheckTask : DefaultTask() {

  /**
   * Input file containing current stability information from compiler.
   */
  @get:InputFiles
  public abstract val stabilityInputFiles: ConfigurableFileCollection

  /**
   * Directory containing the reference stability file.
   */
  @get:InputFiles
  public abstract val stabilityReferenceFiles: ConfigurableFileCollection

  /**
   * Packages to ignore.
   */
  @get:Input
  public abstract val ignoredPackages: ListProperty<String>

  /**
   * Classes to ignore.
   */
  @get:Input
  public abstract val ignoredClasses: ListProperty<String>

  /**
   * Project name (captured at configuration time for configuration cache compatibility).
   */
  @get:Input
  public abstract val projectName: Property<String>

  /**
   * Whether to fail the build when stability changes are detected.
   */
  @get:Input
  public abstract val failOnStabilityChange: Property<Boolean>

  /**
   * Whether to suppress success messages when checks pass.
   */
  @get:Input
  public abstract val quietCheck: Property<Boolean>

  /**
   * Suffix to the generated stability file
   */
  @get:Input
  @get:Optional
  public abstract val stabilityFileSuffix: Property<String>

  /**
   * Whether to only report regressive changes
   */
  @get:Input
  public abstract val ignoreNonRegressiveChanges: Property<Boolean>

  @get:Input
  public abstract val allowMissingBaseline: Property<Boolean>

  @get:InputFiles
  public abstract val stabilityConfigurationFiles: ListProperty<RegularFile>

  init {
    group = "verification"
    description = "Check composable stability against reference file"
  }

  @TaskAction
  public fun check() {
    val inputFile = stabilityInputFiles.files.firstOrNull()
    if (inputFile == null || !inputFile.exists()) {
      // If the file doesn't exist, it means the module has no composable functions
      // This is expected for modules like activities or utilities without composables
      logger.lifecycle(
        "ℹ️  No composables found in :${projectName.get()}, skipping stability check",
      )
      return
    }

    val stabilityReferenceFiles = stabilityReferenceFiles.asFileTree.files
    if (!allowMissingBaseline.get() && stabilityReferenceFiles.isEmpty()) {
      // Directory doesn't exist - no baseline has been created yet
      // This is expected for new modules or before the first stabilityDump
      logger.lifecycle(
        "ℹ️  No stability baseline found for :${projectName.get()}, skipping stability check",
      )
      logger.lifecycle(
        "    Run './gradlew :${projectName.get()}:stabilityDump' to create the baseline",
      )
      return
    }

    val stabilityFileName = if (stabilityFileSuffix.isPresent) {
      "${projectName.get()}-${stabilityFileSuffix.get()}"
    } else {
      projectName.get()
    }

    val referenceFile = stabilityReferenceFiles.firstOrNull {
      it.endsWith("$stabilityFileName.stability")
    }
    if (!allowMissingBaseline.get() && referenceFile?.exists() != true) {
      // Directory exists but file doesn't - unusual but handle gracefully
      logger.lifecycle(
        "ℹ️  No stability baseline found for :$stabilityFileName, skipping stability check",
      )
      logger.lifecycle(
        "    Run './gradlew :${projectName.get()}:stabilityDump' to create the baseline",
      )
      return
    }

    val currentStability = parseStabilityFromCompiler(inputFile)
    val referenceStability = parseStabilityFile(referenceFile)
    val differences =
      compareStability(
        currentStability,
        referenceStability,
        ignoreNonRegressiveChanges.get(),
        getCustomStableTypesAsRegex(),
      )

    if (differences.isNotEmpty()) {
      val message = buildString {
        appendLine("The following composables have changed stability:")
        appendLine()
        differences.forEach { diff ->
          appendLine(diff.format())
        }
        appendLine()
        appendLine(
          "If these changes are intentional, run './gradlew stabilityDump' " +
            "to update the stability file.",
        )
      }

      if (failOnStabilityChange.get()) {
        throw GradleException("❌ Stability check failed!\n\n$message")
      } else {
        logger.warn("⚠️  Stability changes detected:\n\n$message")
        logger.lifecycle("✓ Stability check completed with warnings (failOnStabilityChange=false)")
      }
    } else {
      // Only show success message if quietCheck is false
      if (!quietCheck.get()) {
        logger.lifecycle("✅ Stability check passed.")
      }
    }
  }

  private fun parseStabilityFromCompiler(
    file: java.io.File,
  ): Map<String, StabilityEntry> {
    val content = file.readText()
    val entries = mutableMapOf<String, StabilityEntry>()

    val composablesStart = content.indexOf("\"composables\": [")
    if (composablesStart == -1) return entries

    var pos = composablesStart + "\"composables\": [".length
    val endPos = content.lastIndexOf("]")

    while (pos < endPos) {
      val objStart = content.indexOf("{", pos)
      if (objStart == -1 || objStart > endPos) break

      val objEnd = findMatchingBrace(content, objStart)
      if (objEnd == -1) break

      val obj = content.substring(objStart, objEnd + 1)
      val entry = parseComposableObject(obj)
      // Filter out compiler-generated anonymous composables
      if (entry != null && !entry.qualifiedName.contains("<anonymous>")) {
        entries[entry.qualifiedName] = entry
      }

      pos = objEnd + 1
    }

    return entries
  }

  private fun findMatchingBrace(text: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false

    for (i in start until text.length) {
      val c = text[i]

      when {
        escaped -> escaped = false
        c == '\\' -> escaped = true
        c == '"' -> inString = !inString
        !inString && c == '{' -> depth++
        !inString && c == '}' -> {
          depth--
          if (depth == 0) return i
        }
      }
    }
    return -1
  }

  private fun findMatchingBracket(text: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false

    for (i in start until text.length) {
      val c = text[i]

      when {
        escaped -> escaped = false
        c == '\\' -> escaped = true
        c == '"' -> inString = !inString
        !inString && c == '[' -> depth++
        !inString && c == ']' -> {
          depth--
          if (depth == 0) return i
        }
      }
    }
    return -1
  }

  private fun parseComposableObject(obj: String): StabilityEntry? {
    val qualifiedName = extractJsonString(obj, "qualifiedName") ?: return null
    val simpleName = extractJsonString(obj, "simpleName") ?: return null
    val visibility = extractJsonString(obj, "visibility") ?: "public"
    val returnType = extractJsonString(obj, "returnType") ?: "Unit"
    val skippable = extractJsonBoolean(obj, "skippable") ?: false
    val restartable = extractJsonBoolean(obj, "restartable") ?: true

    val parameters = parseJsonParameters(obj)

    return StabilityEntry(
      qualifiedName = qualifiedName,
      simpleName = simpleName,
      visibility = visibility,
      parameters = parameters,
      returnType = returnType,
      skippable = skippable,
      restartable = restartable,
    )
  }

  private fun parseJsonParameters(obj: String): List<ParameterInfo> {
    val params = mutableListOf<ParameterInfo>()
    val paramsStart = obj.indexOf("\"parameters\": [")
    if (paramsStart == -1) return params

    var pos = paramsStart + "\"parameters\": [".length
    // Find matching ] for the parameters array, not just the first ]
    val paramsEnd = findMatchingBracket(obj, paramsStart + "\"parameters\": ".length)
    if (paramsEnd == -1) return params

    while (pos < paramsEnd) {
      val paramStart = obj.indexOf("{", pos)
      if (paramStart == -1 || paramStart > paramsEnd) break

      val paramEnd = findMatchingBrace(obj, paramStart)
      if (paramEnd == -1) break

      val paramObj = obj.substring(paramStart, paramEnd + 1)
      val name = extractJsonString(paramObj, "name") ?: ""
      val type = extractJsonString(paramObj, "type") ?: ""
      val stability = extractJsonString(paramObj, "stability") ?: "UNSTABLE"
      val reason = extractJsonString(paramObj, "reason")

      // Skip <this> parameter (extension receiver)
      if (name != "<this>") {
        params.add(
          ParameterInfo(
            name = name,
            type = type,
            stability = stability,
            reason = reason,
          ),
        )
      }

      pos = paramEnd + 1
    }

    return params
  }

  private fun extractJsonString(json: String, key: String): String? {
    val pattern = "\"$key\":\\s*\"([^\"]*)\""
    val regex = pattern.toRegex()
    val match = regex.find(json) ?: return null
    return match.groupValues[1].unescapeJson()
  }

  private fun extractJsonBoolean(json: String, key: String): Boolean? {
    val pattern = "\"$key\":\\s*(true|false)"
    val regex = pattern.toRegex()
    val match = regex.find(json) ?: return null
    return match.groupValues[1].toBoolean()
  }

  private fun String.unescapeJson(): String {
    return this
      .replace("\\\\", "\\")
      .replace("\\\"", "\"")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
  }

  private fun parseStabilityFile(file: java.io.File?): Map<String, StabilityEntry> {
    if (file?.exists() != true) {
      return emptyMap()
    }

    val entries = mutableMapOf<String, StabilityEntry>()

    var currentQualifiedName: String? = null
    var currentSimpleName: String? = null
    var currentVisibility: String? = null
    var currentReturnType: String? = null
    var currentParams = mutableListOf<ParameterInfo>()
    var currentSkippable = false
    var currentRestartable = false
    var inParams = false

    file.readLines().forEach { line ->
      when {
        line.startsWith("@Composable") -> {
          if (currentQualifiedName != null && currentSimpleName != null) {
            val qn = currentQualifiedName!!
            val sn = currentSimpleName!!
            entries[qn] = StabilityEntry(
              qualifiedName = qn,
              simpleName = sn,
              visibility = currentVisibility ?: "public",
              parameters = currentParams,
              returnType = currentReturnType ?: "Unit",
              skippable = currentSkippable,
              restartable = currentRestartable,
            )
          }
          currentParams = mutableListOf()
          inParams = false
        }

        line.startsWith("public ") || line.startsWith("internal ") ||
          line.startsWith("private ") -> {
          currentVisibility = line.substringBefore(" fun ").trim()
          val signature = line.substringAfter(" fun ").trim()
          val qn = signature.substringBefore("(")
          currentQualifiedName = qn
          currentSimpleName = qn.substringAfterLast(".")

          if (signature.contains("): ")) {
            currentReturnType = signature.substringAfterLast("): ").trim()
          }
        }

        line.trim().startsWith("skippable:") -> {
          currentSkippable = line.substringAfter(":").trim().toBoolean()
        }

        line.trim().startsWith("restartable:") -> {
          currentRestartable = line.substringAfter(":").trim().toBoolean()
        }

        line.trim().startsWith("params:") -> {
          inParams = true
        }

        inParams && line.trim().startsWith("- ") -> {
          val paramLine = line.trim().removePrefix("- ")
          val parts = paramLine.split(": ", limit = 2)
          if (parts.size == 2) {
            val name = parts[0].trim()
            val stabilityAndReason = parts[1]

            // Skip <this> parameter (extension receiver)
            if (name != "<this>") {
              val stability: String
              val reason: String?
              if (stabilityAndReason.contains(" (")) {
                stability = stabilityAndReason.substringBefore(" (").trim()
                reason = stabilityAndReason
                  .substringAfter(" (")
                  .substringBefore(")")
                  .trim()
              } else {
                stability = stabilityAndReason.trim()
                reason = null
              }

              currentParams.add(
                ParameterInfo(
                  name = name,
                  type = "", // Type not stored in .stability format
                  stability = stability,
                  reason = reason,
                ),
              )
            }
          }
        }

        line.isBlank() && currentQualifiedName != null -> {
          // End of function entry - save it
          val qn = currentQualifiedName!!
          entries[qn] = StabilityEntry(
            qualifiedName = qn,
            simpleName = currentSimpleName ?: "",
            visibility = currentVisibility ?: "public",
            parameters = currentParams,
            returnType = currentReturnType ?: "Unit",
            skippable = currentSkippable,
            restartable = currentRestartable,
          )
          currentQualifiedName = null
          currentParams = mutableListOf()
          inParams = false
        }
      }
    }

    // Save last entry if exists
    if (currentQualifiedName != null && currentSimpleName != null) {
      val qn = currentQualifiedName!!
      val sn = currentSimpleName!!
      entries[qn] = StabilityEntry(
        qualifiedName = qn,
        simpleName = sn,
        visibility = currentVisibility ?: "public",
        parameters = currentParams,
        returnType = currentReturnType ?: "Unit",
        skippable = currentSkippable,
        restartable = currentRestartable,
      )
    }

    return entries
  }

  /**
   * Get custom stable type patterns from configuration file.
   */
  private fun getCustomStableTypesAsRegex(): List<Regex> {
    return try {
      stabilityConfigurationFiles.get().flatMap { stabilityConfigurationFile ->
        val file = stabilityConfigurationFile.asFile
        if (!file.exists() || !file.isFile) {
          return emptyList()
        }

        // Parse the configuration file
        val patterns = mutableListOf<String>()
        file.readLines().forEach { line ->
          val trimmed = line.trim()
          // Skip empty lines and comments
          if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            patterns.add(trimmed)
          }
        }

        // Convert patterns to regex
        patterns.mapNotNull { pattern ->
          try {
            // Convert glob-style wildcards to regex
            pattern
              .replace(".", "\\.")
              .replace("*", ".*")
              .toRegex()
          } catch (e: Exception) {
            null // Skip invalid patterns
          }
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }
}

/**
 * Represents a difference in stability between current and reference.
 */
internal sealed class StabilityDifference {
  public abstract fun format(): String

  public data class NewFunction(
    val name: String,
    val parameters: List<ParameterInfo>,
  ) : StabilityDifference() {
    override fun format(): String {
      val endColon = if (parameters.isEmpty()) "" else ":"
      return "+ $name (new composable)$endColon\n" +
        "${parameters.joinToString("\n") { "    ${it.name}: ${it.stability}" }}"
    }
  }

  public data class RemovedFunction(val name: String) : StabilityDifference() {
    override fun format(): String = "- $name (removed composable)"
  }

  public data class SkippabilityChanged(
    val function: String,
    val from: Boolean,
    val to: Boolean,
  ) : StabilityDifference() {
    override fun format(): String =
      "~ $function: skippable changed from $from to $to"
  }

  public data class ParameterCountChanged(
    val function: String,
    val from: Int,
    val to: Int,
  ) : StabilityDifference() {
    override fun format(): String =
      "~ $function: parameter count changed from $from to $to"
  }

  public data class ParameterStabilityChanged(
    val function: String,
    val parameter: String,
    val from: String,
    val to: String,
  ) : StabilityDifference() {
    override fun format(): String =
      "~ $function($parameter): stability changed from $from to $to"
  }
}
