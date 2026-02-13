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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.skydoves.compose.stability.idea.cascade.CascadePanel

/**
 * Factory for creating the Compose Stability Analyzer tool window.
 */
public class StabilityToolWindowFactory : ToolWindowFactory {

  public override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()

    // Tab 1: Stability Explorer (existing)
    val stabilityToolWindow = StabilityToolWindow(project)
    val explorerContent = contentFactory.createContent(
      stabilityToolWindow.getContent(),
      "Explorer",
      false,
    )
    toolWindow.contentManager.addContent(explorerContent)

    // Tab 2: Recomposition Cascade
    val cascadePanel = CascadePanel(project)
    val cascadeComponent = cascadePanel.getContent()
    cascadeComponent.putClientProperty(CascadePanel::class.java, cascadePanel)
    val cascadeContent = contentFactory.createContent(
      cascadeComponent,
      "Cascade",
      false,
    )
    toolWindow.contentManager.addContent(cascadeContent)
  }

  public override fun shouldBeAvailable(project: Project): Boolean = true
}
