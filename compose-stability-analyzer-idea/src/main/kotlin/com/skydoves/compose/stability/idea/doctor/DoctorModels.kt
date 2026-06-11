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
package com.skydoves.compose.stability.idea.doctor

import com.intellij.psi.SmartPsiElementPointer
import com.skydoves.compose.stability.idea.blame.ArgumentOrigin
import com.skydoves.compose.stability.idea.doctor.fixes.DoctorFix
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Whether a prescription's score is backed by live runtime measurements or static analysis only.
 */
internal enum class ScoreKind {
  /** Static analysis only — no (or not enough) runtime observations for this composable. */
  ESTIMATED,

  /** Backed by live heatmap/Reality-Check data from a connected device. */
  MEASURED,
}

/**
 * The Doctor's priority score for one composable.
 *
 * [value] is on a single 0..100 scale for both kinds, with estimated scores capped at
 * [DoctorScorer.ESTIMATED_CAP] so confirmed measured waste always outranks speculation.
 */
internal data class DoctorScore(
  val kind: ScoreKind,
  val value: Double,
  /** The static component, always computed (shown in tooltips for comparison). */
  val estimatedComponent: Double,
  /** Observed wasted time in ms (wastedRecompositions x avg duration); 0 without live data. */
  val measuredWasteMs: Double,
  /** Downstream unskippable composable count from the cascade; null = not computed. */
  val blastRadius: Int?,
)

/**
 * Where a problem parameter's value comes from at one call site (blame-lite, depth 1).
 */
internal data class CallerArgumentOrigin(
  val callerName: String,
  val callerFqName: String,
  val callSiteFilePath: String?,
  val callSiteLine: Int,
  val origin: ArgumentOrigin,
)

/**
 * One problematic parameter of a prescription: its static verdict, runtime grade, where its
 * value originates, and the fixes the Doctor can apply.
 */
internal data class PrescriptionCause(
  val paramName: String,
  val paramType: String,
  val staticStability: ParameterStability,
  val staticReason: String?,
  /** Reality-Check grade; null when no live data has been observed. */
  val realityGrade: RealityGrade?,
  val callSiteOrigins: List<CallerArgumentOrigin>,
  val fixes: List<DoctorFix>,
)

/**
 * A ranked, actionable Doctor entry: one composable, why it costs you, and how to fix it.
 */
internal data class Prescription(
  val composableName: String,
  val fqName: String,
  /** Overload-safe identity: fqName + parameter count + parameter type hash. */
  val signatureKey: String,
  val filePath: String?,
  val line: Int,
  val score: DoctorScore,
  /** One-line human summary, e.g. "Product has 2 var properties — 218ms measured waste". */
  val problemSummary: String,
  val causes: List<PrescriptionCause>,
  val functionPointer: SmartPsiElementPointer<KtNamedFunction>,
  /**
   * True when live data exists under this composable's simple name but could not be attributed
   * to THIS declaration because multiple project composables share the name (old-runtime logs
   * without an fqName token). The score stays ESTIMATED in that case.
   */
  val ambiguousLiveMatch: Boolean = false,
)
