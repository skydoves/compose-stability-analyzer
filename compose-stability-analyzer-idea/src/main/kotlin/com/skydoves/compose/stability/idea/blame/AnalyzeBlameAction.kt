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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.util.PsiTreeUtil
import com.skydoves.compose.stability.idea.isComposable
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Context menu action to trace the upstream "blame" of a @Composable function — which composables
 * call it and where each argument originates.
 */
public class AnalyzeBlameAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

    val offset = editor.caretModel.offset
    val element = psiFile.findElementAt(offset) ?: return
    val function = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java) ?: return
    if (!function.isComposable()) return

    val toolWindow = ToolWindowManager.getInstance(project)
      .getToolWindow("Compose Stability Analyzer") ?: return
    toolWindow.show {
      val blameContent = toolWindow.contentManager.findContent("Blame") ?: return@show
      toolWindow.contentManager.setSelectedContent(blameContent)
      val panel = blameContent.component
        .getClientProperty(BlamePanel::class.java) as? BlamePanel ?: return@show
      panel.analyzeFunction(function)
    }
  }

  override fun update(e: AnActionEvent) {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)
    val editor = e.getData(CommonDataKeys.EDITOR)

    if (psiFile == null || editor == null) {
      e.presentation.isVisible = false
      return
    }

    e.presentation.isVisible = psiFile is KtFile

    val offset = editor.caretModel.offset
    val element = psiFile.findElementAt(offset)
    val function = element?.let {
      PsiTreeUtil.getParentOfType(it, KtNamedFunction::class.java)
    }

    e.presentation.isEnabled = function?.isComposable() == true
  }
}
