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
package com.skydoves.compose.stability.idea.doctor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Opens the Stability Doctor tab and runs the analysis: a ranked, quantified, actionable
 * list of stability fixes for the whole project.
 */
public class RunDoctorAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolWindow = ToolWindowManager.getInstance(project)
      .getToolWindow("Compose Stability Analyzer") ?: return
    toolWindow.show {
      val doctorContent = toolWindow.contentManager.findContent("Doctor") ?: return@show
      toolWindow.contentManager.setSelectedContent(doctorContent)
      val panel = doctorContent.component
        .getClientProperty(DoctorPanel::class.java) as? DoctorPanel ?: return@show
      panel.refresh()
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}
