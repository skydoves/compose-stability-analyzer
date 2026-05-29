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
package com.skydoves.compose.stability.idea.reality

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.skydoves.compose.stability.idea.StabilityAnalyzer
import com.skydoves.compose.stability.idea.heatmap.AdbLogcatService
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.isPreview
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.idea.toolwindow.combineToolbars
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Module-wide "Reality Check" scorecard: reconciles each composable's compile-time stability
 * prediction with live runtime recomposition data and grades every parameter.
 *
 * Only composables that have actually been observed at runtime (i.e. have live heatmap data) are
 * scored, keeping the scan light and the results actionable.
 */
internal class RealityPanel(private val project: Project) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode

  init {
    rootNode = DefaultMutableTreeNode("Reality")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = RealityCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    tree.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        if (e.clickCount == 2) {
          val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
          val data = node?.userObject as? RealityNodeData.Composable ?: return
          navigateToSource(data.filePath, data.line)
        }
      }
    })

    showEmptyState()
  }

  public fun getContent(): JComponent {
    val mainPanel = SimpleToolWindowPanel(true, true)
    mainPanel.toolbar = createToolbar()
    mainPanel.setContent(ScrollPaneFactory.createScrollPane(tree))
    return mainPanel
  }

  /** Re-scans the project for observed composables and rebuilds the scorecard off the EDT. */
  public fun refresh() {
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Analyzing stability reality", true) {
        override fun run(indicator: ProgressIndicator) {
          val rows = computeRows()
          ApplicationManager.getApplication().invokeLater { displayRows(rows) }
        }
      },
    )
  }

  private fun computeRows(): List<RealityRow> {
    val service = AdbLogcatService.getInstance(project)
    val liveNames = service.getAllComposableNames()
    if (liveNames.isEmpty()) return emptyList()

    return runReadAction {
      val scope = GlobalSearchScope.projectScope(project)
      val psiManager = PsiManager.getInstance(project)
      val docManager = PsiDocumentManager.getInstance(project)

      FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        .asSequence()
        .mapNotNull { psiManager.findFile(it) as? KtFile }
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KtNamedFunction::class.java).asSequence() }
        .filter { it.isComposable() && !it.isPreview() }
        .mapNotNull { fn ->
          val name = fn.name ?: return@mapNotNull null
          if (name !in liveNames) return@mapNotNull null
          val live = service.getHeatmapData(name) ?: return@mapNotNull null
          val info = try {
            StabilityAnalyzer.analyze(fn)
          } catch (_: Exception) {
            return@mapNotNull null
          }
          val reality = RealityClassifier.classify(info, live)
          val vFile = fn.containingFile?.virtualFile
          val line = vFile?.let {
            docManager.getDocument(fn.containingFile)?.getLineNumber(fn.textOffset)?.plus(1)
          } ?: 0
          RealityRow(reality, vFile?.path, line)
        }
        .toList()
        .sortedWith(
          compareByDescending<RealityRow> { it.reality.hasSilentWaste }
            .thenByDescending { it.reality.wastedRecompositions },
        )
    }
  }

  private fun displayRows(rows: List<RealityRow>) {
    rootNode.removeAllChildren()

    if (rows.isEmpty()) {
      rootNode.add(
        DefaultMutableTreeNode(
          RealityNodeData.EmptyMessage(
            "No observed composables yet. Start the recomposition heatmap and interact with " +
              "your app to populate the scorecard.",
          ),
        ),
      )
      treeModel.reload()
      return
    }

    val silentWasteCount = rows.count { it.reality.hasSilentWaste }
    val totalWasted = rows.sumOf { it.reality.wastedRecompositions }
    rootNode.add(
      DefaultMutableTreeNode(
        RealityNodeData.Summary(
          observedCount = rows.size,
          silentWasteCount = silentWasteCount,
          totalWasted = totalWasted,
        ),
      ),
    )

    rows.forEach { row ->
      val composableNode = DefaultMutableTreeNode(
        RealityNodeData.Composable(
          reality = row.reality,
          filePath = row.filePath,
          line = row.line,
        ),
      )
      rootNode.add(composableNode)
      row.reality.parameters.forEach { param ->
        composableNode.add(DefaultMutableTreeNode(RealityNodeData.Parameter(param)))
      }
    }

    treeModel.reload()
    expandAll()
  }

  private fun expandAll() {
    var row = 0
    while (row < tree.rowCount) {
      tree.expandRow(row)
      row++
    }
  }

  private fun showEmptyState() {
    rootNode.removeAllChildren()
    rootNode.add(
      DefaultMutableTreeNode(
        RealityNodeData.EmptyMessage("Click refresh to score observed composables against runtime data"),
      ),
    )
    treeModel.reload()
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
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = tree
    return combineToolbars(toolbar.component, tree)
  }

  private inner class RefreshAction : AnAction(
    "Refresh",
    "Re-score observed composables against live runtime data",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      refresh()
    }
  }

  /** Node data types for the reality scorecard tree. */
  internal sealed class RealityNodeData {
    data class Summary(
      val observedCount: Int,
      val silentWasteCount: Int,
      val totalWasted: Int,
    ) : RealityNodeData()

    data class Composable(
      val reality: ComposableReality,
      val filePath: String?,
      val line: Int,
    ) : RealityNodeData()

    data class Parameter(val param: ParameterReality) : RealityNodeData()

    data class EmptyMessage(val message: String) : RealityNodeData()
  }

  private inner class RealityCellRenderer : ColoredTreeCellRenderer() {
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
        is RealityNodeData.Summary -> {
          icon = AllIcons.Actions.Lightning
          append("Reality scorecard", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append(
            "  ${data.observedCount} observed · ${data.silentWasteCount} with silent waste · " +
              "${data.totalWasted} wasted recompositions (recent)",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
          )
        }

        is RealityNodeData.Composable -> {
          val reality = data.reality
          icon = AllIcons.Nodes.Function
          append(reality.composableName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          when {
            reality.hasSilentWaste ->
              append(
                "  — silent waste",
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, gradeColor(RealityGrade.SILENT_WASTE)),
              )
            reality.hasFalseAlarm ->
              append(
                "  — false alarm",
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, gradeColor(RealityGrade.FALSE_ALARM)),
              )
            else ->
              append("  — confirmed", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
          append(
            "  (${reality.totalObservedRecompositions} recompositions)",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
          )
        }

        is RealityNodeData.Parameter -> {
          val p = data.param
          icon = AllIcons.Nodes.Parameter
          append("${p.name}: ${p.type} ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          append(
            "predicted ${p.predicted.name.lowercase()} → ${gradeLabel(p.grade)}",
            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, gradeColor(p.grade)),
          )
        }

        is RealityNodeData.EmptyMessage -> {
          icon = AllIcons.General.Information
          append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
    }
  }

  private fun gradeColor(grade: RealityGrade): Color = when (grade) {
    RealityGrade.CONFIRMED -> Color(settings.stableHintColorRGB)
    RealityGrade.FALSE_ALARM -> Color(settings.runtimeHintColorRGB)
    RealityGrade.SILENT_WASTE -> Color(settings.unstableHintColorRGB)
    RealityGrade.JUSTIFIED -> JBColor.GRAY
    RealityGrade.OBSERVING -> JBColor.GRAY
  }

  private fun gradeLabel(grade: RealityGrade): String = when (grade) {
    RealityGrade.CONFIRMED -> "confirmed"
    RealityGrade.FALSE_ALARM -> "false alarm"
    RealityGrade.SILENT_WASTE -> "silent waste"
    RealityGrade.JUSTIFIED -> "justified"
    RealityGrade.OBSERVING -> "observing…"
  }

  private data class RealityRow(
    val reality: ComposableReality,
    val filePath: String?,
    val line: Int,
  )
}
