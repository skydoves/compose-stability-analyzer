package com.skydoves.compose.stability.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCustomStableTypesAsRegexTest {
  private lateinit var targetFolder: File

  @BeforeTest
  fun setup() {
    targetFolder = createTempDirectory().toFile()
    targetFolder.deleteOnExit()
  }

  @AfterTest
  fun tearDown() {
    targetFolder.deleteRecursively()
  }

  @Test
  fun `Basic match`() {
    val patterns = """
      mypackage.ClassA
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "otherpackage.ClassB",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA"
      ),
      result
    )
  }

  @Test
  fun `Match multiple patterns`() {
    val patterns = """
      mypackage.ClassA
      mypackage.ClassB
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "otherpackage.ClassB",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA",
        "mypackage.ClassB",
      ),
      result
    )
  }

  @Test
  fun `Match from multiple files`() {

    val firstFile = File(targetFolder, "patterns1.txt")
    firstFile.writeText("mypackage.ClassA")
    val secondFile = File(targetFolder, "patterns2.txt")
    secondFile.writeText("mypackage.ClassB")

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "otherpackage.ClassB",
    )

    val result = getMatches(listOf(firstFile, secondFile), classes)

    assertEquals(
      listOf(
        "mypackage.ClassA",
        "mypackage.ClassB"
      ),
      result
    )
  }

  private fun getMatches(patterns: String, classes: List<String>): List<String> {
    val file = File(targetFolder, "patterns.txt")
    file.writeText(patterns)

    return getMatches(listOf(file), classes)
  }

  private fun getMatches(
    fileList: List<File>,
    classes: List<String>
  ): List<String> {
    val regexes = getCustomStableTypesAsRegex(fileList)

    return classes.filter { clazz ->
      regexes.any { regex -> regex.matches(clazz) }
      }
  }

}
