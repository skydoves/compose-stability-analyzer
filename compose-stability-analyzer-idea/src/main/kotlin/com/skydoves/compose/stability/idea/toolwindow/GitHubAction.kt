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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to open the Compose Stability Analyzer GitHub repository.
 */
internal class GitHubAction : AnAction(
  "GitHub",
  "Open Compose Stability Analyzer on GitHub",
  AllIcons.Vcs.Vendors.Github,
) {
  override fun actionPerformed(e: AnActionEvent) {
    BrowserUtil.browse("https://github.com/skydoves/compose-stability-analyzer")
  }
}
