# Code Inspections

Code inspections go beyond passive visual indicators — they actively analyze your composable functions and suggest concrete improvements. While gutter icons and inline hints show you the current state, inspections draw your attention to problems and help you fix them, making them the most actionable feature of the plugin.

## How Inspections Work

When the plugin detects an unstable composable, it highlights the function with a warning underline in the editor. This is the same kind of underline you see for other IDE warnings (like unused variables or deprecated API calls), so it fits naturally into your existing code review workflow.

Placing your cursor on the highlighted function and pressing **Alt+Enter** (or **Option+Enter** on macOS) opens the quick fix menu. The plugin offers context-aware actions based on the specific stability issue — from adding runtime tracing to suppressing the warning if the instability is intentional.

This approach mirrors having an automated code reviewer focused specifically on Compose performance. Rather than requiring you to remember to check stability manually, the plugin surfaces issues as you write code and provides one-click fixes.

## Available Quick Fixes

### Add @TraceRecomposition

When a composable has unstable parameters, the most common first step is understanding how the instability affects runtime behavior. The plugin offers to add the `@TraceRecomposition` annotation, which enables detailed runtime logging showing exactly when and why the composable recomposes.

```kotlin
// Before: unstable composable with no visibility into recomposition behavior
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

Once the annotation is added, running the app produces Logcat output showing each recomposition event — including which parameters changed, which are stable, and which are unstable. This runtime data helps you decide whether the instability is actually causing performance issues or is benign in practice. See the [TraceRecomposition](../gradle-plugin/trace-recomposition.md) documentation for details on reading the logs.

### Suppress Warning

Not every unstable composable needs to be fixed. Some composables have parameters that are technically unstable (e.g., a `List<Item>`) but rarely change in practice, or the composable is simple enough that re-executing it has negligible cost. In these cases, the quick fix menu offers a suppression option that disables the warning for that specific composable without affecting the rest of your project.

Suppressing a warning is a deliberate decision — it tells the plugin (and anyone reading your code) that you've considered the instability and decided it's acceptable. This is preferable to disabling inspections globally, which would hide genuine issues.

## Inspection Severity

You can configure how prominently the plugin highlights stability issues. Navigate to **Settings** > **Editor** > **Inspections** and search for **Compose Stability**. The severity level controls how the issue is presented in the editor:

**Error** displays a red underline and treats the issue as a compilation-level problem. This is the strictest setting and is useful if your team has decided that all composables should be stable.

**Warning** (the default) displays a yellow underline. This is appropriate for most projects — it draws attention to instability without being as visually aggressive as an error.

**Weak Warning** displays a subtle underline that's less prominent than a full warning. This works well during initial adoption, when you want to see stability issues without them dominating your editor.

**Information** shows the issue only in the inspection results panel and doesn't underline the code at all. Use this if you want to track stability issues without any visual noise in the editor.
