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

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.skydoves.compose.stability.idea.blame.BlameAnalyzer
import com.skydoves.compose.stability.idea.cascade.CascadeAnalyzer
import com.skydoves.compose.stability.idea.doctor.fixes.DoctorFixFactory
import com.skydoves.compose.stability.idea.hasAnnotation
import com.skydoves.compose.stability.idea.heatmap.AdbLogcatService
import com.skydoves.compose.stability.idea.heatmap.ComposableHeatmapData
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.isPreview
import com.skydoves.compose.stability.idea.reality.ComposableReality
import com.skydoves.compose.stability.idea.reality.RealityClassifier
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * The Stability Doctor's analysis pipeline. Produces a ranked list of [Prescription]s by
 * combining the static stability verdict, the cascade blast radius, and live runtime data
 * (heatmap counts/durations + Reality-Check grades).
 *
 * Phases (all off the EDT; chunked read actions per composable so typing is never blocked):
 * 0. Enumerate project composables.
 * 1. Static score for ALL of them (params only — cheap).
 * 2. Cascade blast radius, lazily, for the top candidates + live silent-waste composables.
 * 3. Live join + root cause (blame-lite) for the displayed rows.
 */
internal object DoctorAnalyzer {

  /** Rows materialized with causes/blame in phase 3 (and shown in the panel). */
  private const val DISPLAY_LIMIT = 30

  private const val MAX_BLAME_CALLERS = 5

  internal data class DoctorReport(
    val prescriptions: List<Prescription>,
    val scannedComposables: Int,
    val measuredCount: Int,
    val totalMeasuredWasteMs: Double,
  )

  /** A lightweight handle collected in phase 0; everything PSI-heavy happens later. */
  private data class Target(
    val name: String,
    val fqName: String,
    val signatureKey: String,
    val filePath: String?,
    val line: Int,
    val pointer: SmartPsiElementPointer<KtNamedFunction>,
  )

  private data class ScoredTarget(
    val target: Target,
    val info: ComposableStabilityInfo,
    var blastRadius: Int?,
    var estimatedScore: Double,
  )

  fun analyze(project: Project, indicator: ProgressIndicator): DoctorReport {
    val settings = StabilitySettingsState.getInstance()
    val service = AdbLogcatService.getInstance(project)
    val cache = DoctorCacheService.getInstance(project)

    // ── Phase 0: enumerate ──────────────────────────────────────────────────
    indicator.isIndeterminate = false
    indicator.text = "Doctor: scanning composables..."
    val targets = enumerateTargets(project, settings)
    if (targets.isEmpty()) {
      return DoctorReport(emptyList(), 0, 0, 0.0)
    }
    val duplicateNames = targets.groupingBy { it.name }.eachCount()

    // ── Phase 1: static score for all ──────────────────────────────────────
    indicator.text = "Doctor: static analysis..."
    val scored = mutableListOf<ScoredTarget>()
    targets.forEachIndexed { index, target ->
      indicator.checkCanceled()
      indicator.fraction = 0.1 + 0.4 * index / targets.size
      val info = runReadAction {
        target.pointer.element?.let { cache.verdict(target.signatureKey, it) }
      } ?: return@forEachIndexed
      val inputs = DoctorScorer.staticInputs(
        info = info,
        // Fixability is refined in phase 3 (when fixes are built); 0 here keeps phase 1 cheap.
        fixableParams = 0,
        strongSkipping = settings.isStrongSkippingEnabled,
        blastRadius = null,
      )
      scored += ScoredTarget(target, info, null, DoctorScorer.estimated(inputs))
    }

    // ── Phase 2: cascade blast radius for top candidates ────────────────────
    indicator.text = "Doctor: measuring blast radius..."
    val cascadeCandidates = selectCascadeCandidates(scored, service, duplicateNames, settings)
    cascadeCandidates.forEachIndexed { index, candidate ->
      indicator.checkCanceled()
      indicator.fraction = 0.5 + 0.2 * index / cascadeCandidates.size.coerceAtLeast(1)
      val cached = cache.cachedCascadeSummary(candidate.target.signatureKey)
      val summary = cached ?: runCatching {
        val fn = runReadAction { candidate.target.pointer.element } ?: return@forEachIndexed
        CascadeAnalyzer.analyze(fn, indicator).summary
          .also { cache.storeCascadeSummary(candidate.target.signatureKey, it) }
      }.getOrNull() ?: return@forEachIndexed
      candidate.blastRadius = summary.unskippableCount
      val inputs = DoctorScorer.staticInputs(
        info = candidate.info,
        fixableParams = 0,
        strongSkipping = settings.isStrongSkippingEnabled,
        blastRadius = summary.unskippableCount,
      )
      candidate.estimatedScore = DoctorScorer.estimated(inputs)
    }

    // ── Phase 3: live join + root cause for the display rows ───────────────
    indicator.text = "Doctor: joining live data..."
    val prescriptions = mutableListOf<Prescription>()
    var measuredCount = 0
    var totalWasteMs = 0.0

    // Pre-rank by (estimated + a measured preview) to choose which rows get blame analysis.
    val preRanked = scored.sortedByDescending { candidate ->
      val (live, _) = resolveLive(candidate.target, service, duplicateNames)
      val reality = live?.let { RealityClassifier.classify(candidate.info, it) }
      previewScore(candidate, reality, live, settings)
    }

    preRanked.take(DISPLAY_LIMIT).forEachIndexed { index, candidate ->
      indicator.checkCanceled()
      indicator.fraction = 0.7 + 0.3 * index / DISPLAY_LIMIT
      val (live, ambiguous) = resolveLive(candidate.target, service, duplicateNames)
      val reality = live?.let { RealityClassifier.classify(candidate.info, it) }

      val staticInputs = DoctorScorer.staticInputs(
        info = candidate.info,
        fixableParams = 0,
        strongSkipping = settings.isStrongSkippingEnabled,
        blastRadius = candidate.blastRadius,
      )
      val score = DoctorScorer.score(staticInputs, reality, live)
      if (score.value < settings.doctorMinScore) return@forEachIndexed

      val causes = buildCauses(candidate, reality)
      prescriptions += Prescription(
        composableName = candidate.target.name,
        fqName = candidate.target.fqName,
        signatureKey = candidate.target.signatureKey,
        filePath = candidate.target.filePath,
        line = candidate.target.line,
        score = score,
        problemSummary = buildSummary(candidate, score, reality, ambiguous),
        causes = causes,
        functionPointer = candidate.target.pointer,
        ambiguousLiveMatch = ambiguous,
      )
      if (score.kind == ScoreKind.MEASURED) {
        measuredCount++
        totalWasteMs += score.measuredWasteMs
      }
    }

    return DoctorReport(
      prescriptions = prescriptions.sortedWith(DoctorScorer.ranking),
      scannedComposables = targets.size,
      measuredCount = measuredCount,
      totalMeasuredWasteMs = totalWasteMs,
    )
  }

  // ── Phase 0 ───────────────────────────────────────────────────────────────

  private fun enumerateTargets(
    project: Project,
    settings: StabilitySettingsState,
  ): List<Target> {
    return runReadAction {
      val scope = GlobalSearchScope.projectScope(project)
      val psiManager = PsiManager.getInstance(project)
      val docManager = PsiDocumentManager.getInstance(project)
      val fileIndex = ProjectFileIndex.getInstance(project)
      val pointerManager = SmartPointerManager.getInstance(project)

      FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        .asSequence()
        .filter { settings.doctorIncludeTestSources || !fileIndex.isInTestSourceContent(it) }
        .mapNotNull { psiManager.findFile(it) as? KtFile }
        .flatMap { PsiTreeUtil.findChildrenOfType(it, KtNamedFunction::class.java).asSequence() }
        .filter {
          it.isComposable() && !it.isPreview() && !it.hasAnnotation("IgnoreStabilityReport")
        }
        .mapNotNull { fn ->
          val name = fn.name ?: return@mapNotNull null
          val fqName = fn.fqName?.asString() ?: name
          val paramTypes = fn.valueParameters.joinToString(",") { p ->
            p.typeReference?.text ?: "?"
          }
          val vFile = fn.containingFile?.virtualFile
          val line = vFile?.let {
            docManager.getDocument(fn.containingFile)?.getLineNumber(fn.textOffset)?.plus(1)
          } ?: 0
          Target(
            name = name,
            fqName = fqName,
            signatureKey = "$fqName/${fn.valueParameters.size}/${paramTypes.hashCode()}",
            filePath = vFile?.path,
            line = line,
            pointer = pointerManager.createSmartPsiElementPointer(fn),
          )
        }
        .toList()
    }
  }

  // ── Phase 2 helpers ───────────────────────────────────────────────────────

  private fun selectCascadeCandidates(
    scored: List<ScoredTarget>,
    service: AdbLogcatService,
    duplicateNames: Map<String, Int>,
    settings: StabilitySettingsState,
  ): List<ScoredTarget> {
    val topByStatic = scored
      .sortedByDescending { it.estimatedScore }
      .take(settings.doctorMaxCascadeCandidates)
    // Every composable with observed silent waste will surface as a MEASURED row and
    // deserves a blast radius too, even if its static score didn't make the top-N.
    val withSilentWaste = scored.filter { candidate ->
      val (live, _) = resolveLive(candidate.target, service, duplicateNames)
      val reality = live?.let { RealityClassifier.classify(candidate.info, it) }
      reality?.hasSilentWaste == true
    }
    return (topByStatic + withSilentWaste).distinctBy { it.target.signatureKey }
  }

  // ── Phase 3 helpers ───────────────────────────────────────────────────────

  /**
   * Resolves live data for a target: exact fqName key first; simple-name fallback only when
   * the attribution is unambiguous (single live key AND single project declaration with that
   * simple name). Returns (live, ambiguous).
   */
  private fun resolveLive(
    target: Target,
    service: AdbLogcatService,
    duplicateNames: Map<String, Int>,
  ): Pair<ComposableHeatmapData?, Boolean> {
    service.getHeatmapData(target.fqName)?.let { return it to false }
    val liveKeys = service.countKeysForSimpleName(target.name)
    if (liveKeys == 0) return null to false
    val projectDuplicates = (duplicateNames[target.name] ?: 1) > 1
    if (liveKeys > 1 || projectDuplicates) {
      // Observed under this simple name, but we can't prove it is THIS declaration.
      return null to true
    }
    return service.getHeatmapData(target.name) to false
  }

  private fun previewScore(
    candidate: ScoredTarget,
    reality: ComposableReality?,
    live: ComposableHeatmapData?,
    settings: StabilitySettingsState,
  ): Double {
    val inputs = DoctorScorer.staticInputs(
      info = candidate.info,
      fixableParams = 0,
      strongSkipping = settings.isStrongSkippingEnabled,
      blastRadius = candidate.blastRadius,
    )
    return DoctorScorer.score(inputs, reality, live).value
  }

  /** Builds the per-parameter causes for the problematic params (unstable/unknown/runtime
   * statically, or graded SILENT_WASTE at runtime). Blame-lite runs inside a read action. */
  private fun buildCauses(
    candidate: ScoredTarget,
    reality: ComposableReality?,
  ): List<PrescriptionCause> {
    val problemParams = candidate.info.parameters.filter { p ->
      p.stability != ParameterStability.STABLE ||
        reality?.gradeFor(p.name) == RealityGrade.SILENT_WASTE
    }
    if (problemParams.isEmpty()) return emptyList()

    val callerOrigins = runReadAction {
      val fn = candidate.target.pointer.element ?: return@runReadAction emptyList()
      runCatching { BlameAnalyzer.analyzeDirectCallers(fn, MAX_BLAME_CALLERS) }
        .getOrDefault(emptyList())
    }

    return problemParams.map { p ->
      val grade = reality?.gradeFor(p.name)
      val fixes = runReadAction {
        val fn = candidate.target.pointer.element ?: return@runReadAction emptyList()
        runCatching {
          DoctorFixFactory.buildFixes(fn, p.name, p.stability, grade)
        }.getOrDefault(emptyList())
      }
      PrescriptionCause(
        paramName = p.name,
        paramType = p.type,
        staticStability = p.stability,
        staticReason = p.reason,
        realityGrade = grade,
        callSiteOrigins = callerOrigins.mapNotNull { caller ->
          val origin = caller.origins.find { it.paramName == p.name } ?: return@mapNotNull null
          CallerArgumentOrigin(
            callerName = caller.callerName,
            callerFqName = caller.callerFqName,
            callSiteFilePath = caller.callSiteFilePath,
            callSiteLine = caller.callSiteLine,
            origin = origin,
          )
        },
        fixes = fixes,
      )
    }
  }

  private fun buildSummary(
    candidate: ScoredTarget,
    score: DoctorScore,
    reality: ComposableReality?,
    ambiguous: Boolean,
  ): String {
    val parts = mutableListOf<String>()
    val unstable = candidate.info.parameters.count {
      it.stability == ParameterStability.UNSTABLE
    }
    val unknown = candidate.info.parameters.count {
      it.stability == ParameterStability.UNKNOWN || it.stability == ParameterStability.RUNTIME
    }
    if (unstable > 0) parts += "$unstable unstable param${if (unstable > 1) "s" else ""}"
    if (unknown > 0) parts += "$unknown unknown/runtime"

    if (score.kind == ScoreKind.MEASURED) {
      val wasted = reality?.wastedRecompositions ?: 0
      parts += "≈${"%.0f".format(score.measuredWasteMs)}ms observed waste " +
        "($wasted wasted recomposition${if (wasted != 1) "s" else ""})"
    }
    score.blastRadius?.let { if (it > 0) parts += "$it downstream affected" }
    if (ambiguous) {
      parts += "runtime data ambiguous (same-named composables)"
    }
    return parts.joinToString(" · ").ifEmpty { "no static issues detected" }
  }
}
