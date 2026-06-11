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
package com.skydoves.compose.stability.idea.doctor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import com.skydoves.compose.stability.idea.doctor.fixes.DoctorFix
import com.skydoves.compose.stability.idea.heatmap.AdbLogcatService
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.idea.settings.StabilitySettingsConfigurable
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.idea.toolwindow.combineToolbars
import java.awt.Color
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * The Stability Doctor tab: a ranked, quantified, actionable fix list. Each row is one
 * prescription — a composable, why it costs you (static verdict + measured waste + blast
 * radius), where the problem values come from, and one-click fixes.
 *
 * Double-click a composable to navigate to it; double-click a fix to apply it (after a
 * confirmation dialog showing the change). Auto-refreshes while a heatmap session is running.
 */
internal class DoctorPanel(private val project: Project) : Disposable {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private val analysisRunning = AtomicBoolean(false)

  @Volatile
  private var autoRefreshTask: ScheduledFuture<*>? = null

  @Volatile
  private var lastFullRefreshAtMs: Long = 0L

  init {
    rootNode = DefaultMutableTreeNode("Doctor")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = DoctorCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    tree.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        if (e.clickCount != 2) return
        when (val data = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject) {
          is DoctorNodeData.PrescriptionRow ->
            navigateToSource(data.prescription.filePath, data.prescription.line)
          is DoctorNodeData.FixRow -> applyFix(data.fix, data.signatureKey)
          else -> Unit
        }
      }
    })

    showEmptyState("Click refresh to get a prioritized list of stability fixes")
    startAutoRefresh()
  }

  fun getContent(): JComponent {
    val mainPanel = SimpleToolWindowPanel(true, true)
    mainPanel.toolbar = createToolbar()
    mainPanel.setContent(ScrollPaneFactory.createScrollPane(tree))
    return mainPanel
  }

  /** Runs the full Doctor analysis on a background thread and renders the result. */
  fun refresh() {
    if (!settings.isDoctorEnabled) {
      showEmptyState("Stability Doctor is disabled in Settings > Tools > Compose Stability Analyzer")
      return
    }
    if (!analysisRunning.compareAndSet(false, true)) return

    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Running stability doctor", true) {
        override fun run(indicator: ProgressIndicator) {
          try {
            if (DumbService.getInstance(project).isDumb) {
              ApplicationManager.getApplication().invokeLater {
                showEmptyState("Indexing in progress — run the Doctor again once indexing finishes")
              }
              return
            }
            val report = DoctorAnalyzer.analyze(project, indicator)
            lastFullRefreshAtMs = System.currentTimeMillis()
            ApplicationManager.getApplication().invokeLater { displayReport(report) }
          } finally {
            analysisRunning.set(false)
          }
        }

        override fun onCancel() {
          analysisRunning.set(false)
        }
      },
    )
  }

  /**
   * Periodic tick: while a heatmap session is streaming, re-run the analysis so MEASURED rows
   * pick up fresh waste numbers. The verdict/cascade caches make this cheap; a tick is skipped
   * whenever an analysis is already in flight.
   */
  private fun startAutoRefresh() {
    autoRefreshTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      {
        try {
          if (project.isDisposed) return@scheduleWithFixedDelay
          if (!settings.isDoctorEnabled) return@scheduleWithFixedDelay
          if (!AdbLogcatService.getInstance(project).isRunning) return@scheduleWithFixedDelay
          if (analysisRunning.get()) return@scheduleWithFixedDelay
          val intervalMs = settings.doctorAutoRefreshSeconds.coerceAtLeast(5) * 1000L
          if (System.currentTimeMillis() - lastFullRefreshAtMs < intervalMs) {
            return@scheduleWithFixedDelay
          }
          // Only auto-refresh once the user ran the Doctor at least once.
          if (lastFullRefreshAtMs == 0L) return@scheduleWithFixedDelay
          ApplicationManager.getApplication().invokeLater { refresh() }
        } catch (_: Exception) {
          // Guard against disposal races.
        }
      },
      5L,
      5L,
      TimeUnit.SECONDS,
    )
  }

  override fun dispose() {
    autoRefreshTask?.cancel(false)
    autoRefreshTask = null
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  private fun displayReport(report: DoctorAnalyzer.DoctorReport) {
    rootNode.removeAllChildren()

    if (report.prescriptions.isEmpty()) {
      val message = if (report.scannedComposables == 0) {
        "No composables found in this project"
      } else {
        "No prescriptions — all ${report.scannedComposables} composables look healthy 🎉"
      }
      rootNode.add(DefaultMutableTreeNode(DoctorNodeData.EmptyMessage(message)))
      treeModel.reload()
      return
    }

    rootNode.add(
      DefaultMutableTreeNode(
        DoctorNodeData.Summary(
          prescriptionCount = report.prescriptions.size,
          scannedCount = report.scannedComposables,
          measuredCount = report.measuredCount,
          totalWasteMs = report.totalMeasuredWasteMs,
        ),
      ),
    )

    report.prescriptions.forEachIndexed { index, prescription ->
      val rowNode = DefaultMutableTreeNode(
        DoctorNodeData.PrescriptionRow(rank = index + 1, prescription = prescription),
      )
      rootNode.add(rowNode)
      prescription.causes.forEach { cause ->
        val causeNode = DefaultMutableTreeNode(DoctorNodeData.CauseRow(cause))
        rowNode.add(causeNode)
        cause.fixes.forEach { fix ->
          causeNode.add(
            DefaultMutableTreeNode(
              DoctorNodeData.FixRow(fix, prescription.signatureKey),
            ),
          )
        }
      }
    }

    treeModel.reload()
    // Expand only the top rows so the list stays scannable.
    var row = 0
    while (row < tree.rowCount && row < 12) {
      tree.expandRow(row)
      row++
    }
  }

  private fun showEmptyState(message: String) {
    rootNode.removeAllChildren()
    rootNode.add(DefaultMutableTreeNode(DoctorNodeData.EmptyMessage(message)))
    treeModel.reload()
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private fun applyFix(fix: DoctorFix, signatureKey: String) {
    if (!fix.isAvailable()) {
      Messages.showInfoMessage(
        project,
        "This fix is no longer applicable — the code changed since the analysis. Refresh the Doctor.",
        "Stability Doctor",
      )
      return
    }
    val preview = fix.previewText?.let { "\n\n$it" } ?: ""
    val answer = Messages.showYesNoDialog(
      project,
      "${fix.title}$preview\n\nApply this fix?",
      "Stability Doctor",
      Messages.getQuestionIcon(),
    )
    if (answer != Messages.YES) return

    fix.apply(project)
    DoctorCacheService.getInstance(project).invalidate(signatureKey)
    refresh()
  }

  private fun navigateToSource(filePath: String?, line: Int) {
    val path = filePath ?: return
    val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return
    ApplicationManager.getApplication().invokeLater {
      val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@invokeLater
      val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        ?: return@invokeLater
      val offset = if (line > 0) {
        document.getLineStartOffset((line - 1).coerceIn(0, document.lineCount - 1))
      } else {
        0
      }
      FileEditorManager.getInstance(project).openFile(vFile, true)
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      if (editor != null && editor.document == document) {
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
      }
    }
  }

  private fun createToolbar(): JComponent {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RefreshAction())
    actionGroup.add(SettingsAction())
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = tree
    return combineToolbars(toolbar.component, tree)
  }

  private inner class RefreshAction : AnAction(
    "Run Doctor",
    "Scan the project and produce a ranked, actionable stability fix list",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      refresh()
    }
  }

  private inner class SettingsAction : AnAction(
    "Doctor Settings",
    "Configure the Stability Doctor",
    AllIcons.General.Settings,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance()
        .showSettingsDialog(project, StabilitySettingsConfigurable::class.java)
    }
  }

  // ── Tree node data ────────────────────────────────────────────────────────

  internal sealed class DoctorNodeData {
    data class Summary(
      val prescriptionCount: Int,
      val scannedCount: Int,
      val measuredCount: Int,
      val totalWasteMs: Double,
    ) : DoctorNodeData()

    data class PrescriptionRow(val rank: Int, val prescription: Prescription) : DoctorNodeData()

    data class CauseRow(val cause: PrescriptionCause) : DoctorNodeData()

    data class FixRow(val fix: DoctorFix, val signatureKey: String) : DoctorNodeData()

    data class EmptyMessage(val message: String) : DoctorNodeData()
  }

  private inner class DoctorCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      val node = value as? DefaultMutableTreeNode ?: return
      when (val data = node.userObject) {
        is DoctorNodeData.Summary -> {
          icon = AllIcons.Actions.Lightning
          append("Doctor", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          val waste = if (data.totalWasteMs > 0) {
            " · ≈${"%.0f".format(data.totalWasteMs)}ms observed waste"
          } else {
            ""
          }
          append(
            "  ${data.prescriptionCount} prescriptions · ${data.measuredCount} measured" +
              "$waste · ${data.scannedCount} composables scanned",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
          )
        }

        is DoctorNodeData.PrescriptionRow -> {
          val p = data.prescription
          icon = AllIcons.Nodes.Function
          append("${data.rank}. ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          append(p.composableName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          val badgeColor = if (p.score.kind == ScoreKind.MEASURED) {
            Color(settings.unstableHintColorRGB)
          } else {
            JBColor.GRAY
          }
          append(
            "  [${p.score.kind.name} ${"%.0f".format(p.score.value)}]",
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, badgeColor),
          )
          append("  ${p.problemSummary}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        is DoctorNodeData.CauseRow -> {
          val c = data.cause
          icon = AllIcons.Nodes.Parameter
          append("${c.paramName}: ${c.paramType}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          append(
            "  ${c.staticStability.name.lowercase()}" +
              (c.staticReason?.let { " ($it)" } ?: ""),
            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, causeColor(c.realityGrade)),
          )
          c.realityGrade?.let { grade ->
            append("  · ${gradeLabel(grade)}", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, causeColor(grade)))
          }
          c.callSiteOrigins.firstOrNull()?.let { origin ->
            append(
              "  ← ${origin.origin.origin} from ${origin.callerName}",
              SimpleTextAttributes.GRAYED_ATTRIBUTES,
            )
          }
        }

        is DoctorNodeData.FixRow -> {
          icon = AllIcons.Actions.IntentionBulb
          append("Fix: ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          append(data.fix.title, SimpleTextAttributes.LINK_ATTRIBUTES)
          append("  (double-click to apply)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        is DoctorNodeData.EmptyMessage -> {
          icon = AllIcons.General.Information
          append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
    }
  }

  private fun causeColor(grade: RealityGrade?): Color = when (grade) {
    RealityGrade.SILENT_WASTE -> Color(settings.unstableHintColorRGB)
    RealityGrade.FALSE_ALARM -> Color(settings.runtimeHintColorRGB)
    RealityGrade.CONFIRMED -> Color(settings.stableHintColorRGB)
    RealityGrade.JUSTIFIED, RealityGrade.OBSERVING, null -> JBColor.GRAY
  }

  private fun gradeLabel(grade: RealityGrade): String = when (grade) {
    RealityGrade.CONFIRMED -> "confirmed"
    RealityGrade.FALSE_ALARM -> "false alarm"
    RealityGrade.SILENT_WASTE -> "silent waste"
    RealityGrade.JUSTIFIED -> "justified"
    RealityGrade.OBSERVING -> "observing…"
  }
}
