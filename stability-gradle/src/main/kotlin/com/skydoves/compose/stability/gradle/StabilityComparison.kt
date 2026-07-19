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

  // Check for new functions
  current.keys.subtract(reference.keys).forEach { functionName ->
    if (!ignoreNonRegressiveChanges || !current.getValue(functionName).isStable(forceStableTypes)) {
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
    // true -> false, e.g. by gaining @NonRestartableComposable / @ReadOnlyComposable /
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

private fun ParameterInfo.isStable(forceStableTypes: List<FqNameMatcher>): Boolean =
  stability == "STABLE" ||
    forceStableTypes.any {
      it.matches(type)
    }
