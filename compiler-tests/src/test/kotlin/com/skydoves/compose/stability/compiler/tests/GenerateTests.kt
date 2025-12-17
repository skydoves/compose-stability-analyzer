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

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

/**
 * Main function to generate test classes from test data files.
 *
 * Run this via the `generateTests` Gradle task.
 */
fun main() {
  System.setProperty("line.separator", "\n")

  generateTestGroupSuiteWithJUnit5 {
    testGroup(
      testDataRoot = "compiler-tests/src/test/data",
      testsRoot = "compiler-tests/src/test/java",
    ) {
      // Diagnostic tests - fast frontend-only tests
      testClass<AbstractDiagnosticTest> {
        model("diagnostic")
      }

      // Box tests - full compiler pipeline with execution
      testClass<AbstractBoxTest> {
        model("box")
      }

      // FIR dump tests - dumps Frontend IR tree to .fir.txt files
      testClass<AbstractFirDumpTest> {
        model("dump/fir")
      }

      // IR dump tests - dumps backend IR to .ir.kt.txt files
      testClass<AbstractIrDumpTest> {
        model("dump/ir")
      }
    }
  }
}
