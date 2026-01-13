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
  forceStableTypes: List<Regex> = emptyList(),

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

private fun StabilityEntry.isStable(forceStableTypes: List<Regex>): Boolean {
  return skippable ||
    (parameters.isNotEmpty() && parameters.all { it.isStable(forceStableTypes) })
}

private fun ParameterInfo.isStable(forceStableTypes: List<Regex>): Boolean {
  return stability == "STABLE" || forceStableTypes.any { it.matches(type) }
}
