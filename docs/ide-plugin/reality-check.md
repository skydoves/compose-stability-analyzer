# Stability Reality Check

The Stability Reality Check answers a question static analysis alone cannot: **does the compiler's stability prediction actually hold up at runtime?** Since Compose's *strong skipping* compares unstable parameters by instance identity (`===`) and stable ones by `equals()`, a parameter the compiler flags "unstable" frequently skips perfectly fine in practice — while another quietly recomposes on every frame. This feature joins the **static prediction** (from in-IDE analysis) with **live recomposition data** (from the heatmap stream) and grades each parameter accordingly.

![reality-check](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/reality-check.gif)

## Prerequisites

Reality Check reuses the heatmap's live data pipeline, so it shares the same requirements:

1. The [Compose Stability Analyzer Gradle plugin](../gradle-plugin/getting-started.md) applied to your project.
2. Composable functions annotated with [`@TraceRecomposition`](../gradle-plugin/trace-recomposition.md).
3. Logging enabled via `ComposeStabilityAnalyzer.setEnabled(true)` in your `Application` class.
4. A device or emulator connected via ADB, with the [Recomposition Heatmap](recomposition-heatmap.md) started.

## The Grades

Each parameter of a traced composable is graded by reconciling its compile-time prediction with what actually happened at runtime:

| Grade | Meaning | What to do |
|-------|---------|------------|
| 🟢 **Confirmed** | Predicted stable and it behaves stably — no wasteful recomposition. | Nothing. |
| 🟡 **False alarm** | Predicted *unstable*, but the instance stays referentially stable at runtime, so Compose skips it fine. | Safely ignore the "unstable" warning. |
| 🔴 **Silent waste** | The value is `equals`-equal but arrives as a **new instance every recomposition**, so strong skipping's `===` check fails and it recomposes when it could have skipped. | Hoist the value into `remember`, or make the type stable. |
| ⚪ **Justified** | The value genuinely changes between recompositions, so recomposing is necessary. | Nothing — correct behavior. |
| ⚪ **Observing** | Not enough recompositions observed yet to grade. | Interact with your app a few more times. |

!!! note "Why strong skipping makes this necessary"

    Before strong skipping, an unstable parameter always made a composable non-skippable. With strong skipping (Compose Compiler 2.0+), unstable parameters are compared by identity (`===`), so the *same instance* skips fine — the compiler's "unstable" label is often a false alarm. The real cost is a fresh-but-`equals`-equal instance every frame, which this feature surfaces as **silent waste**.

## Where Grades Appear

- **Editor inlay**: the block above each composable shows the recomposition count plus a grade summary (e.g. `(!) silent waste: user`).
- **Hover tooltip**: hovering a composable adds a "predicted vs. actual" column to the stability table, with a per-parameter grade badge.
- **Reality tab**: the **Reality** tool-window tab is a module-wide scorecard listing each observed composable, its per-parameter grades, and a "wasted recompositions (recent)" tally. Double-click a row to jump to the source.

## How to Use

1. Annotate the composables you want to grade with `@TraceRecomposition`.
2. Start the [Recomposition Heatmap](recomposition-heatmap.md) and interact with your app to drive recompositions.
3. Hover a composable, watch its inlay, or open the **Reality** tab — grades appear once a parameter has been observed a few times (the observation threshold filters out early noise).

!!! note "Grading needs observed recompositions"

    A grade is only assigned after a composable actually recomposes a few times. Because the recomposition tracker runs inside the composable body, a parameter that simply skips is *confirmed* as expected and is not re-tracked — that's the goal, not a problem.
