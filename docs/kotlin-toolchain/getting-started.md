# Kotlin Toolchain

[Kotlin Toolchain](https://kotlin-toolchain.org/) is JetBrains' standalone build system for Kotlin,
configured with YAML instead of Gradle scripts. The Compose Stability Analyzer **compiler plugin**
works there, because Kotlin Toolchain can apply any third-party Kotlin compiler plugin.

You get stability analysis, the `stability-info.json` report, and `@TraceRecomposition`
instrumentation. You do not get the Gradle plugin, so `stabilityDump`, `stabilityCheck` and the
`composeStabilityAnalyzer { }` DSL are unavailable. See
[What is not available](#what-is-not-available).

!!! note "Alpha, and moving"

    Kotlin Toolchain is Alpha and its plugin API is documented as guaranteed to change. Everything
    here was verified against the CLI that the `v0.12.2` wrapper provisions (`0.12.0-dev-4289`)
    with analyzer 0.14.0, on `jvm/lib`, `kmp/lib` and `js`. A working example lives in
    [`samples/kotlin-toolchain`](https://github.com/skydoves/compose-stability-analyzer/tree/main/samples/kotlin-toolchain).

## Setup

```yaml
product: jvm/lib

settings:
  compose:
    enabled: true
  kotlin:
    # Kotlin Toolchain defaults to its own bundled Kotlin version. Pin it to the version this
    # analyzer release is built against, or the compiler plugin may fail to load. See below.
    version: 2.4.20
    compilerPlugins:
      - id: com.skydoves.compose.stability.compiler
        dependency: com.github.skydoves:compose-stability-compiler:$version
        options:
          enabled: true
          stabilityOutputDir: build/stability
```

Then build:

```bash
./kotlin build
cat build/stability/stability-info.json
```

The `id` must be exactly `com.skydoves.compose.stability.compiler`. It has to match the plugin id
the compiler plugin registers, and a wrong value means the plugin is silently never applied.

## Pin the Kotlin version

Kotlin Toolchain ships its own default Kotlin version, and it is not necessarily the one this
analyzer is built against. At the time of writing the CLI defaults to Kotlin **2.4.10** while
analyzer 0.14.0 requires **2.4.20**.

A Kotlin compiler plugin is compiled against unstable compiler internals, so a mismatch can fail
the build or misbehave. Set it explicitly:

```yaml
settings:
  kotlin:
    version: 2.4.20
```

Look up the right pair in the [Kotlin Version Map](../version-map.md). Note that
`settings.compose.version` is a second, independent axis: our inference is aligned against a
specific Compose compiler release, so changing it can shift reported verdicts.

## stabilityOutputDir is required here

This is the one option you must set. Without it the plugin still compiles and still instruments
`@TraceRecomposition`, but it performs no stability analysis and writes no report of any kind. It
emits no warning about this either, so an absent report is the only symptom.

Kotlin Toolchain does not substitute build paths into option values, so the value is a literal
string. A relative value resolves against the compiler process working directory, which for
`./kotlin build` in a single module is that module's directory, putting the report at
`build/stability/stability-info.json`. The Gradle plugin instead passes an absolute, per compile
task path, so values are not portable between the two.

## The report is one shared file

!!! warning "Composables can vanish from the report"

    Every compilation in a module writes to the same literal `stabilityOutputDir`, and the last
    one to finish replaces the file. Nothing warns you.

This is the most important thing to know about the report, and it has two consequences.

**Tests overwrite your report.** `compilerPlugins` lives under `settings.kotlin`, which also applies
to test code. Verified: a `jvm/lib` module with a composable in `src` and another in `test`, after
`./kotlin test`, produced a report containing **only** the test composable. The main one was gone.
If you run tests and then read the report, you may be reading your test sources.

**Multiplatform targets overwrite each other.** In a `kmp/lib` module with more than one platform,
each platform writes the same path. Verified on `[jvm, js]` with one composable in `src@jvm` and one
in `src@js`: the report held the `jvm` one and silently omitted the `js` one. Composables in common
`src` are unaffected, since every platform reports them identically. The plugin does run correctly
on non JVM targets, confirmed by building `platforms: [js]` alone and getting a complete report, so
this is an overwrite and not a gap in platform support.

There is no per platform or per compilation override. Kotlin Toolchain rejects the attempt:

```
ERROR: Setting `compilerPlugins` can only be used without any @platform qualifier.
```

So treat the report as describing whichever compilation ran last. For a multiplatform module, read
it as covering your common sources. If you need dependable per target reporting, keep that module on
the Gradle plugin, which gives each compile task its own directory, though note that `stabilityDump`
there still reads a single compilation (see
[Kotlin Multiplatform](../gradle-plugin/kotlin-multiplatform.md)).

## Options

Pass options as a key to value map under `options`. Every one has a compiler side default except
`stabilityOutputDir`.

| Option | Default | Notes |
|---|---|---|
| `enabled` | `true` | `false` registers nothing at all |
| `stabilityOutputDir` | none | No default. Unset means no report and no analysis |
| `strongSkipping` | `true` | Must mirror the Compose compiler's own `StrongSkipping` flag, or reported `skippable` will contradict the generated code |
| `traceAll` | `false` | See [trace-all](#trace-all-behaves-differently-here) |
| `traceAllThreshold` | `2` | Skips the initial composition burst |
| `stabilityConfigurationFile` | none | A [stability configuration file](../gradle-plugin/stability-configuration-files.md), resolved like `stabilityOutputDir`. The Gradle DSL accepts a list, but a YAML mapping holds the key once, so exactly one file is expressible here |

`enabled`, `traceAll` and `strongSkipping` are parsed strictly and case sensitively: anything other
than `true` or `false` fails the build with a named error. `traceAllThreshold` is not, so a
malformed value silently falls back to `2`.

## The runtime dependency

Add the runtime if you use `@TraceRecomposition` or `@IgnoreStabilityReport` in your sources, **or
if you enable `traceAll`**:

```yaml
dependencies:
  - com.github.skydoves:compose-stability-runtime:$version
```

The Gradle plugin adds this for you. Kotlin Toolchain does not, so you declare it yourself, and the
two ways of getting it wrong fail very differently:

- **Loud.** Naming `@TraceRecomposition` yourself without the dependency fails compilation with
  `Unresolved reference 'TraceRecomposition'`.
- **Silent.** `traceAll: true` without the dependency needs no annotation in your sources, so it
  compiles cleanly, writes a normal looking report, and emits **zero** instrumentation. Verified: a
  successful build with `traceAll: true` and no runtime produced no tracker calls at all and no
  warning.

Stability reporting itself never resolves runtime symbols, so it works without the dependency. That
is why the silent case exists: only the tracing half needs the runtime.

## Trace-all behaves differently here

The `variants` gate is an **Android** property, not a Gradle one. A compilation named `main`, which
is what every non Android target uses, is always instrumented on the Gradle path too. So a Gradle
JVM or KMP module already behaves the way Kotlin Toolchain does.

What is genuinely missing here is any variant dimension to gate on, so `traceAll: true` instruments
every restartable composable in every build of that module, and test compilations are not excluded
the way the Gradle plugin excludes them. Keep the runtime gate wired to your own debug flag:

```kotlin
ComposeStabilityAnalyzer.setEnabled(isDebug)
```

Trace output goes wherever the platform logger writes: `System.out` on JVM, logcat on Android,
`console.log` on JS, `println` on native. A `jvm/lib` user should read stdout, not logcat.

## Previews are not excluded

The plugin excludes `@Preview` composables from the report and from trace-all by matching the
AndroidX annotation `androidx.compose.ui.tooling.preview.Preview`. Kotlin Toolchain's
`settings.compose` is Compose Multiplatform, whose annotation is
`org.jetbrains.compose.ui.tooling.preview.Preview`, which the plugin does not recognise.

So previews written against Compose Multiplatform appear in your report and get instrumented by
trace-all. Use `@IgnoreStabilityReport` on them if that is a problem.

## IDE Stability Explorer

The Explorer looks for `stability-info.json` under `build/stability` in a module's content root,
either directly or one directory deeper, and uses no Gradle API to find it. So
`stabilityOutputDir: build/stability` puts the report where it looks. Keep that value if you want
the tool window to pick it up.

Everything the IDE plugin derives statically (gutter icons, tooltips, inline hints, inspections,
cascade, blame) reads your source through the Kotlin Analysis API and does not depend on the build
system, so it is unaffected either way.

## What is not available

These are Gradle plugin features. Publishing Kotlin Toolchain plugins is listed as planned rather
than available, and custom tasks explicitly cannot customize the Kotlin compilation task, so there
is currently no way for us to supply them.

- `stabilityDump` and `stabilityCheck`, so no baseline `.stability` files and no CI regression gate
- Automatic runtime dependency wiring
- Exclusion of test compilations, and variant aware trace-all
- The `composeStabilityAnalyzer { }` DSL, including `stabilityValidation { }`, `ignoredPackages`,
  `ignoredProjects` and `includeTests`
- `android/lib`, which Kotlin Toolchain does not offer as a product type at all

Android was not tested here. One thing to expect if you try it: our Lint checks ship inside the
runtime AAR's `lint.jar` and are discovered by the Android Gradle plugin, so they most likely do not
run under Kotlin Toolchain. That is untested either way.

If you need stability validation in CI, keep that module on Gradle for now, or
[open an issue](https://github.com/skydoves/compose-stability-analyzer/issues) describing your setup.
