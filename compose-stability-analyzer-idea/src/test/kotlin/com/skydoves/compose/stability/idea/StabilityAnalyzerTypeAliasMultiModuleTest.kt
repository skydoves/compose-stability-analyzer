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

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.nio.file.Files
import java.nio.file.Path

/**
 * Multi-module regression tests for typealias expansion.
 *
 * What we are testing (and why this file exists):
 * - Most tests use [com.intellij.testFramework.fixtures.BasePlatformTestCase], which is ideal for
 *   single-module PSI tests.
 * - Cross-module type resolution behaves differently (and is easy to accidentally not cover),
 *   so here we create *real IntelliJ modules* and wire dependencies explicitly.
 *
 * Why disk-backed modules (instead of purely in-memory fixtures)?
 * - Some IntelliJ project/model behaviors (module roots, VFS refresh, indexing, resolve)
 *   are more reliable and closer to real IDE behavior when the files exist on disk.
 *
 * Two key scenarios covered:
 * 1) Normal cross-module resolution:
 *    - `app` depends on `lib`
 *    - `lib` defines a typealias
 *    - `app` uses it and stability analysis must expand it.
 *
 * 2) Cross-module resolution when reference resolution fails:
 *    - We force `resolveMainReference()` to fail
 *    - The analyzer must fall back to its scanning strategy
 *    - We still expect alias expansion to happen and stability classification to remain correct.
 */
class StabilityAnalyzerTypeAliasMultiModuleTest : UsefulTestCase() {

  /**
   * IntelliJ fixture that owns the test project and PSI infrastructure.
   *
   * We use [CodeInsightTestFixture] rather than BasePlatformTestCase because we need to manually
   * create multiple modules and source roots.
   */
  private lateinit var fixture: CodeInsightTestFixture

  /**
   * Snapshot plugin settings so changes made in this test class do not affect other tests.
   *
   * Settings are global singletons in the IDE process; failing to restore them is a common source
   * of flaky or order-dependent tests.
   */
  private lateinit var snapshot: SettingsSnapshot

  /**
   * Track created modules so we can dispose them in tearDown() while the project is still alive.
   *
   * Disposing after fixture teardown is risky because the project model may already be disposed.
   */
  private val createdModules = mutableListOf<Module>()

  /**
   * Track temporary module roots so we can delete them at the end of the test.
   */
  private val createdRoots = mutableListOf<Path>()

  /**
   * Small struct that keeps the module + its source root VFS handle.
   */
  private data class TempModule(
    val module: Module,
    val src: VirtualFile,
  )

  /**
   * Set up a fresh multi-module IntelliJ project.
   *
   * Notes:
   * - We enable stability checks and disable strong skipping so results reflect actual analysis.
   * - We snapshot/restore settings because these tests mutate global plugin state.
   */
  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val builder = factory.createFixtureBuilder("StabilityAnalyzerTypeAliasMultiModuleTest")
    fixture = factory.createCodeInsightFixture(builder.fixture)
    fixture.setUp()

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
   * Tear down the multi-module project cleanly.
   *
   * Important ordering constraints:
   * 1) Restore settings first (so future tests start from a clean baseline).
   * 2) Dispose modules while the project is still alive (before fixture.tearDown()).
   * 3) Delete temporary directories afterwards.
   */
  override fun tearDown() {
    try {
      // Restore plugin settings to avoid leaking configuration into other suites.
      val state = StabilitySettingsState.getInstance()
      snapshot.restore(state)

      // Dispose modules before tearing down the fixture/project.
      // Disposing modules after fixture teardown can crash or leak project model state.
      runWriteAction {
        val mm = ModuleManager.getInstance(fixture.project)
        createdModules.asReversed().forEach { m ->
          if (!m.isDisposed) mm.disposeModule(m)
        }
      }

      // Clean up disk roots to avoid temp directory buildup in local/CI runs.
      createdRoots.forEach { root ->
        runCatching { FileUtil.delete(root.toFile()) }
      }
    } finally {
      // Always tear down the fixture, even if assertions fail.
      runCatching { fixture.tearDown() }
      super.tearDown()
    }
  }

  /**
   * Creates a real IntelliJ module backed by a temp directory on disk.
   *
   * We create a `/src` directory and register it as a source root so Kotlin PSI can resolve files.
   */
  private fun createDiskModule(name: String): TempModule {
    val rootPath = Files.createTempDirectory("compose-stability-$name-")
    val srcPath = Files.createDirectories(rootPath.resolve("src"))
    createdRoots.add(rootPath)

    val lfs = LocalFileSystem.getInstance()
    val rootVf =
      requireNotNull(lfs.refreshAndFindFileByNioFile(rootPath)) { "No VFS for $rootPath" }
    val srcVf =
      requireNotNull(lfs.refreshAndFindFileByNioFile(srcPath)) { "No VFS for $srcPath" }

    // DO NOT wrap addModule in runWriteAction:
    // PsiTestUtil.addModule already manages write/command internally.
    val module = PsiTestUtil.addModule(fixture.project, StdModuleTypes.JAVA, name, rootVf)
    createdModules += module

    // Adding source roots must be under write action.
    runWriteAction { PsiTestUtil.addSourceRoot(module, srcVf) }

    return TempModule(module = module, src = srcVf)
  }

  /**
   * Adds a Kotlin file under the given module src root.
   *
   * This writes the file via VFS APIs so IntelliJ PSI sees it as a real file.
   */
  private fun addKotlinFile(srcRoot: VirtualFile, relPath: String, text: String): VirtualFile =
    runWriteAction {
      val dirPath = relPath.substringBeforeLast('/', "")
      val fileName = relPath.substringAfterLast('/')

      val dir =
        if (dirPath.isEmpty()) srcRoot else VfsUtil.createDirectoryIfMissing(srcRoot, dirPath)!!
      val vf = dir.findChild(fileName) ?: dir.createChildData(this, fileName)
      VfsUtil.saveText(vf, text)
      vf
    }

  /**
   * Baseline cross-module test:
   * - `lib` defines `typealias ComposableAction = @Composable () -> Unit`
   * - `app` imports and uses `ComposableAction`
   * - analyzer must expand the alias and mark the parameter stable.
   *
   * Why we stub `androidx.compose.runtime.Composable` in `lib`:
   * - We want the typealias text to match real Compose FQNs, which helps both K1 and K2 paths
   *   behave similarly to real projects.
   * - This avoids "string hacks" and keeps the test representative.
   */
  fun testTypealiasImportedFromOtherModuleIsHandled() {
    val lib = createDiskModule("lib")

    // Minimal stub of Compose annotation using the real package name.
    addKotlinFile(
      lib.src,
      "androidx/compose/runtime/Composable.kt",
      """
        package androidx.compose.runtime
        @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
        annotation class Composable
      """.trimIndent(),
    )

    // The typealias we want to resolve *across module boundaries*.
    addKotlinFile(
      lib.src,
      "test/lib/Lib.kt",
      """
        package test.lib

        import androidx.compose.runtime.Composable

        typealias ComposableAction = @Composable () -> Unit
      """.trimIndent(),
    )

    val app = createDiskModule("app")
    val appFile = addKotlinFile(
      app.src,
      "test/app/App.kt",
      """
        package test.app

        import androidx.compose.runtime.Composable
        import test.lib.ComposableAction

        @Composable
        fun Screen(onClick: ComposableAction) { }
      """.trimIndent(),
    )

    // Wire module dependency: app -> lib
    runWriteAction {
      ModuleRootModificationUtil.addDependency(app.module, lib.module)
    }

    // Open the app file in the fixture and force analysis/highlighting.
    fixture.configureFromExistingVirtualFile(appFile)
    fixture.doHighlighting()

    val ktFile = fixture.file as KtFile
    val fn = ktFile.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "Screen" }

    val param = StabilityAnalyzer.analyze(fn).parameters.single { it.name == "onClick" }
    assertEquals(ParameterStability.STABLE, param.stability)
  }

  /**
   * Cross-module + forced resolution failure test.
   *
   * Goal:
   * - Even when resolveMainReference() fails, the analyzer should still locate and expand the
   *   typealias from the dependency module, and correctly classify the parameter as STABLE.
   *
   * Why this test is valuable:
   * - It proves the analyzer's fallback logic works beyond "same file" and "same module"
   *   scenarios, where naive scans often accidentally stop.
   *
   * Important note:
   * - We call analyzePsiForTest() to *force* the PSI path so K2 doesn't short-circuit away
   *   from the code path we want to verify.
   */
  fun testFallbackScanFindsTypealiasFromOtherModuleWhenResolveMainReferenceFails() {
    val lib = createDiskModule("lib")

    // Compose annotation stub (real package name) to keep alias text realistic.
    addKotlinFile(
      lib.src,
      "androidx/compose/runtime/Composable.kt",
      """
      package androidx.compose.runtime
      @Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
      annotation class Composable
      """.trimIndent(),
    )

    // Alias lives in the dependency module.
    addKotlinFile(
      lib.src,
      "test/lib/Lib.kt",
      """
      package test.lib

      import androidx.compose.runtime.Composable

      typealias ComposableAction = @Composable () -> Unit
      """.trimIndent(),
    )

    val app = createDiskModule("app")
    val appFile = addKotlinFile(
      app.src,
      "test/app/App.kt",
      """
      package test.app

      import androidx.compose.runtime.Composable
      import test.lib.ComposableAction

      @Composable
      fun Screen(onClick: ComposableAction) { }
      """.trimIndent(),
    )

    // Ensure `app` can "see" `lib`.
    runWriteAction { ModuleRootModificationUtil.addDependency(app.module, lib.module) }

    fixture.configureFromExistingVirtualFile(appFile)
    fixture.doHighlighting()

    val ktFile = fixture.file as KtFile
    val fn = ktFile.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "Screen" }

    val (info, callCount) = StabilityAnalyzerTestHelpers.withForcedResolveFailure {
      // Force PSI path; otherwise K2 can bypass the exact resolution/scan logic we’re testing.
      StabilityAnalyzer.analyzePsiForTest(fn)
    }

    // Prove the forced-failure resolver was actually invoked.
    assertTrue("Expected resolveMainReferenceOverride to be called", callCount > 0)

    val param = info.parameters.single { it.name == "onClick" }

    // If this fails with RUNTIME, it usually means the fallback scan only looks in the current file
    // (or current module) and does not locate typealiases in dependencies.
    assertEquals(ParameterStability.STABLE, param.stability)

    // Validate that alias expansion actually happened (not just an incidental stable classification).
    val reason = param.reason.orEmpty()
    assertTrue(reason.contains("Typealias ComposableAction expands to"))
    assertTrue(reason.contains("@Composable"))
    assertTrue(reason.contains("->"))
  }
}
