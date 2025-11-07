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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import java.awt.Color

/**
 * Settings configurable for Compose Stability Analyzer plugin.
 * Appears in Settings → Tools → Compose Stability Analyzer
 */
public class StabilitySettingsConfigurable : BoundConfigurable("Compose Stability Analyzer") {

  private val settings: StabilitySettingsState = StabilitySettingsState.getInstance()

  // Gutter color panels
  private lateinit var stableGutterColorPanel: ColorPanel
  private lateinit var unstableGutterColorPanel: ColorPanel
  private lateinit var runtimeGutterColorPanel: ColorPanel

  // Hint color panels
  private lateinit var stableHintColorPanel: ColorPanel
  private lateinit var unstableHintColorPanel: ColorPanel
  private lateinit var runtimeHintColorPanel: ColorPanel

  public override fun createPanel(): DialogPanel {
    return panel {
      group("General") {
        row {
          checkBox("Enable stability analysis")
            .bindSelected(settings::isStabilityCheckEnabled)
            .comment("Enable or disable all stability analysis features")
        }

        row {
          checkBox("Enable strong skipping mode")
            .bindSelected(settings::isStrongSkippingEnabled)
            .comment(
              "When enabled, lambdas and function references are considered stable. " +
                "Enable this if you're using Compose Compiler's strong skipping feature.",
            )
        }
      }

      group("Visual Indicators") {
        row {
          checkBox("Show gutter icons")
            .bindSelected(settings::showGutterIcons)
            .comment("Show colored icons in the gutter for composable functions")
        }

        indent {
          row {
            checkBox("Show only for unskippable composables")
              .bindSelected(settings::showGutterIconsOnlyForUnskippable)
              .comment("When enabled, only unskippable composables will show gutter icons")
          }

          row {
            checkBox("Show in test source sets")
              .bindSelected(settings::showGutterIconsInTests)
              .comment(
                "When enabled, gutter icons will appear in test directories (disabled by default)",
              )
          }
        }

        row {
          checkBox("Show warnings")
            .bindSelected(settings::showWarnings)
            .comment("Show warning underlines and tooltips for unstable functions and parameters")
        }

        row {
          checkBox("Show inline hints")
            .bindSelected(settings::showInlineHints)
            .comment("Show stability hints next to parameters")
        }

        indent {
          row {
            checkBox("Show only unstable parameters")
              .bindSelected(settings::showOnlyUnstableHints)
              .comment("When enabled, only unstable/runtime parameters will show hints")
          }
        }
      }

      group("Gutter Icon Colors") {
        row("Stable color:") {
          stableGutterColorPanel = ColorPanel()
          cell(stableGutterColorPanel)
            .comment("Color for stable (skippable) composable functions")
        }

        row("Unstable color:") {
          unstableGutterColorPanel = ColorPanel()
          cell(unstableGutterColorPanel)
            .comment("Color for unstable (non-skippable) composable functions")
        }

        row("Runtime color:") {
          runtimeGutterColorPanel = ColorPanel()
          cell(runtimeGutterColorPanel)
            .comment("Color for runtime-determined stability (currently not used)")
        }
      }

      group("Parameter Hint Colors") {
        row("Stable color:") {
          stableHintColorPanel = ColorPanel()
          cell(stableHintColorPanel)
            .comment("Color for stable parameter hints")
        }

        row("Unstable color:") {
          unstableHintColorPanel = ColorPanel()
          cell(unstableHintColorPanel)
            .comment("Color for unstable parameter hints")
        }

        row("Runtime color:") {
          runtimeHintColorPanel = ColorPanel()
          cell(runtimeHintColorPanel)
            .comment("Color for runtime-determined parameter hints")
        }
      }

      group("External Configuration") {
        row {
          label("Stability configuration file:")
        }

        row {
          textFieldWithBrowseButton(
            fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(),
            fileChosen = { file -> file.path },
          )
            .bindText(settings::stabilityConfigurationPath)
            .align(AlignX.FILL)
            .comment(
              """
              Path to a file containing custom stable type patterns.
              Each line should be a package/type pattern (supports wildcards).
              Types matching these patterns will be treated as stable.

              Example file content:
                # Custom stable types
                com.example.models.*
                com.example.data.ImmutableData
                org.company.stable.*
              """.trimIndent(),
            )
        }
      }

      group("Ignored Type Patterns") {
        row {
          label("Types matching these patterns will be excluded from stability analysis:")
        }

        row {
          textArea()
            .bindText(settings::ignoredTypePatterns)
            .rows(8)
            .align(AlignX.FILL)
            .comment(
              """
              Enter one pattern per line. Supports wildcards (*) and regex patterns.
              Lines starting with # are treated as comments.

              Examples:
                kotlin.*
                androidx.compose.runtime.* - All Compose Runtime library types
                com.skydoves.models.* - Types in specific package
                .*ViewModel - All ViewModel classes
              """.trimIndent(),
            )
        }
      }
    }
  }

  public override fun reset() {
    super.reset()
    // Reset gutter color panels to current settings values
    stableGutterColorPanel.selectedColor = Color(settings.stableGutterColorRGB)
    unstableGutterColorPanel.selectedColor = Color(settings.unstableGutterColorRGB)
    runtimeGutterColorPanel.selectedColor = Color(settings.runtimeGutterColorRGB)

    // Reset hint color panels to current settings values
    stableHintColorPanel.selectedColor = Color(settings.stableHintColorRGB)
    unstableHintColorPanel.selectedColor = Color(settings.unstableHintColorRGB)
    runtimeHintColorPanel.selectedColor = Color(settings.runtimeHintColorRGB)
  }

  public override fun isModified(): Boolean {
    // Check if parent (bound fields) are modified
    if (super.isModified()) return true

    // Check if gutter color panels are modified
    val stableGutterModified =
      stableGutterColorPanel.selectedColor?.rgb != settings.stableGutterColorRGB
    val unstableGutterModified =
      unstableGutterColorPanel.selectedColor?.rgb != settings.unstableGutterColorRGB
    val runtimeGutterModified =
      runtimeGutterColorPanel.selectedColor?.rgb != settings.runtimeGutterColorRGB

    // Check if hint color panels are modified
    val stableHintModified =
      stableHintColorPanel.selectedColor?.rgb != settings.stableHintColorRGB
    val unstableHintModified =
      unstableHintColorPanel.selectedColor?.rgb != settings.unstableHintColorRGB
    val runtimeHintModified =
      runtimeHintColorPanel.selectedColor?.rgb != settings.runtimeHintColorRGB

    return stableGutterModified || unstableGutterModified || runtimeGutterModified ||
      stableHintModified || unstableHintModified || runtimeHintModified
  }

  public override fun apply() {
    super.apply()

    // Apply gutter color changes
    stableGutterColorPanel.selectedColor?.let { color ->
      settings.stableGutterColorRGB = color.rgb
    }
    unstableGutterColorPanel.selectedColor?.let { color ->
      settings.unstableGutterColorRGB = color.rgb
    }
    runtimeGutterColorPanel.selectedColor?.let { color ->
      settings.runtimeGutterColorRGB = color.rgb
    }

    // Apply hint color changes
    stableHintColorPanel.selectedColor?.let { color ->
      settings.stableHintColorRGB = color.rgb
    }
    unstableHintColorPanel.selectedColor?.let { color ->
      settings.unstableHintColorRGB = color.rgb
    }
    runtimeHintColorPanel.selectedColor?.let { color ->
      settings.runtimeHintColorRGB = color.rgb
    }

    // Restart code analysis in all open projects to apply new settings
    ProjectManager.getInstance().openProjects.forEach { project ->
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}
