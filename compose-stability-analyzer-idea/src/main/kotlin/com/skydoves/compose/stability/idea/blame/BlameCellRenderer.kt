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
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** Renders the upstream blame tree: callers and the arguments they pass toward the target. */
internal class BlameCellRenderer : ColoredTreeCellRenderer() {
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
      is BlameTreeNodeData.Summary -> {
        icon = AllIcons.Actions.Lightning
        append(data.composableName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        append(
          "  ${data.callerCount} caller(s), max depth ${data.maxDepth}",
          SimpleTextAttributes.GRAYED_ATTRIBUTES,
        )
      }

      is BlameTreeNodeData.Caller -> {
        val n = data.node
        icon = AllIcons.Nodes.Function
        append(n.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        if (n.callSiteLine > 0) {
          append("  (calls at line ${n.callSiteLine})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }

      is BlameTreeNodeData.Argument -> {
        val o = data.origin
        icon = AllIcons.Nodes.Parameter
        append("${o.paramName} = ${o.expressionText}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("  [${o.origin}]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      is BlameTreeNodeData.Truncated -> {
        icon = AllIcons.General.Warning
        append(data.reason, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      is BlameTreeNodeData.EmptyMessage -> {
        icon = AllIcons.General.Information
        append(data.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}
