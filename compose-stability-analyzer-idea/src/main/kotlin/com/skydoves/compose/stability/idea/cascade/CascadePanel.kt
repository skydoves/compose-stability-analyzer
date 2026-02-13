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
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * UI panel for the Recomposition Cascade visualizer.
 * Shows a tree of downstream composables affected by recomposition.
 */
internal class CascadePanel(private val project: Project) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private val detailsPanel: JPanel
  private var currentFunction: KtNamedFunction? = null

  init {
    rootNode = DefaultMutableTreeNode("Cascade")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = CascadeCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    detailsPanel = createDetailsPanel()

    // Selection listener for details
    tree.addTreeSelectionListener { event ->
      val node = event.path.lastPathComponent as? DefaultMutableTreeNode
      updateDetailsPanel(node)
    }

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

    // Create split pane with tree and details
    val splitter = OnePixelSplitter(false, 0.65f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
    splitter.secondComponent = JBScrollPane(detailsPanel)

    mainPanel.setContent(splitter)

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
        document.getLineStartOffset(node.line - 1)
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

  private fun createDetailsPanel(): JPanel {
    val panel = JBPanel<JBPanel<*>>(GridBagLayout())
    panel.border = JBUI.Borders.empty(10)

    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.NORTHWEST
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 1.0

    val label = JBLabel("Select a composable to view details")
    label.foreground = UIUtil.getInactiveTextColor()
    panel.add(label, gbc)

    return panel
  }

  private fun updateDetailsPanel(node: DefaultMutableTreeNode?) {
    detailsPanel.removeAll()
    detailsPanel.layout = GridBagLayout()

    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.NORTHWEST
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 1.0
    gbc.insets = JBUI.insets(5)

    when (val data = node?.userObject) {
      is CascadeTreeNodeData.Composable -> {
        val info = data.node.stabilityInfo
        val hasUnstable = info.parameters.any {
          it.stability == ParameterStability.UNSTABLE
        }
        val hasRuntime = info.parameters.any {
          it.stability == ParameterStability.RUNTIME
        }
        val isRuntimeOnly = !info.isSkippable && !hasUnstable && hasRuntime

        // Title
        addDetailRow(detailsPanel, gbc, "Composable:", info.name, bold = true)

        // Stability status
        val statusText = when {
          info.isSkippable -> "STABLE (Skippable)"
          isRuntimeOnly -> "RUNTIME (Determined at runtime)"
          else -> "UNSTABLE (Non-skippable)"
        }
        val statusColor = when {
          info.isSkippable -> Color(settings.stableGutterColorRGB)
          isRuntimeOnly -> Color(settings.runtimeGutterColorRGB)
          else -> Color(settings.unstableGutterColorRGB)
        }
        addDetailRow(detailsPanel, gbc, "Status:", statusText, color = statusColor)

        // Restartable
        addDetailRow(
          detailsPanel,
          gbc,
          "Restartable:",
          if (info.isRestartable) "Yes" else "No",
        )

        // Depth
        addDetailRow(detailsPanel, gbc, "Depth:", "${data.node.depth}")

        // Parameters
        if (info.parameters.isNotEmpty()) {
          gbc.gridy++
          val headerLabel = JBLabel("Parameters:")
          headerLabel.font = headerLabel.font.deriveFont(Font.BOLD)
          detailsPanel.add(headerLabel, gbc)

          info.parameters.forEach { param ->
            gbc.gridy++
            val paramText = "${param.name}: ${param.type}"
            val paramStatus = when (param.stability) {
              ParameterStability.STABLE -> "Stable"
              ParameterStability.RUNTIME -> "Runtime"
              ParameterStability.UNSTABLE -> "Unstable"
            }
            val paramColor = when (param.stability) {
              ParameterStability.STABLE -> Color(settings.stableHintColorRGB)
              ParameterStability.RUNTIME -> Color(settings.runtimeHintColorRGB)
              ParameterStability.UNSTABLE -> Color(settings.unstableHintColorRGB)
            }
            addDetailRow(detailsPanel, gbc, "  $paramText", paramStatus, color = paramColor)

            // Show reason if available
            if (param.reason != null) {
              gbc.gridy++
              val reasonLabel = JBLabel("    Reason: ${param.reason}")
              reasonLabel.foreground = UIUtil.getInactiveTextColor()
              detailsPanel.add(reasonLabel, gbc)
            }
          }
        }

        // Location
        gbc.gridy++
        val fileName = data.node.filePath.substringAfterLast('/')
        addDetailRow(detailsPanel, gbc, "File:", "$fileName:${data.node.line}")

        // Navigation hint
        gbc.gridy++
        val navigateLabel = JBLabel("Double-click to navigate to source")
        navigateLabel.foreground = UIUtil.getInactiveTextColor()
        detailsPanel.add(navigateLabel, gbc)
      }

      is CascadeTreeNodeData.Summary -> {
        addDetailRow(
          detailsPanel,
          gbc,
          "Root Composable:",
          data.composableName,
          bold = true,
        )
        addDetailRow(
          detailsPanel,
          gbc,
          "Total Downstream:",
          "${data.summary.totalCount}",
        )
        addDetailRow(
          detailsPanel,
          gbc,
          "Skippable:",
          "${data.summary.skippableCount}",
          color = Color(settings.stableGutterColorRGB),
        )
        addDetailRow(
          detailsPanel,
          gbc,
          "Unskippable:",
          "${data.summary.unskippableCount}",
          color = Color(settings.unstableGutterColorRGB),
        )
        addDetailRow(
          detailsPanel,
          gbc,
          "Max Depth:",
          "${data.summary.maxDepth}",
        )
        if (data.summary.hasTruncatedBranches) {
          addDetailRow(
            detailsPanel,
            gbc,
            "Note:",
            "Some branches were truncated",
            color = UIUtil.getInactiveTextColor(),
          )
        }
      }

      else -> {
        val label = JBLabel("Select a composable to view details")
        label.foreground = UIUtil.getInactiveTextColor()
        detailsPanel.add(label, gbc)
      }
    }

    // Filler at the bottom
    gbc.gridy++
    gbc.weighty = 1.0
    detailsPanel.add(JPanel(), gbc)

    detailsPanel.revalidate()
    detailsPanel.repaint()
  }

  private fun addDetailRow(
    panel: JPanel,
    gbc: GridBagConstraints,
    label: String,
    value: String,
    bold: Boolean = false,
    color: Color? = null,
  ) {
    gbc.gridy++

    val fullLabel = JBLabel("$label $value")
    if (bold) {
      fullLabel.font = fullLabel.font.deriveFont(Font.BOLD)
    }
    if (color != null) {
      fullLabel.foreground = color
    }

    panel.add(fullLabel, gbc)
  }

  /**
   * Refresh action - re-analyzes the current function.
   */
  private inner class RefreshAction : AnAction(
    "Refresh",
    "Re-analyze recomposition cascade",
    AllIcons.Actions.Refresh,
  ) {
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
      updateDetailsPanel(null)
    }
  }
}
