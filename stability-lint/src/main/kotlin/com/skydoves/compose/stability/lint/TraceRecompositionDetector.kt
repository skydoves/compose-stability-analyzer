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
package com.skydoves.compose.stability.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod

/**
 * Lint detector that ensures @TraceRecomposition is only used on @Composable functions.
 *
 * This provides IDE-level feedback before compilation, with quick fixes to:
 * - Add @Composable annotation
 * - Remove @TraceRecomposition annotation
 */
public class TraceRecompositionDetector :
  Detector(),
  SourceCodeScanner {

  public companion object {
    private const val TRACE_RECOMPOSITION: String =
      "com.skydoves.compose.stability.runtime.TraceRecomposition"
    private const val COMPOSABLE: String = "androidx.compose.runtime.Composable"

    /**
     * Issue: @TraceRecomposition used on non-@Composable function
     */
    public val ISSUE: Issue = Issue.create(
      id = "TraceRecompositionOnNonComposable",
      briefDescription = "@TraceRecomposition must be used on @Composable functions",
      explanation = """
        The @TraceRecomposition annotation is designed to track recomposition events \
        in Jetpack Compose. It can only be applied to functions annotated with @Composable.

        To fix this:
        • Add @Composable annotation to the function, or
        • Remove @TraceRecomposition annotation

        Example:
        ```kotlin
        @TraceRecomposition  // ✗ Error: missing @Composable
        fun MyFunction() { }

        @TraceRecomposition  // ✓ Correct
        @Composable
        fun MyComposable() { }
        ```
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        TraceRecompositionDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
      ),
    )
  }

  override fun getApplicableUastTypes(): List<Class<out org.jetbrains.uast.UElement>> =
    listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // Check if method has @TraceRecomposition annotation
        val traceAnnotation: UAnnotation = node.uAnnotations.firstOrNull {
          it.qualifiedName == TRACE_RECOMPOSITION
        } ?: return

        // Check if method also has @Composable annotation
        val hasComposable = node.uAnnotations.any {
          it.qualifiedName == COMPOSABLE
        }

        if (!hasComposable) {
          reportIssue(context, node, traceAnnotation)
        }
      }
    }

  private fun reportIssue(context: JavaContext, method: UMethod, traceAnnotation: UAnnotation) {
    val methodName = method.name
    val annotationElement = traceAnnotation.sourcePsi ?: traceAnnotation.javaPsi
    val location = context.getLocation(annotationElement)

    val fixes = LintFix.create()
      .alternatives(
        LintFix.create()
          .name("Add @Composable annotation")
          .replace()
          .with("@androidx.compose.runtime.Composable\n@TraceRecomposition")
          .range(context.getLocation(annotationElement))
          .shortenNames()
          .autoFix()
          .build(),
        LintFix.create()
          .name("Remove @TraceRecomposition annotation")
          .replace()
          .with("")
          .range(context.getLocation(annotationElement))
          .autoFix()
          .build(),
      )

    context.report(
      issue = ISSUE,
      location = location,
      message = "@TraceRecomposition can only be used on @Composable functions. " +
        "Add @Composable to `$methodName` or remove @TraceRecomposition.",
      quickfixData = fixes,
    )
  }
}
