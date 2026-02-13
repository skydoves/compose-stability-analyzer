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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import javax.swing.ListSelectionModel

/**
 * Toggle action for starting or stopping the live recomposition heatmap listener.
 *
 * - When stopped: lists connected ADB devices and starts listening on the selected one.
 * - When running: stops the logcat listener.
 */
internal class ToggleHeatmapAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val service = AdbLogcatService.getInstance(project)
    if (service.isRunning) {
      e.presentation.text = "Stop Recomposition Heatmap"
      e.presentation.icon = AllIcons.Actions.Suspend
    } else {
      e.presentation.text = "Start Recomposition Heatmap"
      e.presentation.icon = AllIcons.Actions.Execute
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val service = AdbLogcatService.getInstance(project)

    if (service.isRunning) {
      service.stop()
      notify(project, "Recomposition Heatmap Stopped", "Logcat listener stopped.")
      return
    }

    if (!service.isAdbAvailable()) {
      notify(
        project,
        "ADB Not Found",
        "Cannot find adb. Set ANDROID_HOME environment variable, " +
          "add sdk.dir to local.properties, or ensure adb is on PATH.",
        NotificationType.ERROR,
      )
      return
    }

    val devices = service.listDevices()
    when {
      devices.isEmpty() -> {
        notify(
          project,
          "No Devices Found",
          "No ADB devices connected. Please connect a device or emulator and try again.",
          NotificationType.WARNING,
        )
      }

      devices.size == 1 -> {
        StabilitySettingsState.getInstance().isHeatmapEnabled = true
        service.start(devices[0].serial)
        notify(
          project,
          "Recomposition Heatmap Started",
          "Listening on ${devices[0].serial} (adb: ${service.getAdbPath()})",
        )
      }

      else -> {
        showDevicePicker(project, service, devices)
      }
    }
  }

  private fun showDevicePicker(
    project: com.intellij.openapi.project.Project,
    service: AdbLogcatService,
    devices: List<AdbDevice>,
  ) {
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(devices)
      .setTitle("Select Device for Recomposition Heatmap")
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setItemChosenCallback { device ->
        StabilitySettingsState.getInstance().isHeatmapEnabled = true
        service.start(device.serial)
        notify(
          project,
          "Recomposition Heatmap Started",
          "Listening on ${device.serial}",
        )
      }
      .createPopup()
      .showCenteredInCurrentWindow(project)
  }

  private fun notify(
    project: com.intellij.openapi.project.Project,
    title: String,
    content: String,
    type: NotificationType = NotificationType.INFORMATION,
  ) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Compose Stability Analyzer")
      .createNotification(title, content, type)
      .notify(project)
  }
}

/**
 * Action to clear all accumulated recomposition heatmap data.
 */
internal class ClearHeatmapDataAction : AnAction(
  "Clear Recomposition Data",
  "Clear all accumulated recomposition heatmap data",
  AllIcons.Actions.GC,
) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    AdbLogcatService.getInstance(project).clearData()
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Compose Stability Analyzer")
      .createNotification(
        "Heatmap Data Cleared",
        "All recomposition heatmap data has been reset.",
        NotificationType.INFORMATION,
      )
      .notify(project)
  }
}
