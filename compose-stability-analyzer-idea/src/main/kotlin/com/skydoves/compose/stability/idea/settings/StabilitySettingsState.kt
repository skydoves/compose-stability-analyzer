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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for Compose Stability Analyzer plugin.
 */
@Service
@State(
  name = "ComposeStabilitySettings",
  storages = [Storage("ComposeStabilitySettings.xml")],
)
public class StabilitySettingsState : PersistentStateComponent<StabilitySettingsState> {

  /**
   * Enable or disable stability analysis.
   */
  public var isStabilityCheckEnabled: Boolean = true

  /**
   * Show inline hints for parameter stability.
   */
  public var showInlineHints: Boolean = true

  /**
   * Show only unstable parameters in inline hints.
   */
  public var showOnlyUnstableHints: Boolean = false

  /**
   * Enable gutter icons for composable functions.
   */
  public var showGutterIcons: Boolean = true

  /**
   * Show gutter icons only for unskippable composables.
   * When enabled, only composables that are not skippable will show gutter icons.
   */
  public var showGutterIconsOnlyForUnskippable: Boolean = false

  /**
   * Show gutter icons in test source sets.
   * When disabled, gutter icons will not appear for composables in test directories.
   */
  public var showGutterIconsInTests: Boolean = false

  /**
   * Show warning annotations (underlines and tooltips) for unstable composables and parameters.
   */
  public var showWarnings: Boolean = true

  /**
   * Ignored type patterns (one per line, supports regex).
   * Types matching these patterns will be excluded from stability analysis.
   *
   * Example:
   * kotlin.*
   * androidx.compose.runtime.*
   * com.example.MyStableClass
   */
  public var ignoredTypePatterns: String = ""

  /**
   * Path to external stability configuration file.
   * This file can define custom stable types and ignored patterns.
   */
  public var stabilityConfigurationPath: String = ""

  /**
   * Enable strong skipping mode support.
   * In strong skipping mode, all lambdas and function references are considered stable.
   */
  public var isStrongSkippingEnabled: Boolean = false

  /**
   * Custom color for stable composable gutter icons (RGB format).
   * Default: green (95, 184, 101)
   */
  public var stableGutterColorRGB: Int = (95 shl 16) or (184 shl 8) or 101

  /**
   * Custom color for unstable composable gutter icons (RGB format).
   * Default: red/orange (232, 104, 74)
   */
  public var unstableGutterColorRGB: Int = (232 shl 16) or (104 shl 8) or 74

  /**
   * Custom color for runtime-stable composable gutter icons (RGB format).
   * Default: yellow (240, 198, 116)
   */
  public var runtimeGutterColorRGB: Int = (240 shl 16) or (198 shl 8) or 116

  /**
   * Custom color for stable parameter hints (RGB format).
   * Default: green (95, 184, 101)
   */
  public var stableHintColorRGB: Int = (95 shl 16) or (184 shl 8) or 101

  /**
   * Custom color for unstable parameter hints (RGB format).
   * Default: red/orange (232, 104, 74)
   */
  public var unstableHintColorRGB: Int = (232 shl 16) or (104 shl 8) or 74

  /**
   * Custom color for runtime-stable parameter hints (RGB format).
   * Default: yellow (240, 198, 116)
   */
  public var runtimeHintColorRGB: Int = (240 shl 16) or (198 shl 8) or 116

  public override fun getState(): StabilitySettingsState = this

  public override fun loadState(state: StabilitySettingsState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  /**
   * Get ignored type patterns as a list of regex patterns.
   */
  public fun getIgnoredPatternsAsRegex(): List<Regex> {
    return ignoredTypePatterns
      .split("\n")
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") } // Support comments
      .map { pattern ->
        try {
          // Convert glob-style wildcards to regex
          pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex()
        } catch (e: Exception) {
          // If regex is invalid, treat as literal string
          Regex.escape(pattern).toRegex()
        }
      }
  }

  /**
   * Get custom stable type patterns from configuration file.
   */
  public fun getCustomStableTypesAsRegex(): List<Regex> {
    if (stabilityConfigurationPath.isEmpty()) {
      return emptyList()
    }

    return try {
      val file = java.io.File(stabilityConfigurationPath)
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
    } catch (e: Exception) {
      emptyList()
    }
  }

  public companion object {
    public fun getInstance(): StabilitySettingsState {
      return service()
    }
  }
}
