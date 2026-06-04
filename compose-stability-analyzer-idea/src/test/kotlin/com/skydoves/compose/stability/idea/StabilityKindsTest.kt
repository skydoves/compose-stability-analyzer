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
}
