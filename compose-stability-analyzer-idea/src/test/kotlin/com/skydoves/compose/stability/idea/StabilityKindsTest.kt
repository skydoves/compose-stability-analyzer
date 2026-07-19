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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import java.util.concurrent.Callable
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Locks in the Compose 2.4.0-aligned stability classification across declaration "kinds".
 *
 * The Kotlin 2.4.0 migration changed interfaces and non-final (open/abstract) classes to infer
 * [ParameterStability.UNKNOWN] (mirroring Compose 2.4.0). These tests guard the changed inference
 * branches against regressions — most importantly that a **generic interface** stays UNKNOWN and
 * is not accidentally promoted to STABLE just because its type arguments happen to be stable.
 */
class StabilityKindsTest : BasePlatformTestCase() {

  private lateinit var snapshot: SettingsSnapshot

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

  override fun tearDown() {
    try {
      snapshot.restore(StabilitySettingsState.getInstance())
    } finally {
      super.tearDown()
    }
  }

  private fun stabilities(): Map<String, ParameterStability> {
    val file = myFixture.configureByText(
      "StabilityKinds.kt",
      """
      package test

      import androidx.compose.runtime.Composable

      interface Repo
      fun interface Handler { fun handle() }
      sealed interface State
      open class OpenBase
      abstract class AbstractBase
      interface Generic<T>
      class FinalStable(val id: Int, val name: String)
      class MutableHolder(var count: Int)

      @Composable
      fun Screen(
        repo: Repo,
        handler: Handler,
        state: State,
        openBase: OpenBase,
        abstractBase: AbstractBase,
        generic: Generic<String>,
        finalStable: FinalStable,
        mutable: MutableHolder,
      ) { }
      """.trimIndent(),
    ) as KtFile

    myFixture.doHighlighting()

    val fn = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "Screen" }
    return StabilityAnalyzer.analyze(fn).parameters.associate { it.name to it.stability }
  }

  /**
   * Interfaces and non-final (open/abstract) classes — including a generic interface — must all
   * infer UNKNOWN, matching the Compose 2.4.0 compiler.
   */
  fun testInterfaceAndNonFinalClassesAreUnknown() {
    val s = stabilities()
    assertEquals("interface", ParameterStability.UNKNOWN, s["repo"])
    assertEquals("fun interface", ParameterStability.UNKNOWN, s["handler"])
    assertEquals("sealed interface", ParameterStability.UNKNOWN, s["state"])
    assertEquals("open class", ParameterStability.UNKNOWN, s["openBase"])
    assertEquals("abstract class", ParameterStability.UNKNOWN, s["abstractBase"])
    // Regression guard: a generic interface must stay UNKNOWN — it must NOT be promoted to STABLE
    // just because its type argument (String) is stable.
    assertEquals("generic interface", ParameterStability.UNKNOWN, s["generic"])
  }

  /**
   * Final classes are unaffected by the migration: a final class with stable `val` properties is
   * STABLE, and a class with a mutable `var` is UNSTABLE.
   */
  fun testFinalAndMutableClassesUnchanged() {
    val s = stabilities()
    assertEquals("final class with stable vals", ParameterStability.STABLE, s["finalStable"])
    assertEquals("class with mutable var", ParameterStability.UNSTABLE, s["mutable"])
  }

  /**
   * Issue #175: a vararg parameter compiles to an array, so it must not be reported as its
   * (often stable) element type. It is treated as an array, which is never stable.
   */
  fun testVarargParameterIsTreatedAsArray() {
    val file = myFixture.configureByText(
      "VarargStability.kt",
      """
      package test

      import androidx.compose.runtime.Composable

      @Composable
      fun VarargScreen(vararg values: Int, plain: Int) { }
      """.trimIndent(),
    ) as KtFile
    myFixture.doHighlighting()
    val fn = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "VarargScreen" }
    // Run analysis off the EDT: the K2 Analysis API is prohibited on the EDT (where tests run),
    // which would otherwise silently fall back to the PSI analyzer and bypass the K2 path.
    // waitForFuture pumps the event queue instead of hard-blocking the EDT with Future.get().
    val future = ApplicationManager.getApplication()
      .executeOnPooledThread(Callable { runReadAction { StabilityAnalyzer.analyze(fn) } })
    val params = PlatformTestUtil.waitForFuture(future).parameters.associate { it.name to it.stability }
    assertEquals("vararg parameter must be treated as an array", ParameterStability.RUNTIME, params["values"])
    assertEquals("plain Int parameter", ParameterStability.STABLE, params["plain"])
  }

  /**
   * Issue #178: computed getter-only properties store no state and must be ignored (matching the
   * compiler). A concrete class extending an unannotated abstract/open base with no destabilizing
   * state stays STABLE; an inherited `var` still makes it UNSTABLE. This keeps the IDE's K2
   * inference in sync with the compiler's `stabilityDump`.
   *
   * The analysis runs off the EDT because the K2 Analysis API is prohibited on the EDT (where
   * tests run) and would otherwise silently fall back to the PSI analyzer, bypassing the K2 path.
   */
  fun testInheritedAndComputedPropertyStability() {
    val file = myFixture.configureByText(
      "InheritedStability.kt",
      """
      package test

      import androidx.compose.runtime.Composable

      // Inherited getter-only (no backing field) property -> STABLE
      data class DataHolder(val input: String) : AbstractHolderBase()
      abstract class AbstractHolderBase { open val meta: MetaInfo? get() = null }
      interface MetaInfo

      // Own getter-only computed property -> STABLE
      data class CardData(val title: String) { val badge: MetaInfo? get() = null }

      // Inherited stored var -> UNSTABLE
      data class CounterData(val label: String) : CounterBase(0)
      open class CounterBase(var count: Int)

      // Inherited stored stable val -> STABLE
      data class LabeledData(val label: String) : LabelBase(0)
      open class LabelBase(val labelId: Int)

      // Only computed props + abstract base with no stored state -> STABLE
      class ProfileCard : BaseCard() {
        val header: String get() = "profile"
        override fun render() {}
      }
      abstract class BaseCard { abstract fun render() }

      @Composable
      fun InheritScreen(
        holder: DataHolder,
        card: CardData,
        counter: CounterData,
        labeled: LabeledData,
        profile: ProfileCard,
      ) { }
      """.trimIndent(),
    ) as KtFile
    myFixture.doHighlighting()
    val fn = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "InheritScreen" }
    // Run analysis off the EDT (K2 is prohibited on the EDT), and wait with waitForFuture, which
    // pumps the event queue instead of hard-blocking the EDT with Future.get().
    val future = ApplicationManager.getApplication()
      .executeOnPooledThread(Callable { runReadAction { StabilityAnalyzer.analyze(fn) } })
    val info = PlatformTestUtil.waitForFuture(future)
    val s = info.parameters.associate { it.name to it.stability }
    assertEquals("inherited computed property (no backing field)", ParameterStability.STABLE, s["holder"])
    assertEquals("own computed property (no backing field)", ParameterStability.STABLE, s["card"])
    assertEquals("inherited stored var", ParameterStability.UNSTABLE, s["counter"])
    assertEquals("inherited stored stable val", ParameterStability.STABLE, s["labeled"])
    assertEquals("computed-only class extending an abstract base", ParameterStability.STABLE, s["profile"])
  }
}
