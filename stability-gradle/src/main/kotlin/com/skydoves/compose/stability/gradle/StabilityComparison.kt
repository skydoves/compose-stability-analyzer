package com.skydoves.compose.stability.gradle

internal fun compareStability(
  current: Map<String, StabilityEntry>,
  reference: Map<String, StabilityEntry>,
  ignoreNonRegressiveChanges: Boolean = false,

  ): List<StabilityDifference> {
  val differences = mutableListOf<StabilityDifference>()

  // Check for new functions
  current.keys.subtract(reference.keys).forEach { functionName ->
    current.getValue(functionName).parameters
    if (!ignoreNonRegressiveChanges || !current.getValue(functionName).skippable) {
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
    if (currentEntry.skippable != referenceEntry.skippable &&
      (!ignoreNonRegressiveChanges || !currentEntry.skippable)
    ) {
      differences.add(
        StabilityDifference.SkippabilityChanged(
          functionName,
          referenceEntry.skippable,
          currentEntry.skippable,
        ),
      )
    }

    // Check if parameter count changed
    if (currentEntry.parameters.size != referenceEntry.parameters.size) {
      if (
        !ignoreNonRegressiveChanges ||
        currentEntry.parameters.any { it.stability != "STABLE" }
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
          (!ignoreNonRegressiveChanges || current.stability != "STABLE")
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
