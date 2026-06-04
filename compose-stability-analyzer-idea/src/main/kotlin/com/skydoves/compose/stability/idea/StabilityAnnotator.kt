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

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.skydoves.compose.stability.idea.quickfix.AddSuppressIntentionAction
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import com.skydoves.compose.stability.runtime.ParameterStabilityInfo
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Annotates unstable parameters with warnings/info messages.
 */
public class StabilityAnnotator : Annotator {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!settings.isStabilityCheckEnabled) {
      return
    }

    if (!settings.showWarnings) {
      return
    }

    if (element !is KtParameter) return

    val function = element.parent?.parent as? KtNamedFunction ?: return
    if (!function.isComposable()) return
    if (function.isPreview()) return

    if (isSuppressed(function)) return

    val analysis = try {
      StabilityAnalyzer.analyze(function)
    } catch (e: Exception) {
      return
    }

    val paramInfo = analysis.parameters.find { it.name == element.name } ?: return

    when (paramInfo.stability) {
      ParameterStability.UNSTABLE -> {
        element.nameIdentifier?.let { nameElement ->
          val suppressName = getSuppressName()
          val elementPointer = SmartPointerManager.createPointer(element as PsiElement)
          val quickFix = AddSuppressIntentionAction(suppressName, elementPointer)

          holder.newAnnotation(
            HighlightSeverity.WARNING,
            buildUnstableMessage(paramInfo),
          )
            .range(nameElement)
            .tooltip(buildUnstableTooltip(paramInfo))
            .highlightType(ProblemHighlightType.WARNING)
            .newFix(quickFix).registerFix()
            .create()
        }
      }

      ParameterStability.RUNTIME, ParameterStability.UNKNOWN -> {
        element.nameIdentifier?.let { nameElement ->
          holder.newAnnotation(
            HighlightSeverity.WEAK_WARNING,
            buildRuntimeMessage(paramInfo),
          )
            .range(nameElement)
            .tooltip(buildRuntimeTooltip(paramInfo))
            .create()
        }
      }

      ParameterStability.STABLE -> {
      }
    }
  }

  private fun buildUnstableMessage(param: ParameterStabilityInfo): String {
    return "Unstable parameter '${param.name}' prevents composable from being skippable"
  }

  private fun buildUnstableTooltip(param: ParameterStabilityInfo): String {
    return buildString {
      append("⚠️ Unstable Parameter\n\n")
      append("Parameter '${param.name}' of type '${param.type}' is unstable.\n\n")
      append("This prevents the composable from being skipped during recomposition, ")
      append("which may impact performance.\n\n")
      if (param.reason != null) {
        append("Reason: ${param.reason}\n\n")
      }
      append("To fix:\n")
      append("• Use 'val' instead of 'var' in data classes\n")
      append("• Replace mutable collections with immutable ones\n")
      append("• Annotate the type with @Stable or @Immutable\n")
    }
  }

  private fun buildRuntimeMessage(param: ParameterStabilityInfo): String {
    return "Parameter '${param.name}' has runtime-determined stability"
  }

  private fun buildRuntimeTooltip(param: ParameterStabilityInfo): String {
    return buildString {
      append("ℹ️ Runtime Stability\n\n")
      append("Parameter '${param.name}' of type '${param.type}' has stability ")
      append("that will be determined at runtime.\n\n")
      append("This may prevent compile-time optimizations. Consider using types ")
      append("with known stability (primitives, @Stable/@Immutable annotated classes).")
    }
  }

  /**
   * Check if the function is already suppressed.
   */
  private fun isSuppressed(function: KtNamedFunction): Boolean {
    val suppressAnnotation = function.annotationEntries.find {
      it.shortName?.asString() == "Suppress"
    } ?: return false

    // Check if any of the suppress names match
    val valueArguments = suppressAnnotation.valueArgumentList?.arguments ?: emptyList()
    return valueArguments.any { arg ->
      val text = arg.getArgumentExpression()?.text?.trim('"') ?: ""
      text == AddSuppressIntentionAction.NON_SKIPPABLE_COMPOSABLE ||
        text == AddSuppressIntentionAction.PARAMS_COMPARED_BY_REF
    }
  }

  /**
   * Get the appropriate suppress name based on the current mode.
   */
  private fun getSuppressName(): String {
    return if (settings.isStrongSkippingEnabled) {
      AddSuppressIntentionAction.PARAMS_COMPARED_BY_REF
    } else {
      AddSuppressIntentionAction.NON_SKIPPABLE_COMPOSABLE
    }
  }
}
