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
package com.skydoves.compose.stability.idea.blame

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
import com.skydoves.compose.stability.idea.toolwindow.combineToolbars
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Tool-window panel showing the upstream "blame" tree for a composable: who calls it and where each
 * argument originates. Mirrors [com.skydoves.compose.stability.idea.cascade.CascadePanel], reversed.
 */
internal class BlamePanel(private val project: Project) {

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private var currentFunction: KtNamedFunction? = null

  init {
    rootNode = DefaultMutableTreeNode("Blame")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = BlameCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    tree.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        if (e.clickCount == 2) {
          val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
          val caller = node?.userObject as? BlameTreeNodeData.Caller ?: return
          navigateToSource(caller.node.filePath, caller.node.line)
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

  public fun analyzeFunction(function: KtNamedFunction) {
    currentFunction = function
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Analyzing recomposition blame", true) {
        override fun run(indicator: ProgressIndicator) {
          val result = BlameAnalyzer.analyze(function, indicator)
          ApplicationManager.getApplication().invokeLater { displayResult(result) }
        }
      },
    )
  }

  private fun displayResult(result: BlameResult) {
    rootNode.removeAllChildren()
    rootNode.add(
      DefaultMutableTreeNode(
        BlameTreeNodeData.Summary(result.root.name, result.callerCount, result.maxDepth),
      ),
    )
    if (result.root.children.isEmpty()) {
      rootNode.add(
        DefaultMutableTreeNode(
          BlameTreeNodeData.EmptyMessage(
            "No composable callers found in project sources (top-level entry point, or callers " +
              "are in libraries).",
          ),
        ),
      )
    } else {
      result.root.children.forEach { addNodes(rootNode, it) }
    }
    treeModel.reload()
    expandAll()
  }

  private fun addNodes(parent: DefaultMutableTreeNode, node: BlameNode) {
    val treeNode = DefaultMutableTreeNode(BlameTreeNodeData.Caller(node))
    parent.add(treeNode)
    node.passedArguments.forEach {
      treeNode.add(DefaultMutableTreeNode(BlameTreeNodeData.Argument(it)))
    }
    if (node.isTruncated && node.truncationReason != null) {
      treeNode.add(DefaultMutableTreeNode(BlameTreeNodeData.Truncated(node.truncationReason)))
    }
    node.children.forEach { addNodes(treeNode, it) }
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
        BlameTreeNodeData.EmptyMessage(
          "Right-click a @Composable and choose \"Blame this Recomposition\" to trace where its " +
            "inputs come from.",
        ),
      ),
    )
    treeModel.reload()
  }

  private fun navigateToSource(filePath: String, line: Int) {
    if (filePath.isEmpty()) return
    val vFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
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
    actionGroup.add(ClearAction())
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = tree
    return combineToolbars(toolbar.component, tree)
  }

  private inner class RefreshAction : AnAction(
    "Refresh",
    "Re-run blame analysis for the current composable",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFunction?.let { analyzeFunction(it) }
    }
  }

  private inner class ClearAction : AnAction(
    "Clear",
    "Clear the blame view",
    AllIcons.Actions.GC,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFunction = null
      showEmptyState()
    }
  }
}
