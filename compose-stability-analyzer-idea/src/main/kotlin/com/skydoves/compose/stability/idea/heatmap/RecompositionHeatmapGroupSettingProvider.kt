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

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider

/**
 * Registers the recomposition heatmap as a toggleable Code Vision group
 * under Settings -> Editor -> Inlay Hints -> Code Vision.
 */
internal class RecompositionHeatmapGroupSettingProvider : CodeVisionGroupSettingProvider {

  companion object {
    const val GROUP_ID = "compose.recomposition.heatmap.group"
  }

  override val groupId: String = GROUP_ID
  override val groupName: String = "Compose Recomposition Heatmap"
  override val description: String =
    "Shows live recomposition counts above @Composable functions from a connected device"
}
