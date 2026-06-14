# Trace-All Mode

By default, runtime recomposition data flows only for composables you annotate with [`@TraceRecomposition`](trace-recomposition.md) one by one. **Trace-all mode** instruments every restartable composable in the module automatically — as if each one carried the annotation — so the [Live Heatmap](../ide-plugin/recomposition-heatmap.md), [Reality Check](../ide-plugin/reality-check.md), and [Stability Doctor](../ide-plugin/stability-doctor.md) receive module-wide runtime data without manual annotations.

## Setup

```kotlin
composeStabilityAnalyzer {
  traceAll {
    enabled.set(true)             // default: false (opt-in)
    threshold.set(2)              // default: 2
    variants.set(listOf("debug")) // default: ["debug"]
  }
}
```

## How It Behaves

- **Debug-oriented by default**: only compilations whose variant name equals or ends with one of the `variants` tokens are instrumented — `debug` matches `debug`, `stagingDebug`, and `fullDebug`, while release builds stay untouched. Test compilations are never instrumented. For KMP/JVM targets without variants, the runtime `ComposeStabilityAnalyzer.setEnabled(...)` gate is the production safety net.
- **Explicit annotations win**: a composable with `@TraceRecomposition` keeps its own `tag`, `threshold`, and `traceStates` settings.
- **Quiet by default**: auto-traced composables only start logging from their 2nd recomposition (`threshold = 2`), so the initial composition of a screen produces no logcat flood. A composable that never *re*-composes emits nothing — which is exactly the population that needs no attention. Raise the threshold if very active composables (e.g. animations) get noisy.
- **Skips what shouldn't be traced**: `@Preview` composables, `@IgnoreStabilityReport`, inline/readonly/non-restartable composables, and property getters are excluded automatically.
- **Cheap when disabled**: with `ComposeStabilityAnalyzer.setEnabled(false)`, the residual cost per composition is a map lookup plus early-returned calls.

## Fully Qualified Names in Logs

Trace-all-capable runtimes (0.10.0+) append two trailing tokens to every log header:

```
D/Recomposition: [Recomposition #2] UserProfile (1.20ms) (fq: com.example.profile.UserProfile) (auto)
```

- `(fq: ...)` — the fully qualified composable name, emitted for annotated and auto-traced composables alike. The IDE uses it to attribute runtime data precisely, so two composables that share a simple name across packages no longer share an inlay.
- `(auto)` — marks events from trace-all instrumentation (absent for explicitly annotated composables).

Both tokens are **trailing and optional**, so older log parsers simply ignore them — old IDE + new runtime and new IDE + old runtime combinations keep working.

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Opt-in master switch for auto-instrumentation. |
| `threshold` | `2` | Recomposition count at which auto-traced composables start logging. |
| `variants` | `["debug"]` | Android variant/build-type name tokens (case-insensitive equals/endsWith match) that receive instrumentation. |

!!! note "Logging still requires the runtime gate"

    Trace-all only decides *what gets instrumented at compile time*. No logs appear unless you also call `ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)` in your `Application` class, exactly as with `@TraceRecomposition`.
