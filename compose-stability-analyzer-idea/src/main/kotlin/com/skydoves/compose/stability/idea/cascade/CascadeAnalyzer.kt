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
package com.skydoves.compose.stability.idea.cascade

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.skydoves.compose.stability.idea.StabilityAnalyzer
import com.skydoves.compose.stability.idea.StabilityConstants
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * Analyzes the recomposition cascade by walking the call graph
 * from a root composable function to find all downstream composables
 * that would be affected by recomposition.
 */
internal object CascadeAnalyzer {

  private const val MAX_DEPTH = 10

  /**
   * Analyzes the recomposition cascade starting from the given root function.
   *
   * @param rootFunction the @Composable function to start analysis from
   * @param indicator progress indicator for background task reporting
   * @return the cascade analysis result
   */
  internal fun analyze(
    rootFunction: KtNamedFunction,
    indicator: ProgressIndicator,
  ): CascadeResult {
    return ReadAction.compute<CascadeResult, Exception> {
      indicator.text = "Analyzing recomposition cascade..."
      val visitedFqNames = mutableSetOf<String>()
      val root = buildCascadeNode(rootFunction, 0, visitedFqNames, indicator)
      val summary = computeSummary(root)
      CascadeResult(root = root, summary = summary)
    }
  }

  /**
   * Recursively builds a cascade node for the given function.
   */
  private fun buildCascadeNode(
    function: KtNamedFunction,
    depth: Int,
    visitedFqNames: MutableSet<String>,
    indicator: ProgressIndicator,
  ): CascadeNode {
    val fqName = function.fqName?.asString() ?: function.name ?: "unknown"
    val filePath = function.containingFile.virtualFile?.path ?: ""
    val line = getLineNumber(function)

    // Analyze stability of this function
    val stabilityInfo = try {
      StabilityAnalyzer.analyze(function)
    } catch (e: Exception) {
      ComposableStabilityInfo(
        name = function.name ?: StabilityConstants.Strings.UNKNOWN,
        fqName = fqName,
        isRestartable = true,
        isSkippable = false,
        isReadonly = false,
        parameters = emptyList(),
      )
    }

    indicator.text2 = "Analyzing ${stabilityInfo.name}..."

    // Check depth limit
    if (depth >= MAX_DEPTH) {
      return CascadeNode(
        stabilityInfo = stabilityInfo,
        filePath = filePath,
        line = line,
        depth = depth,
        children = emptyList(),
        isTruncated = true,
        truncationReason = "Maximum depth ($MAX_DEPTH) reached",
      )
    }

    // Cycle detection
    if (fqName in visitedFqNames) {
      return CascadeNode(
        stabilityInfo = stabilityInfo,
        filePath = filePath,
        line = line,
        depth = depth,
        children = emptyList(),
        isTruncated = true,
        truncationReason = "Cycle detected: $fqName",
      )
    }

    // Mark as visited for this branch
    visitedFqNames.add(fqName)

    // Find and recursively analyze child composable calls
    val callees = findComposableCallees(function)
    val children = callees.map { callee ->
      buildCascadeNode(callee, depth + 1, visitedFqNames, indicator)
    }

    // Remove from visited so same composable can appear in different branches
    visitedFqNames.remove(fqName)

    return CascadeNode(
      stabilityInfo = stabilityInfo,
      filePath = filePath,
      line = line,
      depth = depth,
      children = children,
    )
  }

  /**
   * Finds all @Composable functions called within the body of the given function.
   * Only resolves to project-source KtNamedFunction (library compiled elements are excluded).
   */
  private fun findComposableCallees(function: KtNamedFunction): List<KtNamedFunction> {
    val body = function.bodyBlockExpression ?: function.bodyExpression ?: return emptyList()

    val callExpressions = PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java)
    val seenFqNames = mutableSetOf<String>()
    val result = mutableListOf<KtNamedFunction>()

    for (callExpr in callExpressions) {
      val callee = callExpr.calleeExpression as? KtReferenceExpression ?: continue
      val resolved = try {
        callee.resolveMainReference()
      } catch (e: Exception) {
        null
      }

      val namedFunction = resolved as? KtNamedFunction ?: continue
      if (!namedFunction.isComposable()) continue

      // Deduplicate by fqName within a single body
      val calleeFqName = namedFunction.fqName?.asString() ?: namedFunction.name ?: continue
      if (calleeFqName in seenFqNames) continue
      seenFqNames.add(calleeFqName)

      result.add(namedFunction)
    }

    return result
  }

  /**
   * Computes summary statistics by walking the cascade tree.
   */
  private fun computeSummary(root: CascadeNode): CascadeSummary {
    var totalCount = 0
    var skippableCount = 0
    var unskippableCount = 0
    var maxDepth = 0
    var hasTruncatedBranches = false

    fun walk(node: CascadeNode) {
      totalCount++
      if (node.stabilityInfo.isSkippable) {
        skippableCount++
      } else {
        unskippableCount++
      }
      if (node.depth > maxDepth) {
        maxDepth = node.depth
      }
      if (node.isTruncated) {
        hasTruncatedBranches = true
      }
      node.children.forEach { walk(it) }
    }

    walk(root)

    return CascadeSummary(
      totalCount = totalCount,
      skippableCount = skippableCount,
      unskippableCount = unskippableCount,
      maxDepth = maxDepth,
      hasTruncatedBranches = hasTruncatedBranches,
    )
  }

  private fun getLineNumber(function: KtNamedFunction): Int {
    val document = PsiDocumentManager.getInstance(function.project)
      .getDocument(function.containingFile)
    return if (document != null) {
      document.getLineNumber(function.textOffset) + 1
    } else {
      0
    }
  }
}
