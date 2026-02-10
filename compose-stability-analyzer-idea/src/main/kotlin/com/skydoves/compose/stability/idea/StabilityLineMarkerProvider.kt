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

    val icon = getIcon(analysis)
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
   *
   * Colors:
   * - Green (stable): All parameters are stable, composable is skippable
   * - Yellow (runtime): All non-stable parameters are RUNTIME (stability decided at runtime)
   * - Red (unstable): Has at least one UNSTABLE parameter
   */
  private fun getIcon(analysis: ComposableStabilityInfo): Icon {
    val allParams = analysis.parameters + analysis.receivers.map {
      com.skydoves.compose.stability.runtime.ParameterStabilityInfo(
        name = it.type,
        type = it.type,
        stability = it.stability,
        reason = null,
      )
    }

    val hasUnstable = allParams.any { it.stability == ParameterStability.UNSTABLE }
    val hasRuntime = allParams.any { it.stability == ParameterStability.RUNTIME }
    val allStable = allParams.all { it.stability == ParameterStability.STABLE }

    val color = when {
      // Non-restartable / non-skippable composables have no stability semantics — show gray
      !analysis.isSkippable && allParams.isEmpty() -> Color(0x80, 0x80, 0x80)
      analysis.isSkippable && allStable -> Color(settings.stableGutterColorRGB)
      analysis.isSkippable -> Color(settings.stableGutterColorRGB)
      hasUnstable -> Color(settings.unstableGutterColorRGB)
      hasRuntime -> Color(settings.runtimeGutterColorRGB)
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
      val stableCount = analysis.parameters.count { it.stability == ParameterStability.STABLE }
      val unstableCount = analysis.parameters.count { it.stability == ParameterStability.UNSTABLE }
      val runtimeCount = analysis.parameters.count { it.stability == ParameterStability.RUNTIME }
      val totalCount = analysis.parameters.size

      // Check if all non-stable parameters are runtime
      val isRuntimeOnly = unstableCount == 0 && runtimeCount > 0

      append(
        when {
          analysis.isSkippableInStrongSkippingMode ->
            "✅ Skippable (strong skipping mode enabled)"

          analysis.isSkippable ->
            "✅ Skippable (all parameters are stable)"

          isRuntimeOnly ->
            "🟡 Runtime Stability (skippability determined at runtime)"

          // Non-restartable / non-skippable with no params → annotated to skip caching
          !analysis.isRestartable && totalCount == 0 ->
            "⏭️ Non-Restartable (@NonRestartableComposable)" +
              "\nStability analysis is not applicable."

          analysis.isRestartable && !analysis.isSkippable && totalCount == 0 ->
            "⏭️ Non-Skippable (@NonSkippableComposable)" +
              "\nStability analysis is not applicable."

          analysis.isRestartable ->
            "⚠️ Restartable (not skippable)"

          else ->
            "❌ Not Restartable"
        },
      )

      // Parameter count with breakdown
      if (totalCount > 0) {
        append("\n")
        append("Parameters: $stableCount/$totalCount stable")
        if (runtimeCount > 0) {
          append(", $runtimeCount runtime")
        }
        if (unstableCount > 0) {
          append(", $unstableCount unstable")
        }
      }

      // List runtime parameters
      if (runtimeCount > 0) {
        append("\n")
        append("Runtime: ")
        append(
          analysis.parameters
            .filter { it.stability == ParameterStability.RUNTIME }
            .joinToString(", ") { it.name },
        )
      }

      // List unstable parameters
      if (unstableCount > 0) {
        append("\n")
        append("Unstable: ")
        append(
          analysis.parameters
            .filter { it.stability == ParameterStability.UNSTABLE }
            .joinToString(", ") { it.name },
        )
      }

      // Receiver information
      val stableReceiverCount = analysis.receivers.count {
        it.stability == ParameterStability.STABLE
      }
      val unstableReceiverCount = analysis.receivers.count {
        it.stability == ParameterStability.UNSTABLE
      }
      val runtimeReceiverCount = analysis.receivers.count {
        it.stability == ParameterStability.RUNTIME
      }
      val totalReceiverCount = analysis.receivers.size

      if (totalReceiverCount > 0) {
        append("\n")
        append("Receivers: $stableReceiverCount/$totalReceiverCount stable")
        if (runtimeReceiverCount > 0) {
          append(", $runtimeReceiverCount runtime")
        }
      }

      // List unstable receivers
      if (unstableReceiverCount > 0) {
        append("\n")
        append("Unstable receivers: ")
        append(
          analysis.receivers
            .filter { it.stability == ParameterStability.UNSTABLE }
            .joinToString(", ") {
              "${it.receiverKind.name.lowercase()}: ${it.type}"
            },
        )
      }

      // Runtime stability explanation
      if (isRuntimeOnly || runtimeCount > 0) {
        append("\n\n🟡 Runtime Stability:")
        append("\nStability is determined at runtime based on")
        append("\nactual parameter values and their implementations.")
        append("\nSkippability may change between library versions")
        append("\nor when parameter implementations change.")
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
