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
package com.skydoves.compose.stability.idea.doctor.fixes

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.skydoves.compose.stability.idea.settings.StabilitySettingsConfigurable
import java.io.File

/**
 * Appends a type's fully qualified name to the stability configuration file, marking it stable
 * for the IDE's analysis. This is the designated fix for LIBRARY types the other fixes can't
 * touch. Note: the Compose compiler only honors the entry if the build's
 * `stabilityConfigurationFile` points at the same file.
 *
 * When no configuration path is set ([configFilePath] == null), applying opens the settings
 * dialog instead so the user can configure one.
 */
internal class AddToStabilityConfigFix(
  private val typeFqName: String,
  private val configFilePath: String?,
) : DoctorFix {

  override val title: String = if (configFilePath != null) {
    "Add $typeFqName to stability config (${File(configFilePath).name})"
  } else {
    "Set a stability configuration file to mark $typeFqName stable"
  }

  override val previewText: String? = configFilePath?.let {
    "Appends \"$typeFqName\" to $it.\n" +
      "Takes effect in the IDE immediately; the Compose compiler honors it only if your " +
      "build's stabilityConfigurationFile points at the same file."
  }

  override fun isAvailable(): Boolean =
    configFilePath == null || File(configFilePath).parentFile?.exists() == true

  override fun apply(project: Project) {
    val path = configFilePath
    if (path == null) {
      ShowSettingsUtil.getInstance()
        .showSettingsDialog(project, StabilitySettingsConfigurable::class.java)
      return
    }

    val ioFile = File(path)
    if (!ioFile.exists()) {
      runCatching { ioFile.createNewFile() }.onFailure {
        Messages.showErrorDialog(
          project,
          "Could not create stability configuration file at $path",
          "Stability Doctor",
        )
        return
      }
    }

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    if (vFile == null) {
      Messages.showErrorDialog(
        project,
        "Could not open stability configuration file at $path",
        "Stability Doctor",
      )
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(vFile)
    if (document == null) {
      Messages.showErrorDialog(
        project,
        "Could not read stability configuration file at $path",
        "Stability Doctor",
      )
      return
    }

    // Skip when the exact entry is already present.
    if (document.text.lineSequence().any { it.trim() == typeFqName }) return

    WriteCommandAction.runWriteCommandAction(project, title, null, {
      val needsNewline = document.textLength > 0 && !document.text.endsWith("\n")
      document.insertString(
        document.textLength,
        (if (needsNewline) "\n" else "") + typeFqName + "\n",
      )
      FileDocumentManager.getInstance().saveDocument(document)
    })
  }
}
