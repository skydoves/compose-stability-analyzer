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
import com.skydoves.compose.stability.idea.k2.StabilityAnalyzerK2
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Regression tests for typealias expansion in stability analysis.
 *
 * These tests cover PSI fallback behavior (K1 compatibility / K2-unavailable scenarios) and
 * K2 Analysis API behavior (when available).
 */
class StabilityAnalyzerTypeAliasTest : BasePlatformTestCase() {

  /**
   * Settings snapshot (to avoid leaking into other test classes)
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

    // Ensure analysis is enabled and avoid strong-skipping hiding bugs in function-type detection.
    state.apply {
      isStabilityCheckEnabled = true
      isStrongSkippingEnabled = false
      ignoredTypePatterns = ""
      stabilityConfigurationPath = ""
    }
  }

  /**
   * Rolling back to previous state
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
   * Ensures PSI-based analysis expands a typealias that points to a composable function type
   * (e.g., `typealias ComposableAction = @Composable () -> Unit`) and treats it as stable.
   */
  fun testPsiExpandsTypealiasToComposableFunctionType() {
    val file = myFixture.configureByText(
      "TypeAliasPsi.kt",
      """
        package test

        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        typealias ComposableAction = @Composable () -> Unit

        // Intentionally NOT annotated with @Composable so K2 analyzer returns null and PSI path is used.
        fun BottomSheet(bottomContent: ComposableAction) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "BottomSheet" }

    val info = StabilityAnalyzer.analyze(function)

    val param = info.parameters.single { it.name == "bottomContent" }
    assertEquals(ParameterStability.STABLE, param.stability)

    // Regression assertion: ensure we actually expanded the alias (the original bug skipped this).
    assertNotNull(param.reason)
    assertTrue(param.reason!!.contains("Typealias ComposableAction expands to"))
    assertTrue(param.reason!!.contains("@Composable"))
    assertTrue(param.reason!!.contains("->"))
  }

  /**
   * Ensures analysis does not overflow/loop forever when encountering circular typealiases.
   *
   * Circular typealiases are invalid Kotlin, so frontends may refuse to resolve them.
   * The key requirement is that we return a conservative RUNTIME result and never recurse.
   */
  fun testPsiDetectsCircularTypealiasAndDoesNotOverflow() {
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

    val info = StabilityAnalyzer.analyze(function)
    val param = info.parameters.single { it.name == "value" }

    assertEquals(ParameterStability.RUNTIME, param.stability)
    assertNotNull(param.reason)
    // resolution differs by frontend.
    // we just care that we don't overflow and return a conservative result.
    assertTrue(
      param.reason!!.contains("Circular typealias expansion") ||
        param.reason!!.contains("Cannot analyze in K2 mode") ||
        param.reason!!.contains("Type could not be resolved"),
    )
  }

  /**
   * Ensures PSI-based analysis expands a plain function typealias (e.g., `typealias Action = () -> Unit`)
   * and classifies it as stable.
   */
  fun testPsiExpandsTypealiasToPlainFunctionType() {
    val file = myFixture.configureByText(
      "TypeAliasPlainFunction.kt",
      """
        package test

        typealias Action = () -> Unit

        // Intentionally NOT annotated with @Composable so K2 analyzer returns null and PSI path is used.
        fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val info = StabilityAnalyzer.analyze(function)

    val param = info.parameters.single { it.name == "onClick" }
    assertEquals(ParameterStability.STABLE, param.stability)

    // Regression assertion: ensure we actually expanded the alias.
    assertNotNull(param.reason)
    assertTrue(param.reason!!.contains("Typealias Action expands to"))
    assertTrue(param.reason!!.contains("->"))
  }

  /**
   * Ensures K2 Analysis API (when available) expands typealiases for composable function types.
   *
   * In environments without K2 Analysis API support, this test becomes a no-op.
   */
  fun testK2ExpandsTypealiasToComposableFunctionTypeWhenAvailable() {
    val file = myFixture.configureByText(
      "TypeAliasK2.kt",
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

    val k2Info = StabilityAnalyzerK2.analyze(function)

    // In environments where K2 Analysis API isn't available, analyzer returns null and PSI fallback is used.
    // We keep this test non-failing in that scenario, while still asserting correctness when K2 is present.
    if (k2Info != null) {
      val param = k2Info.parameters.single { it.name == "bottomContent" }
      assertEquals(ParameterStability.STABLE, param.stability)
    }
  }
}
