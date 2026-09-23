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
package com.skydoves.compose.stability.gradle

internal fun compareStability(
  current: Map<String, StabilityEntry>,
  reference: Map<String, StabilityEntry>,
  ignoreNonRegressiveChanges: Boolean = false,
  forceStableTypes: List<FqNameMatcher> = emptyList(),
): List<StabilityDifference> {
  val differences = mutableListOf<StabilityDifference>()

  // Check for new functions. Under ignoreNonRegressiveChanges, a new composable is a regression
  // only if it introduces an unstable parameter. Being non-restartable or opting out of skipping
  // (@NonSkippableComposable) with all-stable parameters is not a regression, so it must not use the
  // skippability-oriented isStable() here, which would wrongly report those stable composables
  // (issue #192).
  current.keys.subtract(reference.keys).forEach { functionName ->
    if (!ignoreNonRegressiveChanges ||
      current.getValue(functionName).hasUnstableParameter(forceStableTypes)
    ) {
      val parametersWithFixedStability = current.getValue(functionName).parameters
        .map { parameter ->
          parameter.copy(
            stability = if (parameter.isStable(forceStableTypes)) {
              "STABLE"
            } else {
              parameter.stability
            },
          )
        }

      differences.add(StabilityDifference.NewFunction(functionName, parametersWithFixedStability))
    }
  }

  // Check for removed functions
  if (!ignoreNonRegressiveChanges) {
    reference.keys.subtract(current.keys).forEach { functionName ->
      differences.add(StabilityDifference.RemovedFunction(functionName))
    }
  }

  // Check for changed stability
  current.keys.intersect(reference.keys).forEach { functionName ->
    val currentEntry = current[functionName]!!
    val referenceEntry = reference[functionName]!!

    // Check skippability change
    if (currentEntry.isStable(forceStableTypes) != referenceEntry.isStable(forceStableTypes) &&
      (!ignoreNonRegressiveChanges || !currentEntry.isStable(forceStableTypes))
    ) {
      differences.add(
        StabilityDifference.SkippabilityChanged(
          functionName,
          referenceEntry.isStable(forceStableTypes),
          currentEntry.isStable(forceStableTypes),
        ),
      )
    }

    // Check restartability change. A composable that loses its restart group (restartable
    // true -> false, e.g. by gaining @NonRestartableComposable / the `open` modifier /
    // @ExplicitGroupsComposable / inline / a non-Unit return type) can no longer be skipped and
    // re-runs whenever its parent recomposes, so it is a regression worth surfacing. Restartability
    // is structural, so the stability configuration cannot rescue it (issue #184).
    if (currentEntry.restartable != referenceEntry.restartable &&
      (!ignoreNonRegressiveChanges || !currentEntry.restartable)
    ) {
      differences.add(
        StabilityDifference.RestartabilityChanged(
          functionName,
          referenceEntry.restartable,
          currentEntry.restartable,
        ),
      )
    }

    // Check if parameter count changed
    if (currentEntry.parameters.size != referenceEntry.parameters.size) {
      if (
        !ignoreNonRegressiveChanges ||
        currentEntry.parameters.any { !it.isStable(forceStableTypes) }
      ) {
        differences.add(
          StabilityDifference.ParameterCountChanged(
            functionName,
            referenceEntry.parameters.size,
            currentEntry.parameters.size,
          ),
        )
      }
    } else {
      // Check parameter stability changes (only if count is the same)
      currentEntry.parameters.zip(referenceEntry.parameters).forEach { (current, ref) ->
        if (current.stability != ref.stability &&
          (!ignoreNonRegressiveChanges || !current.isStable(forceStableTypes))
        ) {
          differences.add(
            StabilityDifference.ParameterStabilityChanged(
              functionName,
              current.name,
              ref.stability,
              current.stability,
            ),
          )
        }
      }
    }
  }

  return differences
}

/**
 * Whether a composable is effectively skippable for comparison purposes. The compiler's [skippable]
 * verdict is authoritative; the only reason to override it is that a stability configuration can
 * mark a previously-unstable parameter type as stable at check time (without recompiling), which
 * would make an otherwise-unstable composable skippable.
 *
 * That override only applies when the composable is [restartable] (a non-restartable composable is
 * never skippable) and the configuration actually rescues a parameter the compiler saw as unstable.
 * If every parameter was already stable yet the compiler still reported `skippable = false`, the
 * composable opted out of skipping (`@NonSkippableComposable`), so it must not be treated as
 * skippable (issue #184).
 */
private fun StabilityEntry.isStable(forceStableTypes: List<FqNameMatcher>): Boolean = skippable ||
  (
    restartable &&
      parameters.isNotEmpty() &&
      parameters.all { it.isStable(forceStableTypes) } &&
      parameters.any { it.stability != "STABLE" }
    )

/**
 * Whether the entry is a stability issue worth recording in an `unstableOnly` baseline: it either
 * introduces an unstable parameter, or the compiler could not make it skippable or restartable.
 *
 * This is the union issue #128 asked for ("only baseline composables that are considered UNSTABLE,
 * or not restartable or skippable"). It is deliberately a *superset* of [hasUnstableParameter], the
 * predicate `compareStability` uses to decide whether a *new* composable is a regression under
 * `ignoreNonRegressiveChanges`: it also keeps entries that are merely non-skippable or
 * non-restartable, which the check does not report as new-composable regressions.
 *
 * Being a superset is what makes an `unstableOnly` baseline usable. Filtering by a *narrower*
 * predicate than the check reports on makes it unusable: an entry the dump drops but the check
 * would flag comes back as a new unstable composable on every run, and `stabilityDump` cannot
 * accept it because it drops the entry again. Every entry this predicate drops is stable,
 * skippable and restartable, so [hasUnstableParameter] is false for it and the check stays quiet.
 *
 * That round-trip property holds under `ignoreNonRegressiveChanges`, which is how issue #128
 * describes using the option. With it disabled, `compareStability` reports *every* entry missing
 * from the reference as a new composable, so no filtered baseline of any shape can compare clean
 * against the code it was dumped from.
 */
internal fun StabilityEntry.isStabilityIssue(forceStableTypes: List<FqNameMatcher>): Boolean =
  hasUnstableParameter(forceStableTypes) || !skippable || !restartable

/**
 * The warning `stabilityDump` prints when `unstableOnly` actually dropped entries while
 * `ignoreNonRegressiveChanges` is off, or null when the combination is fine.
 *
 * [isStabilityIssue] fixes the case where the dump dropped an entry the check would report as a
 * *new unstable* composable. It cannot fix the other door: with `ignoreNonRegressiveChanges` off,
 * `compareStability` reports **every** entry missing from the reference, unstable or not, so any
 * filtered baseline fails against the code it was dumped from and `stabilityDump` cannot accept
 * the failure because it drops the same entries again.
 *
 * Nothing rejects that combination, because it is harmless when the filter happens to drop nothing,
 * and failing the build would break those projects. So it warns only once entries were actually
 * dropped, which is exactly when the next `stabilityCheck` will fail.
 */
internal fun unstableOnlyWarning(droppedCount: Int, ignoreNonRegressiveChanges: Boolean): String? {
  if (droppedCount <= 0 || ignoreNonRegressiveChanges) return null
  return "composeStabilityAnalyzer: unstableOnly left $droppedCount stable composable(s) out of " +
    "the baseline, but ignoreNonRegressiveChanges is false, so stabilityCheck will report every " +
    "one of them as a new composable and fail. Set " +
    "stabilityValidation.ignoreNonRegressiveChanges.set(true) to use unstableOnly, or set " +
    "unstableOnly.set(false) to write a complete baseline."
}

/**
 * Whether the composable has at least one unstable parameter, i.e. it introduces instability. This
 * is the signal used to decide whether a *new* composable is a regression under
 * `ignoreNonRegressiveChanges`, independently of skippability/restartability: a composable whose
 * parameters are all stable is not a regression even when it is non-restartable or opts out of
 * skipping (issue #192). A parameterless composable introduces no instability.
 */
private fun StabilityEntry.hasUnstableParameter(forceStableTypes: List<FqNameMatcher>): Boolean =
  parameters.any { !it.isStable(forceStableTypes) }

private fun ParameterInfo.isStable(forceStableTypes: List<FqNameMatcher>): Boolean =
  stability == "STABLE" ||
    forceStableTypes.any {
      it.matches(type)
    }
