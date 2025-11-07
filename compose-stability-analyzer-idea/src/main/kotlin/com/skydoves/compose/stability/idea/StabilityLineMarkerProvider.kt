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
package com.skydoves.compose.stability.idea

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import javax.swing.Icon

/**
 * Provides gutter icons showing stability status of @Composable functions.
 */
public class StabilityLineMarkerProvider : LineMarkerProvider {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  public override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (!settings.isStabilityCheckEnabled || !settings.showGutterIcons) {
      return null
    }

    val function = element.parent as? KtNamedFunction ?: return null

    if (element != function.nameIdentifier) return null

    if (!function.isComposable()) {
      return null
    }

    if (function.isPreview()) {
      return null
    }

    // Check if we should show gutter icons in test code
    if (!settings.showGutterIconsInTests) {
      val containingFile = function.containingFile.virtualFile
      if (containingFile != null) {
        val project = function.project
        // Check if the file is in a test source set
        if (TestSourcesFilter.isTestSources(containingFile, project)) {
          return null
        }
      }
    }

    val analysis = try {
      StabilityAnalyzer.analyze(function)
    } catch (_: Exception) {
      return null
    }

    if (settings.showGutterIconsOnlyForUnskippable && analysis.isSkippable) {
      return null
    }

    val icon = getIcon(analysis.isSkippable, analysis.isRestartable)
    val tooltip = buildTooltip(analysis)

    return LineMarkerInfo(
      element,
      element.textRange,
      icon,
      { tooltip },
      null,
      GutterIconRenderer.Alignment.LEFT,

    )
  }

  /**
   * Gets the appropriate icon based on stability status.
   */
  private fun getIcon(isSkippable: Boolean, isRestartable: Boolean): Icon {
    val color = when {
      isSkippable -> Color(settings.stableGutterColorRGB)
//            isRestartable -> Color(settings.runtimeGutterColorRGB)
      else -> Color(settings.unstableGutterColorRGB)
    }

    return ColorIcon(JBUI.scale(12), color)
  }

  /**
   * Builds a tooltip string for the gutter icon.
   */
  private fun buildTooltip(
    analysis: ComposableStabilityInfo,
  ): String {
    return buildString {
      append(
        when {
          analysis.isSkippableInStrongSkippingMode ->
            "✅ Skippable (strong skipping mode enabled)"

          analysis.isSkippable ->
            "✅ Skippable (all parameters are stable)"

          analysis.isRestartable ->
            "⚠️ Restartable (not skippable)"

          else ->
            "❌ Not Restartable"
        },
      )

      // Parameter count
      val unstableCount = analysis.parameters.count { it.stability != ParameterStability.STABLE }
      val totalCount = analysis.parameters.size

      if (totalCount > 0) {
        append("\n")
        append("Parameters: ${totalCount - unstableCount}/$totalCount stable")
      }

      // List unstable parameters
      if (unstableCount > 0) {
        append("\n")
        append("Unstable: ")
        append(
          analysis.parameters
            .filter { it.stability != ParameterStability.STABLE }
            .joinToString(", ") { it.name },
        )
      }

      // Receiver information
      val unstableReceiverCount = analysis.receivers.count {
        it.stability != ParameterStability.STABLE
      }
      val totalReceiverCount = analysis.receivers.size

      if (totalReceiverCount > 0) {
        append("\n")
        val stableReceivers = totalReceiverCount - unstableReceiverCount
        append("Receivers: $stableReceivers/$totalReceiverCount stable")
      }

      // List unstable receivers
      if (unstableReceiverCount > 0) {
        append("\n")
        append("Unstable receivers: ")
        append(
          analysis.receivers
            .filter { it.stability != ParameterStability.STABLE }
            .joinToString(", ") {
              "${it.receiverKind.name.lowercase()}: ${it.type}"
            },
        )
      }

      // Additional info for strong skipping mode
      if (analysis.isSkippableInStrongSkippingMode) {
        append("\n\n💡 Strong Skipping Mode: All composables are skippable")
        append("\neven with unstable parameters.")
      }

      append("\n\nClick function name for detailed analysis.")
    }
  }
}
