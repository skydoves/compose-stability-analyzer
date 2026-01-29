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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import com.skydoves.compose.stability.runtime.ParameterStabilityInfo
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Regression tests for typealias expansion in stability analysis.
 *
 * The production analyzer automatically prefers K2 Analysis API when available and applicable,
 * and falls back to PSI/K1. Tests should be written to pass in either environment.
 */
class StabilityAnalyzerTypeAliasTest : BasePlatformTestCase() {

  /**
   * Settings snapshot (to avoid leaking into other test classes).
   */
  private data class SettingsSnapshot(
    val isStabilityCheckEnabled: Boolean,
    val isStrongSkippingEnabled: Boolean,
    val ignoredTypePatterns: String,
    val stabilityConfigurationPath: String,
  )

  private lateinit var snapshot: SettingsSnapshot

  /**
   * Initializes plugin settings for analysis tests.
   *
   * We explicitly disable strong-skipping so stability classification for function types
   * (including typealias-expanded function types) is exercised.
   */
  override fun setUp() {
    super.setUp()

    val state = StabilitySettingsState.getInstance()
    snapshot = SettingsSnapshot(
      isStabilityCheckEnabled = state.isStabilityCheckEnabled,
      isStrongSkippingEnabled = state.isStrongSkippingEnabled,
      ignoredTypePatterns = state.ignoredTypePatterns,
      stabilityConfigurationPath = state.stabilityConfigurationPath,
    )

    state.apply {
      isStabilityCheckEnabled = true
      isStrongSkippingEnabled = false
      ignoredTypePatterns = ""
      stabilityConfigurationPath = ""
    }
  }

  /**
   * Restores settings to avoid cross-test leakage.
   */
  override fun tearDown() {
    try {
      val state = StabilitySettingsState.getInstance()
      state.apply {
        isStabilityCheckEnabled = snapshot.isStabilityCheckEnabled
        isStrongSkippingEnabled = snapshot.isStrongSkippingEnabled
        ignoredTypePatterns = snapshot.ignoredTypePatterns
        stabilityConfigurationPath = snapshot.stabilityConfigurationPath
      }
    } finally {
      super.tearDown()
    }
  }

  /**
   * Analyzes the given function using the production analyzer (K2 when available; otherwise PSI/K1),
   * then returns the requested parameter.
   */
  private fun analyzeParam(function: KtNamedFunction, paramName: String): ParameterStabilityInfo {
    val info = StabilityAnalyzer.analyze(function)
    return info.parameters.single { it.name == paramName }
  }

  /**
   * Ensures a typealias that points to a composable function type is classified as stable.
   */
  fun testTypealiasToComposableFunctionTypeIsStable() {
    val file = myFixture.configureByText(
      "TypeAliasComposableFunction.kt",
      """
        package test

        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        typealias ComposableAction = @Composable () -> Unit

        @Composable
        fun BottomSheet(bottomContent: ComposableAction) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "BottomSheet" }

    val param = analyzeParam(function, "bottomContent")
    assertEquals(ParameterStability.STABLE, param.stability)

    // Optional regression assertion for PSI path: if PSI produced the "Typealias expands" reason,
    // ensure we really expanded to a function type.
    val reason = param.reason.orEmpty()
    if (reason.contains("Typealias ComposableAction expands to")) {
      assertTrue(reason.contains("@Composable"))
      assertTrue(reason.contains("->"))
    }
  }

  /**
   * Ensures a plain function typealias (e.g., `typealias Action = () -> Unit`) is classified as stable.
   */
  fun testTypealiasToPlainFunctionTypeIsStable() {
    val file = myFixture.configureByText(
      "TypeAliasPlainFunction.kt",
      """
        package test

        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        typealias Action = () -> Unit

        @Composable
        fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val param = analyzeParam(function, "onClick")
    assertEquals(ParameterStability.STABLE, param.stability)

    // PSI-only optional check for alias expansion evidence
    val reason = param.reason.orEmpty()
    if (reason.contains("Typealias Action expands to")) {
      assertTrue(reason.contains("->"))
    }
  }

  /**
   * Ensures analysis does not overflow/loop forever when encountering circular typealiases.
   *
   * Circular typealiases are invalid Kotlin, so frontends may refuse to resolve them.
   * The key requirement is that we return a conservative RUNTIME result and never recurse.
   */
  fun testCircularTypealiasDoesNotOverflow() {
    val file = myFixture.configureByText(
      "TypeAliasCircular.kt",
      """
        package test

        typealias A = B
        typealias B = A

        fun UsesAlias(value: A) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "UsesAlias" }

    val param = analyzeParam(function, "value")

    assertEquals(ParameterStability.RUNTIME, param.stability)
    assertNotNull(param.reason)

    val reason = param.reason!!
    assertTrue(
      reason.contains("Circular typealias expansion", ignoreCase = true) ||
        reason.contains("Cannot analyze", ignoreCase = true) ||
        reason.contains("could not be resolved", ignoreCase = true),
    )
  }

  /**
   * Ensures we do NOT misclassify generic containers that *contain* a function type argument
   * as function types themselves.
   *
   * Regression for overly-broad "->" string detection:
   *   Holder<() -> Unit> contains "->" but is NOT a function type.
   */
  fun testGenericContainerWithFunctionTypeArgumentIsNotFunctionType() {
    val file = myFixture.configureByText(
      "GenericContainsFunctionType.kt",
      """
        package test

        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        class Holder<T>(var value: T)

        typealias CallbackHolder = Holder<() -> Unit>

        @Composable
        fun Foo(callbackHolder: CallbackHolder) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Foo" }

    val param = analyzeParam(function, "callbackHolder")

    // Holder has a mutable 'var' property -> should be UNSTABLE if we correctly analyze the class,
    // and not incorrectly short-circuit as a function type just because nested args contain "->".
    assertEquals(ParameterStability.UNSTABLE, param.stability)
  }
}
