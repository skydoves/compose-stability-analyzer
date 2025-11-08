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
package com.skydoves.compose.stability.idea.settings

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for StabilitySettingsState configuration and pattern parsing.
 */
class StabilitySettingsStateTest {

  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testDefaultSettings() {
    val settings = StabilitySettingsState()

    assertTrue(settings.isStabilityCheckEnabled)
    assertTrue(settings.showInlineHints)
    assertFalse(settings.showOnlyUnstableHints)
    assertTrue(settings.showGutterIcons)
    assertFalse(settings.showGutterIconsOnlyForUnskippable)
    assertTrue(settings.showWarnings)
    assertTrue(settings.isStrongSkippingEnabled)
    assertEquals("", settings.ignoredTypePatterns)
    assertEquals("", settings.stabilityConfigurationPath)
  }

  @Test
  fun testDefaultColors_stable() {
    val settings = StabilitySettingsState()

    // Default stable color: green (95, 184, 101)
    val expectedRGB = (95 shl 16) or (184 shl 8) or 101
    assertEquals(expectedRGB, settings.stableGutterColorRGB)
    assertEquals(expectedRGB, settings.stableHintColorRGB)
  }

  @Test
  fun testDefaultColors_unstable() {
    val settings = StabilitySettingsState()

    // Default unstable color: red/orange (232, 104, 74)
    val expectedRGB = (232 shl 16) or (104 shl 8) or 74
    assertEquals(expectedRGB, settings.unstableGutterColorRGB)
    assertEquals(expectedRGB, settings.unstableHintColorRGB)
  }

  @Test
  fun testDefaultColors_runtime() {
    val settings = StabilitySettingsState()

    // Default runtime color: yellow (240, 198, 116)
    val expectedRGB = (240 shl 16) or (198 shl 8) or 116
    assertEquals(expectedRGB, settings.runtimeGutterColorRGB)
    assertEquals(expectedRGB, settings.runtimeHintColorRGB)
  }

  @Test
  fun testGetState_returnsSelf() {
    val settings = StabilitySettingsState()
    assertEquals(settings, settings.getState())
  }

  @Test
  fun testLoadState_copiesProperties() {
    val original = StabilitySettingsState()
    original.isStabilityCheckEnabled = false
    original.showInlineHints = false
    original.showGutterIcons = false

    val target = StabilitySettingsState()
    target.loadState(original)

    assertFalse(target.isStabilityCheckEnabled)
    assertFalse(target.showInlineHints)
    assertFalse(target.showGutterIcons)
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_emptyInput() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = ""

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertTrue(patterns.isEmpty())
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_singlePattern() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = "com.example.*"

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(1, patterns.size)

    assertTrue(patterns[0].matches("com.example.User"))
    assertTrue(patterns[0].matches("com.example.ui.Card"))
    assertFalse(patterns[0].matches("com.other.User"))
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_multiplePatterns() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = """
      com.example.*
      androidx.compose.runtime.*
      kotlin.collections.*
    """.trimIndent()

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(3, patterns.size)

    // Test first pattern
    assertTrue(patterns[0].matches("com.example.User"))

    // Test second pattern
    assertTrue(patterns[1].matches("androidx.compose.runtime.Composable"))

    // Test third pattern
    assertTrue(patterns[2].matches("kotlin.collections.List"))
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_ignoresComments() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = """
      # This is a comment
      com.example.*
      # Another comment
      androidx.compose.*
    """.trimIndent()

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(2, patterns.size) // Comments should be ignored
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_ignoresBlankLines() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = """
      com.example.*

      androidx.compose.*

    """.trimIndent()

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(2, patterns.size) // Blank lines should be ignored
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_handlesWildcards() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = "com.example.*"

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(1, patterns.size)

    // Wildcard should match multiple segments
    assertTrue(patterns[0].matches("com.example.User"))
    assertTrue(patterns[0].matches("com.example.ui.UserCard"))
    assertTrue(patterns[0].matches("com.example.data.model.User"))

    // Should not match different package
    assertFalse(patterns[0].matches("com.other.User"))
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_handlesDotEscaping() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = "com.example.User"

    val patterns = settings.getIgnoredPatternsAsRegex()

    // Dots should be escaped - should match exact class name
    assertTrue(patterns[0].matches("com.example.User"))

    // Should not match variations (dots are literal)
    assertFalse(patterns[0].matches("comXexampleXUser"))
  }

  @Test
  fun testGetCustomStableTypesAsRegex_emptyPath() {
    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = ""

    val patterns = settings.getCustomStableTypesAsRegex()
    assertTrue(patterns.isEmpty())
  }

  @Test
  fun testGetCustomStableTypesAsRegex_nonExistentFile() {
    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = "/non/existent/file.txt"

    val patterns = settings.getCustomStableTypesAsRegex()
    assertTrue(patterns.isEmpty())
  }

  @Test
  fun testGetCustomStableTypesAsRegex_validFile() {
    val configFile = tempFolder.newFile("stability-config.txt")
    configFile.writeText(
      """
      com.example.StableClass
      com.example.immutable.*
      androidx.compose.ui.CustomModifier
      """.trimIndent(),
    )

    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = configFile.absolutePath

    val patterns = settings.getCustomStableTypesAsRegex()
    assertEquals(3, patterns.size)

    assertTrue(patterns[0].matches("com.example.StableClass"))
    assertTrue(patterns[1].matches("com.example.immutable.User"))
    assertTrue(patterns[2].matches("androidx.compose.ui.CustomModifier"))
  }

  @Test
  fun testGetCustomStableTypesAsRegex_ignoresCommentsAndBlankLines() {
    val configFile = tempFolder.newFile("stability-config.txt")
    configFile.writeText(
      """
      # This is a comment
      com.example.StableClass

      # Another comment
      com.example.immutable.*

      """.trimIndent(),
    )

    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = configFile.absolutePath

    val patterns = settings.getCustomStableTypesAsRegex()
    assertEquals(2, patterns.size)
  }

  @Test
  fun testGetCustomStableTypesAsRegex_handlesInvalidRegex() {
    val configFile = tempFolder.newFile("stability-config.txt")
    configFile.writeText(
      """
      com.example.ValidClass
      [invalid(regex
      com.example.AnotherValid
      """.trimIndent(),
    )

    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = configFile.absolutePath

    val patterns = settings.getCustomStableTypesAsRegex()

    // Should skip invalid patterns and only include valid ones
    assertEquals(2, patterns.size)
    assertTrue(patterns[0].matches("com.example.ValidClass"))
    assertTrue(patterns[1].matches("com.example.AnotherValid"))
  }

  @Test
  fun testGetCustomStableTypesAsRegex_handlesIOException() {
    val settings = StabilitySettingsState()
    // Use a directory instead of a file to trigger IOException
    settings.stabilityConfigurationPath = tempFolder.root.absolutePath

    val patterns = settings.getCustomStableTypesAsRegex()
    assertTrue(patterns.isEmpty()) // Should handle exception gracefully
  }

  @Test
  fun testColorSettingsCanBeModified() {
    val settings = StabilitySettingsState()

    // Modify colors
    settings.stableGutterColorRGB = 0xFF0000 // Red
    settings.unstableGutterColorRGB = 0x00FF00 // Green
    settings.runtimeGutterColorRGB = 0x0000FF // Blue

    assertEquals(0xFF0000, settings.stableGutterColorRGB)
    assertEquals(0x00FF00, settings.unstableGutterColorRGB)
    assertEquals(0x0000FF, settings.runtimeGutterColorRGB)
  }

  @Test
  fun testToggleSettingsCanBeModified() {
    val settings = StabilitySettingsState()

    // Toggle all boolean settings
    settings.isStabilityCheckEnabled = false
    settings.showInlineHints = false
    settings.showOnlyUnstableHints = true
    settings.showGutterIcons = false
    settings.showGutterIconsOnlyForUnskippable = true
    settings.showWarnings = false
    settings.isStrongSkippingEnabled = true

    assertFalse(settings.isStabilityCheckEnabled)
    assertFalse(settings.showInlineHints)
    assertTrue(settings.showOnlyUnstableHints)
    assertFalse(settings.showGutterIcons)
    assertTrue(settings.showGutterIconsOnlyForUnskippable)
    assertFalse(settings.showWarnings)
    assertTrue(settings.isStrongSkippingEnabled)
  }

  @Test
  fun testPathSettingsCanBeModified() {
    val settings = StabilitySettingsState()

    settings.stabilityConfigurationPath = "/custom/path/config.txt"
    assertEquals("/custom/path/config.txt", settings.stabilityConfigurationPath)
  }

  @Test
  fun testPatternSettingsCanBeModified() {
    val settings = StabilitySettingsState()

    val patterns = """
      com.example.*
      androidx.compose.*
    """.trimIndent()

    settings.ignoredTypePatterns = patterns
    assertEquals(patterns, settings.ignoredTypePatterns)
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_complexWildcards() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = "com.example.*.internal.*"

    val patterns = settings.getIgnoredPatternsAsRegex()
    assertEquals(1, patterns.size)

    // Multiple wildcards should work
    assertTrue(patterns[0].matches("com.example.ui.internal.Helper"))
    assertTrue(patterns[0].matches("com.example.data.internal.Utils"))
    assertFalse(patterns[0].matches("com.example.public.Helper"))
  }

  @Test
  fun testGetIgnoredPatternsAsRegex_exactMatch() {
    val settings = StabilitySettingsState()
    settings.ignoredTypePatterns = "com.example.SpecificClass"

    val patterns = settings.getIgnoredPatternsAsRegex()

    // Exact match without wildcards
    assertTrue(patterns[0].matches("com.example.SpecificClass"))
    assertFalse(patterns[0].matches("com.example.SpecificClassExtended"))
    assertFalse(patterns[0].matches("com.example.SpecificClass.Inner"))
  }

  @Test
  fun testGetCustomStableTypesAsRegex_wildcardMatching() {
    val configFile = tempFolder.newFile("stability-config.txt")
    configFile.writeText("com.example.stable.*")

    val settings = StabilitySettingsState()
    settings.stabilityConfigurationPath = configFile.absolutePath

    val patterns = settings.getCustomStableTypesAsRegex()
    assertEquals(1, patterns.size)

    assertTrue(patterns[0].matches("com.example.stable.User"))
    assertTrue(patterns[0].matches("com.example.stable.data.Repository"))
    assertFalse(patterns[0].matches("com.example.unstable.User"))
  }
}
