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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.JBUI
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.isPreview
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Manages block inlays above @Composable functions to show live
 * recomposition counts.
 *
 * The renderer uses **pre-computed, cached** text and color so that [paint]
 * is fully deterministic — no live data reads during the paint cycle.
 * Inlays are only disposed/recreated when the display text actually changes,
 * and no blanket editor repaint is triggered, eliminating flicker.
 */
@Service(Service.Level.PROJECT)
internal class HeatmapInlayManager(
  private val project: Project,
) : Disposable {

  private val settings get() = StabilitySettingsState.getInstance()
  private val service get() = AdbLogcatService.getInstance(project)

  /**
   * Per-editor tracking. Key = composableName, value = current inlay + last rendered text.
   * Outer key = editor identity hash.
   */
  private val editorState = ConcurrentHashMap<Int, MutableMap<String, InlayEntry>>()

  private data class InlayEntry(
    val inlay: Inlay<*>,
    val displayText: String,
  )

  @Volatile
  private var refreshTask: ScheduledFuture<*>? = null

  @Volatile
  private var clickListenerRegistered = false

  /**
   * Mouse listener that detects clicks on heatmap block inlays
   * and opens the Heatmap tab to show recomposition logs.
   */
  private val clickListener = object : EditorMouseListener {
    override fun mouseClicked(event: EditorMouseEvent) {
      if (event.mouseEvent.clickCount != 1) return
      val editor = event.editor
      if (editor.project != project) return

      val editorKey = System.identityHashCode(editor)
      val entries = editorState[editorKey] ?: return

      val point = event.mouseEvent.point
      for ((name, entry) in entries) {
        if (!entry.inlay.isValid) continue
        val bounds = entry.inlay.bounds ?: continue
        if (bounds.contains(point)) {
          openHeatmapPanel(name)
          return
        }
      }
    }
  }

  fun start() {
    if (!clickListenerRegistered) {
      clickListenerRegistered = true
      EditorFactory.getInstance().eventMulticaster
        .addEditorMouseListener(clickListener, this)
    }

    refreshTask = com.intellij.util.concurrency.AppExecutorUtil
      .getAppScheduledExecutorService()
      .scheduleWithFixedDelay(
        {
          try {
            if (project.isDisposed) return@scheduleWithFixedDelay
            service.flushParser()
            ApplicationManager.getApplication().invokeLater(
              { refreshAllEditors() },
              ModalityState.any(),
            )
          } catch (_: Exception) {
            // Guard against disposal races
          }
        },
        500L,
        REFRESH_INTERVAL_MS,
        TimeUnit.MILLISECONDS,
      )
  }

  fun stop() {
    refreshTask?.cancel(false)
    refreshTask = null
    ApplicationManager.getApplication().invokeLater(
      { clearAllInlays() },
      ModalityState.any(),
    )
  }

  override fun dispose() {
    refreshTask?.cancel(false)
    refreshTask = null
  }

  // ── Core logic (all runs on EDT) ────────────────────────────────────────

  private fun refreshAllEditors() {
    if (project.isDisposed) return
    if (!settings.isHeatmapEnabled) return
    if (!service.isRunning && !settings.showHeatmapWhenStopped) {
      clearAllInlays()
      return
    }

    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor.isDisposed || editor.project != project) continue
      refreshEditor(editor)
    }

    // Clean up entries for disposed editors
    editorState.keys.removeAll { key ->
      EditorFactory.getInstance().allEditors.none { System.identityHashCode(it) == key }
    }
  }

  private fun refreshEditor(editor: Editor) {
    val composables = runReadAction {
      val psiFile = PsiDocumentManager.getInstance(project)
        .getPsiFile(editor.document) as? KtFile ?: return@runReadAction null
      PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
        .filter { it.isComposable() && !it.isPreview() }
        .mapNotNull { fn ->
          val name = fn.name ?: return@mapNotNull null
          val anchor = fn.nameIdentifier ?: fn.funKeyword ?: return@mapNotNull null
          name to anchor.textRange.startOffset
        }
    } ?: return

    val editorKey = System.identityHashCode(editor)
    val entries = editorState.getOrPut(editorKey) { mutableMapOf() }

    // Figure out what should be shown
    val desired = mutableMapOf<String, Pair<Int, String>>() // name → (offset, displayText)
    for ((name, offset) in composables) {
      val data = service.getHeatmapData(name) ?: continue
      desired[name] = offset to buildDisplayText(data)
    }

    // Remove inlays for composables no longer needed
    val toRemoveNames = entries.keys - desired.keys
    for (name in toRemoveNames) {
      entries.remove(name)?.inlay?.let { if (it.isValid) it.dispose() }
    }

    // Update or create inlays
    for ((name, pair) in desired) {
      val (offset, newText) = pair
      val existing = entries[name]

      if (existing != null && existing.inlay.isValid && existing.displayText == newText) {
        // Nothing changed — don't touch the inlay at all
        continue
      }

      // Text changed or inlay is new/invalid: dispose old, create new
      existing?.inlay?.let { if (it.isValid) it.dispose() }

      val data = service.getHeatmapData(name) ?: continue
      val color = severityColor(data.totalRecompositionCount)
      val renderer = HeatmapBlockRenderer(newText, color, editor)
      val inlay = editor.inlayModel.addBlockElement(
        offset,
        true, // relatesToPrecedingText
        true, // showAbove
        0, // priority
        renderer,
      )
      if (inlay != null) {
        entries[name] = InlayEntry(inlay, newText)
      } else {
        entries.remove(name)
      }
    }
  }

  private fun clearAllInlays() {
    for ((_, entries) in editorState) {
      for ((_, entry) in entries) {
        if (entry.inlay.isValid) entry.inlay.dispose()
      }
    }
    editorState.clear()
  }

  // ── Display text / color helpers ────────────────────────────────────────

  private fun buildDisplayText(data: ComposableHeatmapData): String {
    return buildString {
      val count = data.totalRecompositionCount
      append("$count recomposition")
      if (count != 1) append("s")
      if (data.unstableParameters.isNotEmpty()) {
        append("  |  unstable: ${data.unstableParameters.joinToString(", ")}")
      }
    }
  }

  private fun severityColor(count: Int): Color {
    return when {
      count < settings.heatmapGreenThreshold -> Color(0x5F, 0xB8, 0x65)
      count < settings.heatmapRedThreshold -> Color(0xF0, 0xC6, 0x74)
      else -> Color(0xE8, 0x68, 0x4A)
    }
  }

  // ── Renderer (fully deterministic — no external reads in paint) ─────────

  /**
   * Block element renderer with **pre-baked** text and color.
   * Displayed above the @Composable function declaration.
   * [paint] never reads from [AdbLogcatService]; it draws exactly
   * the [text] and [color] it was created with. A new renderer instance
   * is created only when the display text actually changes.
   */
  private class HeatmapBlockRenderer(
    private val text: String,
    private val color: Color,
    editor: Editor,
  ) : EditorCustomElementRenderer {

    // Pre-compute the background color once at creation time
    private val bgColor: Color = editor.colorsScheme.defaultBackground

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
      return inlay.editor.scrollingModel.visibleArea.width.coerceAtLeast(JBUI.scale(600))
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight

    override fun paint(
      inlay: Inlay<*>,
      g: Graphics2D,
      targetRegion: Rectangle2D,
      textAttributes: TextAttributes,
    ) {
      // Fill background to prevent bleed-through from previous renders
      g.color = bgColor
      g.fillRect(
        targetRegion.x.toInt(),
        targetRegion.y.toInt(),
        targetRegion.width.toInt(),
        targetRegion.height.toInt(),
      )

      val editor = inlay.editor
      val editorFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      val smallFont = editorFont.deriveFont(
        Font.PLAIN,
        editor.colorsScheme.editorFontSize2D,
      )
      g.font = smallFont
      val fm = g.fontMetrics

      val x = targetRegion.x.toFloat() + JBUI.scale(4)
      val baseline =
        (targetRegion.y + (targetRegion.height + fm.ascent - fm.descent) / 2).toFloat()

      // Text
      g.color = color
      g.drawString(text, x, baseline)
    }
  }

  /**
   * Opens the Heatmap tab in the tool window and displays
   * recomposition data for the given composable.
   */
  private fun openHeatmapPanel(composableName: String) {
    ApplicationManager.getApplication().invokeLater {
      val toolWindow = ToolWindowManager.getInstance(project)
        .getToolWindow("Compose Stability Analyzer") ?: return@invokeLater
      toolWindow.show {
        val heatmapContent = toolWindow.contentManager
          .findContent("Heatmap") ?: return@show
        toolWindow.contentManager.setSelectedContent(heatmapContent)
        val panel = heatmapContent.component
          .getClientProperty(HeatmapPanel::class.java) as? HeatmapPanel ?: return@show
        panel.showComposableData(composableName)
      }
    }
  }

  companion object {
    private const val REFRESH_INTERVAL_MS = 1000L
    fun getInstance(project: Project): HeatmapInlayManager =
      project.getService(HeatmapInlayManager::class.java)
  }
}
