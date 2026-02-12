# Custom Logger

You can completely customize the logging behavior of `@TraceRecomposition` by implementing your own `RecompositionLogger`.

## Setting a Custom Logger

```kotlin
ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
    override fun log(event: RecompositionEvent) {
        val message = buildString {
            append("Recomposition #${event.recompositionCount}")
            append(" - ${event.composableName}")
            if (event.tag.isNotEmpty()) {
                append(" [${event.tag}]")
            }
            appendLine()

            event.parameterChanges.forEach { change ->
                append("  ${change.name}: ${change.type}")
                when {
                    change.changed -> append(" CHANGED")
                    change.stable -> append(" STABLE")
                    else -> append(" UNSTABLE")
                }
                appendLine()
            }
        }

        Log.d("CustomRecomposition", message)
    }
})
```

## Firebase Analytics Integration

Track excessive recompositions in production by sending events to Firebase:

```kotlin
ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
    override fun log(event: RecompositionEvent) {
        if (event.recompositionCount >= 10) {
            FirebaseAnalytics.getInstance(context).logEvent("excessive_recomposition") {
                param("tag", event.tag)
                param("composable", event.composableName)
                param("count", event.recompositionCount.toLong())
                param("unstable_params", event.unstableParameters.joinToString())
            }
        }
    }
})
```

## Tag-Based Filtering

Filter logs to only specific composables using tags:

```kotlin
val tagsToLog = setOf("user-profile", "checkout", "performance")

ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
    override fun log(event: RecompositionEvent) {
        if (!BuildConfig.DEBUG) {
            if (event.tag in tagsToLog || event.tag.isEmpty()) {
                // Send to analytics in release mode
                FirebaseAnalytics.getInstance(context).logEvent("recomposition") {
                    param("tag", event.tag)
                    param("composable", event.composableName)
                    param("count", event.recompositionCount.toLong())
                }
            }
        } else {
            // Log everything in debug mode
            Log.d("Recomposition", "${event.composableName}: #${event.recompositionCount}")
        }
    }
})
```

## RecompositionEvent Fields

| Field | Type | Description |
|-------|------|-------------|
| `composableName` | `String` | Name of the composable function |
| `tag` | `String` | Tag from `@TraceRecomposition(tag = "...")` |
| `recompositionCount` | `Int` | How many times this instance has recomposed |
| `parameterChanges` | `List<ParameterChange>` | Detailed parameter change information |
| `unstableParameters` | `List<String>` | Names of parameters causing instability |

## Filtering Logcat

With the default logger, you can filter Logcat to find specific information:

- **All recompositions**: Filter by `Recomposition`
- **Tagged recompositions**: Filter by `tag: <tag name>`
- **Specific composable**: Filter by the composable name and `Recomposition`
