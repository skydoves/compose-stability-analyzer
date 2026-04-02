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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Creates a shared toolbar with right-aligned global actions
 * (Toggle Heatmap, Clear Recomposition Data, Settings, GitHub).
 */
internal fun createSharedToolbar(targetComponent: JComponent): JComponent {
  val actionGroup = DefaultActionGroup()
  val actionManager = ActionManager.getInstance()
  actionManager.getAction("com.skydoves.compose.stability.idea.heatmap.ToggleHeatmapAction")
    ?.let { actionGroup.add(it) }
  actionManager.getAction("com.skydoves.compose.stability.idea.heatmap.ClearHeatmapDataAction")
    ?.let { actionGroup.add(it) }
  actionGroup.add(SettingsAction())
  actionGroup.add(GitHubAction())

  val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
  toolbar.targetComponent = targetComponent
  return toolbar.component
}

/**
 * Combines a tab-specific toolbar (left) with the shared global toolbar (right).
 */
internal fun combineToolbars(
  tabToolbar: JComponent,
  targetComponent: JComponent,
): JComponent {
  val sharedToolbar = createSharedToolbar(targetComponent)
  return JPanel(BorderLayout()).apply {
    isOpaque = false
    add(tabToolbar, BorderLayout.WEST)
    add(sharedToolbar, BorderLayout.EAST)
  }
}
