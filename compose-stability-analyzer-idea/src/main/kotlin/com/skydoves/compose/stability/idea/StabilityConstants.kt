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

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Constants used across the Compose Stability Analyzer IDE plugin.
 */
internal object StabilityConstants {

  // Annotation names
  internal object Annotations {
    const val COMPOSABLE = "Composable"
    const val PREVIEW = "Preview"
    const val STABLE_FQ = "androidx.compose.runtime.Stable"
    const val IMMUTABLE_FQ = "androidx.compose.runtime.Immutable"

    const val ERROR_PRONE_IMMUTABLE_FQ = "com.google.errorprone.annotations.Immutable"

    const val STABLE_FOR_ANALYSIS = "com.skydoves.compose.stability.runtime.StableForAnalysis"
  }

  // Colors used for stability indication
  internal object Colors {
    // RGB values
    const val STABLE_R = 95
    const val STABLE_G = 184
    const val STABLE_B = 101

    const val UNSTABLE_R = 232
    const val UNSTABLE_G = 104
    const val UNSTABLE_B = 74

    const val RUNTIME_R = 240
    const val RUNTIME_G = 198
    const val RUNTIME_B = 116

    // JBColor instances
    internal val STABLE_COLOR: JBColor = JBColor(
      Color(STABLE_R, STABLE_G, STABLE_B),
      Color(STABLE_R, STABLE_G, STABLE_B),
    )

    internal val UNSTABLE_COLOR: JBColor = JBColor(
      Color(UNSTABLE_R, UNSTABLE_G, UNSTABLE_B),
      Color(UNSTABLE_R, UNSTABLE_G, UNSTABLE_B),
    )

    internal val RUNTIME_COLOR: JBColor = JBColor(
      Color(RUNTIME_R, RUNTIME_G, RUNTIME_B),
      Color(RUNTIME_R, RUNTIME_G, RUNTIME_B),
    )

    // HTML color codes
    const val STABLE_HTML = "#5FB865"
    const val UNSTABLE_HTML = "#E8684A"
    const val RUNTIME_HTML = "#F0C674"
    const val GRAY_HTML = "#808080"
  }

  // Common string constants
  internal object Strings {
    const val UNKNOWN = "Unknown"
    const val COMPOSABLE = "Composable"
    const val NON_RESTARTABLE_COMPOSABLE = "NonRestartableComposable"
    const val NON_SKIPPABLE_COMPOSABLE = "NonSkippableComposable"
    const val READ_ONLY_COMPOSABLE = "ReadOnlyComposable"
  }

  // Stability reason messages
  internal object Messages {
    // Stable reasons
    const val STRING_STABLE = "String is stable"
    const val UNIT_STABLE = "Unit/Nothing are stable"
    const val COMPOSABLE_FUNCTION_STABLE =
      "@Composable function types are always stable with Strong Skipping mode, " +
        "otherwise stability depends on captured values."
    const val FUNCTION_STABLE = "Function types are stable"
    const val ENUM_STABLE = "Enum class"
    const val SUSPEND_FUNCTION_STABLE =
      "Suspend function types are always stable with Strong Skipping mode, " +
        "otherwise stability depends on captured values."
    const val KOTLINX_IMMUTABLE_STABLE = "Kotlinx immutable collections are always stable."
    const val KNOWN_STABLE_TYPE = "Known stable type"
    const val ALL_PROPERTIES_STABLE = "All properties are stable val"
    const val PRIMITIVE_TYPE = "Primitive type"
    const val NO_MUTABLE_PROPERTIES = "No mutable properties"
    const val STABLE_ANNOTATION = "Annotated with @Stable or @Immutable"

    // Unstable reasons
    const val MUTABLE_COLLECTION_UNSTABLE = "Mutable collection type"
    const val MUTABLE_PROPERTIES_UNSTABLE =
      "This parameter has mutable properties or is a mutable collection."
    const val HAS_UNSTABLE_PROPERTIES = "Has unstable properties"
    const val NOT_CLASS_TYPE = "Not a class type"

    // Special cases
    const val CIRCULAR_REFERENCE = "Circular reference detected - assuming stable"
    const val IGNORED_BY_SETTINGS = "Ignored by user settings"
    const val CUSTOM_STABLE_TYPE = "Custom stable type from configuration"

    // Runtime reasons
    const val RUNTIME_STABILITY = "Stability will be determined at runtime based on actual type."
    const val RUNTIME_STABILITY_CHECK = "Runtime stability check required"
    const val UNKNOWN_STABILITY = "Unknown stability"
    const val INTERFACE_OR_ABSTRACT = "interface or abstract type"
  }

  // Label strings
  internal object Labels {
    const val STABLE = "stable"
    const val UNSTABLE = "unstable"
    const val RUNTIME = "runtime"
  }

  // Display group names
  internal object Groups {
    const val COMPOSE = "Compose"
  }

  // Inspection names
  internal object Inspections {
    const val UNSTABLE_COMPOSABLE_DISPLAY_NAME = "Unstable Composable Function"
    const val UNSTABLE_COMPOSABLE_SHORT_NAME = "UnstableComposable"
  }
}
