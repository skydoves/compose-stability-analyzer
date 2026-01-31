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
package com.skydoves.compose.stability.idea

import com.skydoves.compose.stability.idea.settings.StabilitySettingsState

/**
 * Snapshot of [com.skydoves.compose.stability.idea.settings.StabilitySettingsState] values that tests commonly mutate.
 *
 * Why this exists:
 * - These plugin settings are global within the test JVM / IntelliJ test environment.
 * - If a test modifies them and forgets to restore, later tests may become flaky or
 *   incorrectly pass/fail depending on execution order.
 *
 * This value object is intentionally small and "just enough" for tests that need stability
 * analysis to behave deterministically.
 */
internal data class SettingsSnapshot(
  val isStabilityCheckEnabled: Boolean,
  val isStrongSkippingEnabled: Boolean,
  val ignoredTypePatterns: String,
  val stabilityConfigurationPath: String,
) {
  fun restore(state: StabilitySettingsState) {
    val snapshot = this
    state.apply {
      isStabilityCheckEnabled = snapshot.isStabilityCheckEnabled
      isStrongSkippingEnabled = snapshot.isStrongSkippingEnabled
      ignoredTypePatterns = snapshot.ignoredTypePatterns
      stabilityConfigurationPath = snapshot.stabilityConfigurationPath
    }
  }

  companion object {
    fun fromState(state: StabilitySettingsState) = SettingsSnapshot(
      isStabilityCheckEnabled = state.isStabilityCheckEnabled,
      isStrongSkippingEnabled = state.isStrongSkippingEnabled,
      ignoredTypePatterns = state.ignoredTypePatterns,
      stabilityConfigurationPath = state.stabilityConfigurationPath,
    )
  }
}
