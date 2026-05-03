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
package com.skydoves.compose.stability.compiler.tests

import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirLightTreeBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/**
 * Base class for box tests.
 *
 * Box tests execute the full compiler pipeline (FIR + IR + codegen) and
 * run the generated code. The test passes if the `box()` function returns "OK".
 *
 * These tests are good for testing:
 * - End-to-end compiler behavior
 * - IR transformations
 * - Runtime behavior
 * - Code generation correctness
 *
 * Example test file:
 * ```kotlin
 * import androidx.compose.runtime.Composable
 *
 * @Composable
 * fun StableFunction(text: String) {
 *   // ...
 * }
 *
 * fun box(): String {
 *   // Test code here
 *   return "OK"
 * }
 * ```
 */
open class AbstractBoxTest : AbstractFirLightTreeBlackBoxCodegenTest() {

  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider

  override fun configure(builder: TestConfigurationBuilder) {
    super.configure(builder)

    with(builder) {
      configurePlugin()

      defaultDirectives {
        JVM_TARGET.with(JvmTarget.JVM_11)
        +FULL_JDK
        +WITH_STDLIB
        +IGNORE_DEXING
      }
    }
  }
}
