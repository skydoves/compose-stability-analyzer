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
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.DUMP_IR
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.DUMP_KT_IR
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.runners.ir.AbstractFirLightTreeJvmIrTextTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/**
 * Base class for IR dump tests.
 *
 * IR dump tests generate a text representation of the backend IR (Intermediate Representation)
 * for inspection and comparison. The output is saved to `.txt` or `.ir.kt.txt` files.
 *
 * These tests are useful for:
 * - Verifying IR transformations by the compiler plugin
 * - Debugging backend code generation
 * - Tracking IR changes over time
 * - Understanding how high-level Kotlin code becomes IR
 *
 * Test data files should be placed in `compiler-tests/src/test/data/dump/ir/`.
 *
 * Example test file:
 * ```kotlin
 * // DUMP_KT_IR
 *
 * data class User(val name: String)
 *
 * fun processUser(user: User) {
 *     println(user.name)
 * }
 * ```
 *
 * This will generate a `.ir.kt.txt` file with the IR representation showing:
 * - Function declarations
 * - Property access
 * - Call sites
 * - Type information
 * - Our plugin's IR transformations
 */
open class AbstractIrDumpTest : AbstractFirLightTreeJvmIrTextTest() {
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
        +DISABLE_GENERATED_FIR_TAGS

        // Use DUMP_KT_IR for more readable Kotlin-like IR output
        -DUMP_IR
        +DUMP_KT_IR
      }
    }
  }
}
