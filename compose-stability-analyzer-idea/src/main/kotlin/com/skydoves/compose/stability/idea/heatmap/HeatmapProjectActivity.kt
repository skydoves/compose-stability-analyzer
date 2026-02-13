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

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState

/**
 * Auto-starts the recomposition heatmap listener on project open,
 * if the feature is enabled and auto-start is configured and
 * exactly one ADB device is connected.
 */
internal class HeatmapProjectActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    val settings = StabilitySettingsState.getInstance()
    if (!settings.isHeatmapEnabled || !settings.heatmapAutoStart) return

    val service = AdbLogcatService.getInstance(project)
    val devices = service.listDevices()
    if (devices.size == 1) {
      settings.isHeatmapEnabled = true
      service.start(devices[0].serial)
    }
  }
}
