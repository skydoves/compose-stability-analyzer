# Recomposition Blame

Recomposition Blame traces a recomposition back to **its cause**. Where the [Recomposition Cascade](recomposition-cascade.md) walks *downstream* (what this composable affects), Blame walks *upstream* (what caused this composable to recompose) — in two complementary ways.

## 1. State Write-Site (runtime)

For composables annotated with `@TraceRecomposition(traceStates = true)`, a Compose Snapshot write observer records *where* each internal state was mutated and appends it to the log:

```
[state] counter: Int changed (0 → 1) ← onClick (MainActivity.kt:97)
```

The `← method (File.kt:line)` suffix tells you exactly which line of code wrote the state that triggered the recomposition — turning "why did this recompose?" guesswork into a precise pointer. The same suffix appears in the heatmap inlay's tooltip.

### Prerequisites

1. `@TraceRecomposition(traceStates = true)` on the composable.
2. `ComposeStabilityAnalyzer.setEnabled(true)` in your `Application`.
3. A running app on a connected device (Android/JVM).

!!! note "Supported state"

    Write-site capture works for **delegated scalar state** — `var x by remember { mutableStateOf(...) }` (and `mutableIntStateOf`, etc.). `derivedStateOf` has no write site, and non-delegated state (`val s = mutableStateOf(...)`) is not tracked.

## 2. Parameter Provenance (static)

To trace where a *parameter's* value comes from, right-click any `@Composable` in the editor and choose **"Blame this Recomposition"**:

![blame-menu](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/blame-context-menu.png)

The **Blame** tool-window tab opens with the reverse of the cascade — which composables call this one, and where each argument's value originates:

![blame-window](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/blame-window.png)

Each caller node lists the arguments it passes, annotated with the static origin of each value:

| Origin label | Meaning |
|--------------|---------|
| `val property` / `var property` | The argument reads a property (e.g. `viewModel.products`). |
| `parameter` | The argument is forwarded from the caller's own parameter. |
| `call to foo()` | The argument is the result of a function call. |
| `expression` | A literal, lambda, or other dynamic expression. |

This view is **fully static** — it needs no running app, so you can trace provenance while reading code.

## How to Use

- **Write-sites**: add `@TraceRecomposition(traceStates = true)`, run the app, and read the `← …` suffix on `[state]` lines in Logcat (or the heatmap tooltip).
- **Provenance**: right-click a composable in the editor → **Blame this Recomposition** → inspect the upstream tree. Double-click a node to jump to its source.

!!! note "Best-effort provenance"

    Parameter provenance is a static estimate. It searches project-source callers only (library callers are not shown), maps arguments by name/position (which can be approximate for `vararg` or mixed named/positional calls), and resolves the outermost expression; dynamic origins are shown as `expression`.
