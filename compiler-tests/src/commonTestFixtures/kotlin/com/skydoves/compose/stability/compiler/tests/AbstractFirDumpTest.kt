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

import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.FIR_DUMP
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives.RUN_PIPELINE_TILL
import org.jetbrains.kotlin.test.services.TestPhase

/**
 * Base class for FIR dump tests.
 *
 * FIR dump tests generate a text representation of the Frontend IR (FIR) tree
 * for inspection and comparison. The output is saved to `.fir.txt` files.
 *
 * These tests are useful for:
 * - Verifying FIR transformations
 * - Debugging frontend analysis
 * - Tracking FIR tree changes over time
 *
 * Test data files should be placed in `compiler-tests/src/commonTest/data/dump/fir/`.
 *
 * Example test file:
 * ```kotlin
 * // RUN_PIPELINE_TILL: FRONTEND
 * // FIR_DUMP
 *
 * data class User(val name: String)
 * ```
 *
 * This will generate a `User.fir.txt` file with the FIR tree representation.
 */
open class AbstractFirDumpTest : AbstractDiagnosticTest() {
  init {
    configure {
      defaultDirectives {
        RUN_PIPELINE_TILL.with(TestPhase.FRONTEND)
        +FIR_DUMP
        +DISABLE_GENERATED_FIR_TAGS
      }
    }
  }
}
