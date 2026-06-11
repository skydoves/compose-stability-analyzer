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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker
import com.skydoves.compose.stability.idea.StabilityAnalyzer
import com.skydoves.compose.stability.idea.cascade.CascadeSummary
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-level caches for the Stability Doctor.
 *
 * - Static verdicts are cached by signature key + the containing file's modification stamp
 *   (same pattern as HeatmapInlayManager's verdict cache).
 * - Cascade summaries are cached by signature key + the GLOBAL PSI modification count:
 *   per-file stamps are wrong for call-graph data, because an edit in a downstream file must
 *   invalidate an upstream composable's blast radius.
 */
@Service(Service.Level.PROJECT)
internal class DoctorCacheService(
  private val project: Project,
) : Disposable {

  private val verdictCache =
    ConcurrentHashMap<String, Pair<Long, ComposableStabilityInfo>>()

  private val cascadeCache =
    ConcurrentHashMap<String, Pair<Long, CascadeSummary>>()

  /**
   * Returns the (possibly cached) static stability verdict for [function].
   * MUST be called inside a read action.
   */
  fun verdict(signatureKey: String, function: KtNamedFunction): ComposableStabilityInfo? {
    return try {
      val stamp = function.containingFile?.modificationStamp ?: return null
      val cached = verdictCache[signatureKey]
      if (cached != null && cached.first == stamp) {
        cached.second
      } else {
        val info = StabilityAnalyzer.analyze(function)
        if (verdictCache.size > CACHE_LIMIT) verdictCache.clear()
        verdictCache[signatureKey] = stamp to info
        info
      }
    } catch (_: Exception) {
      null
    }
  }

  /** Returns the cached cascade summary if still valid for the current PSI generation. */
  fun cachedCascadeSummary(signatureKey: String): CascadeSummary? {
    val cached = cascadeCache[signatureKey] ?: return null
    return if (cached.first == psiModificationCount()) cached.second else null
  }

  fun storeCascadeSummary(signatureKey: String, summary: CascadeSummary) {
    if (cascadeCache.size > CACHE_LIMIT) cascadeCache.clear()
    cascadeCache[signatureKey] = psiModificationCount() to summary
  }

  /** Drops cached results for one composable (e.g. after a fix is applied to it). */
  fun invalidate(signatureKey: String) {
    verdictCache.remove(signatureKey)
    cascadeCache.remove(signatureKey)
  }

  fun psiModificationCount(): Long =
    PsiModificationTracker.getInstance(project).modificationCount

  override fun dispose() {
    verdictCache.clear()
    cascadeCache.clear()
  }

  companion object {
    private const val CACHE_LIMIT = 1000

    fun getInstance(project: Project): DoctorCacheService =
      project.getService(DoctorCacheService::class.java)
  }
}
