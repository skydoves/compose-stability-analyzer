# Custom Logger

By default, `@TraceRecomposition` logs recomposition events to Logcat using Android's `Log.d()`. You can completely replace this behavior by implementing your own `RecompositionLogger`. This is useful when you want to format logs differently, send analytics events, or integrate with your existing logging infrastructure.

## Setting a Custom Logger

To override the default logging behavior, call `ComposeStabilityAnalyzer.setLogger()` with your own `RecompositionLogger` implementation. The `log()` method receives a `RecompositionEvent` containing everything about the recomposition — which composable, how many times, which parameters changed, and which are unstable.

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

In this example, the logger builds a human-readable message that includes:

- The recomposition count and composable name
- The tag (if one was set via `@TraceRecomposition(tag = "...")`)
- Each parameter's name, type, and whether it changed, is stable, or is unstable

You can place this call in your `Application.onCreate()` alongside `ComposeStabilityAnalyzer.setEnabled()`.

## Firebase Analytics Integration

One of the most powerful use cases for a custom logger is **tracking excessive recompositions in production**. By sending recomposition data to Firebase Analytics (or any analytics platform), you can identify performance hotspots in real user sessions — not just during local development.

The example below sends a Firebase event whenever a composable recomposes 10 or more times. This threshold filters out normal recompositions (initial layout, expected state changes) and only captures composables that are recomposing excessively, which often indicates a stability problem.

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

!!! note "Choosing a threshold"

    The threshold of `10` is a starting point. Adjust it based on your app's behavior — list items or frequently updating screens may need a higher threshold (e.g., `20` or `50`), while static screens should rarely exceed `3`.

With this data flowing into Firebase, you can:

- Create dashboards showing which composables recompose most across your user base
- Set up alerts when a composable exceeds a recomposition threshold
- Track whether stability fixes reduce recomposition counts over time
- Correlate excessive recompositions with user-reported performance issues

## Tag-Based Filtering

Tags (set via `@TraceRecomposition(tag = "...")`) let you categorize composables by feature or screen. A tag-based logger uses these tags to route events differently — for example, sending analytics for specific features while logging everything else to Logcat during development.

This pattern is especially useful in large apps where you want to monitor critical flows (checkout, authentication, feed) in production without the noise from every composable in the app.

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

**How this works:**

- **In release builds** (`!BuildConfig.DEBUG`): Only composables with tags in the `tagsToLog` set (or untagged composables) send events to Firebase. This keeps analytics focused on the features you care about.
- **In debug builds**: Every recomposition is logged to Logcat regardless of tag, giving you full visibility during development.

You can update the `tagsToLog` set as your monitoring needs change — add new tags when investigating a performance issue, remove them once the issue is resolved.

## RecompositionEvent Fields

The `RecompositionEvent` object passed to your logger contains all the information about a recomposition. Here is a reference of all available fields:

| Field | Type | Description |
|-------|------|-------------|
| `composableName` | `String` | Name of the composable function that recomposed |
| `tag` | `String` | Tag from `@TraceRecomposition(tag = "...")`. Empty string if no tag was set |
| `recompositionCount` | `Int` | How many times this composable instance has recomposed (starts at 1) |
| `parameterChanges` | `List<ParameterChange>` | Detailed information about each parameter — its name, type, whether it changed, and whether it is stable |
| `unstableParameters` | `List<String>` | Convenience list of parameter names that are unstable. Useful for quick checks without iterating `parameterChanges` |

Each `ParameterChange` in the `parameterChanges` list contains:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Parameter name |
| `type` | `String` | Parameter type (e.g., `User`, `String`, `List<Product>`) |
| `changed` | `Boolean` | Whether this parameter's value changed since the last composition |
| `stable` | `Boolean` | Whether the Compose compiler considers this parameter stable |

## Filtering Logcat

When using the default logger (without setting a custom one), recomposition events are logged to Logcat with the tag `Recomposition`. You can filter Logcat to find specific information:

- **All recompositions**: Filter by `Recomposition`
- **Tagged recompositions**: Filter by `tag: <tag name>` (e.g., `tag: user-profile`)
- **Specific composable**: Filter by the composable name (e.g., `ProductCard`) combined with `Recomposition`
- **Unstable parameters**: Filter by `Unstable parameters:` to find composables with stability issues
