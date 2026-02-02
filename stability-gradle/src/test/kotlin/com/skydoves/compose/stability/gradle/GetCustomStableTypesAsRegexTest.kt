package com.skydoves.compose.stability.gradle

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

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

  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun `Fail binary files with invalid characters exception`() {
    val file = File(targetFolder, "patterns.txt")
    // Random string generated from the https://it-tools.tech/token-generator.
    // It results in a random binary file
    file.writeBytes(Base64.decode("SMeCMZ8rlwx1i42PskSpoYiF0fXmiYxWlHSk6yI0DifWgPTE7HWaHTz4dO2BP05lTYJdIPf8jcjGaI44UfBfWouZ42ogkafZvVRWq0RbQm74ScODlB4OWc5aXpKMecEbJduhlYSrOysYwvcAV0Yo9UOHjZ3U9H3uqsF182oXsK2HyUaZ0r7DDFVZej2VLpwFKEbgaMyP8Vwvx0szxRT3QIGXPJ5dtH3ZWJxRRPI5deID8hRjbZOHI1sF9Y7DHTPoTXUaphSaXSUACeBkUB7UMd9fUKwkx4pDa3Hujo2dksEqo5YYtOZATJH9oaDsMGDguCuTRLtbT9gmgFkTSMCHV35buQJDUKyQLPJcAxG4QDuGq9tcmvKGPr6wreqVSkAyyr7zl3SmSlHF"))

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "otherpackage.ClassB",
    )

    val message = assertFails {
      getMatches(listOf(file), classes)
    }.message

    assertNotNull(message)
    assertContains(message, "is not a valid pattern", ignoreCase = true)
  }

  @Test
  fun `Prevent use of characters not allowed in the package names`() {
    val patterns = """
      mypackage.ClassA+
    """.trimIndent()

    val classes = listOf(
      "mypackage.ClassA",
      "mypackage.ClassB",
      "otherpackage.ClassA",
      "otherpackage.ClassB",
    )

    val message = assertFails {
      getMatches(patterns, classes)
    }.message

    assertNotNull(message)
    assertContains(message, "is not a valid pattern")
  }

  @Test
  fun `Allow dollar numbers and underscores`() {
    val patterns = """
      my_package.Class1.SubclassB
    """.trimIndent()

    val classes = listOf(
      "my_package.Class1",
      "my_package.Class2",
      "my_package.Class1.SubclassA",
      "my_package.Class1.SubclassB",
    )

    val result = getMatches(patterns, classes)

    assertEquals(
      listOf(
        "my_package.Class1.SubclassB",
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
    val parsers = fileList.map { StabilityConfigParser.fromFile(it.path) }

    return classes.filter { clazz ->
      parsers.any { stabilityConfigParser ->
        stabilityConfigParser.stableTypeMatchers.any { matcher ->
          matcher.matches(clazz)
        }
      }
    }
  }
}
