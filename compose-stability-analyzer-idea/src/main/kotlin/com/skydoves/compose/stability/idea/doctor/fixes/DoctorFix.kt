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

import com.intellij.openapi.project.Project

/**
 * An actionable fix attached to a Doctor prescription cause.
 *
 * Implementations re-validate their smart pointers in [isAvailable] (PSI may have shifted since
 * analysis) and perform the mutation inside a `WriteCommandAction` in [apply].
 */
internal interface DoctorFix {
  /** Short, imperative label shown on the fix node, e.g. "Change 2 var → val in Product". */
  val title: String

  /** A preview of the change shown in the confirmation dialog; null = no preview available. */
  val previewText: String? get() = null

  /** Whether the fix can still be applied (smart pointers valid, target writable, ...). */
  fun isAvailable(): Boolean

  /** Applies the fix. Must be called on the EDT; runs its own write command. */
  fun apply(project: Project)
}
