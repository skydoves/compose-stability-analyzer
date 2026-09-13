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
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import java.util.concurrent.Callable
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Issue #184: a composable is only `restartable` when the Compose compiler wraps it in a restart
 * group. `@NonRestartableComposable`, `@ExplicitGroupsComposable`, `inline` functions, composables
 * returning a non-`Unit` value, `open` members of non-final classes, and local composables get no
 * restart group, so they are neither restartable nor skippable. `@NonSkippableComposable` keeps its
 * restart group (restartable) but opts out of skipping.
 *
 * `@ReadOnlyComposable` is NOT one of the rules: verified against the Compose compiler's own
 * metrics, a `Unit`-returning read-only composable is `restartable skippable`. The familiar
 * read-only composables are non-restartable because they return a value.
 *
 * These verdicts must match the compiler's `stabilityDump`, so the tests assert on both analyzer
 * paths the IDE uses:
 * - the PSI analyzer (exercised on the EDT, where the K2 Analysis API is prohibited), and
 * - the K2 Analysis API analyzer (exercised off the EDT).
 */
class StabilityRestartableTest : BasePlatformTestCase() {

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

  private fun configureFixture(): KtFile = myFixture.configureByText(
    "Restartability.kt",
    """
    package test

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.ExplicitGroupsComposable
    import androidx.compose.runtime.NonRestartableComposable
    import androidx.compose.runtime.NonSkippableComposable
    import androidx.compose.runtime.ReadOnlyComposable

    @Composable
    fun Normal(text: String) { }

    @Composable
    @NonRestartableComposable
    fun NonRestartable(text: String) { }

    @Composable
    @NonSkippableComposable
    fun NonSkippable(text: String) { }

    @Composable
    @ReadOnlyComposable
    fun ReadOnlyUnit() { }

    @Composable
    @ExplicitGroupsComposable
    fun ExplicitGroups(text: String) { }

    @Composable
    fun NonUnitReturn(): Int = 42

    @Composable
    fun NonUnitInferred() = 42

    @Composable
    inline fun InlineWrapper(content: @Composable () -> Unit) { content() }

    open class Host {
      @Composable
      open fun OpenMember(text: String) { }

      @Composable
      fun FinalMember(text: String) { }
    }

    interface Screen {
      @Composable
      fun Content(text: String) { }
    }

    abstract class AbstractHost {
      @Composable
      abstract fun AbstractMember(text: String)
    }
    """.trimIndent(),
  ) as KtFile

  private fun KtFile.function(name: String): KtNamedFunction =
    declarations.filterIsInstance<KtNamedFunction>().single { it.name == name }

  private fun KtFile.member(className: String, name: String): KtNamedFunction {
    val klass = declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtClass>()
      .single { it.name == className }
    return klass.declarations.filterIsInstance<KtNamedFunction>().single { it.name == name }
  }

  private fun assertVerdicts(analyze: (KtNamedFunction) -> ComposableStabilityInfo) {
    val file = configureFixture()
    myFixture.doHighlighting()

    val normal = analyze(file.function("Normal"))
    assertTrue("a normal composable is restartable", normal.isRestartable)
    assertTrue("a composable with only stable params is skippable", normal.isSkippable)

    val nonRestartable = analyze(file.function("NonRestartable"))
    assertFalse("@NonRestartableComposable is not restartable", nonRestartable.isRestartable)
    assertFalse("a non-restartable composable can never be skipped", nonRestartable.isSkippable)

    val nonSkippable = analyze(file.function("NonSkippable"))
    assertTrue("@NonSkippableComposable keeps its restart group", nonSkippable.isRestartable)
    assertFalse("@NonSkippableComposable opts out of skipping", nonSkippable.isSkippable)

    // Verified against the Compose compiler's metrics: a Unit-returning read-only composable is
    // reported `restartable skippable`, so the annotation alone must not clear restartability.
    val readOnlyUnit = analyze(file.function("ReadOnlyUnit"))
    assertTrue("a Unit-returning @ReadOnlyComposable is restartable", readOnlyUnit.isRestartable)
    assertTrue("a Unit-returning @ReadOnlyComposable is skippable", readOnlyUnit.isSkippable)

    val explicitGroups = analyze(file.function("ExplicitGroups"))
    assertFalse("@ExplicitGroupsComposable is not restartable", explicitGroups.isRestartable)
    assertFalse("an @ExplicitGroupsComposable composable can never be skipped", explicitGroups.isSkippable)

    val nonUnit = analyze(file.function("NonUnitReturn"))
    assertFalse("a non-Unit-returning composable is not restartable", nonUnit.isRestartable)
    assertFalse("a non-Unit-returning composable can never be skipped", nonUnit.isSkippable)

    val inlineWrapper = analyze(file.function("InlineWrapper"))
    assertFalse("an inline composable is not restartable", inlineWrapper.isRestartable)
    assertFalse("an inline composable can never be skipped", inlineWrapper.isSkippable)

    // Restart logic makes a virtual call, so open members of a non-final class get no restart
    // group (b/329477544), while a final member of the same class keeps one.
    val openMember = analyze(file.member("Host", "OpenMember"))
    assertFalse("an open member composable is not restartable", openMember.isRestartable)
    assertFalse("an open member composable can never be skipped", openMember.isSkippable)

    // setUp() runs this class with strong skipping OFF, where skippability also depends on the
    // dispatch receiver (`Host` is open, so it is not STABLE). Restartability is the property under
    // test here; strong-skipping behaviour is covered by testStrongSkippingDoesNotRescueNonRestartable.
    val finalMember = analyze(file.member("Host", "FinalMember"))
    assertTrue("a final member of an open class is restartable", finalMember.isRestartable)

    val interfaceContent = analyze(file.member("Screen", "Content"))
    assertFalse("an interface method with a body is open, so not restartable", interfaceContent.isRestartable)
    assertFalse("an interface method with a body can never be skipped", interfaceContent.isSkippable)

    val abstractMember = analyze(file.member("AbstractHost", "AbstractMember"))
    assertFalse("an abstract composable has no body and is not restartable", abstractMember.isRestartable)
    assertFalse("an abstract composable can never be skipped", abstractMember.isSkippable)
  }

  /**
   * PSI analyzer path (used when K2 is unavailable). Running on the EDT forces this path because the
   * K2 Analysis API is prohibited on the EDT and [StabilityAnalyzer.analyze] falls back to PSI.
   */
  fun testRestartabilityOnPsiPath() {
    assertVerdicts { StabilityAnalyzer.analyze(it) }
  }

  /**
   * K2 Analysis API path (preferred in production). Analysis must run off the EDT, otherwise the K2
   * API is prohibited and would silently fall back to PSI, bypassing the K2 path. [waitForFuture]
   * pumps the event queue instead of hard-blocking the EDT with `Future.get()`.
   */
  fun testRestartabilityOnK2Path() {
    assertVerdicts { fn ->
      val future = ApplicationManager.getApplication()
        .executeOnPooledThread(Callable { runReadAction { StabilityAnalyzer.analyze(fn) } })
      PlatformTestUtil.waitForFuture(future)
    }
  }

  /**
   * A composable with an expression body that infers a non-`Unit` type (no explicit return type) is
   * also non-restartable. The K2 analyzer resolves the inferred type directly, which is the path
   * that runs in a K2 project. The PSI fallback resolves it via the K1 descriptor when running in
   * K1 mode; that branch cannot be exercised here because the BasePlatformTestCase harness resolves
   * with K2, where the K1 descriptor API returns nothing (issue #184).
   */
  fun testInferredNonUnitReturnIsNotRestartable() {
    val file = configureFixture()
    myFixture.doHighlighting()
    val fn = file.function("NonUnitInferred")

    val future = ApplicationManager.getApplication()
      .executeOnPooledThread(Callable { runReadAction { StabilityAnalyzer.analyze(fn) } })
    val k2 = PlatformTestUtil.waitForFuture(future)
    assertFalse("inferred non-Unit return is not restartable (K2)", k2.isRestartable)
    assertFalse("inferred non-Unit return is not skippable (K2)", k2.isSkippable)
  }

  /**
   * Strong-skipping mode makes composables with unstable parameters skippable, but it must NOT
   * rescue a composable that has no restart group (non-restartable) or that explicitly opted out
   * (`@NonSkippableComposable`) — those stay non-skippable (issue #184). Verified on both paths.
   */
  fun testStrongSkippingDoesNotRescueNonRestartable() {
    StabilitySettingsState.getInstance().isStrongSkippingEnabled = true
    val file = configureFixture()
    myFixture.doHighlighting()

    val paths = listOf<(KtNamedFunction) -> ComposableStabilityInfo>(
      { StabilityAnalyzer.analyze(it) }, // PSI path (EDT)
      { fn -> // K2 path (off-EDT)
        val future = ApplicationManager.getApplication()
          .executeOnPooledThread(Callable { runReadAction { StabilityAnalyzer.analyze(fn) } })
        PlatformTestUtil.waitForFuture(future)
      },
    )

    for (analyze in paths) {
      assertFalse("non-restartable stays non-skippable", analyze(file.function("NonRestartable")).isSkippable)
      assertFalse("@NonSkippableComposable stays non-skippable", analyze(file.function("NonSkippable")).isSkippable)
      assertFalse("inline stays non-skippable", analyze(file.function("InlineWrapper")).isSkippable)
      assertFalse("an open member stays non-skippable", analyze(file.member("Host", "OpenMember")).isSkippable)
      assertTrue("strong skipping makes a normal composable skippable", analyze(file.function("Normal")).isSkippable)
    }
  }
}
