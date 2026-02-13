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
package com.skydoves.compose.stability.idea.heatmap

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.isPreview
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import java.awt.event.MouseEvent

/**
 * DaemonBoundCodeVisionProvider that renders live recomposition count annotations
 * above `@Composable` functions. Data is sourced from [AdbLogcatService].
 *
 * Color severity:
 * - Green:  < greenThreshold (default 10)
 * - Yellow: greenThreshold .. redThreshold (default 10-50)
 * - Red:    >= redThreshold (default 50)
 */
internal class RecompositionHeatmapProvider : DaemonBoundCodeVisionProvider {

  companion object {
    const val PROVIDER_ID = "compose.recomposition.heatmap"
  }

  override val id: String = PROVIDER_ID
  override val name: String = "Compose Recomposition Heatmap"
  override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
  override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
  override val groupId: String = RecompositionHeatmapGroupSettingProvider.GROUP_ID

  override fun computeForEditor(
    editor: Editor,
    file: PsiFile,
  ): List<Pair<TextRange, CodeVisionEntry>> {
    if (file !is KtFile) return emptyList()

    val project = editor.project ?: return emptyList()
    val settings = StabilitySettingsState.getInstance()

    if (!settings.isHeatmapEnabled) return emptyList()

    val service = AdbLogcatService.getInstance(project)
    if (!service.isRunning && !settings.showHeatmapWhenStopped) {
      return emptyList()
    }

    val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()

    val functions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
    for (function in functions) {
      if (!function.isComposable() || function.isPreview()) continue

      val name = function.name ?: continue
      val data = service.getHeatmapData(name) ?: continue

      val count = data.totalRecompositionCount
      val color = severityColor(count, settings)
      val icon = ColorIcon(JBUI.scale(8), color)

      val unstableList = data.unstableParameters
      val text = buildString {
        append("\u2191 $count recomposition")
        if (count != 1) append("s")
        if (unstableList.isNotEmpty()) {
          append(" | unstable: ${unstableList.joinToString(", ")}")
        }
      }

      val tooltip = buildTooltipHtml(data)

      val entry = ClickableTextCodeVisionEntry(
        text,
        PROVIDER_ID,
        onClick@{ mouseEvent, _ ->
          mouseEvent ?: return@onClick
          onEntryClick(mouseEvent, data, project)
        },
        icon,
        tooltip,
        tooltip,
      )

      val anchor = function.nameIdentifier ?: function.funKeyword ?: continue
      lenses.add(anchor.textRange to entry)
    }

    return lenses
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  private fun severityColor(count: Int, settings: StabilitySettingsState): Color {
    return when {
      count < settings.heatmapGreenThreshold -> Color(0x5F, 0xB8, 0x65) // green
      count < settings.heatmapRedThreshold -> Color(0xF0, 0xC6, 0x74) // yellow
      else -> Color(0xE8, 0x68, 0x4A) // red
    }
  }

  private fun buildTooltipHtml(data: ComposableHeatmapData): String {
    return buildString {
      append("<html><body style='font-size:11px'>")
      append("<b>${data.composableName}</b> &mdash; ")
      append("${data.totalRecompositionCount} recomposition(s)")
      append("<br/>Max single count: ${data.maxSingleCount}")

      if (data.changedParameters.isNotEmpty()) {
        append("<br/><br/><b>Parameter changes:</b><br/>")
        data.changedParameters.entries
          .sortedByDescending { it.value }
          .forEach { (param, changeCount) ->
            append("&nbsp;&nbsp;$param: $changeCount change(s)<br/>")
          }
      }

      if (data.unstableParameters.isNotEmpty()) {
        append("<br/><b>Unstable:</b> ${data.unstableParameters.joinToString(", ")}")
      }

      append("<br/><br/><i>Note: matching by simple name; ")
      append("composables with the same name share data.</i>")
      append("</body></html>")
    }
  }

  private fun onEntryClick(
    mouseEvent: MouseEvent,
    data: ComposableHeatmapData,
    project: Project,
  ) {
    val content = buildString {
      append("${data.composableName}: ${data.totalRecompositionCount} recomposition(s)\n")
      append("Max single count: ${data.maxSingleCount}\n\n")

      if (data.changedParameters.isNotEmpty()) {
        append("Parameter changes:\n")
        data.changedParameters.entries
          .sortedByDescending { it.value }
          .forEach { (param, changeCount) ->
            append("  $param: $changeCount change(s)\n")
          }
        append("\n")
      }

      if (data.unstableParameters.isNotEmpty()) {
        append("Unstable parameters: ${data.unstableParameters.joinToString(", ")}\n\n")
      }

      val recent = data.recentEvents.takeLast(5)
      if (recent.isNotEmpty()) {
        append("Recent events:\n")
        recent.forEach { event ->
          val tagSuffix = if (event.tag.isNotEmpty()) " (tag: ${event.tag})" else ""
          append("  [#${event.recompositionCount}]$tagSuffix\n")
          event.parameterEntries.forEach { param ->
            append("    ${param.name}: ${param.type} ${param.status.name.lowercase()}")
            if (param.detail.isNotEmpty()) append(" (${param.detail})")
            append("\n")
          }
        }
      }
    }

    val balloon = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(
        "<pre>${content.replace("<", "&lt;").replace(">", "&gt;")}</pre>",
        com.intellij.openapi.ui.MessageType.INFO,
        null,
      )
      .setFadeoutTime(0)
      .setHideOnClickOutside(true)
      .setHideOnKeyOutside(true)
      .createBalloon()

    val component = mouseEvent.component
    val point = mouseEvent.point
    balloon.show(
      com.intellij.ui.awt.RelativePoint(component, point),
      com.intellij.openapi.ui.popup.Balloon.Position.below,
    )
  }
}
