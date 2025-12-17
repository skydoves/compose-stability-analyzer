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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [TraceRecompositionDetector].
 */
@RunWith(JUnit4::class)
class TraceRecompositionDetectorTest : LintDetectorTest() {

  override fun getDetector(): Detector = TraceRecompositionDetector()

  override fun getIssues(): List<Issue> = listOf(TraceRecompositionDetector.ISSUE)

  private val traceRecompositionStub: TestFile = kotlin(
    """
    package com.skydoves.compose.stability.runtime

    annotation class TraceRecomposition(
        val tag: String = "",
        val threshold: Int = 1
    )
    """,
  ).indented()

  private val composableStub: TestFile = kotlin(
    """
    package androidx.compose.runtime

    annotation class Composable
    """,
  ).indented()

  @Test
  fun `error when TraceRecomposition used on non-Composable function`() {
    lint()
      .files(
        traceRecompositionStub,
        composableStub,
        kotlin(
          """
          package com.example

          import com.skydoves.compose.stability.runtime.TraceRecomposition

          @TraceRecomposition
          fun NotComposable() {
              // This should trigger an error
          }
          """,
        ).indented(),
      )
      .allowMissingSdk()
      .run()
      .expect(
        """
        src/com/example/test.kt:5: Error: @TraceRecomposition can only be used on @Composable functions. Add @Composable to NotComposable or remove @TraceRecomposition. [TraceRecompositionOnNonComposable]
        @TraceRecomposition
        ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent(),
      )
  }

  @Test
  fun `no error when TraceRecomposition used on Composable function`() {
    lint()
      .files(
        traceRecompositionStub,
        composableStub,
        kotlin(
          """
          package com.example

          import androidx.compose.runtime.Composable
          import com.skydoves.compose.stability.runtime.TraceRecomposition

          @TraceRecomposition
          @Composable
          fun ValidComposable() {
              // This is correct usage
          }
          """,
        ).indented(),
      )
      .allowMissingSdk()
      .run()
      .expectClean()
  }

  @Test
  fun `no error when TraceRecomposition used with tag parameter`() {
    lint()
      .files(
        traceRecompositionStub,
        composableStub,
        kotlin(
          """
          package com.example

          import androidx.compose.runtime.Composable
          import com.skydoves.compose.stability.runtime.TraceRecomposition

          @TraceRecomposition(tag = "my-screen", threshold = 3)
          @Composable
          fun TrackedScreen() {
              // Valid usage with parameters
          }
          """,
        ).indented(),
      )
      .allowMissingSdk()
      .run()
      .expectClean()
  }

  @Test
  fun `no error when function has no TraceRecomposition annotation`() {
    lint()
      .files(
        traceRecompositionStub,
        composableStub,
        kotlin(
          """
          package com.example

          fun NormalFunction() {
              // No annotation, no error
          }
          """,
        ).indented(),
      )
      .allowMissingSdk()
      .run()
      .expectClean()
  }
}
