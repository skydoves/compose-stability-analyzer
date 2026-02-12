# Code Inspections

Code inspections go beyond visual indicators — they actively suggest improvements for unstable composables.

## What Inspections Do

When you have an unstable composable, the plugin can:

1. **Highlight the issue** with a warning underline
2. **Suggest quick fixes** via the Alt+Enter menu
3. **Add @TraceRecomposition** to help you debug recompositions
4. **Provide suppression options** if the instability is intentional

This is like having an automated code review for Compose performance. The plugin doesn't just tell you about problems — it helps you fix them.

## Available Quick Fixes

### Add @TraceRecomposition

When a composable has unstable parameters, the plugin offers to add the `@TraceRecomposition` annotation. This enables runtime logging that shows you exactly when and why the composable recomposes.

```kotlin
// Before: unstable composable
@Composable
fun UserCard(user: MutableUser) {
    Text(user.name)
}

// After: with trace annotation added via quick fix
@TraceRecomposition(tag = "user-card")
@Composable
fun UserCard(user: MutableUser) {
    Text(user.name)
}
```

### Suppress Warning

If the instability is intentional (e.g., you know the parameter rarely changes), you can suppress the warning for that specific composable.

## Inspection Severity

You can configure the severity level of stability inspections:

1. Go to **Settings** > **Editor** > **Inspections**
2. Search for **Compose Stability**
3. Adjust the severity (Error, Warning, Weak Warning, or Information)
