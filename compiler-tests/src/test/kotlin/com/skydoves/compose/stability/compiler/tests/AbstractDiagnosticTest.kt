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
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.runners.AbstractPhasedJvmDiagnosticLightTreeTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/**
 * Base class for diagnostic tests.
 *
 * Diagnostic tests execute only the frontend (FIR analysis) and inject
 * compiler diagnostics directly into the test file as comments.
 *
 * These tests are very fast and good for testing:
 * - Error detection and reporting
 * - Warning messages
 * - Stability analysis results
 */
open class AbstractDiagnosticTest : AbstractPhasedJvmDiagnosticLightTreeTest() {

  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
    ClasspathBasedStandardLibrariesPathProvider

  override fun configure(builder: TestConfigurationBuilder) {
    super.configure(builder)

    with(builder) {
      configurePlugin()

      defaultDirectives {
        JVM_TARGET.with(JvmTarget.JVM_11)
        +FULL_JDK
        +IGNORE_DEXING
        +DISABLE_GENERATED_FIR_TAGS
      }
    }
  }
}
