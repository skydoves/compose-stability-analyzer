# Stability Doctor

The Stability Doctor answers the question every other feature leads up to: **what should I fix first, and what will I gain?** It scans your project, scores every composable by combining the static stability verdict, the downstream [cascade](recomposition-cascade.md) blast radius, and the measured runtime waste from the [Reality Check](reality-check.md), then presents a ranked list of prescriptions with one-click fixes.

![doctor](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/doctor.png)

## Scores: ESTIMATED vs. MEASURED

Each prescription carries a score (0–100) with one of two badges:

| Badge | Inputs | When |
|-------|--------|------|
| **ESTIMATED** | Unstable/unknown parameter count, skippability, cascade blast radius | Always available — no device needed. Capped below measured scores. |
| **MEASURED** | Observed wasted recompositions × average duration, silent-waste grades, blast radius | When a [heatmap](recomposition-heatmap.md) session has collected enough observations. |

Two ranking behaviors follow from this design:

- A composable with **confirmed, measured waste always outranks** a speculative static finding — estimated scores are capped below the measured range.
- A composable the compiler flags as unstable but that **skips fine at runtime sinks toward the bottom**, because the measurement proved the warning to be a false alarm.

## Prescriptions

Expanding a prescription shows its problem parameters, each with:

- The **static reason** (e.g. "Has 2 mutable (var) properties"), from in-IDE analysis.
- The **Reality Check grade** (silent waste / false alarm / justified), when live data exists.
- The **value provenance** at each call site (a `val`/`var` property, a parameter, a function call), reusing the [Blame](recomposition-blame.md) analysis.

## One-Click Fixes

Under each cause, the Doctor offers fixes you can apply by double-clicking:

| Fix | Applies to | Safety |
|-----|-----------|--------|
| **Change `var` → `val`** | The parameter's class, when it lives in your project | Searches for write usages first; refuses to apply if any assignment exists. |
| **Annotate with `@Immutable` / `@Stable`** | Project classes without an existing stability annotation | The confirmation dialog reminds you this is a *promise* to the compiler, not a verification. `@Stable` is offered instead of `@Immutable` when the class keeps `var` properties. |
| **Add to stability configuration file** | Library types you cannot modify | Skipped for platform/known-stable types and patterns already covered. |
| **Wrap argument in `remember(keys) { ... }`** | Call-site arguments of *silent waste* parameters | Offered only when safety rules prove the transformation valid (see below). |

!!! note "Remember-hoisting safety rules"

    The `remember` fix is the cure for an `equals`-equal value that arrives as a new instance on every recomposition. The Doctor offers it only when **all** of the following hold: the argument is evaluated directly in composition (not inside a lambda), it is a genuine computation (not a bare reference, constant, or lambda), it contains no composable calls, and every input resolves to a caller parameter or an earlier local `val`. The remember keys are derived from those inputs automatically, and a preview dialog shows the exact replacement before anything changes. Purity cannot be proven statically — verify the expression is side-effect free before confirming.

## How to Use

1. Open **View → Tool Windows → Compose Stability Analyzer → Doctor** tab (or **Code menu → Run Stability Doctor**) and hit refresh. This works immediately with `ESTIMATED` scores — no device required.
2. For measured scores, enable [trace-all](../gradle-plugin/trace-all.md) (or annotate composables with `@TraceRecomposition`), start the [Recomposition Heatmap](recomposition-heatmap.md), and interact with your app. Rows upgrade to `MEASURED` and re-rank automatically while the session runs.
3. Double-click a row to jump to the composable; double-click a fix to apply it. After a fix is applied, the affected prescription is re-analyzed.

## Configuration

**Settings → Tools → Compose Stability Analyzer → Stability Doctor** provides:

| Setting | Default | Description |
|---------|---------|-------------|
| Enable Stability Doctor | on | Master switch for the Doctor tab and auto-refresh. |
| Max cascade candidates | 15 | How many top-scored composables get the (expensive) downstream blast-radius analysis. |
| Auto-refresh interval | 10s | Refresh cadence while a heatmap session is running. |
| Minimum score | 5 | Prescriptions scoring below this are hidden. |
| Include test sources | off | Whether composables in test source roots are scanned. |

!!! tip "Same-named composables"

    Runtime data is matched by fully qualified name when the runtime reports it (version 0.10.0+). If two composables share a simple name and the runtime data cannot be attributed precisely (older runtimes), the prescription stays `ESTIMATED` with a note rather than guessing.
