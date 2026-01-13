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
      differences.add(StabilityDifference.NewFunction(functionName))
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
