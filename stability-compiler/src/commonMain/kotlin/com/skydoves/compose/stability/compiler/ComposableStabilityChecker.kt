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
package com.skydoves.compose.stability.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR checker for composable functions.
 * Currently unused - all stability analysis is performed in the IR phase.
 * This is kept as infrastructure for potential future FIR-phase checks.
 *
 * Note: Uses [FirCallableDeclarationChecker] instead of `FirSimpleFunctionChecker` because
 * `FirSimpleFunction` was renamed to `FirNamedFunction` in Kotlin 2.3.20, causing linkage failures.
 */
public object ComposableStabilityChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {

  private val COMPOSABLE_FQ_NAME = ClassId(
    FqName("androidx.compose.runtime"),
    Name.identifier("Composable"),
  )

  context(ctx: CheckerContext, _: DiagnosticReporter)
  override fun check(declaration: FirCallableDeclaration) {
    if (declaration !is FirFunction) return
    if (declaration.symbol.getAnnotationByClassId(COMPOSABLE_FQ_NAME, ctx.session) == null) {
      return
    }

    // no-op - This checker infrastructure is kept for potential future use
  }
}
