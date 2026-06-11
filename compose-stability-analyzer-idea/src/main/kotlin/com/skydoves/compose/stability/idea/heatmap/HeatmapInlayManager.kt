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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
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
import com.skydoves.compose.stability.idea.StabilityAnalyzer
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.isPreview
import com.skydoves.compose.stability.idea.reality.ComposableReality
import com.skydoves.compose.stability.idea.reality.RealityClassifier
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
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
    val color: Color,
  )

  /** A composable's anchor in the editor plus its (cached) static stability verdict. */
  private data class ComposableAnchor(
    val name: String,
    val offset: Int,
    val verdict: ComposableStabilityInfo?,
  )

  /** Fully-resolved inlay content for one composable (display text already folds in the grade). */
  private data class DesiredInlay(
    val offset: Int,
    val displayText: String,
    val color: Color,
    val tooltip: String,
  )

  /**
   * Cache of static stability verdicts keyed by composable fqName, invalidated by the file's
   * modification stamp, so [StabilityAnalyzer.analyze] (K2 resolve — not cheap) does not run on
   * the 1s refresh for every composable in every open editor.
   */
  private val verdictCache = ConcurrentHashMap<String, Pair<Long, ComposableStabilityInfo>>()

  @Volatile
  private var refreshTask: ScheduledFuture<*>? = null

  /** True while a snapshot→compute→apply refresh cycle is running (ticks are skipped). */
  private val refreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

  @Volatile
  private var listenersRegistered = false

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

  /** Tracks the inlay currently showing a tooltip. */
  private var activeTooltipInlay: Inlay<*>? = null
  private var activeBalloon:
    com.intellij.openapi.ui.popup.Balloon? = null

  /**
   * Mouse motion listener that shows a tooltip when the cursor
   * hovers over a heatmap block inlay. Uses [IdeTooltipManager]
   * which handles positioning automatically.
   */
  private val hoverListener =
    object : com.intellij.openapi.editor.event.EditorMouseMotionListener {
      override fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor
        if (editor.project != project) return
        val key = System.identityHashCode(editor)
        val entries = editorState[key] ?: return
        val pt = event.mouseEvent.point

        for ((name, entry) in entries) {
          if (!entry.inlay.isValid) continue
          val b = entry.inlay.bounds ?: continue
          if (!b.contains(pt)) continue
          // Same inlay — don't re-show
          if (activeTooltipInlay == entry.inlay) return
          // Dismiss previous balloon
          activeBalloon?.hide()
          activeBalloon = null
          activeTooltipInlay = entry.inlay
          // Get LIVE data (not cached renderer html)
          val data =
            service.getHeatmapData(name) ?: return
          val html = buildTooltipHtml(data)
          if (html.isEmpty()) return
          val balloon = com.intellij.openapi.ui.popup
            .JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
              html,
              com.intellij.openapi.ui.MessageType.INFO,
              null,
            )
            .setFadeoutTime(5000)
            .setAnimationCycle(0)
            .createBalloon()
          activeBalloon = balloon
          val relPoint =
            com.intellij.ui.awt.RelativePoint(
              editor.contentComponent,
              pt,
            )
          balloon.show(
            relPoint,
            com.intellij.openapi.ui.popup
              .Balloon.Position.above,
          )
          return
        }
        // Mouse left all inlays — dismiss
        activeBalloon?.hide()
        activeBalloon = null
        activeTooltipInlay = null
      }
    }

  fun start() {
    if (!listenersRegistered) {
      listenersRegistered = true
      val multicaster =
        EditorFactory.getInstance().eventMulticaster
      multicaster.addEditorMouseListener(clickListener, this)
      multicaster.addEditorMouseMotionListener(
        hoverListener,
        this,
      )
    }

    refreshTask = com.intellij.util.concurrency.AppExecutorUtil
      .getAppScheduledExecutorService()
      .scheduleWithFixedDelay(
        {
          try {
            if (project.isDisposed) return@scheduleWithFixedDelay
            service.flushParser()
            // Skip the tick while a previous refresh is still computing.
            if (!refreshInFlight.compareAndSet(false, true)) return@scheduleWithFixedDelay
            ApplicationManager.getApplication().invokeLater(
              { snapshotEditorsAndRefresh() },
              ModalityState.any(),
            )
          } catch (_: Exception) {
            refreshInFlight.set(false)
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
      {
        if (!project.isDisposed) {
          clearAllInlays()
        }
      },
      ModalityState.any(),
    )
  }

  override fun dispose() {
    refreshTask?.cancel(false)
    refreshTask = null
  }

  // ── Core logic ─────────────────────────────────────────────────────────────
  // Refresh runs in three stages — snapshot editors (EDT) → compute desired inlays
  // (pooled thread, read actions) → apply inlay mutations (EDT) — so the EDT never
  // runs PSI/K2 analysis (Issue #168).

  private fun snapshotEditorsAndRefresh() {
    try {
      if (project.isDisposed || !settings.isHeatmapEnabled) {
        refreshInFlight.set(false)
        return
      }
      if (!service.isRunning && !settings.showHeatmapWhenStopped) {
        clearAllInlays()
        refreshInFlight.set(false)
        return
      }
      // Keep previous inlays during indexing; retry on the next tick.
      if (DumbService.getInstance(project).isDumb) {
        refreshInFlight.set(false)
        return
      }

      val editors = EditorFactory.getInstance().allEditors
        .filter { !it.isDisposed && it.project == project }

      // Clean up entries for disposed editors.
      editorState.keys.removeAll { key ->
        editors.none { System.identityHashCode(it) == key }
      }

      com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().execute {
        computeAndApply(editors)
      }
    } catch (t: Throwable) {
      refreshInFlight.set(false)
      throw t
    }
  }

  /** Stage 2 (pooled thread): all analysis happens here, inside read actions. */
  private fun computeAndApply(editors: List<Editor>) {
    try {
      val computed = editors.mapNotNull { editor ->
        if (editor.isDisposed || project.isDisposed) return@mapNotNull null
        val desired = computeDesiredInlays(editor) ?: return@mapNotNull null
        editor to desired
      }
      ApplicationManager.getApplication().invokeLater(
        {
          try {
            if (!project.isDisposed) {
              computed.forEach { (editor, desired) ->
                if (!editor.isDisposed) applyDesiredInlays(editor, desired)
              }
            }
          } finally {
            refreshInFlight.set(false)
          }
        },
        ModalityState.any(),
      )
    } catch (t: Throwable) {
      refreshInFlight.set(false)
      if (t is ProcessCanceledException) throw t
    }
  }

  private fun computeDesiredInlays(editor: Editor): Map<String, DesiredInlay>? {
    val realityEnabled = settings.isRealityCheckEnabled
    val anchors = runReadAction {
      if (project.isDisposed || editor.isDisposed) return@runReadAction null
      val psiFile = PsiDocumentManager.getInstance(project)
        .getPsiFile(editor.document) as? KtFile ?: return@runReadAction null
      PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
        .filter { it.isComposable() && !it.isPreview() }
        .mapNotNull { fn ->
          val name = fn.name ?: return@mapNotNull null
          val anchor = fn.nameIdentifier ?: fn.funKeyword ?: return@mapNotNull null
          val verdict = if (realityEnabled) cachedVerdict(fn) else null
          ComposableAnchor(name, anchor.textRange.startOffset, verdict)
        }
    } ?: return null

    // The reality grade is folded into displayText so the apply-stage diff invalidates on
    // grade-only changes; the tooltip is prebuilt so the EDT never touches live data.
    val desired = mutableMapOf<String, DesiredInlay>()
    for (anchor in anchors) {
      val data = service.getHeatmapData(anchor.name) ?: continue
      val reality = anchor.verdict?.let { RealityClassifier.classify(it, data) }
      desired[anchor.name] = DesiredInlay(
        offset = anchor.offset,
        displayText = buildDisplayText(data, reality),
        color = severityColor(data, reality),
        tooltip = buildTooltipHtml(data),
      )
    }
    return desired
  }

  /** Stage 3 (EDT): inlay-model mutations only — no analysis, no live-data access. */
  private fun applyDesiredInlays(editor: Editor, desired: Map<String, DesiredInlay>) {
    val editorKey = System.identityHashCode(editor)
    val entries = editorState.getOrPut(editorKey) { mutableMapOf() }

    // Remove inlays for composables no longer needed
    val toRemoveNames = entries.keys - desired.keys
    for (name in toRemoveNames) {
      entries.remove(name)?.inlay?.let { if (it.isValid) it.dispose() }
    }

    // Update or create inlays
    for ((name, d) in desired) {
      val existing = entries[name]

      if (existing != null && existing.inlay.isValid &&
        existing.displayText == d.displayText && existing.color == d.color
      ) {
        // Nothing changed (neither text nor color) — don't touch the inlay at all
        continue
      }

      // Text changed or inlay is new/invalid: dispose old, create new
      existing?.inlay?.let { if (it.isValid) it.dispose() }

      val renderer =
        HeatmapBlockRenderer(d.displayText, d.color, editor, d.tooltip)
      val inlay = editor.inlayModel.addBlockElement(
        d.offset,
        true, // relatesToPrecedingText
        true, // showAbove
        0, // priority
        renderer,
      )
      if (inlay != null) {
        entries[name] = InlayEntry(inlay, d.displayText, d.color)
      } else {
        entries.remove(name)
      }
    }
  }

  /**
   * Returns the static stability verdict for [function], cached by fqName and invalidated by the
   * file's modification stamp. MUST be called inside a read action (it resolves PSI).
   */
  private fun cachedVerdict(function: KtNamedFunction): ComposableStabilityInfo? {
    return try {
      val key = function.fqName?.asString() ?: function.name ?: return null
      val stamp = function.containingFile?.modificationStamp ?: return null
      val cached = verdictCache[key]
      if (cached != null && cached.first == stamp) {
        cached.second
      } else {
        val info = StabilityAnalyzer.analyze(function)
        // Backstop against unbounded growth over a long session (fqName keys accumulate).
        if (verdictCache.size > VERDICT_CACHE_LIMIT) verdictCache.clear()
        verdictCache[key] = stamp to info
        info
      }
    } catch (_: Exception) {
      null
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

  private fun buildDisplayText(data: ComposableHeatmapData, reality: ComposableReality?): String {
    return buildString {
      val count = data.totalRecompositionCount
      append("$count recomposition")
      if (count != 1) append("s")

      val silent = reality?.parameters
        ?.filter { it.grade == RealityGrade.SILENT_WASTE }
        ?.map { it.name }
        .orEmpty()
      val falseAlarm = reality?.parameters
        ?.filter { it.grade == RealityGrade.FALSE_ALARM }
        ?.map { it.name }
        .orEmpty()

      when {
        silent.isNotEmpty() ->
          append("  |  (!) silent waste: ${silent.joinToString(", ")}")
        falseAlarm.isNotEmpty() ->
          append("  |  false alarm: ${falseAlarm.joinToString(", ")}")
        data.changedParameters.isNotEmpty() -> {
          val topChanged = data.changedParameters.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { it.key }
          append("  |  changed: $topChanged")
        }
      }
    }
  }

  private fun severityColor(data: ComposableHeatmapData, reality: ComposableReality?): Color {
    // Silent waste is the actionable problem — surface it in red regardless of the raw count.
    if (reality != null && reality.hasSilentWaste) return Color(0xE8, 0x68, 0x4A)
    val count = data.totalRecompositionCount
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
    val tooltipHtml: String = "",
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
   * Builds an HTML tooltip string summarising the most recent
   * recomposition event and cumulative totals.
   */
  private fun buildTooltipHtml(
    data: ComposableHeatmapData,
  ): String {
    return buildString {
      append("<html><body style='font-size:11px'>")
      append("<b>Last Recomposition (#")
      append(data.totalRecompositionCount)
      append(")</b>")
      if (data.lastDurationMs > 0) {
        append(" &mdash; ")
        append("%.2f".format(data.lastDurationMs))
        append("ms")
      }
      append("<br>")

      if (data.lastParameterChanges.isNotEmpty()) {
        data.lastParameterChanges.forEach { line ->
          append(escapeHtml(line))
          append("<br>")
        }
      }

      if (data.lastStateChanges.isNotEmpty()) {
        data.lastStateChanges.forEach { line ->
          append(escapeHtml(line))
          append("<br>")
        }
      }

      if (data.unstableParameters.isNotEmpty()) {
        append("Unstable: ")
        append(escapeHtml(data.unstableParameters.toString()))
        append("<br>")
      }

      append("<br><i>Total: ")
      append(data.totalRecompositionCount)
      append(" recomposition")
      if (data.totalRecompositionCount != 1) append("s")
      if (data.totalDurationMs > 0) {
        append(", ")
        append("%.1f".format(data.totalDurationMs))
        append("ms cumulative")
      }
      append("</i></body></html>")
    }
  }

  private fun escapeHtml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
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
    private const val VERDICT_CACHE_LIMIT = 1000
    fun getInstance(project: Project): HeatmapInlayManager =
      project.getService(HeatmapInlayManager::class.java)
  }
}
