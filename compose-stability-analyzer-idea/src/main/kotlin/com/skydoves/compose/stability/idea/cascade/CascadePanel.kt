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
package com.skydoves.compose.stability.idea.cascade

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * UI panel for the Recomposition Cascade visualizer.
 * Shows a tree of downstream composables affected by recomposition.
 */
internal class CascadePanel(private val project: Project) {

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private var currentFunction: KtNamedFunction? = null

  init {
    rootNode = DefaultMutableTreeNode("Cascade")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = CascadeCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    // Double-click to navigate to source
    tree.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        if (e.clickCount == 2) {
          val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
          val composable = node?.userObject as? CascadeTreeNodeData.Composable
          if (composable != null) {
            navigateToSource(composable.node)
          }
        }
      }
    })

    // Show empty state initially
    showEmptyState()
  }

  public fun getContent(): JComponent {
    val mainPanel = SimpleToolWindowPanel(true, true)

    // Create toolbar
    val toolbar = createToolbar()
    mainPanel.toolbar = toolbar

    mainPanel.setContent(ScrollPaneFactory.createScrollPane(tree))

    return mainPanel
  }

  /**
   * Analyzes the recomposition cascade for the given composable function.
   * Runs analysis in background and displays results on EDT.
   */
  public fun analyzeFunction(function: KtNamedFunction) {
    currentFunction = function

    ProgressManager.getInstance().run(object : Task.Backgroundable(
      project,
      "Analyzing Recomposition Cascade",
      true,
    ) {
      override fun run(indicator: ProgressIndicator) {
        val result = CascadeAnalyzer.analyze(function, indicator)
        ApplicationManager.getApplication().invokeLater {
          displayResult(result)
        }
      }
    })
  }

  private fun displayResult(result: CascadeResult) {
    rootNode.removeAllChildren()

    // Add summary node
    val summaryNode = DefaultMutableTreeNode(
      CascadeTreeNodeData.Summary(
        composableName = result.root.stabilityInfo.name,
        summary = result.summary,
      ),
    )
    rootNode.add(summaryNode)

    // Recursively add composable nodes
    addCascadeNodes(rootNode, result.root)

    treeModel.reload()
    expandAll()
  }

  private fun addCascadeNodes(parentTreeNode: DefaultMutableTreeNode, cascadeNode: CascadeNode) {
    val treeNode = DefaultMutableTreeNode(
      CascadeTreeNodeData.Composable(cascadeNode),
    )
    parentTreeNode.add(treeNode)

    if (cascadeNode.isTruncated && cascadeNode.truncationReason != null) {
      val truncatedNode = DefaultMutableTreeNode(
        CascadeTreeNodeData.Truncated(cascadeNode.truncationReason),
      )
      treeNode.add(truncatedNode)
    }

    for (child in cascadeNode.children) {
      addCascadeNodes(treeNode, child)
    }
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
    val emptyNode = DefaultMutableTreeNode(
      CascadeTreeNodeData.EmptyMessage(
        "Right-click a @Composable function and select 'Analyze Recomposition Cascade'",
      ),
    )
    rootNode.add(emptyNode)
    treeModel.reload()
  }

  private fun createToolbar(): JComponent {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RefreshAction())
    actionGroup.add(ClearAction())

    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = tree

    return toolbar.component
  }

  private fun navigateToSource(node: CascadeNode) {
    val virtualFile = LocalFileSystem.getInstance()
      .findFileByPath(node.filePath) ?: return

    ApplicationManager.getApplication().invokeLater {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@invokeLater
      val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        ?: return@invokeLater

      val offset = if (node.line > 0) {
        val targetLine = (node.line - 1).coerceIn(0, document.lineCount - 1)
        document.getLineStartOffset(targetLine)
      } else {
        0
      }

      FileEditorManager.getInstance(project).openFile(virtualFile, true)
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      if (editor != null && editor.document == document) {
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
      }
    }
  }

  /**
   * Refresh action - re-analyzes the current function.
   */
  private inner class RefreshAction : AnAction(
    "Refresh",
    "Re-analyze recomposition cascade",
    AllIcons.Actions.Refresh,
  ) {
    override fun getActionUpdateThread(): com.intellij.openapi.actionSystem.ActionUpdateThread =
      com.intellij.openapi.actionSystem.ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      currentFunction?.let { analyzeFunction(it) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = currentFunction != null
    }
  }

  /**
   * Clear action - resets to empty state.
   */
  private inner class ClearAction : AnAction(
    "Clear",
    "Clear cascade analysis",
    AllIcons.Actions.GC,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFunction = null
      showEmptyState()
    }
  }
}
