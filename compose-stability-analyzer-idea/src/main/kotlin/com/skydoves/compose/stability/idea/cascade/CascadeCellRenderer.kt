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
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Tree cell renderer for the cascade visualization tree.
 * Renders each node type with appropriate icons, colors, and text.
 */
internal class CascadeCellRenderer : ColoredTreeCellRenderer() {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

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
      is CascadeTreeNodeData.Summary -> renderSummary(data)
      is CascadeTreeNodeData.Composable -> renderComposable(data)
      is CascadeTreeNodeData.Truncated -> renderTruncated(data)
      is CascadeTreeNodeData.EmptyMessage -> renderEmptyMessage(data)
    }
  }

  private fun renderSummary(data: CascadeTreeNodeData.Summary) {
    icon = AllIcons.General.BalloonInformation
    append(
      "Blast Radius: ",
      SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
    )
    append(
      "${data.summary.totalCount} composables",
      SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
    )
    append(" (", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    append(
      "${data.summary.skippableCount} skippable",
      SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN,
        Color(settings.stableGutterColorRGB),
      ),
    )
    append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    append(
      "${data.summary.unskippableCount} unskippable",
      SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN,
        Color(settings.unstableGutterColorRGB),
      ),
    )
    append(")", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  private fun renderComposable(data: CascadeTreeNodeData.Composable) {
    val info = data.node.stabilityInfo
    val isSkippable = info.isSkippable
    val hasUnstable = info.parameters.any { it.stability == ParameterStability.UNSTABLE }
    val hasRuntime = info.parameters.any { it.stability == ParameterStability.RUNTIME }
    val isRuntimeOnly = !isSkippable && !hasUnstable && hasRuntime

    icon = AllIcons.Nodes.Function
    val nameColor = when {
      isSkippable -> Color(settings.stableGutterColorRGB)
      isRuntimeOnly -> Color(settings.runtimeGutterColorRGB)
      else -> Color(settings.unstableGutterColorRGB)
    }
    append(
      info.name,
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, nameColor),
    )

    // Show non-stable parameter count
    val unstableCount = info.parameters.count { it.stability == ParameterStability.UNSTABLE }
    val runtimeCount = info.parameters.count { it.stability == ParameterStability.RUNTIME }
    if (unstableCount > 0) {
      append(
        " ($unstableCount unstable)",
        SimpleTextAttributes(
          SimpleTextAttributes.STYLE_ITALIC,
          UIUtil.getInactiveTextColor(),
        ),
      )
    } else if (runtimeCount > 0) {
      append(
        " ($runtimeCount runtime)",
        SimpleTextAttributes(
          SimpleTextAttributes.STYLE_ITALIC,
          UIUtil.getInactiveTextColor(),
        ),
      )
    }

    // Show skippable badge
    val badge = when {
      isSkippable -> " [skippable]"
      isRuntimeOnly -> " [runtime]"
      else -> " [NOT skippable]"
    }
    val badgeColor = when {
      isSkippable -> Color(settings.stableGutterColorRGB)
      isRuntimeOnly -> Color(settings.runtimeGutterColorRGB)
      else -> Color(settings.unstableGutterColorRGB)
    }
    append(
      badge,
      SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, badgeColor),
    )
  }

  private fun renderTruncated(data: CascadeTreeNodeData.Truncated) {
    icon = AllIcons.General.Warning
    append(
      data.reason,
      SimpleTextAttributes(
        SimpleTextAttributes.STYLE_ITALIC,
        UIUtil.getInactiveTextColor(),
      ),
    )
  }

  private fun renderEmptyMessage(data: CascadeTreeNodeData.EmptyMessage) {
    icon = AllIcons.General.Information
    append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
