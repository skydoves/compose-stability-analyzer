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
package com.skydoves.compose.stability.idea.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Main tool window UI for Compose Stability Analyzer.
 * Shows a tree of all composables in the project grouped by module/package/file.
 */
public class StabilityToolWindow(private val project: Project) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private val detailsPanel: JPanel
  private var currentStats = StabilityStats()
  private var allComposables: List<ComposableInfo> = emptyList()
  private var currentFilter: FilterType = FilterType.ALL

  private enum class FilterType {
    ALL, SKIPPABLE, UNSKIPPABLE
  }

  init {
    rootNode = DefaultMutableTreeNode("Composables")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = StabilityTreeCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    detailsPanel = createDetailsPanel()

    // Add selection listener
    tree.addTreeSelectionListener { event ->
      val node = event.path.lastPathComponent as? DefaultMutableTreeNode
      updateDetailsPanel(node)
    }

    // Add double-click listener for navigation and single-click for GitHub links
    tree.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent) {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode

        // Handle single click on GitHub links
        if (e.clickCount == 1) {
          val gitHubLink = node?.userObject as? StabilityNodeData.GitHubLink
          if (gitHubLink != null) {
            BrowserUtil.browse(gitHubLink.url)
            return
          }
        }

        // Handle double click on composables for navigation
        if (e.clickCount == 2) {
          val composable = node?.userObject as? StabilityNodeData.Composable
          if (composable != null) {
            navigateToSource(composable.info)
          }
        }
      }
    })
  }

  public fun getContent(): JComponent {
    val mainPanel = SimpleToolWindowPanel(true, true)

    // Create toolbar
    val toolbar = createToolbar()
    mainPanel.toolbar = toolbar

    // Create split pane with tree and details
    val splitter = OnePixelSplitter(false, 0.6f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
    splitter.secondComponent = JBScrollPane(detailsPanel)

    mainPanel.setContent(splitter)

    // Load data initially
    ApplicationManager.getApplication().invokeLater {
      refresh()
    }

    return mainPanel
  }

  private fun createToolbar(): JComponent {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RefreshAction())
    actionGroup.addSeparator()
    actionGroup.add(FilterAllAction())
    actionGroup.add(FilterSkippableAction())
    actionGroup.add(FilterUnskippableAction())
    actionGroup.addSeparator()
    actionGroup.add(SettingsAction())
    actionGroup.add(GitHubAction())
    actionGroup.addSeparator()
    val toggleHeatmap = ActionManager.getInstance()
      .getAction("com.skydoves.compose.stability.idea.heatmap.ToggleHeatmapAction")
    if (toggleHeatmap != null) {
      actionGroup.add(toggleHeatmap)
    }

    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = tree

    return toolbar.component
  }

  private fun refresh() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val collector = ComposableStabilityCollector(project)
      val results = collector.collectAll()

      ApplicationManager.getApplication().invokeLater {
        allComposables = results.composables
        applyFilter()
      }
    }
  }

  private fun applyFilter() {
    // Get ignored patterns from settings
    val settings = com.skydoves.compose.stability.idea.settings.StabilitySettingsState.getInstance()
    val ignoredPatterns = settings.getIgnoredPatternsAsRegex()

    // Process all composables: mark ignored parameters as stable and recalculate skippability
    val processedComposables = allComposables.map { composable ->
      // Mark ignored parameters as stable instead of removing them
      val processedParameters = composable.parameters.map { param ->
        if (shouldIgnoreParameter(param.type, ignoredPatterns)) {
          // Treat ignored parameters as stable
          param.copy(isStable = true, isRuntime = false)
        } else {
          param
        }
      }

      // Recalculate skippability: a composable is skippable if all parameters are stable
      // (after applying ignore patterns)
      val allParametersStable = processedParameters.all { it.isStable }
      val isSkippable = composable.isRestartable &&
        (processedParameters.isEmpty() || allParametersStable)

      composable.copy(
        parameters = processedParameters,
        isSkippable = isSkippable,
      )
    }

    // Filter composables based on current filter type
    val filteredComposables = when (currentFilter) {
      FilterType.ALL -> processedComposables
      FilterType.SKIPPABLE -> processedComposables.filter { it.isSkippable }
      FilterType.UNSKIPPABLE -> processedComposables.filter { !it.isSkippable }
    }

    val stats = StabilityStats(
      totalCount = filteredComposables.size,
      skippableCount = filteredComposables.count { it.isSkippable },
      unskippableCount = filteredComposables.count { !it.isSkippable },
    )

    val results = ComposableStabilityResults(filteredComposables, stats)
    updateTree(results)
  }

  /**
   * Check if a parameter type should be ignored based on user-configured patterns.
   */
  private fun shouldIgnoreParameter(typeName: String, ignoredPatterns: List<Regex>): Boolean {
    return ignoredPatterns.any { pattern -> pattern.matches(typeName) }
  }

  private fun navigateToSource(composable: ComposableInfo) {
    val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
      .findFileByPath(composable.filePath) ?: return

    ApplicationManager.getApplication().invokeLater {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@invokeLater
      val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile)
        ?: return@invokeLater

      val offset = if (composable.line > 0) {
        document.getLineStartOffset(composable.line - 1)
      } else {
        0
      }

      FileEditorManager.getInstance(project).openFile(virtualFile, true)
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      if (editor != null && editor.document == document) {
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
      }
    }
  }

  private fun updateTree(results: ComposableStabilityResults) {
    rootNode.removeAllChildren()
    currentStats = results.stats

    // Show helpful message if no composables found
    if (results.composables.isEmpty()) {
      val headerNode = DefaultMutableTreeNode(
        StabilityNodeData.EmptyMessage("To view compose stability information:"),
      )
      rootNode.add(headerNode)

      // Add each step as a child node
      val steps = listOf(
        "1. Apply the Compose Stability Analyzer Gradle plugin to your module",
        "2. Build your project to generate stability reports",
        "3. Click the refresh button above",
      )

      steps.forEach { step ->
        headerNode.add(DefaultMutableTreeNode(StabilityNodeData.EmptyMessage(step)))
      }

      // Add GitHub link
      val githubNode = DefaultMutableTreeNode(
        StabilityNodeData.GitHubLink(
          "For more information, check out GitHub",
          "https://github.com/skydoves/compose-stability-analyzer",
        ),
      )
      headerNode.add(githubNode)

      treeModel.reload()
      tree.expandRow(0) // Expand the header to show all steps
      return
    }

    // Group by module
    results.composables.groupBy { it.moduleName }.forEach { (moduleName, composables) ->
      val moduleNode = DefaultMutableTreeNode(
        StabilityNodeData.Module(
          name = moduleName,
          skippableCount = composables.count { it.isSkippable },
          unskippableCount = composables.count { !it.isSkippable },
        ),
      )
      rootNode.add(moduleNode)

      // Group by package
      composables.groupBy { it.packageName }.forEach { (packageName, packageComposables) ->
        val packageNode = DefaultMutableTreeNode(
          StabilityNodeData.Package(
            name = packageName,
            skippableCount = packageComposables.count { it.isSkippable },
            unskippableCount = packageComposables.count { !it.isSkippable },
          ),
        )
        moduleNode.add(packageNode)

        // Group by file
        packageComposables.groupBy { it.fileName }.forEach { (fileName, fileComposables) ->
          val fileNode = DefaultMutableTreeNode(
            StabilityNodeData.File(
              name = fileName,
              skippableCount = fileComposables.count { it.isSkippable },
              unskippableCount = fileComposables.count { !it.isSkippable },
            ),
          )
          packageNode.add(fileNode)

          // Add individual composables
          fileComposables.forEach { composable ->
            val composableNode = DefaultMutableTreeNode(
              StabilityNodeData.Composable(composable),
            )
            fileNode.add(composableNode)
          }
        }
      }
    }

    treeModel.reload()
    expandDefaultNodes()
  }

  private fun expandDefaultNodes() {
    // Expand root and module nodes by default
    tree.expandRow(0)
    for (i in 0 until rootNode.childCount) {
      tree.expandPath(tree.getPathForRow(i + 1))
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
      is StabilityNodeData.Composable -> {
        val composable = data.info

        // Title
        addDetailRow(detailsPanel, gbc, "Composable:", composable.functionName, bold = true)

        // Stability status
        val statusText = when {
          composable.isSkippable -> "STABLE (Skippable)"
          else -> "UNSTABLE (Non-skippable)"
        }
        val statusColor = when {
          composable.isSkippable -> Color(settings.stableGutterColorRGB)
          else -> Color(settings.runtimeGutterColorRGB)
        }
        addDetailRow(detailsPanel, gbc, "Status:", statusText, color = statusColor)

        // Restartable
        addDetailRow(
          detailsPanel,
          gbc,
          "Restartable:",
          if (composable.isRestartable) "Yes" else "No",
        )

        // Parameters
        if (composable.parameters.isNotEmpty()) {
          gbc.gridy++
          val headerLabel = JBLabel("Parameters:")
          headerLabel.font = headerLabel.font.deriveFont(java.awt.Font.BOLD)
          detailsPanel.add(headerLabel, gbc)

          composable.parameters.forEach { param ->
            gbc.gridy++
            val paramText = "${param.name}: ${param.type}"
            val paramStatus = when {
              param.isStable -> "Stable"
              param.isRuntime -> "Runtime"
              else -> "Unstable"
            }
            val paramColor = when {
              param.isStable -> Color(settings.stableHintColorRGB)
              param.isRuntime -> Color(settings.runtimeHintColorRGB)
              else -> Color(settings.unstableHintColorRGB)
            }
            addDetailRow(detailsPanel, gbc, "  $paramText", paramStatus, color = paramColor)
          }
        }

        // Location
        gbc.gridy++
        addDetailRow(detailsPanel, gbc, "File:", "${composable.fileName}:${composable.line}")

        // Double-click to navigate
        gbc.gridy++
        val navigateLabel = JBLabel("Double-click to navigate to source")
        navigateLabel.foreground = UIUtil.getInactiveTextColor()
        detailsPanel.add(navigateLabel, gbc)
      }

      is StabilityNodeData.Module -> {
        addDetailRow(detailsPanel, gbc, "Module:", data.name, bold = true)
        addStatsRows(detailsPanel, gbc, data.skippableCount, data.unskippableCount)
      }

      is StabilityNodeData.Package -> {
        addDetailRow(detailsPanel, gbc, "Package:", data.name, bold = true)
        addStatsRows(detailsPanel, gbc, data.skippableCount, data.unskippableCount)
      }

      is StabilityNodeData.File -> {
        addDetailRow(detailsPanel, gbc, "File:", data.name, bold = true)
        addStatsRows(detailsPanel, gbc, data.skippableCount, data.unskippableCount)
      }

      else -> {
        val label = JBLabel("Select a composable to view details")
        label.foreground = UIUtil.getInactiveTextColor()
        detailsPanel.add(label, gbc)
      }
    }

    // Add filler at the bottom
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
      fullLabel.font = fullLabel.font.deriveFont(java.awt.Font.BOLD)
    }
    if (color != null) {
      fullLabel.foreground = color
    }

    panel.add(fullLabel, gbc)
  }

  private fun addStatsRows(
    panel: JPanel,
    gbc: GridBagConstraints,
    skippableCount: Int,
    unskippableCount: Int,
  ) {
    addDetailRow(
      panel,
      gbc,
      "Skippable:",
      "$skippableCount",
      color = Color(settings.stableGutterColorRGB),
    )
    addDetailRow(
      panel,
      gbc,
      "Non-skippable:",
      "$unskippableCount",
      color = Color(settings.runtimeGutterColorRGB),
    )
  }

  /**
   * Refresh action for toolbar
   */
  private inner class RefreshAction : AnAction(
    "Refresh",
    "Refresh stability analysis",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      refresh()
    }
  }

  /**
   * Filter actions
   */
  private inner class FilterAllAction : AnAction(
    "All",
    "Show all composables",
    AllIcons.Actions.Show,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFilter = FilterType.ALL
      applyFilter()
    }
  }

  private inner class FilterSkippableAction : AnAction(
    "Skippable",
    "Show only skippable composables",
    AllIcons.RunConfigurations.TestPassed,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFilter = FilterType.SKIPPABLE
      applyFilter()
    }
  }

  private inner class FilterUnskippableAction : AnAction(
    "Unskippable",
    "Show only unskippable composables",
    AllIcons.RunConfigurations.TestFailed,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentFilter = FilterType.UNSKIPPABLE
      applyFilter()
    }
  }

  /**
   * Settings action
   */
  private inner class SettingsAction : AnAction(
    "Settings",
    "Open Compose Stability Analyzer settings",
    AllIcons.General.Settings,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      com.intellij.openapi.options.ShowSettingsUtil.getInstance()
        .showSettingsDialog(project, "Compose Stability Analyzer")
    }
  }

  /**
   * GitHub link action
   */
  private inner class GitHubAction : AnAction(
    "GitHub",
    "Open Compose Stability Analyzer on GitHub",
    AllIcons.Vcs.Vendors.Github,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      com.intellij.ide.BrowserUtil.browse("https://github.com/skydoves/compose-stability-analyzer")
    }
  }

  /**
   * Custom tree cell renderer with color coding
   */
  private inner class StabilityTreeCellRenderer : ColoredTreeCellRenderer() {
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
        is StabilityNodeData.Module -> {
          icon = AllIcons.Nodes.Module
          append(data.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          appendStats(data.skippableCount, data.unskippableCount)
        }

        is StabilityNodeData.Package -> {
          icon = AllIcons.Nodes.Package
          append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          appendStats(data.skippableCount, data.unskippableCount)
        }

        is StabilityNodeData.File -> {
          icon = AllIcons.FileTypes.Any_type
          append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          appendStats(data.skippableCount, data.unskippableCount)
        }

        is StabilityNodeData.Composable -> {
          val info = data.info
          val color = when {
            info.isSkippable -> Color(settings.stableGutterColorRGB)
            else -> Color(settings.runtimeGutterColorRGB)
          }

          icon = AllIcons.Nodes.Function
          append(info.functionName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))

          // Add parameter info
          val unstableParams = info.parameters.count { !it.isStable }
          if (unstableParams > 0) {
            append(
              " ($unstableParams unstable)",
              SimpleTextAttributes(
                SimpleTextAttributes.STYLE_ITALIC,
                UIUtil.getInactiveTextColor(),
              ),
            )
          }
        }

        is StabilityNodeData.EmptyMessage -> {
          icon = AllIcons.General.Information
          append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        is StabilityNodeData.GitHubLink -> {
          icon = AllIcons.Ide.External_link_arrow
          append(
            data.text,
            SimpleTextAttributes(
              SimpleTextAttributes.STYLE_PLAIN,
              JBUI.CurrentTheme.Link.Foreground.ENABLED,
            ),
          )
        }

        else -> {
          append(data.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      }
    }

    private fun appendStats(skippableCount: Int, unskippableCount: Int) {
      val total = skippableCount + unskippableCount
      if (total > 0) {
        append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("(", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

        if (skippableCount > 0) {
          append(
            "${skippableCount}S",
            SimpleTextAttributes(
              SimpleTextAttributes.STYLE_PLAIN,
              Color(settings.stableGutterColorRGB),
            ),
          )
        }

        if (unskippableCount > 0) {
          if (skippableCount > 0) {
            append(", ", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
          append(
            "${unskippableCount}NS",
            SimpleTextAttributes(
              SimpleTextAttributes.STYLE_PLAIN,
              Color(settings.runtimeGutterColorRGB),
            ),
          )
        }

        append(")", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
    }
  }
}
