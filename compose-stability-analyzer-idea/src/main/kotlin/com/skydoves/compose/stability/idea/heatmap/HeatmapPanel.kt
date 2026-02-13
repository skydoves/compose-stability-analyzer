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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * UI panel for viewing recomposition heatmap logs.
 * Shows a tree of recomposition events for a selected composable.
 */
internal class HeatmapPanel(private val project: Project) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  private var currentComposableName: String? = null

  init {
    rootNode = DefaultMutableTreeNode("Heatmap")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)

    tree.cellRenderer = HeatmapTreeCellRenderer()
    tree.isRootVisible = false
    tree.showsRootHandles = true

    showEmptyState()
  }

  public fun getContent(): JComponent {
    val mainPanel = SimpleToolWindowPanel(true, true)

    val toolbar = createToolbar()
    mainPanel.toolbar = toolbar

    mainPanel.setContent(ScrollPaneFactory.createScrollPane(tree))
    return mainPanel
  }

  /**
   * Displays recomposition data for the given composable name.
   * Called when the user clicks on a heatmap inlay in the editor.
   */
  public fun showComposableData(composableName: String) {
    currentComposableName = composableName
    val service = AdbLogcatService.getInstance(project)
    val data = service.getHeatmapData(composableName)
    if (data != null) {
      displayData(data)
    } else {
      showNoDataState(composableName)
    }
  }

  private fun displayData(data: ComposableHeatmapData) {
    rootNode.removeAllChildren()

    // Summary node
    val summaryNode = DefaultMutableTreeNode(
      HeatmapNodeData.Summary(
        composableName = data.composableName,
        totalCount = data.totalRecompositionCount,
        unstableParameters = data.unstableParameters,
        changedParameters = data.changedParameters,
      ),
    )
    rootNode.add(summaryNode)

    // Events header
    val eventsHeaderNode = DefaultMutableTreeNode(
      HeatmapNodeData.EventsHeader(count = data.recentEvents.size),
    )
    rootNode.add(eventsHeaderNode)

    // Individual events (most recent first)
    data.recentEvents.asReversed().forEach { event ->
      val eventNode = DefaultMutableTreeNode(
        HeatmapNodeData.Event(event),
      )
      eventsHeaderNode.add(eventNode)

      // Add parameter entries as children
      event.parameterEntries.forEach { param ->
        val paramNode = DefaultMutableTreeNode(
          HeatmapNodeData.Parameter(param),
        )
        eventNode.add(paramNode)
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
    val emptyNode = DefaultMutableTreeNode(
      HeatmapNodeData.EmptyMessage(
        "Click a recomposition count in the editor to view logs here",
      ),
    )
    rootNode.add(emptyNode)
    treeModel.reload()
  }

  private fun showNoDataState(composableName: String) {
    rootNode.removeAllChildren()
    val emptyNode = DefaultMutableTreeNode(
      HeatmapNodeData.EmptyMessage(
        "No recomposition data available for $composableName",
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

  /**
   * Node data types for the heatmap tree.
   */
  internal sealed class HeatmapNodeData {

    data class Summary(
      val composableName: String,
      val totalCount: Int,
      val unstableParameters: Set<String>,
      val changedParameters: Map<String, Int>,
    ) : HeatmapNodeData()

    data class EventsHeader(val count: Int) : HeatmapNodeData()

    data class Event(val event: ParsedRecompositionEvent) : HeatmapNodeData()

    data class Parameter(val entry: ParsedParameterEntry) : HeatmapNodeData()

    data class EmptyMessage(val message: String) : HeatmapNodeData()
  }

  /**
   * Custom tree cell renderer for heatmap nodes.
   */
  private inner class HeatmapTreeCellRenderer : ColoredTreeCellRenderer() {
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
        is HeatmapNodeData.Summary -> {
          icon = AllIcons.Actions.Lightning
          append(data.composableName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append(
            " (${data.totalCount} recompositions)",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
          )
        }

        is HeatmapNodeData.EventsHeader -> {
          icon = AllIcons.Debugger.Console
          append("Recent Events", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append(" (${data.count})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        is HeatmapNodeData.Event -> {
          val event = data.event
          icon = AllIcons.Actions.Refresh
          append("#${event.recompositionCount}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append(" ${event.composableName}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          if (event.tag.isNotEmpty()) {
            append(" (${event.tag})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }

          val changed = event.parameterEntries.count { it.status == ParameterStatus.CHANGED }
          if (changed > 0) {
            append(
              " $changed changed",
              SimpleTextAttributes(
                SimpleTextAttributes.STYLE_ITALIC,
                Color(settings.unstableHintColorRGB),
              ),
            )
          }
        }

        is HeatmapNodeData.Parameter -> {
          val param = data.entry
          val statusColor = when (param.status) {
            ParameterStatus.CHANGED -> Color(settings.unstableHintColorRGB)
            ParameterStatus.STABLE -> Color(settings.stableHintColorRGB)
            ParameterStatus.UNSTABLE -> Color(settings.runtimeHintColorRGB)
          }
          val statusText = when (param.status) {
            ParameterStatus.CHANGED -> "changed"
            ParameterStatus.STABLE -> "stable"
            ParameterStatus.UNSTABLE -> "unstable"
          }

          icon = AllIcons.Nodes.Parameter
          append("${param.name}: ${param.type} ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          append(
            statusText,
            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, statusColor),
          )
          if (param.detail.isNotEmpty()) {
            append(" (${param.detail})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
        }

        is HeatmapNodeData.EmptyMessage -> {
          icon = AllIcons.General.Information
          append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
    }
  }

  /**
   * Refresh action - re-loads data for the current composable.
   */
  private inner class RefreshAction : AnAction(
    "Refresh",
    "Refresh heatmap data",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentComposableName?.let { showComposableData(it) }
    }
  }

  /**
   * Clear action - resets to empty state.
   */
  private inner class ClearAction : AnAction(
    "Clear",
    "Clear heatmap view",
    AllIcons.Actions.GC,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      currentComposableName = null
      showEmptyState()
    }
  }
}
