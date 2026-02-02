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

  @Test
  fun `Match single wildcard at the end`() {
    val patterns = """
      mypackage.Class*
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "mypackage.NotClass",
      "mypackage.ClassA.Child",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA",
        "mypackage.ClassB"
      ),
      result
    )
  }

  @Test
  fun `Match double wildcard at the end`() {
    val patterns = """
      mypackage.Class**
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "mypackage.NotClass",
      "mypackage.ClassA.Child",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA",
        "mypackage.ClassB",
        "mypackage.ClassA.Child",
      ),
      result
    )
  }

  @Test
  fun `Match single wildcard in the middle`() {
    val patterns = """
      mypackage.*.ClassC
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "mypackage.NotClass",
      "mypackage.ClassA.Child",
      "mypackage.ClassA.ClassC",
      "mypackage.ClassB.ClassC",
      "mypackage.ClassB.ClassC.Another.ClassC",
      "mypackage.ClassB.ClassC.Another.YetAnother.ClassC",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA.ClassC",
        "mypackage.ClassB.ClassC"
      ),
      result
    )
  }

  @Test
  fun `Match double wildcard in the middle`() {
    val patterns = """
      mypackage.**.ClassC
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "mypackage.NotClass",
      "mypackage.ClassA.Child",
      "mypackage.ClassA.ClassC",
      "mypackage.ClassB.ClassC",
      "mypackage.ClassB.ClassC.Another.ClassC",
      "mypackage.ClassB.ClassC.Another.YetAnother.ClassC",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "mypackage.ClassA.ClassC",
        "mypackage.ClassB.ClassC",
        "mypackage.ClassB.ClassC.Another.ClassC",
        "mypackage.ClassB.ClassC.Another.YetAnother.ClassC",
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
