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
package com.skydoves.compose.stability.idea.doctor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.skydoves.compose.stability.idea.doctor.fixes.DoctorFixFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * One test per remember-hoist safety rule (positive and negative). The applicability check
 * must be CONSERVATIVE: when in doubt, return null (no fix offered).
 */
class RememberHoistApplicabilityTest : BasePlatformTestCase() {

  private val composableStub = """
    package androidx.compose.runtime
    annotation class Composable
  """.trimIndent()

  /**
   * Configures [code], finds the call to [calleeName] inside [callerName], and runs the
   * applicability check on its first argument.
   */
  private fun applicabilityOf(
    code: String,
    callerName: String = "Caller",
    calleeName: String = "Child",
  ): List<String>? {
    myFixture.addFileToProject("Composable.kt", composableStub)
    val file = myFixture.configureByText("Test.kt", code) as KtFile
    val caller = file.collectDescendantsOfType<KtNamedFunction>()
      .first { it.name == callerName }
    val call = caller.collectDescendantsOfType<KtCallExpression>()
      .first { it.calleeExpression?.text == calleeName }
    val arg = call.valueArguments.first().getArgumentExpression()!!
    return DoctorFixFactory.rememberHoistApplicability(caller, arg)
  }

  fun testApplicable_callWithParamInput() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(query: String) {
        Child(mapToUi(query))
      }
      """.trimIndent(),
    )
    assertEquals(listOf("query"), keys)
  }

  fun testApplicable_localValInput() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(raw: String) {
        val trimmed = raw.trim()
        Child(mapToUi(trimmed))
      }
      """.trimIndent(),
    )
    assertEquals(listOf("trimmed"), keys)
  }

  fun testRejected_insideLambda() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      fun row(content: () -> Unit) { content() }
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(query: String) {
        row {
          Child(mapToUi(query))
        }
      }
      """.trimIndent(),
    )
    assertNull("argument inside a lambda must be rejected (rule 1)", keys)
  }

  fun testRejected_bareReference() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(ui: Ui) {
        Child(ui)
      }
      """.trimIndent(),
    )
    assertNull("bare reference is useless to hoist (rule 2)", keys)
  }

  fun testRejected_constant() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      @Composable fun Child(count: Int) {}
      @Composable fun Caller() {
        Child(42)
      }
      """.trimIndent(),
    )
    assertNull("constants must be rejected (rule 2)", keys)
  }

  fun testRejected_lambdaArgument() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      @Composable fun Child(onClick: () -> Unit) {}
      @Composable fun Caller(query: String) {
        Child({ println(query) })
      }
      """.trimIndent(),
    )
    assertNull("lambda arguments must be rejected (rule 2)", keys)
  }

  fun testRejected_alreadyRemembered() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      fun <T> remember(calc: () -> T): T = calc()
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(query: String) {
        Child(remember { mapToUi(query) })
      }
      """.trimIndent(),
    )
    assertNull("already-remembered expression must be rejected (rule 2)", keys)
  }

  fun testRejected_composableCallInside() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      @Composable fun produceUi(q: String): Ui = Ui(q)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(query: String) {
        Child(produceUi(query))
      }
      """.trimIndent(),
    )
    assertNull("composable call inside remember is illegal (rule 3)", keys)
  }

  fun testRejected_varInput() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      var globalQuery: String = ""
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller() {
        Child(mapToUi(globalQuery))
      }
      """.trimIndent(),
    )
    assertNull("var inputs must be rejected (rule 4)", keys)
  }

  fun testRejected_customGetterInput() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      val nowQuery: String get() = System.nanoTime().toString()
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller() {
        Child(mapToUi(nowQuery))
      }
      """.trimIndent(),
    )
    assertNull("custom-getter inputs must be rejected (rule 4)", keys)
  }

  fun testApplicable_topLevelValIsNotAKey() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(q: String) = Ui(q)
      val defaultQuery: String = "default"
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller() {
        Child(mapToUi(defaultQuery))
      }
      """.trimIndent(),
    )
    // Stable global input: hoistable with NO keys (remember { ... }).
    assertEquals(emptyList<String>(), keys)
  }

  fun testRejected_thisUsage() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      @Composable fun Child(ui: Ui) {}
      class Screen(val q: String) {
        fun toUi() = Ui(q)
        @Composable fun Caller() {
          Child(this.toUi())
        }
      }
      """.trimIndent(),
    )
    assertNull("`this` usage must be rejected (rule 4)", keys)
  }

  fun testApplicable_multipleKeysDeduplicated() {
    val keys = applicabilityOf(
      """
      import androidx.compose.runtime.Composable
      data class Ui(val v: String)
      fun mapToUi(a: String, b: Int, c: String) = Ui(a + b + c)
      @Composable fun Child(ui: Ui) {}
      @Composable fun Caller(query: String, count: Int) {
        Child(mapToUi(query, count, query))
      }
      """.trimIndent(),
    )
    assertEquals(listOf("query", "count"), keys)
  }
}
