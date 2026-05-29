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

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_END
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_START
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_START
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_END
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_START
import com.intellij.psi.PsiElement
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.idea.heatmap.AdbLogcatService
import com.skydoves.compose.stability.idea.reality.ComposableReality
import com.skydoves.compose.stability.idea.reality.RealityClassifier
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Provides hover documentation for @Composable functions showing stability analysis.
 */
public class StabilityDocumentationProvider : AbstractDocumentationProvider() {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  public override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    if (!settings.isStabilityCheckEnabled) {
      return null
    }

    val function = element as? KtNamedFunction ?: return null
    if (!function.isComposable()) {
      return null
    }

    if (function.isPreview()) {
      return null
    }

    val analysis = StabilityAnalyzer.analyze(function)
    val reality = buildReality(function, analysis)
    return buildString {
      append(DEFINITION_START)
      append("<b>${function.name}</b> - Compose Stability Analysis")
      append(DEFINITION_END)

      append(CONTENT_START)

      append("<p style='margin-top: 8px;'>")
      append(
        when {
          analysis.isSkippableInStrongSkippingMode -> {
            "<span style='color: #5FB865;'><b>✅ Skippable</b></span> " +
              "(strong skipping mode enabled) - " +
              "All composables are skippable with strong skipping mode"
          }

          analysis.isSkippable -> {
            "<span style='color: #5FB865;'><b>✅ Skippable</b></span> " +
              "- This composable can skip recomposition when inputs haven't changed"
          }

          else -> {
            "<span style='color: #E8684A;'><b>❌ Not Skippable</b></span> " +
              "- This composable will always recompose. " +
              "Enable <b>Strong Skipping mode</b> to make it skippable"
          }
        },
      )
      append("</p>")

      // Restartable status
      append("<p>")
      append(
        if (analysis.isRestartable) {
          "<span style='color: #5FB865;'>🔄 <b>Restartable</b></span> " +
            "- Can restart from the beginning"
        } else {
          "<span style='color: #808080;'>⛔ <b>Non-Restartable</b></span> " +
            "- Cannot be restarted"
        },
      )
      append("</p>")

      // Read-only status
      if (analysis.isReadonly) {
        append("<p>")
        append(
          "<span style='color: #5FB865;'>📖 <b>Read-Only</b></span> " +
            "- Guaranteed not to modify state",
        )
        append("</p>")
      }

      // Live Reality Check headline (only when runtime data has been observed)
      if (reality != null && !reality.isObserving) {
        append("<p>")
        append("<span style='color: #808080;'>📡 <b>Live:</b> ")
        append("${reality.totalObservedRecompositions} recomposition")
        if (reality.totalObservedRecompositions != 1) append("s")
        if (reality.wastedRecompositions > 0) {
          append(
            " &mdash; <span style='color: ${StabilityConstants.Colors.SILENT_WASTE_HTML};'>" +
              "${reality.wastedRecompositions} wasted (recent)</span>",
          )
        }
        append("</span></p>")
      }

      // Parameter details
      if (analysis.parameters.isNotEmpty()) {
        append("<br/>")
        append(SECTIONS_START)
        append("<tr><td colspan='2'><b>Parameters:</b></td></tr>")

        analysis.parameters.forEach { param ->
          append("<tr>")
          append("<td style='padding: 4px 8px 4px 0;'>")

          val icon = when (param.stability) {
            ParameterStability.STABLE ->
              "<span style='color: ${StabilityConstants.Colors.STABLE_HTML};'>✅</span>"

            ParameterStability.UNSTABLE ->
              "<span style='color: ${StabilityConstants.Colors.UNSTABLE_HTML};'>❌</span>"

            ParameterStability.RUNTIME ->
              "<span style='color: ${StabilityConstants.Colors.RUNTIME_HTML};'>⚠️</span>"
          }
          append(icon)
          append(" <code>${param.name}: ${param.type}</code>")
          append("</td>")

          append("<td style='padding: 4px 0 4px 8px; color: #808080;'>")
          append("<i>predicted: ${param.stability.name.lowercase()}</i>")
          val grade = reality?.gradeFor(param.name)
          if (grade != null && grade != RealityGrade.OBSERVING) {
            append(gradeBadgeHtml(grade))
          }
          append("</td>")
          append("</tr>")

          if (param.stability != ParameterStability.STABLE) {
            append("<tr>")
            append(
              "<td colspan='2' style='padding: 0 8px 8px 24px; font-size: 0.9em; color: #808080;'>",
            )
            val explanation = param.reason ?: when (param.stability) {
              ParameterStability.UNSTABLE ->
                StabilityConstants.Messages.MUTABLE_PROPERTIES_UNSTABLE

              ParameterStability.RUNTIME ->
                StabilityConstants.Messages.RUNTIME_STABILITY

              else -> ""
            }
            append(explanation)
            append("</td>")
            append("</tr>")
          }
        }
        append(SECTIONS_END)
      }

      // Receiver details
      if (analysis.receivers.isNotEmpty()) {
        append("<br/>")
        append(SECTIONS_START)
        append("<tr><td colspan='2'><b>Receivers:</b></td></tr>")

        analysis.receivers.forEach { receiver ->
          append("<tr>")
          append("<td style='padding: 4px 8px 4px 0;'>")

          val icon = when (receiver.stability) {
            ParameterStability.STABLE ->
              "<span style='color: ${StabilityConstants.Colors.STABLE_HTML};'>✅</span>"

            ParameterStability.UNSTABLE ->
              "<span style='color: ${StabilityConstants.Colors.UNSTABLE_HTML};'>❌</span>"

            ParameterStability.RUNTIME ->
              "<span style='color: ${StabilityConstants.Colors.RUNTIME_HTML};'>⚠️</span>"
          }
          append(icon)
          append(" <code>${receiver.receiverKind.name.lowercase()}: ${receiver.type}</code>")
          append("</td>")

          append("<td style='padding: 4px 0 4px 8px; color: #808080;'>")
          append(
            "<i>${receiver.stability.name.lowercase().replaceFirstChar { it.uppercase() }}</i>",
          )
          append("</td>")
          append("</tr>")

          // Add explanation for non-stable receivers
          if (receiver.stability != ParameterStability.STABLE) {
            append("<tr>")
            append(
              "<td colspan='2' style='padding: 0 8px 8px 24px; font-size: 0.9em; color: #808080;'>",
            )
            val explanation = receiver.reason ?: when (receiver.stability) {
              ParameterStability.UNSTABLE ->
                "Unstable receiver makes the composable unstable"

              ParameterStability.RUNTIME ->
                "Receiver stability depends on implementation"

              else -> ""
            }
            append(explanation)
            append("</td>")
            append("</tr>")
          }
        }
        append(SECTIONS_END)
      }

      // Suggestions for unstable parameters and receivers
      if (!analysis.isSkippable) {
        val unstableParams =
          analysis.parameters.filter { it.stability == ParameterStability.UNSTABLE }
        val unstableReceivers =
          analysis.receivers.filter { it.stability == ParameterStability.UNSTABLE }

        if (unstableParams.isNotEmpty() || unstableReceivers.isNotEmpty()) {
          append("<br/>")
          append(SECTIONS_START)
          append("<tr><td colspan='2'><b>💡 Optimization Suggestions:</b></td></tr>")

          unstableParams.forEach { param ->
            append("<tr><td colspan='2' style='padding: 4px 0 4px 16px;'>")
            append("<b>${param.name}</b> (parameter)")
            append("<ul style='margin: 4px 0;'>")
            append("<li>Use <code>val</code> instead of <code>var</code> in data classes</li>")
            append(
              "<li>Replace <code>MutableList</code>/<code>MutableSet</code>/<code>" +
                "MutableMap</code> with immutable versions</li>",
            )
            append(
              "<li>Annotate the class with <code>@Stable</code> " +
                "or <code>@Immutable</code></li>",
            )
            append(
              "<li>Use <code>@Stable</code> annotation on the parameter " +
                "type if you control the stability contract</li>",
            )
            append("</ul>")
            append("</td></tr>")
          }

          unstableReceivers.forEach { receiver ->
            append("<tr><td colspan='2' style='padding: 4px 0 4px 16px;'>")
            append("<b>${receiver.type}</b> (${receiver.receiverKind.name.lowercase()} receiver)")
            append("<ul style='margin: 4px 0;'>")
            append(
              "<li>Make the ${receiver.receiverKind.name.lowercase()} receiver type stable " +
                "by using only <code>val</code> properties</li>",
            )
            append(
              "<li>Annotate the receiver class with <code>@Stable</code> " +
                "or <code>@Immutable</code></li>",
            )
            append(
              "<li>Consider refactoring to pass the receiver as a stable parameter instead</li>",
            )
            append("</ul>")
            append("</td></tr>")
          }

          append(SECTIONS_END)
        }
      }

      // Performance impact explanation
      append("<br/>")
      append(SECTIONS_START)
      append("<tr><td colspan='2'><b>Performance Impact:</b></td></tr>")
      append("<tr><td colspan='2' style='padding: 4px 0 4px 16px; color: #808080;'>")
      if (analysis.isSkippable) {
        append(
          "This composable can be skipped during recomposition " +
            "if none of its inputs have changed, ",
        )
        append("improving performance by avoiding unnecessary work.")
      } else {
        append("This composable will <b>always</b> recompose when its parent recomposes, ")
        append("which may impact performance in frequently recomposing UI hierarchies. ")
        append("Consider making all parameters stable to enable smart recomposition.")
      }
      append("</td></tr>")
      append(SECTIONS_END)

      append(CONTENT_END)
    }
  }

  /**
   * Builds the live reconciliation (Reality Check) for a composable, or null when the feature is
   * disabled or no runtime data has been observed yet. Reads the thread-safe live data map.
   */
  private fun buildReality(
    function: KtNamedFunction,
    analysis: ComposableStabilityInfo,
  ): ComposableReality? {
    if (!settings.isRealityCheckEnabled || !settings.showRealityCheckInTooltips) return null
    val name = function.name ?: return null
    val live = AdbLogcatService.getInstance(function.project).getHeatmapData(name) ?: return null
    return RealityClassifier.classify(analysis, live)
  }

  /** Renders a small "actual: …" badge describing the runtime grade for a parameter. */
  private fun gradeBadgeHtml(grade: RealityGrade): String {
    val (color, label) = when (grade) {
      RealityGrade.CONFIRMED ->
        StabilityConstants.Colors.CONFIRMED_HTML to "✓ confirmed stable at runtime"
      RealityGrade.FALSE_ALARM ->
        StabilityConstants.Colors.FALSE_ALARM_HTML to "false alarm — instance stays referentially stable"
      RealityGrade.SILENT_WASTE ->
        StabilityConstants.Colors.SILENT_WASTE_HTML to "silent waste — recomposes, could skip"
      RealityGrade.JUSTIFIED ->
        StabilityConstants.Colors.JUSTIFIED_HTML to "justified — value genuinely changes"
      RealityGrade.OBSERVING ->
        StabilityConstants.Colors.GRAY_HTML to "observing…"
    }
    return "<br/><span style='color: $color;'>● actual: $label</span>"
  }

  public override fun getQuickNavigateInfo(
    element: PsiElement?,
    originalElement: PsiElement?,
  ): String? {
    val function = element as? KtNamedFunction ?: return null
    if (!function.isComposable()) return null
    if (function.isPreview()) return null

    val analysis = StabilityAnalyzer.analyze(function)
    return buildString {
      append(if (analysis.isSkippable) "✅ Skippable" else "❌ Not Skippable")
      append(" | ")
      val stableCount = analysis.parameters.count { it.stability == ParameterStability.STABLE }
      val totalCount = analysis.parameters.size
      append("$stableCount/$totalCount stable parameters")

      if (analysis.receivers.isNotEmpty()) {
        append(" | ")
        val stableReceiverCount = analysis.receivers.count {
          it.stability == ParameterStability.STABLE
        }
        val totalReceiverCount = analysis.receivers.size
        append("$stableReceiverCount/$totalReceiverCount stable receivers")
      }
    }
  }
}

/**
 * Extension function to check if a function is composable.
 */
internal fun KtNamedFunction.isComposable(): Boolean {
  return annotationEntries.any {
    it.shortName?.asString() == StabilityConstants.Annotations.COMPOSABLE
  }
}

/**
 * Extension function to check if a function is annotated with @Preview
 * or any custom annotation that is meta-annotated with @Preview.
 *
 * Examples:
 * - @Preview @Composable fun Foo() -> true (direct)
 * - @MyPreview @Composable fun Bar() -> true (if @MyPreview has @Preview)
 */
internal fun KtNamedFunction.isPreview(): Boolean {
  return annotationEntries.any { annotation ->
    if (annotation.shortName?.asString() == StabilityConstants.Annotations.PREVIEW) {
      return@any true
    }

    try {
      val typeReference = annotation.typeReference
      val userType = typeReference?.typeElement as? org.jetbrains.kotlin.psi.KtUserType
      val referenceExpression = userType?.referenceExpression
      val annotationClass = referenceExpression?.resolveMainReference() as? KtClass

      annotationClass?.annotationEntries?.any {
        it.shortName?.asString() == StabilityConstants.Annotations.PREVIEW
      } == true
    } catch (e: Exception) {
      false
    }
  }
}
