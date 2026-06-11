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
package com.skydoves.compose.stability.idea.blame

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.skydoves.compose.stability.idea.isComposable
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * The reverse of [com.skydoves.compose.stability.idea.cascade.CascadeAnalyzer]: walks UPSTREAM from
 * a composable to the composables that call it, annotating each call edge with the static origin of
 * the arguments passed. Helps answer "where does this composable's (changed) input come from?".
 *
 * Static, best-effort: dynamic origins terminate as "expression"/"unknown". All resolution runs in
 * a read action on a background thread.
 */
internal object BlameAnalyzer {

  private const val MAX_DEPTH = 8

  internal fun analyze(target: KtNamedFunction, indicator: ProgressIndicator): BlameResult {
    return ReadAction.compute<BlameResult, Exception> {
      indicator.text = "Analyzing recomposition blame..."
      indicator.checkCanceled()
      val visited = mutableSetOf<String>()
      val root = buildNode(target, callToCallee = null, calleeParams = null, depth = 0, visited, indicator)
      var count = 0
      var maxDepth = 0
      fun walk(n: BlameNode) {
        if (n.depth > 0) count++
        if (n.depth > maxDepth) maxDepth = n.depth
        n.children.forEach { walk(it) }
      }
      walk(root)
      BlameResult(root, count, maxDepth)
    }
  }

  private fun buildNode(
    function: KtNamedFunction,
    callToCallee: KtCallExpression?,
    calleeParams: List<String>?,
    depth: Int,
    visited: MutableSet<String>,
    indicator: ProgressIndicator,
  ): BlameNode {
    val fqName = function.fqName?.asString() ?: function.name ?: "unknown"
    val name = function.name ?: "unknown"
    val filePath = function.containingFile.virtualFile?.path ?: ""
    val line = lineOf(function, function.textOffset)
    val passed = if (callToCallee != null && calleeParams != null) {
      resolveArguments(callToCallee, calleeParams)
    } else {
      emptyList()
    }
    val callSiteFile = callToCallee?.containingFile?.virtualFile?.path
    val callSiteLine = callToCallee?.let { lineOf(function, it.textOffset) } ?: 0

    indicator.text2 = name

    if (depth >= MAX_DEPTH) {
      return BlameNode(
        name, fqName, filePath, line, depth, passed, callSiteFile, callSiteLine,
        emptyList(), isTruncated = true, truncationReason = "Maximum depth ($MAX_DEPTH) reached",
      )
    }
    if (fqName in visited) {
      return BlameNode(
        name, fqName, filePath, line, depth, passed, callSiteFile, callSiteLine,
        emptyList(), isTruncated = true, truncationReason = "Cycle detected: $fqName",
      )
    }
    visited.add(fqName)

    val thisParams = function.valueParameters.mapNotNull { it.name }
    val children = findCallers(function).map { (caller, callExpr) ->
      indicator.checkCanceled()
      buildNode(caller, callExpr, thisParams, depth + 1, visited, indicator)
    }

    visited.remove(fqName)
    return BlameNode(name, fqName, filePath, line, depth, passed, callSiteFile, callSiteLine, children)
  }

  /**
   * Blame-lite for the Stability Doctor: resolves the direct (depth-1) composable callers of
   * [target] and the static origin of each argument they pass, capped at [maxCallers].
   * MUST be called inside a read action.
   */
  internal fun analyzeDirectCallers(
    target: KtNamedFunction,
    maxCallers: Int = 5,
  ): List<DirectCallerOrigins> {
    val params = target.valueParameters.mapNotNull { it.name }
    return findCallers(target).take(maxCallers).map { (caller, callExpr) ->
      DirectCallerOrigins(
        callerName = caller.name ?: "unknown",
        callerFqName = caller.fqName?.asString() ?: caller.name ?: "unknown",
        callSiteFilePath = callExpr.containingFile?.virtualFile?.path,
        callSiteLine = lineOf(caller, callExpr.textOffset),
        origins = resolveArguments(callExpr, params),
      )
    }
  }

  /** One direct caller of a composable plus the origins of the arguments it passes. */
  internal data class DirectCallerOrigins(
    val callerName: String,
    val callerFqName: String,
    val callSiteFilePath: String?,
    val callSiteLine: Int,
    val origins: List<ArgumentOrigin>,
  )

  /** Finds composable callers of [function] (deduplicated), with the call site for each. */
  internal fun findCallers(function: KtNamedFunction): List<Pair<KtNamedFunction, KtCallExpression>> {
    val scope = GlobalSearchScope.projectScope(function.project)
    val result = mutableListOf<Pair<KtNamedFunction, KtCallExpression>>()
    val seen = mutableSetOf<String>()
    runCatching {
      ReferencesSearch.search(function, scope).forEach { ref ->
        val callExpr = PsiTreeUtil.getParentOfType(ref.element, KtCallExpression::class.java)
          ?: return@forEach
        val caller = PsiTreeUtil.getParentOfType(callExpr, KtNamedFunction::class.java)
          ?: return@forEach
        if (!caller.isComposable()) return@forEach
        val key = caller.fqName?.asString() ?: caller.name ?: return@forEach
        if (!seen.add(key)) return@forEach
        result.add(caller to callExpr)
      }
    }
    return result
  }

  /** Maps each argument of [callExpr] to the callee's parameter name and its static origin. */
  private fun resolveArguments(
    callExpr: KtCallExpression,
    calleeParams: List<String>,
  ): List<ArgumentOrigin> {
    val origins = mutableListOf<ArgumentOrigin>()
    callExpr.valueArguments.forEachIndexed { index, arg ->
      val named = arg.getArgumentName()?.asName?.asString()
      val paramName = named ?: calleeParams.getOrNull(index) ?: return@forEachIndexed
      val expr = arg.getArgumentExpression() ?: return@forEachIndexed
      origins.add(
        ArgumentOrigin(
          paramName = paramName,
          expressionText = expr.text.replace('\n', ' ').take(80),
          origin = describeOrigin(expr),
        ),
      )
    }
    return origins
  }

  private fun describeOrigin(expr: KtExpression): String {
    return try {
      // Unwrap qualified access (e.g. viewModel.products -> resolve the selector) so property
      // origins aren't lost to a generic "expression" — the common Compose case.
      val target = (expr as? KtQualifiedExpression)?.selectorExpression ?: expr
      val ref = target as? KtReferenceExpression
        ?: (target as? KtCallExpression)?.calleeExpression as? KtReferenceExpression
      when (val resolved = ref?.let { runCatching { it.resolveMainReference() }.getOrNull() }) {
        is KtParameter -> "parameter"
        is KtProperty -> if (resolved.isVar) "var property" else "val property"
        is KtNamedFunction -> "call to ${resolved.name}()"
        else -> if (expr is KtCallExpression) "call expression" else "expression"
      }
    } catch (_: Exception) {
      "unknown"
    }
  }

  private fun lineOf(function: KtNamedFunction, offset: Int): Int {
    val document = PsiDocumentManager.getInstance(function.project)
      .getDocument(function.containingFile)
    return if (document != null && offset in 0..document.textLength) {
      document.getLineNumber(offset) + 1
    } else {
      0
    }
  }
}
