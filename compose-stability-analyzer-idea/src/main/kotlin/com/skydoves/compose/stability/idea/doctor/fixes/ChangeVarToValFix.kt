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
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * Changes `var` declarations to `val` on the unstable class behind a composable parameter.
 *
 * Safety: before applying, every `var` is searched for WRITE usages across the project. If any
 * assignment exists, the fix aborts with a message instead of silently breaking compilation —
 * a `var` that is actually written needs a design change, not a keyword flip.
 *
 * Targets cover both mutable constructor parameters (`var` in the primary constructor) and
 * `var` body properties.
 */
internal class ChangeVarToValFix(
  private val className: String,
  private val varPointers: List<SmartPsiElementPointer<out KtDeclaration>>,
) : DoctorFix {

  override val title: String =
    "Change ${varPointers.size} var → val in $className"

  override val previewText: String =
    "All `var` declarations in $className become `val`. " +
      "Aborts automatically if any write usage exists."

  override fun isAvailable(): Boolean = runReadAction {
    varPointers.isNotEmpty() &&
      varPointers.all {
        val element = it.element
        element != null && element.isValid && element.containingFile?.isWritable == true
      }
  }

  override fun apply(project: Project) {
    val targets = runReadAction { varPointers.mapNotNull { it.element } }
    if (targets.size != varPointers.size) return

    // Refuse when any var is actually written somewhere — flipping the keyword would break it.
    val writeUsages = findWriteUsages(project, targets)
    if (writeUsages > 0) {
      Messages.showWarningDialog(
        project,
        "$className has $writeUsages write usage(s) of its var properties. " +
          "Changing them to val would break those assignments — refactor the writes first.",
        "Stability Doctor",
      )
      return
    }

    val file = runReadAction { targets.first().containingFile } ?: return
    WriteCommandAction.runWriteCommandAction(project, title, null, {
      val factory = KtPsiFactory(project)
      val valKeyword = factory.createProperty("val x = 1").valOrVarKeyword
      targets.forEach { declaration ->
        val keyword = when (declaration) {
          is KtProperty -> declaration.valOrVarKeyword
          is KtParameter -> declaration.valOrVarKeyword
          else -> null
        }
        keyword?.replace(valKeyword.copy())
      }
    }, file)
  }

  private fun findWriteUsages(project: Project, targets: List<KtDeclaration>): Int {
    var count = 0
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        runReadAction {
          val scope = GlobalSearchScope.projectScope(project)
          targets.forEach { declaration ->
            runCatching {
              ReferencesSearch.search(declaration, scope).forEach { ref ->
                if (isWriteAccess(ref.element)) count++
              }
            }
          }
        }
      },
      "Checking for write usages...",
      true,
      project,
    )
    return count
  }

  /** True when [element] is the target of an assignment (`x = ...`, `x += ...`, `x++`). */
  private fun isWriteAccess(element: PsiElement): Boolean {
    // Climb through qualified access (obj.x = ...) to the expression that owns the reference.
    var expr: PsiElement = element
    val qualifiedParent = expr.parent as? KtQualifiedExpression
    if (qualifiedParent != null && qualifiedParent.selectorExpression == expr) {
      expr = qualifiedParent
    }
    val parent = expr.parent
    return when {
      parent is KtBinaryExpression &&
        parent.operationToken in ASSIGNMENT_TOKENS &&
        parent.left?.isAncestor(element, strict = false) == true -> true
      parent is KtUnaryExpression &&
        (
          parent.operationToken == KtTokens.PLUSPLUS ||
            parent.operationToken == KtTokens.MINUSMINUS
          ) -> true
      else -> false
    }
  }

  private companion object {
    val ASSIGNMENT_TOKENS = setOf(
      KtTokens.EQ,
      KtTokens.PLUSEQ,
      KtTokens.MINUSEQ,
      KtTokens.MULTEQ,
      KtTokens.DIVEQ,
      KtTokens.PERCEQ,
    )
  }
}
