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

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides inline hints showing stability information for parameters.
 */
@Suppress("UnstableApiUsage")
public class StabilityInlayHintsProvider :
  InlayHintsProvider<StabilityInlayHintsProvider.Settings> {

  override val key: SettingsKey<Settings> = SettingsKey("compose.stability.inlay.hints")

  override val name: String = "Compose Stability Hints"

  override val previewText: String = """
        @Composable
        fun UserCard(
            user: User,
            onClick: () -> Unit,
            items: MutableList<String>
        ) {
            // ...
        }
  """.trimIndent()

  override fun createSettings(): Settings = Settings()

  override fun createConfigurable(settings: Settings): ImmediateConfigurable {
    return object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent {
        val panel = JPanel()
        return panel
      }
    }
  }

  override fun getCollectorFor(
    file: PsiFile,
    editor: Editor,
    settings: Settings,
    sink: InlayHintsSink,
  ): InlayHintsCollector {
    return object : FactoryInlayHintsCollector(editor) {
      private val globalSettings = StabilitySettingsState.getInstance()

      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!globalSettings.isStabilityCheckEnabled || !globalSettings.showInlineHints) {
          return true
        }

        if (element !is KtNamedFunction) return true

        if (!element.isComposable()) {
          return true
        }

        if (element.isPreview()) {
          return true
        }

        val analysis = try {
          StabilityAnalyzer.analyze(element)
        } catch (_: Exception) {
          return true
        }

        // Add hints for each parameter
        element.valueParameters.forEach { param ->
          val paramInfo = analysis.parameters.find { it.name == param.name }
          if (paramInfo != null) {
            if (
              globalSettings.showOnlyUnstableHints &&
              paramInfo.stability == ParameterStability.STABLE
            ) {
              return@forEach
            }

            val hint = createHint(factory, paramInfo)
            param.typeReference?.let { typeRef ->
              sink.addInlineElement(
                offset = typeRef.textRange.endOffset,
                relatesToPrecedingText = true,
                presentation = hint,
                placeAtTheEndOfLine = false,
              )
            }
          }
        }

        return true
      }

      /**
       * Creates an inlay hint presentation for a parameter with colored icon and tooltip.
       */
      private fun createHint(
        factory: PresentationFactory,
        param: com.skydoves.compose.stability.runtime.ParameterStabilityInfo,
      ): InlayPresentation {
        val (text, color) = when (param.stability) {
          ParameterStability.STABLE ->
            StabilityConstants.Labels.STABLE to Color(globalSettings.stableHintColorRGB)

          ParameterStability.UNSTABLE ->
            StabilityConstants.Labels.UNSTABLE to Color(globalSettings.unstableHintColorRGB)

          ParameterStability.RUNTIME, ParameterStability.UNKNOWN ->
            StabilityConstants.Labels.RUNTIME to Color(globalSettings.runtimeHintColorRGB)
        }

        val icon = ColorIcon(JBUI.scale(13), color)
        val iconPresentation = factory.icon(icon)
        val textPresentation = factory.smallText(" $text")

        var presentation = factory.roundWithBackground(
          factory.seq(iconPresentation, textPresentation),
        )

        val tooltipText = buildTooltipText(param)
        if (tooltipText != null) {
          presentation = factory.withTooltip(tooltipText, presentation)
        }

        return presentation
      }

      /**
       * Builds tooltip text for a parameter.
       */
      private fun buildTooltipText(
        param: com.skydoves.compose.stability.runtime.ParameterStabilityInfo,
      ): String? {
        return when {
          param.reason != null -> {
            "${param.name}: ${param.type}\n${
              param.stability.name.lowercase().replaceFirstChar { it.uppercase() }
            }\n\n${param.reason}"
          }

          param.stability != ParameterStability.STABLE -> {
            val defaultReason = when (param.stability) {
              ParameterStability.UNSTABLE ->
                StabilityConstants.Messages.MUTABLE_PROPERTIES_UNSTABLE

              ParameterStability.RUNTIME ->
                StabilityConstants.Messages.RUNTIME_STABILITY

              else -> null
            }
            if (defaultReason != null) {
              "${param.name}: ${param.type}\n${
                param.stability.name.lowercase().replaceFirstChar { it.uppercase() }
              }\n\n$defaultReason"
            } else {
              null
            }
          }

          else -> null
        }
      }
    }
  }

  /**
   * Settings for the inlay hints provider.
   */
  public data class Settings(
    var showOnlyUnstable: Boolean = false, // Show all parameters by default
    var showStableHints: Boolean = true, // Show stable hints too
  )
}
