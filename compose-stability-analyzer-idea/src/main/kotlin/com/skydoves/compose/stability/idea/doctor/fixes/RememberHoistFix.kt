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

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Wraps a call-site argument in `remember(keys) { ... }` so a SILENT_WASTE parameter — an
 * equals-equal value recreated on every recomposition — keeps its instance identity and lets
 * strong skipping's `===` comparison succeed.
 *
 * Applicability is decided conservatively at analysis time by
 * [DoctorFixFactory.rememberHoistApplicability]; this class only re-validates that the
 * expression has not changed since then and performs the replacement.
 *
 * Purity cannot be proven statically — the confirmation dialog shows the exact replacement
 * and warns the user to verify the expression is side-effect free.
 */
internal class RememberHoistFix(
  private val callerName: String,
  private val paramName: String,
  private val argumentPointer: SmartPsiElementPointer<KtExpression>,
  /** The argument expression's text at analysis time (revalidated before applying). */
  private val originalExpressionText: String,
  /** Remember keys: the free value inputs of the expression (may be empty). */
  private val keys: List<String>,
) : DoctorFix {

  private val replacementText: String = if (keys.isEmpty()) {
    "remember { $originalExpressionText }"
  } else {
    "remember(${keys.joinToString(", ")}) { $originalExpressionText }"
  }

  override val title: String =
    "Wrap '$paramName' argument in remember(...) at $callerName call site"

  override val previewText: String =
    "Before: $originalExpressionText\n" +
      "After:  $replacementText\n\n" +
      "Verify the expression is side-effect free — remember caches its result."

  override fun isAvailable(): Boolean = runReadAction {
    val expr = argumentPointer.element
    expr != null &&
      expr.isValid &&
      expr.containingFile?.isWritable == true &&
      // The code changed since analysis — the stored keys may no longer be correct.
      expr.text == originalExpressionText
  }

  override fun apply(project: Project) {
    val expr = runReadAction { argumentPointer.element } ?: return
    if (runReadAction { expr.text } != originalExpressionText) return
    val file = expr.containingFile as? KtFile ?: return

    WriteCommandAction.runWriteCommandAction(project, title, null, {
      val factory = KtPsiFactory(project)
      val replacement = expr.replace(factory.createExpression(replacementText))
      addImportIfMissing(factory, file, "androidx.compose.runtime.remember")
      CodeStyleManager.getInstance(project).reformat(replacement)
    }, file)
  }
}
