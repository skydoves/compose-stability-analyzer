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
 * What we are testing:
 * - The stability analyzer can correctly classify parameters whose types are defined via `typealias`.
 * - This must work for both:
 *   1) K2 Analysis API path (preferred in production when available), and
 *   2) PSI/K1 fallback path (used when K2 is unavailable or cannot analyze a particular case).
 *
 * Why these tests exist:
 * - Kotlin typealiases can hide the actual shape of a type at the call-site.
 *   Example: `ComposableAction` does not contain `->` text, but expands to a function type.
 * - The analyzer must expand aliases before applying heuristics like "is this a function type?"
 * - We also test failure scenarios where reference resolution fails and the analyzer must
 *   use its "fallback scan" logic.
 */
class StabilityAnalyzerTypeAliasTest : BasePlatformTestCase() {

  /**
   * Settings snapshot (to avoid leaking into other test classes).
   */
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
    snapshot = SettingsSnapshot.fromState(state)

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
      snapshot.restore(state)
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

        // Local stub annotation; we don't need real Compose here because this is a PSI test.
        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        // The alias hides the real function type shape from the call-site.
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

        // Declared but unused; included to match typical test scaffolding patterns.
        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        // Basic function type alias.
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
   * Guard against a subtle false-positive:
   * Generic types that *contain* a function type argument are NOT themselves function types.
   *
   * Example:
   *   Holder<() -> Unit>
   *
   * If the analyzer used an overly-broad "string contains ->" heuristic, it might treat this
   * container as a function type and mark it STABLE, skipping deeper class analysis.
   *
   * We want the analyzer to treat it as a class, inspect its properties, and conclude UNSTABLE
   * because `Holder` has a mutable `var`.
   */
  fun testGenericContainerWithFunctionTypeArgumentIsNotFunctionType() {
    val file = myFixture.configureByText(
      "GenericContainsFunctionType.kt",
      """
        package test

        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable

        // Mutable container -> should be unstable when analyzed as a class.
        class Holder<T>(var value: T)

        // Alias contains a function type argument, but the resulting type is still `Holder<...>`.
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

  /**
   * Typealias lookup must work even when the alias is declared in a different file.
   *
   * This exercises "file/project scope" logic rather than only "same file" scanning.
   * (In a real codebase, aliases are commonly stored in a shared `Types.kt` file.)
   */
  fun testTypealiasDefinedInOtherFileIsHandled() {
    // Put the alias into a separate project file.
    myFixture.addFileToProject(
      "test/TypeAliases.kt",
      """
      package test

      typealias Action = () -> Unit
      """.trimIndent(),
    )

    // Use the alias from another file.
    val file = myFixture.configureByText(
      "UseAlias.kt",
      """
      package test

      fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val param = analyzeParam(function, "onClick")
    assertEquals(ParameterStability.STABLE, param.stability)
  }

  /**
   * Typealiases nested inside objects should be resolvable via **qualified** access:
   *   Aliases.Action
   *
   * This ensures we do not only support simple-name lookup.
   */
  fun testTypealiasInsideObjectIsHandled_Qualified() {
    val file = myFixture.configureByText(
      "NestedAliasQualified.kt",
      """
      package test

      object Aliases {
        typealias Action = () -> Unit
      }

      fun Button(onClick: Aliases.Action) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val param = analyzeParam(function, "onClick")
    assertEquals(ParameterStability.STABLE, param.stability)
  }

  /**
   * Typealiases nested inside objects should also be resolvable via **import**:
   *   import test.Aliases.Action
   *
   * This tests the common Kotlin style where nested aliases are imported for readability.
   */
  fun testTypealiasInsideObjectIsHandled_Imported() {
    val file = myFixture.configureByText(
      "NestedAliasImported.kt",
      """
      package test

      import test.Aliases.Action

      object Aliases {
        typealias Action = () -> Unit
      }

      fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val function = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val param = analyzeParam(function, "onClick")
    assertEquals(ParameterStability.STABLE, param.stability)
  }

  /**
   * When reference resolution fails, the analyzer must still:
   * - locate the typealias,
   * - expand it to the underlying function type,
   * - and classify it as STABLE.
   *
   * This case covers a @Composable function type alias.
   */
  fun testFallbackScanIsUsedWhenResolveMainReferenceFails_ComposableAlias() {
    val file = myFixture.configureByText(
      "FallbackComposableAlias.kt",
      """
      package test

      @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
      annotation class Composable

      typealias ComposableAction = @Composable () -> Unit

      @Composable
      fun Screen(onClick: ComposableAction) { }
      """.trimIndent(),
    ) as KtFile

    val fn = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "Screen" }

    val (info, callCount) = StabilityAnalyzerTestHelpers.withForcedResolveFailure {
      // Must force PSI path; otherwise K2 may analyze everything without ever calling
      // resolveMainReference(), making the test meaningless.
      StabilityAnalyzer.analyzePsiForTest(fn)
    }

    // Prove we hit the forced-failure path.
    assertTrue("Expected resolveMainReferenceOverride to be called", callCount > 0)

    val param = info.parameters.single { it.name == "onClick" }
    assertEquals(ParameterStability.STABLE, param.stability)

    // Prove expansion happened and the analyzer "saw" a function type.
    val reason = param.reason.orEmpty()
    assertTrue(reason.contains("Typealias ComposableAction expands to"))
    assertTrue(reason.contains("@Composable"))
    assertTrue(reason.contains("->"))
  }

  /**
   * Same as the previous test, but for a plain function typealias.
   *
   * This ensures fallback scanning isn't coupled to @Composable annotations.
   */
  fun testFallbackScanIsUsedWhenResolveMainReferenceFails_PlainFunctionAlias() {
    val file = myFixture.configureByText(
      "FallbackPlainAlias.kt",
      """
      package test

      typealias Action = () -> Unit

      fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    val fn = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "Button" }

    val (info, callCount) = StabilityAnalyzerTestHelpers.withForcedResolveFailure {
      StabilityAnalyzer.analyzePsiForTest(fn)
    }

    assertTrue("Expected resolveMainReferenceOverride to be called", callCount > 0)

    val param = info.parameters.single { it.name == "onClick" }
    assertEquals(ParameterStability.STABLE, param.stability)

    val reason = param.reason.orEmpty()
    assertTrue(reason.contains("Typealias Action expands to"))
    assertTrue(reason.contains("->"))
  }

  /**
   * Fallback scan must handle nested aliases imported into scope:
   * - The alias is declared as `Aliases.Action`
   * - and imported as `import test.Aliases.Action`
   * - then used unqualified as `Action`.
   *
   * This is a common case where simple-name matching can be ambiguous,
   * so the analyzer's fallback needs to be robust.
   */
  fun testFallbackScanIsUsedWhenResolveMainReferenceFails_NestedAliasImported() {
    val file = myFixture.configureByText(
      "FallbackNestedAliasImported.kt",
      """
      package test

      object Aliases {
        typealias Action = () -> Unit
      }

      import test.Aliases.Action

      fun Button(onClick: Action) { }
      """.trimIndent(),
    ) as KtFile

    val fn = file.declarations.filterIsInstance<KtNamedFunction>()
      .single { it.name == "Button" }

    val (info, callCount) = StabilityAnalyzerTestHelpers.withForcedResolveFailure {
      StabilityAnalyzer.analyzePsiForTest(fn)
    }

    assertTrue("Expected resolveMainReferenceOverride to be called", callCount > 0)

    val param = info.parameters.single { it.name == "onClick" }
    assertEquals(ParameterStability.STABLE, param.stability)

    val reason = param.reason.orEmpty()
    assertTrue(reason.contains("Typealias Action expands to"))
    assertTrue(reason.contains("() -> Unit"))
  }
}
