# Kotlin Toolchain sample

A minimal [Kotlin Toolchain](https://kotlin-toolchain.org/) module that applies the Compose
Stability Analyzer compiler plugin. It is the worked example behind
[docs/kotlin-toolchain/getting-started.md](../../docs/kotlin-toolchain/getting-started.md).

## Running it

This sample does not check in the `kotlin` wrapper script. Get one the way the Kotlin Toolchain
docs describe (`kotlin init`, or copy the wrapper out of an existing Kotlin Toolchain project),
drop it in this directory, then:

```bash
./kotlin build
cat build/stability/stability-info.json
```

The wrapper provisions its own CLI distribution and JDK, so no local toolchain setup is needed.

The wrapper is deliberately absent rather than committed. The only wrapper published at the time of
writing pins a development build of the CLI (`0.12.0-dev-4289`) together with its SHA256, and
committing a script that downloads and executes a pinned development binary into a library
repository is not a trade worth making for a sample. The Kotlin Toolchain repository does commit
wrappers into its own examples, but those share its monorepo pin.

## What it demonstrates

| Declaration | Expected report |
|---|---|
| `ShowStable(StableUser)` | `STABLE`, `skippable: true` |
| `ShowUnstable(UnstableUser)` | `UNSTABLE`, and still `skippable: true` under strong skipping |
| `ShowRuntime(UserPage)` | `RUNTIME`, a standard collection defers to a runtime check |
| `Traced(StableUser)` | tracker calls emitted into the bytecode |

## Verified state

Checked against the CLI that the `v0.12.2` wrapper provisions (`0.12.0-dev-4289`), analyzer
`0.14.0`, Kotlin pinned to `2.4.20`, on macOS arm64:

- The report is written to `build/stability/stability-info.json`. Removing the `compilerPlugins`
  block produces no report at all, so the file is attributable to this plugin.
- Verdicts are identical to the same sources built through the Gradle plugin, on `skippable`,
  `restartable`, and every parameter stability.
- `@TraceRecomposition` reaches the bytecode. `rememberRecompositionTracker`, `trackParameter` and
  `recordDuration` are all present in the compiled class, so instrumentation is not silently
  skipped.
- Option plumbing reaches the compiler: `strongSkipping: false` flips `ShowUnstable` to
  `skippable: false`, and a `stabilityConfigurationFile` naming `UnstableUser` flips it to `STABLE`.

## Why there is no `@StabilityInferred` case here

An earlier draft included a pair of classes, one annotated `@StabilityInferred` and one not, meant
to show that the analyzer ignores that annotation for classes in the module being compiled. It was
removed because **it cannot demonstrate anything**: the annotation is only consulted for classes
from outside the compilation unit, so in a single module sample every value of `parameters` reports
the same verdict. Measured: `parameters = 0`, `parameters = 1` and no annotation at all all produce
`RUNTIME`.

That behaviour is worth guarding, and it already is, by
`compiler-tests/src/test/data/dump/ir/StabilityInferredIsBinaryOnly.kt` and
`StabilityInferredCrossModule.kt`, which run in CI and can actually fail. Do not re-add it here.

## Releasing

`module.yaml` is a real build file, so it cannot use the `$version` placeholder the docs use. It
hardcodes three things that must be bumped on every release: both artifact coordinates and the
pinned `kotlin.version`. Nothing enforces this, because the sample is not built in CI.

## Scope of what this covers

**Not built in CI**, and **not covered by Spotless** (the root project applies no conventions
plugin, so `samples/` is outside `target("**/*.kt")`; the license header here is hand maintained).

Kotlin Toolchain is Alpha and its plugin API is documented as guaranteed to change, so a CI job here
would report upstream churn as our breakage. What that leaves uncovered is the Kotlin Toolchain
*wiring*, meaning option plumbing and the report path, rather than the inference itself, which
`compiler-tests` covers. Rerun this by hand when the wiring could have moved.
