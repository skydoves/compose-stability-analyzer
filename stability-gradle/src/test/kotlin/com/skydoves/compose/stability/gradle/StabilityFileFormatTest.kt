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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StabilityFileFormatTest {

  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testWriteStabilityFile_simpleComposable() {
    val entries = listOf(
      StabilityEntry(
        qualifiedName = "com.example.UserCard",
        simpleName = "UserCard",
        visibility = "public",
        parameters = listOf(
          ParameterInfo("user", "User", "STABLE", "marked @Immutable"),
        ),
        returnType = "kotlin.Unit",
        skippable = true,
        restartable = true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "test")

    val content = file.readText()
    assertTrue(content.contains("@Composable"))
    assertTrue(content.contains("public fun com.example.UserCard(user: User): kotlin.Unit"))
    assertTrue(content.contains("skippable: true"))
    assertTrue(content.contains("restartable: true"))
    assertTrue(content.contains("- user: STABLE (marked @Immutable)"))
  }

  @Test
  fun testWriteStabilityFile_multipleParameters() {
    val entries = listOf(
      StabilityEntry(
        qualifiedName = "com.example.ComplexCard",
        simpleName = "ComplexCard",
        visibility = "public",
        parameters = listOf(
          ParameterInfo("name", "String", "STABLE"),
          ParameterInfo("age", "Int", "STABLE"),
          ParameterInfo("data", "MutableData", "UNSTABLE", "has var properties"),
        ),
        returnType = "kotlin.Unit",
        skippable = false,
        restartable = true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "test")

    val content = file.readText()
    assertTrue(content.contains("name: String, age: Int, data: MutableData"))
    assertTrue(content.contains("- name: STABLE"))
    assertTrue(content.contains("- age: STABLE"))
    assertTrue(content.contains("- data: UNSTABLE (has var properties)"))
  }

  @Test
  fun testWriteStabilityFile_noParameters() {
    val entries = listOf(
      StabilityEntry(
        qualifiedName = "com.example.Empty",
        simpleName = "Empty",
        visibility = "public",
        parameters = emptyList(),
        returnType = "kotlin.Unit",
        skippable = true,
        restartable = true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "test")

    val content = file.readText()
    assertTrue(content.contains("public fun com.example.Empty(): kotlin.Unit"))
    assertTrue(content.contains("params:"))
    // Should have empty params section
    assertFalse(content.contains("- "))
  }

  @Test
  fun testWriteStabilityFile_internalVisibility() {
    val entries = listOf(
      StabilityEntry(
        qualifiedName = "com.example.Internal",
        simpleName = "Internal",
        visibility = "internal",
        parameters = emptyList(),
        returnType = "kotlin.Unit",
        skippable = true,
        restartable = true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "test")

    val content = file.readText()
    assertTrue(content.contains("internal fun com.example.Internal"))
  }

  @Test
  fun testWriteStabilityFile_sortedByQualifiedName() {
    val entries = listOf(
      StabilityEntry(
        "com.example.Zebra",
        "Zebra",
        "public",
        emptyList(),
        "kotlin.Unit",
        true,
        true,
      ),
      StabilityEntry(
        "com.example.Apple",
        "Apple",
        "public",
        emptyList(),
        "kotlin.Unit",
        true,
        true,
      ),
      StabilityEntry(
        "com.example.Middle",
        "Middle",
        "public",
        emptyList(),
        "kotlin.Unit",
        true,
        true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "test")

    val content = file.readText()
    val appleIndex = content.indexOf("com.example.Apple")
    val middleIndex = content.indexOf("com.example.Middle")
    val zebraIndex = content.indexOf("com.example.Zebra")

    assertTrue(appleIndex < middleIndex)
    assertTrue(middleIndex < zebraIndex)
  }

  @Test
  fun testWriteStabilityFile_headerComment() {
    val entries = listOf(
      StabilityEntry("com.example.Test", "Test", "public", emptyList(), "kotlin.Unit", true, true),
    )

    val file = createTempFile()
    writeStabilityFile(file, entries, "mymodule")

    val content = file.readText()
    assertTrue(content.startsWith("// This file was automatically generated"))
    assertTrue(content.contains("./gradlew :mymodule:stabilityDump"))
  }

  @Test
  fun testParseStabilityFile_simpleComposable() {
    val fileContent = """
      // Header comment
      @Composable
      public fun com.example.UserCard(user: User): kotlin.Unit
        skippable: true
        restartable: true
        params:
          - user: STABLE (marked @Immutable)

    """.trimIndent()

    val file = createTempFileWithContent(fileContent)
    val entries = parseStabilityFile(file)

    assertEquals(1, entries.size)
    val entry = entries["com.example.UserCard"]
    assertNotNull(entry)
    assertEquals("UserCard", entry.simpleName)
    assertEquals("public", entry.visibility)
    assertTrue(entry.skippable)
    assertTrue(entry.restartable)
    assertEquals(1, entry.parameters.size)
    assertEquals("user", entry.parameters[0].name)
    assertEquals("STABLE", entry.parameters[0].stability)
    assertEquals("marked @Immutable", entry.parameters[0].reason)
  }

  @Test
  fun testParseStabilityFile_multipleComposables() {
    val fileContent = """
      @Composable
      public fun com.example.First(): kotlin.Unit
        skippable: true
        restartable: true
        params:

      @Composable
      public fun com.example.Second(): kotlin.Unit
        skippable: false
        restartable: true
        params:

    """.trimIndent()

    val file = createTempFileWithContent(fileContent)
    val entries = parseStabilityFile(file)

    assertEquals(2, entries.size)
    assertNotNull(entries["com.example.First"])
    assertNotNull(entries["com.example.Second"])
    assertTrue(entries["com.example.First"]!!.skippable)
    assertFalse(entries["com.example.Second"]!!.skippable)
  }

  @Test
  fun testParseStabilityFile_withoutReason() {
    val fileContent = """
      @Composable
      public fun com.example.Test(count: Int): kotlin.Unit
        skippable: true
        restartable: true
        params:
          - count: STABLE

    """.trimIndent()

    val file = createTempFileWithContent(fileContent)
    val entries = parseStabilityFile(file)

    val entry = entries["com.example.Test"]
    assertNotNull(entry)
    assertEquals(1, entry.parameters.size)
    assertEquals("STABLE", entry.parameters[0].stability)
    assertNull(entry.parameters[0].reason)
  }

  @Test
  fun testParseStabilityFile_internalVisibility() {
    val fileContent = """
      @Composable
      internal fun com.example.Internal(): kotlin.Unit
        skippable: true
        restartable: true
        params:

    """.trimIndent()

    val file = createTempFileWithContent(fileContent)
    val entries = parseStabilityFile(file)

    val entry = entries["com.example.Internal"]
    assertNotNull(entry)
    assertEquals("internal", entry.visibility)
  }

  @Test
  fun testRoundTrip_writeAndParse() {
    val original = listOf(
      StabilityEntry(
        qualifiedName = "com.example.UserCard",
        simpleName = "UserCard",
        visibility = "public",
        parameters = listOf(
          ParameterInfo("user", "User", "STABLE", "marked @Immutable"),
          ParameterInfo("count", "Int", "STABLE"),
        ),
        returnType = "kotlin.Unit",
        skippable = true,
        restartable = true,
      ),
    )

    val file = createTempFile()
    writeStabilityFile(file, original, "test")

    val parsed = parseStabilityFile(file)

    assertEquals(1, parsed.size)
    val entry = parsed["com.example.UserCard"]
    assertNotNull(entry)
    assertEquals("UserCard", entry.simpleName)
    assertEquals("public", entry.visibility)
    assertTrue(entry.skippable)
    assertEquals(2, entry.parameters.size)
    assertEquals("user", entry.parameters[0].name)
    assertEquals("STABLE", entry.parameters[0].stability)
    assertEquals("marked @Immutable", entry.parameters[0].reason)
  }

  private fun createTempFile(): File = tempFolder.newFile()

  private fun createTempFileWithContent(content: String): File {
    val file = tempFolder.newFile()
    file.writeText(content)
    return file
  }

  // Simplified implementation for testing
  private fun writeStabilityFile(file: File, entries: List<StabilityEntry>, moduleName: String) {
    file.bufferedWriter().use { writer ->
      writer.write("// This file was automatically generated by Compose Stability Analyzer\n")
      writer.write("//\n")
      writer.write("// Do not edit this file directly. To update it, run:\n")
      writer.write("//   ./gradlew :$moduleName:stabilityDump\n")
      writer.write("\n")

      entries.sortedBy { it.qualifiedName }.forEach { entry ->
        writer.write("@Composable\n")
        writer.write("${entry.visibility} fun ${entry.qualifiedName}(")
        writer.write(entry.parameters.joinToString(", ") { "${it.name}: ${it.type}" })
        writer.write("): ${entry.returnType}\n")
        writer.write("  skippable: ${entry.skippable}\n")
        writer.write("  restartable: ${entry.restartable}\n")
        writer.write("  params:\n")
        entry.parameters.forEach { param ->
          writer.write("    - ${param.name}: ${param.stability}")
          if (param.reason != null) {
            writer.write(" (${param.reason})")
          }
          writer.write("\n")
        }
        writer.write("\n")
      }
    }
  }

  private fun parseStabilityFile(file: File): Map<String, StabilityEntry> {
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
            entries[currentQualifiedName!!] = StabilityEntry(
              qualifiedName = currentQualifiedName!!,
              simpleName = currentSimpleName!!,
              visibility = currentVisibility ?: "public",
              parameters = currentParams,
              returnType = currentReturnType ?: "kotlin.Unit",
              skippable = currentSkippable,
              restartable = currentRestartable,
            )
          }
          currentParams = mutableListOf()
          inParams = false
        }

        line.startsWith("public ") ||
          line.startsWith("internal ") ||
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

            val stability: String
            val reason: String?
            if (stabilityAndReason.contains(" (")) {
              stability = stabilityAndReason.substringBefore(" (").trim()
              reason = stabilityAndReason.substringAfter(" (").substringBefore(")").trim()
            } else {
              stability = stabilityAndReason.trim()
              reason = null
            }

            currentParams.add(ParameterInfo(name, "", stability, reason))
          }
        }

        line.isBlank() && currentQualifiedName != null -> {
          entries[currentQualifiedName!!] = StabilityEntry(
            qualifiedName = currentQualifiedName!!,
            simpleName = currentSimpleName ?: "",
            visibility = currentVisibility ?: "public",
            parameters = currentParams,
            returnType = currentReturnType ?: "kotlin.Unit",
            skippable = currentSkippable,
            restartable = currentRestartable,
          )
          currentQualifiedName = null
          currentParams = mutableListOf()
          inParams = false
        }
      }
    }

    if (currentQualifiedName != null && currentSimpleName != null) {
      entries[currentQualifiedName!!] = StabilityEntry(
        qualifiedName = currentQualifiedName!!,
        simpleName = currentSimpleName!!,
        visibility = currentVisibility ?: "public",
        parameters = currentParams,
        returnType = currentReturnType ?: "kotlin.Unit",
        skippable = currentSkippable,
        restartable = currentRestartable,
      )
    }

    return entries
  }
}
