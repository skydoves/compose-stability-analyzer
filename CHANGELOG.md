# Compose Stability Analyzer - Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **`unstableOnly` no longer drops composables that `stabilityCheck` then reports.** The baseline was filtered by `skippable` alone, while `compareStability` decides whether a *new* composable is a regression with `hasUnstableParameter` (#192). Under strong skipping those disagree constantly: a composable stays skippable while a parameter is `UNSTABLE`, `RUNTIME` or `UNKNOWN`, so the entry was written out of the baseline and reported as a new unstable composable on the very next check, with no way to accept it because `stabilityDump` dropped it again. The baseline is now filtered by the union issue #128 asked for, recording a composable when it has an unstable parameter, or is not skippable, or is not restartable. That is a superset of the predicate the check reports new composables on, so a dumped baseline can no longer report its own code. On one 54-module project, 296 of 1,543 entries were affected.

Stability inference is now checked rule-by-rule against the Compose compiler's own `analysis/Stability.kt`, and several rules were wrong. The sample app is verified against the compiler's own metrics output: **0 skippable and 0 restartable disagreements across 49 composables**.

- **A delegated `var` no longer makes a class unstable.** Compose exempts it (`if (member.isVar && !member.isDelegated) return Unstable`) and scores the delegate instead, so the canonical state holder is stable:
  ```kotlin
  class UiState { var text by mutableStateOf("") }   // was UNSTABLE, Compose says stable
  ```
  This told users the correct Compose pattern was broken. Properties are now scored by their backing field type, as Compose does, so `var x by mutableStateOf(...)` is seen as the `@Stable MutableState` it is.
- **`@StabilityInferred` was decoded backwards.** The `parameters` value is a bitmask, not a boolean: bits `0..n-1` mark which type parameters the stability depends on and bit `n` is a "known stable" sentinel, so for a class with no type parameters `1` means stable and `0` means not stable. Reading it as `== 0 -> STABLE` reported cross-module classes the compiler had marked **unstable** as **STABLE** — the one direction that makes a user do nothing when they should act. Fixed in the compiler and at both IDE sites.
- **Open, abstract and sealed classes no longer short-circuit to `UNKNOWN`.** The Compose compiler has no such rule; `modality == FINAL ? Stable : Unknown` is the *seed* of its member loop, so a non-final class still becomes unstable from a `var` or an unstable member and still reaches the cross-module rules. Short-circuiting skipped all of that: `androidx.lifecycle.ViewModel` reported `UNKNOWN` where the compiler says unstable, and a sealed class with no stored properties reported **STABLE** where the compiler says uncertain.
- **A superclass now contributes unconditionally.** It was only consulted when the subclass declared no state of its own, so `class Foo : ViewModel() { val a = "" }` reported **STABLE** while the compiler reported unstable. Only the single class supertype is considered, matching Compose; scanning every supertype made the verdict depend on the order they were written (`class A : Marker, ViewModel()` and `class A : ViewModel(), Marker` disagreed).
- **`object` declarations are stable**, a rule that was missing entirely. A singleton's identity never changes, so its properties cannot destabilize a parameter of its type.
- **`vararg` parameters are scored by their element type**, not the synthesized `Array<out T>`, which is a stdlib stub and made every vararg parameter UNSTABLE. Applied to both the report and the recomposition-tracing path, which must agree because the log line is a wire protocol the IDE parses.
- **Suspend function types are no longer claimed stable.** Compose's stable shortcut covers ordinary and `@Composable` function types only, so a `kotlin.coroutines.SuspendFunctionN` is analysed as the interface it is.
- **The IDE now resolves `@StableMarker` as a rule**, walking supertypes, so a project's own marker annotation works there as it already did in the compiler, and it recognises `object` declarations and delegated `var`s the same way.
- **`stabilityConfigurationFiles` reaches the compiler again.** The compiler read the top-level Gradle property while the dump and check tasks read the deprecated nested one, and the README documents the nested form: a configuration written the documented way was honoured by `stabilityCheck` but silently ignored by the compiler's own inference. Both forms are now merged for every consumer.

### Migration

Verdicts change for real code, so **run `./gradlew stabilityDump` once after upgrading** and commit the result. The corrections move some parameters in both directions: `STABLE -> UNSTABLE` where a class inherits from an unstable base, `STABLE -> UNKNOWN` for sealed classes, and `UNSTABLE -> STABLE` for state holders built on delegated `var`s.

### Known gap

A user-defined generic class is still reported `RUNTIME` regardless of its type argument. The Compose compiler records such a class as `Stability.Parameter(T)` and substitutes the actual argument at each use site, so `UiResult<Unit>` is stable there and merely `RUNTIME` here. Implementing that needs an argument-aware recursion guard and is tracked separately.

## [0.14.0] - 2026-09-16

### Changed
- **Bumped Kotlin to 2.4.20** (from 2.4.10). Use the same Kotlin version as this library. The compiler plugin, runtime, Gradle plugin, Lint rules and IntelliJ plugin all build against 2.4.20, and all twelve published runtime targets still compile. **No stability verdicts change**: `stabilityDump` on the sample app produces no diff and `stabilityCheck` passes against the committed baselines, so `.stability` files do not need refreshing for this bump alone.

  Unlike the 2.4.10 bump, the Compose compiler's `analysis/Stability.kt` is *not* byte-identical this time. Its one semantic addition is a branch for [KEEP-0454](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0454-better-immutability-value-classes-MFVC.md) "full value classes" (a `value class` *without* `@JvmInline`), which now report `Stable` when marked `@Stable`/`@Immutable`, `Unstable` when abstract, and otherwise the combined stability of their underlying properties. The language feature behind them, `FullValueClasses` ([KT-84904](https://youtrack.jetbrains.com/issue/KT-84904)), is not enabled in any released language version and forces pre-release binaries when switched on, and `-Xvalue-classes` was removed from the JVM compiler in 2.4.20 — so no code you can ship today reaches that branch.
- **The compiler plugin no longer reads `IrAnnotation.symbol`**, which 2.4.20 deprecates in favour of `classSymbol` + `argumentMapping` ([KT-74200](https://youtrack.jetbrains.com/issue/KT-74200)). `@TraceRecomposition`'s arguments and `@StabilityInferred(parameters = ...)` are now looked up by name through `argumentMapping` rather than by position in the annotation constructor — the same move the Compose compiler made for its own `@StabilityInferred` read in this release. The annotation itself is found with `IrAnnotationContainer.getAnnotation`, which is the predicate the surrounding `hasAnnotation` guards already used, so a guard and its reader can no longer disagree about which annotation matched.

### Fixed

- **`skippable` now follows Compose's strong skipping semantics.** The Compose compiler enables its `StrongSkipping` feature flag by default, and with it on an unstable parameter no longer prevents a restartable composable from skipping (Compose compares such parameters by instance identity). The compiler plugin and the Gradle tasks were still reporting the strong-skipping-*off* rule, while the IDE already modelled the on rule, so gutter icons and the generated `.stability` file disagreed about the same function. Measured against the Compose compiler's own metrics on the sample app, **14 of 37 composables (38%) reported the wrong `skippable` value**; that is now 0 of 45. A new `composeStabilityAnalyzer { strongSkipping }` option (default `true`) mirrors the compiler flag for projects that turn it off.
- **`restartable` now matches `shouldBeRestartable()`.** Added the clauses this plugin was missing: `open` members of non-final classes (including interface methods with a body), abstract declarations, local composables, and composable delegated property accessors. Removed `@ReadOnlyComposable`, which the Compose compiler does not consult here: a `Unit`-returning read-only composable *is* restartable, and the familiar read-only composables are non-restartable because they return a value.
- **Recomposition durations are no longer lost on comma-decimal locales.** The Android and JVM runtimes formatted the duration with the default locale, so on a `de`, `fr`, `pt-BR`, `ru`, `tr` or `id` device the log line read `(1,20ms)`. The IDE's parser expects a dot, so every duration silently parsed back as `0.0`: heatmap tooltips lost their timing line and the Stability Doctor's measured waste collapsed to its 1ms floor. Durations now format with `Locale.ROOT`, matching what the native, JS and Wasm runtimes already emitted, and the parser still accepts the old comma form.
- **Generic type arguments are honoured for known-stable types.** `Pair`, `Triple`, `Comparator`, `ClosedRange`, the Guava and kotlinx immutable collections and `dagger.Lazy` carry a bitmask saying which type arguments must themselves be stable. Ignoring it reported `Pair<String, MutableUser>` as `STABLE`.
- **`@StableMarker` is resolved as a rule rather than hardcoded.** Any annotation whose own class carries `@StableMarker` now marks a type stable, walking supertypes, so a project's own marker annotation works as it does in the Compose compiler. `com.google.errorprone.annotations.Immutable` is recognised too.
- The IDE plugin no longer uses `KaTypeNullability`, which the Kotlin 2.5 Analysis API deprecates at `HIDDEN` level (an unresolved reference that `@Suppress` cannot reach).
- **A blanket fallback no longer overrides the generic-argument masks.** `isKnownStableType` correctly rejected `PersistentList<MutableUser>`, but a `kotlinx.collections.immutable.*` rule further down the chain then reported it `STABLE` anyway. The fallback now skips names the mask table already owns.
- **Boolean compiler options are parsed strictly.** `String.toBoolean()` maps every value that is not `true` to `false`, so `strongSkipping=treu` silently inverted the option and produced verdicts that disagree with the compiler. `enabled` and `traceAll` had the same hole; all three now fail on anything other than `true` or `false`.

### Migration

`skippable` and `restartable` verdicts change for real code, so **run `./gradlew stabilityDump` once after upgrading** and commit the result. With the default `failOnStabilityChange = true`, `stabilityCheck` will otherwise fail on the corrected values: `skippable: false → true` wherever strong skipping applies, and `restartable: true → false` for `open`, interface, abstract and local composables. Those `restartable` transitions are reported as regressions even under `ignoreNonRegressiveChanges`, because a composable losing its restart group normally *is* one; here it means the previous value was wrong.

### Note for contributors

The `compiler-tests` golden IR dumps moved from `<name>.fir.kt.txt` to `<name>.kt.txt`. Kotlin's test framework used to prefix `fir.` to tell K1 dumps apart from K2 ones; K1 is gone in 2.4.20, so the qualifier went with it. The file *contents* are unchanged, which is the evidence that the IR this plugin generates is identical between 2.4.10 and 2.4.20.

`kotlin-js-store/yarn.lock` was regenerated with `./gradlew kotlinUpgradeYarnLock`, since a Kotlin bump moves the JS toolchain's npm dependencies and `:stability-runtime:build` fails on `:kotlinStoreYarnLock` otherwise.

## [0.13.0] - 2026-08-22

### Changed
- **Bumped Kotlin to 2.4.10** (from 2.4.0). Use the same Kotlin version as this library. The compiler plugin, runtime, Gradle plugin, Lint rules and IntelliJ plugin all build against 2.4.10, and every published target still compiles. **No stability verdicts change**: the Compose compiler's `analysis/Stability.kt` is byte-identical between 2.4.0 and 2.4.10, and re-running `stabilityDump` on the sample app produced no diff, so committed `.stability` baselines do not need refreshing for this bump alone.

  2.4.10's one Compose compiler fix, [b/522127447](https://issuetracker.google.com/issues/522127447) ("classes previously inferred `stable` now reported `runtime`/`Uncertain`"), is in `ComposableFunctionBodyTransformer`, not in stability inference. It restores the *cast target* type's stability when an argument is passed through a `CAST`/`IMPLICIT_CAST`, which affects the `$changed` metadata Compose computes per call-site argument. This analyzer reports the stability of a parameter's *declared type*, so the fix is orthogonal to what `stabilityDump` and the IDE surface.
- **`allowIncrementalDisabling` is now scoped per project.** Previously a stability task anywhere in the build disabled Kotlin incremental compilation in *every* project applying the plugin. Each project now decides based on its own task paths. `./gradlew stabilityDump`, `stabilityCheck` and `check` behave identically; only a path-qualified invocation such as `./gradlew :app:stabilityCheck` differs, and there it is more correct — that task only reads `:app`'s own `stability-info.json`.
- **The implicit `:stability-runtime` / `:stability-lint` project wiring is gone.** The plugin used to call `rootProject.findProject(...)` and, when a module with either path existed, add that `Project` object as a dependency notation. That only ever resolved inside this repository, silently hijacked any consumer build containing a module with those names, and is deprecated in Gradle 9 (an error in Gradle 10). The published Maven coordinates are now always used; lint checks continue to ship inside the runtime AAR's `lint.jar`.
- The compiler plugin's `projectDependencies` option is deprecated and ignored. It stays registered so builds that pin the compiler artifact independently keep working, and will be removed in 1.0.

### Fixed
- **Gradle Isolated Projects compatibility** (#107): the plugin no longer performs any cross-project access at configuration time, so builds using `--isolated-projects` / `org.gradle.isolated-projects=true` (incubating since Gradle 9.7) configure and compile cleanly. Two violations are gone:
  - `collectProjectDependencies()` walked `rootProject.allprojects` and read every sibling project's `group`, `projectDir` and `path` — including a filesystem scan of each sibling's sources — on every Kotlin compile-task configuration.
  - The incremental-compilation guard read `gradle.taskGraph.allTasks`, which observes tasks created by other projects.
- **Types declared in the module being compiled are no longer mis-reported as `UNSTABLE`**: cross-module detection used to match a parameter type's fully-qualified name against package prefixes guessed from sibling projects, and the first guess was the sibling's Gradle `group`. With the very common `allprojects { group = "com.example" }`, that prefix also matched the module's *own* `com.example.*` types, marking them unstable. Detection now relies solely on the IR declaration origin (`IR_EXTERNAL_DECLARATION_STUB`), which is the signal the Compose compiler itself uses and already covered every cross-module case. **If you were affected, run `./gradlew stabilityDump` to refresh your baseline** — with the default `failOnStabilityChange = true`, `stabilityCheck` will otherwise fail on the resulting `UNSTABLE → STABLE` transitions.
- **Cross-module types report an accurate reason**: they were labelled `UNSTABLE (has mutable properties or unstable members)` even when they had none. They now read `UNSTABLE (cross-module type without @Stable/@Immutable)`. Reasons are not compared by `stabilityCheck`, so this alone cannot fail a build.
- **Stability verdicts no longer depend on compiler-plugin ordering**: `@StabilityInferred` is honoured only on declarations from outside the module being compiled, where it is baked into the binary and is the intended cross-module channel. On a class in the module being compiled it exists only after the Compose compiler plugin's IR lowering has run, and whether that happens before or after this plugin is decided by the resolved order of `kotlinCompilerPluginClasspath`. The same source could therefore report `STABLE` in one build and `RUNTIME` in another. Our own property analysis is authoritative for source classes, and ignoring the annotation there also matches the IDE plugin, which only ever sees source.

### Note for contributors

The sample `:app` module now resolves the runtime from its published coordinate like any consumer, instead of from the in-build `:stability-runtime` project. Two consequences: `./gradlew :stability-runtime:publishToMavenLocal` must run before `:app` picks up local runtime edits, and after a version bump `:app` cannot build until the new version is published to Maven Local.

That change also shifted where the analyzer's IR pass sits relative to the Compose compiler plugin's IR lowering — plugin order follows the resolved `kotlinCompilerPluginClasspath` order, and the in-build project dependency used to push the analyzer behind Compose. Consumers were always on the other side of that boundary, so nothing changes for them, but `app/stability/*.stability` was regenerated and now records what a real consumer sees: composable-lambda parameters render in their pre-lowering form (`@[Composable] ComposableFunction0<T>` rather than `Function2<Composer, Int, T>`), and `Icon.normalSealedClass` reports `RUNTIME` instead of `STABLE` because `@StabilityInferred` is not yet attached to same-module classes when the pass runs. The analysis no longer *depends* on that ordering — see the `@StabilityInferred` entry under Fixed — so the regenerated baseline is now stable regardless of how the plugin classpath resolves.

## [0.12.0] - 2026-07-28

### Added
- **New Kotlin/Native targets for the runtime** (#196, thanks @der-fruhling): `linuxX64`, `linuxArm64` and `mingwX64` are now published, so applying the Gradle plugin to a KMP project with Linux or Windows native targets no longer fails to resolve the runtime.
- **`recompositionNanoTime()`**: the public monotonic clock behind `RecompositionTracker.recordDuration`, used by compiler-generated timing code. Compiler-generated code prefers it and falls back to `System.nanoTime()` when the runtime predates it.

### Fixed
- **`@TraceRecomposition` now works on every published target** (#197): tracing was silently a no-op on iOS, macOS, Linux, Windows, JS and Wasm. The compiler plugin skips instrumentation when it cannot resolve `rememberRecompositionTracker`, and that entry point only existed for Android and JVM. It moved to `commonMain`, so every target the runtime publishes gets parameter tracing, tags, thresholds and per-recomposition timing. Internal state **write-site** capture (the `← onClick (Screen.kt:42)` suffix) still needs the Compose Snapshot observer and stays Android/JVM-only.
- **Recomposition durations are no longer `0.00ms` outside Android and JVM** (#197): `currentNanoTime()` returned a hardcoded `0` on JS, Wasm and all native targets. It now reads `kotlin.time.TimeSource.Monotonic` there, which maps to `performance.now()` and `getTimeNanos()`.

### Changed
- Runtime source sets are grouped so each target family owns its platform code: `jvmCommon` (JVM, Android), `web` (JS, Wasm) and `native` (Apple, Linux, MinGW). A new target now inherits a working set of actuals instead of silently losing the entry points the compiler plugin resolves. `skiaMain` became `nativeMain`.
- The tracker cache is safe against concurrent inserts on every threaded platform: `ConcurrentHashMap` on JVM/Android, a copy-on-write map behind an atomic compare-and-set on native, and a plain map on JS/Wasm where Kotlin runs single-threaded.
- New call sites of `rememberRecompositionTracker` target the `RememberRecompositionTrackerKt` JVM facade. The previous `RememberRecompositionTracker_jvmKt` and `RememberRecompositionTracker_androidKt` facades are retained as hidden delegates, so binaries instrumented by 0.11.x and earlier keep linking. No action needed when upgrading.

## [0.11.1] - 2026-07-24

### Fixed
- **IDE plugin compatibility with newer IDEs** — removed the `until-build` upper bound so the plugin installs on IntelliJ IDEA / Android Studio 2026.2 and later, and K2 Analysis API access now falls back to PSI on any linkage error so it stays resilient across IDE versions (#191).
- **`ignoreNonRegressiveChanges`** — a new composable whose parameters are all stable is no longer reported when it is non-restartable or opts out of skipping (`@NonSkippableComposable`, a non-`Unit` return, `inline`, etc.). Only composables that introduce an unstable parameter are reported (#192).

## [0.11.0] - 2026-07-19

### Added
- **Stability configuration files in the compiler plugin** — configuration files (the same format as the Compose compiler's) can now be passed straight to the compiler through the new top-level `composeStabilityAnalyzer { stabilityConfigurationFiles }` option, so configured types are treated as stable everywhere the compiler runs — `stabilityDump`/`stabilityCheck`, trace-all, and the IDE data. This fixes a data class that wraps a configured type being reported unstable by `stabilityCheck` (#176). The old `stabilityValidation { stabilityConfigurationFiles }` option is deprecated in favor of the top-level one.

### Fixed
- **Non-restartable composables are reported as not skippable or restartable** (#184) — `@NonRestartableComposable`, `@ReadOnlyComposable`, `@ExplicitGroupsComposable`, `inline` functions, and composables returning a non-`Unit` value are no longer reported as restartable/skippable, matching the Compose compiler. `stabilityCheck` now also detects `restartable`/`skippable` regressions.
- **Computed getter-only properties are ignored in stability inference** (#178) — a property with no backing field (`val x get() = ...`) no longer makes its class unstable, in both the compiler and the IDE.
- **Vararg composable parameters are treated as arrays** (#175) — a `vararg` parameter compiles to an array, so it is no longer reported as its (often stable) element type.
- **Kotlin Multiplatform**: `stabilityDump`/`stabilityCheck` now find `stability-info.json` under the KMP Android compile task path `compile<Variant>KotlinAndroid` (#183).

## [0.10.0] - 2026-06-11

### Added
- **Stability Doctor (IDE plugin)** — a ranked, quantified "what to fix first" list that combines the static stability verdict, the downstream cascade blast radius, and measured runtime waste (Reality Check) into prioritized prescriptions. Scores are **ESTIMATED** (static only, works with no device) or **MEASURED** (backed by live heatmap data; measured waste always outranks estimates). Each prescription shows its problem parameters with static reasons, runtime grades, and value provenance, plus one-click fixes: change `var` → `val` (aborts if write usages exist), annotate with `@Immutable`/`@Stable`, add the type to the stability configuration file, and wrap call-site arguments in `remember(keys) { ... }` for silent-waste parameters (guarded by conservative safety rules and a preview dialog). New **Doctor** tool-window tab, **Code → Run Stability Doctor** action, and a settings group.
- **Trace-All mode (Gradle + compiler + runtime)** — opt-in module-wide auto-instrumentation: every restartable composable is traced as if it carried `@TraceRecomposition`, so the Live Heatmap, Reality Check, and Stability Doctor get module-wide runtime data without manual annotations.
  ```kotlin
  composeStabilityAnalyzer {
    traceAll {
      enabled.set(true)             // default: false (opt-in)
      threshold.set(2)              // default: 2 — skips the initial-composition burst
      variants.set(listOf("debug")) // default: ["debug"]; never applies to tests
    }
  }
  ```
  Explicit `@TraceRecomposition` annotations keep their own tag/threshold; previews, inline/readonly/non-restartable composables, and property getters are excluded automatically.
- **Fully qualified names in recomposition logs** — log headers now carry trailing `(fq: com.example.UserProfile)` and `(auto)` tokens (backward compatible with older parsers), so the IDE attributes runtime data precisely even when composables share a simple name across packages. `RecompositionEvent` gains additive `fqName` and `isAutoTraced` fields.

### Fixed
- **Android Studio freeze when starting the heatmap on large projects** (#168) — the typealias-resolution fallback iterated and parsed every Kotlin file in the project, and the heatmap inlay refresh ran analysis on the EDT. Lookups now use stub indexes, and the refresh computes on a background thread (the EDT only applies inlay mutations).
- **AGP 9 no longer leaks to consumers** (#165, thanks to @valeriopilo-tomtom) — the Gradle plugin depends on `gradle-api` as `compileOnly` and isolates all AGP types behind an Android-only registrar, so KMP/JVM projects without AGP work and AGP 8.x projects no longer get AGP 9 on their buildscript classpath.
- **Nullable types now match the stability configuration file** (#166, thanks to @xplayerCZ) — `kotlinx.datetime.LocalTime?` matches a `kotlinx.datetime.LocalTime` config entry.

### Changed
- **Runtime disabled-path hardening** — with `ComposeStabilityAnalyzer.setEnabled(false)`, trackers allocate nothing (early exits before any event construction); the tracker cache is thread-safe and keyed by fully qualified name to avoid cross-package collisions.

## [0.9.0] - 2026-06-04

### Added
- **`ParameterStability.UNKNOWN`** — a fourth stability value for types whose stability cannot be determined statically (interfaces and non-final classes). Mirrors the Compose 2.4.0 compiler's `Stability.Unknown`.

### Changed
- **Bumped Kotlin to 2.4.0** (from 2.3.21). The compiler plugin, runtime, Gradle plugin, Lint rules, and IntelliJ plugin all build against Kotlin 2.4.0.
  - K2 FIR extension registration migrated off the removed IntelliJ `ProjectExtensionDescriptor` mechanism (`KT-83341`); the deprecated `IrPluginContext.referenceClass`/`referenceFunctions` were replaced with the `finderForSource` API.
- **New `UNKNOWN` stability rule** — **interfaces** and **non-final (`open`/`abstract`) classes** now report `UNKNOWN` instead of `RUNTIME`/`UNSTABLE`, matching the Compose 2.4.0 compiler. This includes any `kotlin.Any?` parameter (since `Any` is `open`). Skippability is unchanged (these types were already non-skippable), but the rule **will produce diffs in committed `.stability` baselines** — run `./gradlew stabilityDump` to refresh them. A `STABLE → UNKNOWN` transition is reported as a regression by `stabilityCheck`.

## [0.8.0] - 2026-05-29

### Added
- **Stability Reality Check (IDE plugin)** — reconciles the compiler's *static* stability prediction with *live* runtime recomposition data and grades each parameter as **confirmed / false alarm / silent waste / justified**. It surfaces the strong-skipping gap: a parameter flagged "unstable" that actually skips fine at runtime (false alarm) versus one that recomposes on a fresh-but-`equals`-equal instance every frame (silent waste). Grades appear in editor inlays, hover tooltips (predicted vs. actual), and a new **Reality** tool-window tab with a wasted-recomposition tally.
- **Recomposition Blame (IDE plugin + runtime)** — traces a recomposition back to its cause in two ways:
  - **State write-site** (runtime, Android/JVM): with `@TraceRecomposition(traceStates = true)`, a Compose Snapshot write observer records where each internal state was mutated, e.g. `[state] counter: Int changed (0 → 1) ← onClick (Screen.kt:42)`.
  - **Parameter provenance** (IDE): right-click a `@Composable` → **Blame this Recomposition** to walk the reverse call graph and see where each argument's value originates, in a new **Blame** tool-window tab.
- **`ParameterChange.referenceChanged`** (runtime API) — distinguishes an `equals`-equal value delivered as a new instance (a strong-skipping `===` miss) from a genuine value change; this powers the Reality Check.

### Changed
- `stability-runtime` now declares a `compileOnly` dependency on the Compose runtime for its Android/JVM source sets (used only by the state-write observer); all other Kotlin Multiplatform targets remain Compose-free via no-op `actual`s.
- Kotlin remains **2.3.21**.

## [0.7.5] - 2026-05-16

### Added
- **`allowIncrementalDisabling` configuration switch** (Issue #156, #158)
  - New `stabilityValidation.allowIncrementalDisabling` property (default: `true`) to control whether the plugin may disable Kotlin's incremental compilation when stability validation tasks are in the build graph
  - Set to `false` to opt out and keep incremental compilation enabled even when running `stabilityDump`/`stabilityCheck`

### Changed
- **Bumped Kotlin to 2.3.21** along with related toolchain upgrades:
  - Compose BOM `2026.04.01`, Android Gradle Plugin `8.13.2`, Lint API `32.1.1`
  - Dokka `2.2.0`, kotlinx.serialization `1.11.0`, Spotless `7.0.2`, Nexus publish plugin `0.36.0`, androidx Activity `1.13.0`, runtime annotation `1.11.0`
- **Per-compilation stability output directory** (#154) — each `KotlinCompile`-dependent task now writes to a dedicated `build/stability/<name>/` directory to prevent cross-variant clobbering. Consumers (Stability Explorer, `stabilityDump`/`stabilityCheck`) read from both the new and legacy layouts for backward compatibility.

### Fixed
- **Incremental compilation now disabled when stability tasks run** (Issue #156, #157) — Kotlin's incremental compiler could skip recompiling files when dependency changes were binary-compatible, even though stability could still change (e.g. `val` → `var`). The plugin now disables incremental compilation while `stabilityDump`/`stabilityCheck` are in the task graph so stability results stay accurate (can be opted out via `allowIncrementalDisabling`).

## [0.7.4] - 2026-04-25

### Added
- **Recomposition Profiler: Timing measurement** (Issue #89)
  - `@TraceRecomposition` now measures composable recomposition duration via `System.nanoTime()` IR injection
  - Duration displayed in logcat output: `[Recomposition #3] UserCard (2.30ms)`
  - Compiler plugin wraps composable body in try-finally for accurate timing even on exceptions
  - `RecompositionEvent.durationNanos` field added for custom logger consumption
  - KMP-compatible via `expect/actual currentNanoTime()` (Android/JVM supported, other platforms gracefully skip)
- **Heatmap Tooltip** — Hover over recomposition count inlay in the editor to see:
  - Last recomposition duration
  - Parameter changes with old/new values
  - State changes with old/new values
  - Unstable parameter summary
  - Cumulative total recomposition count and duration
- **Heatmap logcat parser updates** — Parses `[param]` and `[state]` prefixed lines and duration from log output

### Changed
- **Shadow plugin upgraded to 9.0.0-beta12** for Gradle 9.x compatibility (plugin ID changed from `com.github.johnrengelman.shadow` to `com.gradleup.shadow`)

## [0.7.3] - 2026-04-11

### Added
- **Stability configuration file support for `stabilityDump`** (Issue #130, PR #105)
  - `stabilityConfigurationFiles` now applies to both `stabilityDump` and `stabilityCheck` tasks
  - Types matching configuration patterns are overridden to STABLE in the baseline file
  - Composable `skippable` flag is recalculated based on resolved parameter stability
- **`unstableOnly` option for stability baseline** (Issue #128)
  - New `unstableOnly` option: when enabled, only unstable composables are included in the baseline file
  - Reduces baseline file size in large projects and focuses on stability issues
- **New composable diff now includes parameter-level stability details** (PR #105)
  - `stabilityCheck` output for new composables shows each parameter's stability status
- **Internal state change tracking for `@TraceRecomposition`** (Issue #89)
  - New `traceStates` annotation parameter: `@TraceRecomposition(traceStates = true)`
  - Tracks `mutableStateOf`, `mutableIntStateOf`, `derivedStateOf` and other Compose state changes
  - Compiler plugin detects delegated state variables via `IrLocalDelegatedProperty` IR analysis
  - Logs state changes with `[state]` prefix, parameter changes with `[param]` prefix
  - Only changed states are logged to reduce noise

### Fixed
- **`ignoredPackages` now consistently respected during `stabilityCheck`** (Issue #129)
  - Previously, composables in ignored packages were excluded from `stabilityDump` but still detected as "new composable" during `stabilityCheck`
  - Now both tasks apply the same package/class filtering
- **`@Optional` annotation added to `stabilityConfigurationFiles` task input** (PR #105)
  - Prevents Gradle task validation failure when configuration files are not set
- **ADB not found on Windows** (Issue #139)
  - Fixed `adb.exe` detection on Windows for the Heatmap feature
  - Added Windows default SDK path (`%LOCALAPPDATA%\Android\Sdk`)
  - Uses `where` command instead of `which` on Windows for PATH lookup

### Changed
- **Tool window actions always visible** — Toggle Heatmap, Clear Data, Settings, and GitHub icons moved from hover-only title bar to content toolbar across all tabs
- **Tool window icon updated** — Changed from monochrome gray to blue color matching the plugin icon
- Stability comparison logic extracted to `StabilityComparison.kt` (PR #105)
- Stability configuration parser added as `StabilityConfigParser.kt` (PR #105)

## [0.7.1] - 2026-02-13

### Fixed
- **Fixed Kotlin 2.3.20 compatibility** (Issue #133)
  - Resolved `NoSuchMethodError` for `IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB` and `DEFINED` constants
  - Used reflection-based origin lookups to support Kotlin's changed return types in 2.3.20
  - Migrated `ComposableStabilityChecker` from `FirSimpleFunctionChecker` to `FirCallableDeclarationChecker` to handle `FirSimpleFunction` → `FirNamedFunction` rename

### Changed
- **Upgraded to Kotlin 2.3.20**

## [0.7.0] - 2026-02-13

### Added
- **Recomposition Cascade Visualizer** (PR #119)
  - Right-click any `@Composable` function and select "Analyze Recomposition Cascade" to trace downstream composables affected by recomposition
  - Tree view showing each downstream composable with stability status (skippable vs. non-skippable)
  - Summary statistics: total downstream count, skippable/unskippable counts, and max depth
  - Cycle detection and configurable depth limits (max 10) prevent infinite analysis
  - Double-click any node to navigate directly to its source code
  - Available via editor right-click context menu
  - New "Cascade" tab in the Compose Stability Analyzer tool window
- **Live Recomposition Heatmap** (PR #120, #121)
  - Real-time recomposition counts from a connected device overlaid directly above composable functions in the editor
  - Reads `@TraceRecomposition` events from ADB logcat and aggregates per-composable data
  - Color-coded severity: green (< 10 recompositions), yellow (10-50), red (50+)
  - Click any recomposition count to open the Heatmap tab with detailed event logs and parameter change history
  - Start/Stop toggle button in the tool window title bar and Code menu
  - Multi-device support with device picker when multiple ADB devices are connected
  - Flicker-free rendering using deterministic pre-baked inlay renderers
  - Heatmap enabled by default in plugin settings
  - Configurable severity thresholds in Settings > Tools > Compose Stability Analyzer
  - New "Heatmap" tab in the Compose Stability Analyzer tool window
- **Plugin Verifier integration** (PR #118)
  - Extended IDE compatibility to build 261 (IntelliJ IDEA 2026.1)
  - Added `runPluginVerifier` task for automated compatibility testing

### Improved
- Tool window now has three tabs: Explorer, Cascade, and Heatmap
- Start/Stop Recomposition Heatmap button moved to tool window title bar for visibility across all tabs
- K2-safe reference resolution using `runCatching` pattern in cascade analyzer
- Cancellation support in cascade background analysis via `ProgressIndicator.checkCanceled()`

## [0.6.7] - 2026-02-10

### Added
- **Android variant-specific stability tasks** (Issue #85, PR #101)
  - Gradle plugin now creates per-variant tasks (e.g., `debugStabilityDump`, `releaseStabilityCheck`) for Android projects
  - Allows running stability analysis on a single variant without compiling others
  - Aggregate tasks (`stabilityDump`, `stabilityCheck`) still available for all variants
  - Improved build cache compatibility for Kotlin compile output registration
- **Non-regressive change filtering for stability validation** (Issue #82, PR #104)
  - New `ignoreNonRegressiveChanges` option: only flag stability regressions, ignore non-regressive changes (e.g., new stable parameters)
  - New `allowMissingBaseline` option: allow stability checks to run even without an existing baseline file
  - With both flags enabled, the Gradle plugin reports all unstable composables found in the module
- **Stability configuration file wildcard support** (Issue #108, PR #110)
  - Implemented `stabilityPatternToRegex` to support `*` and `**` wildcard syntax in stability configuration files
  - Matches the format used by the official Compose compiler stability configuration
  - Example: `com.datalayer.*`, `com.example.**`

### Fixed
- **`@StabilityInferred` annotation now supported in Gradle plugin** (Issue #102, PR #112)
  - Immutable classes from other modules annotated with `@StabilityInferred(parameters=0)` are now correctly treated as stable during `stabilityDump`/`stabilityCheck`
  - Previously, cross-module classes with `@StabilityInferred` were incorrectly marked as UNSTABLE
  - Aligns Gradle plugin behavior with the IDEA plugin, which already handled this correctly
- **Skip analysis for `@NonRestartableComposable` and `@NonSkippableComposable`** (Issue #103, PR #111)
  - Composable functions annotated with `@NonRestartableComposable` or `@NonSkippableComposable` are now excluded from stability analysis
  - These functions are not subject to recomposition skipping, so stability analysis is not applicable
- **Improved typealias handling in IDEA plugin** (Issue #16, PR #106)
  - Parameters using a typealias to a function type (e.g., `typealias ComposableAction = @Composable () -> Unit`) are now correctly recognized as stable
  - Added typealias expansion support across PSI fallback, K1, and K2 analysis paths
  - Includes circular alias recursion guard to prevent infinite loops

### Improved
- **Replaced internal `nj2k.descendantsOfType` with stable `PsiTreeUtil` API** (PR #109)
  - Implemented intelligent caching mechanism for typealias resolution with automatic expiration
  - Streamlined function-type detection and composability checking logic
  - Improved IDE responsiveness during analysis

## [0.6.6] - 2025-12-24

### Fixed
- **Fixed stabilityDump task incorrectly marked as UP-TO-DATE**
  - Task now properly tracks the `stability-info.json` input file for up-to-date checks
  - Changed from `@Internal` to `@InputFiles` annotation on input file property
  - Ensures stability files are regenerated when compiler output changes
  - Fixes issue where running `./gradlew stabilityDump` would skip execution even when stability files were missing
  - Task now correctly runs after `clean` or when stability output is deleted

## [0.6.5] - 2025-12-17

### Added
- **Quiet mode for stability validation** (Issue #83)
  - New `quietCheck: Boolean = false` option in `stabilityValidation` configuration
  - Suppresses "✅ Stability check passed." messages for modules that pass checks
  - Reduces log noise in multi-module projects where many modules pass validation
  - Errors and warnings still shown normally
  - Example: `stabilityValidation { quietCheck.set(true) }`

### Changed
- **Upgraded to Kotlin 2.3.0**

## [0.6.4] - 2025-12-16

### Fixed
- **Fixed "Wrong plugin option format: null" compilation error** (Issue #87)
  - Changed cross-module detection to use file-based approach instead of string-based SubpluginOption
  - Project dependencies now written to `build/stability/project-dependencies.txt` (one package per line)
  - Compiler plugin reads dependencies from file instead of parsing comma-separated string
  - Resolves build failures in multi-module projects introduced in 0.6.3
  - Users experiencing compilation errors with 0.6.3 should upgrade to 0.6.4

### Improved
- More robust cross-module dependency passing mechanism
- Better handling of empty dependency lists
- Follows common patterns used by other Kotlin compiler plugins

## [0.6.3] - 2025-12-13

### Added
- **Cross-module stability detection** - Classes from other Gradle modules now require explicit stability annotations
  - Compiler plugin: Detects cross-module types via IR origins and package matching
  - Gradle plugin: Automatically collects all subproject packages for cross-module detection
  - IDE plugin: Uses IntelliJ module system to identify cross-module boundaries
  - Classes from different modules marked as UNSTABLE unless annotated with @Stable/@Immutable/@StabilityInferred
  - Prevents accidentally assuming stability for types where implementation details aren't visible
  - Provides consistent behavior across compiler plugin, IDE plugin, and stability validation

### Fixed
- **Fixed Gradle compatibility issues**
  - Removed deprecated `getDependencyProject()` usage for broader Gradle version compatibility
  - Implemented portable dependency collection that works across all Gradle versions
- **Fixed compiler tests compatibility**
  - Updated StabilityTestConfigurator to pass new projectDependencies parameter
  - All compiler tests now passing with cross-module detection enabled
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names navigates to correct source location

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters displayed as stable instead of hidden
  - Composable skippability recalculated based on processed parameters
  - Better visibility of composable signatures while respecting ignore patterns
- **Compacted code comments** for better readability across all cross-module detection implementations

## [0.6.2] - 2025-12-10

### Fixed
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names navigates to correct source location
  - Extended source location search to include `KtProperty` declarations

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters now displayed as stable instead of being hidden completely
  - Composable skippability is recalculated based on processed parameters
  - Provides better visibility of composable signatures while respecting ignore patterns

## [0.6.1] - 2025-12-06

### Added
- Settings icon in IDE plugin tool window toolbar for quick access to configuration
- Support for ignored type patterns in tool window

### Fixed
- Tool window now respects ignored type patterns (Issue #74)
- WASM build failures with Gradle task dependencies (Issue #70)
- Property name display showing as `<get-propertyName>` (Issue #67)

### Improved
- Updated tool window icon to monochrome style
- Updated dependencies (Android Lint, Nexus Plugin, AGP, Compose BOM)

## [0.6.0] - 2025-11-24

### Added
- Per-project stability configuration file support (Issue #60)
- Runtime gutter icon for runtime-only composables
- Generic type argument inference at compile time

### Improved
- Enhanced tooltip information for runtime parameters
- Better visual distinction between unstable and runtime stability

## [0.5.3] - 2025-11-18

### Fixed
- iOS native compilation with kotlinx.serialization (Issue #48)
- Gradle Configuration Cache compatibility (Issue #41)

## [0.5.2] - 2025-11-13

### Fixed
- APK size increase in release builds (Issue #39)
- Optimized ProGuard rules to reduce APK size

## [0.5.1] - 2025-11-10

### Added
- wasmJs target support for Kotlin Multiplatform

### Fixed
- Sealed class stability inheritance (Issue #31)

## [0.5.0] - 2025-11-08

### Breaking Changes
- Minimum IDE version updated to IntelliJ IDEA 2024.2+ (build 242+)

### Added
- New Compose Stability Tool Window (Issue #14)
- Interactive empty state guide
- Show in test source sets setting (Issue #21)
- @StabilityInferred annotation parameter support (Issue #18)

### Improved
- Enhanced UI/UX for Tool Window
- Performance optimization using pre-computed JSON files
- Added IntelliJ Plugin Verifier integration

### Fixed
- PluginException in IntelliJ IDEA 2025.2.4 (Issue #33)
- Typealias detection for Composable function types (Issue #16)
- ImmutableList/Set/Map detection in test code (Issue #21)

## [0.4.2] - 2025-11-03

### Fixed
- @Parcelize data classes stability detection (Issue #3)
- StackOverflowError with recursive types (Issue #11)
- Compose shape types stability analysis

## [0.4.1] - 2025-11-02

### Fixed
- Stability analysis for Compose shape types
- StackOverflowError with recursive types
- False positive warnings for @Parcelize classes

## [0.4.0] - 2025-11-02

### Added
- ProGuard consumer rules for R8/ProGuard compatibility
- Comprehensive compiler-tests module
- Enhanced documentation for stability validation

### Improved
- @TraceRecomposition visualization
- Stability analysis for complex generics

## [0.3.0] - 2025-10-28

### Added
- @IgnoreStabilityReport annotation
- Runtime and Gradle module unit tests
- Stability validation workflow (stabilityDump and stabilityCheck tasks)
- IDE quick fixes for @TraceRecomposition

## [0.2.3] - 2025-10-23

### Fixed
- Compiler test compatibility with Kotlin 2.2.21

## [0.2.2] - 2025-10-20

### Changed
- Unified maven publishing configuration

## [0.2.1] - 2025-10-15

### Fixed
- K2 API compatibility for Android Studio AI-243
- Graceful fallback to PSI analyzer

## [0.2.0] - 2025-10-10

### Added
- K2 Analysis API support
- Enhanced @Preview detection
- IntelliJ IDEA 2025.2 support

## [0.1.0] - 2025-10-01

### Added
- Initial release
- Hover documentation
- Gutter icons
- Inline hints
- Code inspections and quick fixes

## Legend

- **Added** - New features
- **Changed** - Changes in existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Improved** - Enhancements to existing features
- **Security** - Security-related changes
- **Breaking Changes** - Breaking changes requiring migration

## Links

- [GitHub Repository](https://github.com/skydoves/compose-stability-analyzer)
- [Issue Tracker](https://github.com/skydoves/compose-stability-analyzer/issues)
- [Documentation](https://github.com/skydoves/compose-stability-analyzer/blob/main/README.md)
- [IDE Plugin Changelog](compose-stability-analyzer-idea/CHANGELOG.md)
