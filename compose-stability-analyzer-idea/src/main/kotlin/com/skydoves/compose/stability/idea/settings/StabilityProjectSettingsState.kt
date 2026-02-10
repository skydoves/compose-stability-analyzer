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
package com.skydoves.compose.stability.idea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level settings for Compose Stability Analyzer plugin.
 * These settings are stored per-project in .idea/composeStabilityProject.xml
 */
@Service(Service.Level.PROJECT)
@State(
  name = "ComposeStabilityProjectSettings",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
public class StabilityProjectSettingsState :
  PersistentStateComponent<StabilityProjectSettingsState> {

  /**
   * Path to external stability configuration file for this project.
   * This file can define custom stable types and ignored patterns.
   */
  public var stabilityConfigurationPath: String = ""

  public override fun getState(): StabilityProjectSettingsState = this

  public override fun loadState(state: StabilityProjectSettingsState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  /**
   * Get custom stable type patterns from configuration file.
   */
  public fun getCustomStableTypesAsRegex(): List<Regex> {
    if (stabilityConfigurationPath.isEmpty()) {
      return emptyList()
    }

    return try {
      val file = java.io.File(stabilityConfigurationPath)
      if (!file.exists() || !file.isFile) {
        return emptyList()
      }

      val patterns = mutableListOf<String>()
      file.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
          patterns.add(trimmed)
        }
      }

      patterns.mapNotNull { pattern ->
        try {
          stabilityPatternToRegex(pattern)
        } catch (e: Exception) {
          null
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  public companion object {
    public fun getInstance(project: Project): StabilityProjectSettingsState {
      return project.getService(StabilityProjectSettingsState::class.java)
    }
  }
}
