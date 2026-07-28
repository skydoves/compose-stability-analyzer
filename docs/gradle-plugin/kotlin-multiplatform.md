# Kotlin Multiplatform

The compiler plugin runs on every Kotlin compilation in your module, not only the Android or JVM one, and the runtime is published as a multiplatform library. Stability analysis therefore covers all of your targets, and `@TraceRecomposition` logs recompositions on all of them.

## Supported targets

The runtime publishes these targets:

| Family | Targets |
|--------|---------|
| Android | `androidTarget` |
| JVM | `jvm` |
| iOS | `iosX64`, `iosArm64`, `iosSimulatorArm64` |
| macOS | `macosX64`, `macosArm64` |
| Linux | `linuxX64`, `linuxArm64` (since 0.12.0) |
| Windows | `mingwX64` (since 0.12.0) |
| Web | `js` (browser and Node.js), `wasmJs` |

watchOS, tvOS, Android Native and wasmWasi are not published yet. If your project needs one of them, please [open an issue](https://github.com/skydoves/compose-stability-analyzer/issues). Adding a target is a small change on our side because the runtime groups its source sets by target family, so a new target inherits implementations that already exist.

## Setup

Apply the plugin to your shared module exactly as you would for an Android module:

```kotlin
plugins {
    alias(libs.plugins.stability.analyzer)
}
```

The plugin detects the Kotlin Multiplatform plugin and adds the runtime to the `commonMain` source set, so you do not declare the dependency yourself. If you prefer to add it manually, the coordinates are:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.skydoves:compose-stability-runtime:$version")
        }
    }
}
```

Logging is off until you enable it. Put this in your shared initialization code, behind whatever debug flag your project uses:

```kotlin
ComposeStabilityAnalyzer.setEnabled(isDebugBuild)
```

On Android that flag is usually `BuildConfig.DEBUG`. Other targets have no equivalent, so pass in your own value. This call is the only thing standing between a release build and recomposition logging on targets that have no variant concept, so it is worth wiring up properly.

## What each target supports

| Feature | Android | JVM | iOS, macOS, Linux, Windows | JS, Wasm |
|---------|:-------:|:---:|:--------------------------:|:--------:|
| Stability analysis and `stability-info.json` | Yes | Yes | Yes | Yes |
| `@TraceRecomposition` parameters, tags, thresholds | Yes | Yes | Yes (0.12.0) | Yes (0.12.0) |
| Recomposition timing in the log header | Yes | Yes | Yes (0.12.0) | Yes (0.12.0) |
| Internal state changes with `traceStates = true` | Yes | Yes | Yes | Yes |
| State write site, `← onClick (Screen.kt:42)` | Yes | Yes | No | No |
| Live Heatmap, Reality Check, measured Doctor scores | Yes | No | No | No |
| Android Lint rules | Yes | No | No | No |

Two of those rows deserve an explanation.

**State write sites** need Compose's Snapshot write observer to record where a state was mutated. That observer is wired on Android and JVM only, so `[state]` lines on other targets show the value change without the `← method (File.kt:line)` suffix.

**The IDE plugin's live features** read `@TraceRecomposition` output from a connected device over ADB logcat, which means Android. Everything the IDE derives from static analysis, including gutter icons, tooltips, inline hints, the Stability Explorer, Recomposition Cascade and estimated Doctor scores, works the same for a multiplatform module. To collect runtime data from a non Android target, install a [custom logger](custom-logger.md) and send the events wherever you want them.

## Where the logs go

| Target | Destination |
|--------|-------------|
| Android | Logcat, tag `Recomposition` |
| JVM, native, wasmJs | Standard output, through `println` |
| JS | Browser or Node console, through `console.log` |

The line format is identical everywhere, so the same parsing you use for Logcat works for the other targets:

```
[Recomposition #2] Greeting (tag: e2e) (0.01ms) (fq: com.example.Greeting)
  ├─ [param] name: kotlin.String changed (first → update-0)
  ├─ [param] holder: com.example.Mutable unstable (Mutable@5105ae0)
  └─ Unstable parameters: [holder]
```

## Trace-all in a multiplatform module

[Trace-all](trace-all.md) instruments every restartable composable in the module. Its `variants` option filters by compilation name, which behaves differently across targets:

- A compilation named `main`, which is what every non Android target uses, is always instrumented.
- Variant based names such as `debug` or `stagingDebug`, which only exist when the Android plugin is applied, are matched against the `variants` list.
- Test compilations are never instrumented.

So on iOS, desktop and web the `variants` list does not protect a release build. `ComposeStabilityAnalyzer.setEnabled(false)` is the switch that matters there, and with it the residual cost per composition is a cache lookup plus calls that return immediately.

## Stability validation in a multiplatform module

Task registration depends on whether the Android plugin is applied to the same module:

- Multiplatform without Android: one `stabilityDump` and one `stabilityCheck` task.
- Multiplatform with `androidTarget`: variant specific tasks as well, such as `debugStabilityDump` and `releaseStabilityCheck`.

As always, run the task after compiling, because it reads what the compiler wrote:

```bash
./gradlew :shared:compileKotlinJvm :shared:stabilityDump
```

The baseline is generated from one compilation's data rather than merged across every target. Composables in `commonMain` are analyzed identically for each target, so this makes no difference for most projects. It does matter when a platform source set declares its own composables: those appear in the baseline only when that target's compilation is the one the task reads. If your module has platform specific composables, delete `build/stability` and compile only the target you want the baseline to describe.

## Requirements

Use the exact Kotlin version this release is built against, listed in the [version map](../version-map.md). The compiler plugin extends Kotlin compiler internals, so a mismatch usually fails the build. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) must be applied to the module as well, since there are no composables to analyze without it.
