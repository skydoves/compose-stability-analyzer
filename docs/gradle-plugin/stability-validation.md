# Stability Validation

Stability Validation prevents stability regressions from reaching production. It's like **git diff for composable stability** — it tracks your composables' stability over time and automatically fails your build if stability regresses. Without it, a seemingly innocent change (like converting a `val` to `var` in a data class) can silently destabilize dozens of composables, and the performance regression slips through code review unnoticed.

## How It Works

Two Gradle tasks power stability validation. The **`stabilityDump`** task analyzes your compiled code and creates a snapshot (baseline) of every composable's stability status — function signatures, skippability, restartability, and the stability of each parameter. The **`stabilityCheck`** task compares the current compilation output against that snapshot and reports any differences.

| Task | Purpose |
|------|---------|
| `stabilityDump` | Creates a snapshot of all composables' stability |
| `stabilityCheck` | Compares current stability against the snapshot |

Think of `stabilityDump` as "save the current state" and `stabilityCheck` as "has anything changed since the last save?" Together, they create a feedback loop that catches stability regressions the moment they're introduced.

!!! note "Android variants"

    For Android projects, variant-specific tasks are created, such as `debugStabilityDump` and `releaseStabilityCheck`. You can use those to only compile one variant, which speeds up the process in CI.

## Step 1: Create a Baseline

The first step is generating a baseline — a snapshot of your current composables' stability. This establishes the "known good" state that future changes are compared against. Run the compilation first (the stability analysis reads the compiled output), then generate the baseline.

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityDump
```

This creates a human-readable `.stability` file at `app/stability/app.stability`. The file is intentionally designed to be readable in code reviews — when someone updates the baseline, reviewers can see exactly what changed and why.

**Example content:**

```
@Composable
public fun com.example.UserCard(user: com.example.User): kotlin.Unit
  skippable: true
  restartable: true
  params:
    - user: STABLE (marked @Stable or @Immutable)

@Composable
public fun com.example.UnstableCard(user: com.example.MutableUser): kotlin.Unit
  skippable: false
  restartable: true
  params:
    - user: UNSTABLE (has mutable properties)
```

Each composable entry shows the fully-qualified function signature, whether it's skippable and restartable, and the stability status of every parameter with a brief explanation of why it's stable or unstable.

**Commit this file to git** so it becomes the shared baseline for your team:

```bash
git add app/stability/app.stability
git commit -m "Add stability baseline for app module"
```

## Step 2: Check for Regressions

The `stabilityCheck` task compares your current code against the baseline. Run it after compilation to verify that no composable's stability has regressed.

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityCheck
```

**If nothing changed**, the task passes silently:

```
✅ Stability check passed.
```

**If stability regressed**, the task fails with a clear message showing exactly what changed:

```
❌ Stability check failed!

The following composables have changed stability:

~ com.example.UserCard(user): stability changed from STABLE to UNSTABLE

If these changes are intentional, run './gradlew stabilityDump' to update the stability file.
```

The build **fails**, preventing the regression from being merged. The error message includes instructions for updating the baseline if the change is intentional — but updating requires a deliberate `stabilityDump` and a separate commit, creating a documented decision in git history rather than an accidental regression.

### Change Types

The check detects three types of changes, each marked with a different symbol in the output.

The `~` symbol indicates a **stability regression** — a parameter or composable that was previously stable is now unstable. This is the most critical change type and the primary reason for running stability validation.

The `+` symbol indicates a **new composable** was added. This appears when a new composable function exists in the current code but not in the baseline. This is informational and typically non-breaking.

The `-` symbol indicates a **composable was removed**. This appears when a composable that existed in the baseline no longer exists in the current code.

| Symbol | Change Type | Example |
|--------|-------------|---------|
| `~` | Stability regressed | Parameter changed from STABLE to UNSTABLE |
| `+` | New composable added | `+ com.example.NewScreen(title)` |
| `-` | Composable removed | `- com.example.OldScreen(data)` |

## Configuration

You can customize what gets tracked, where baseline files are stored, and how the plugin responds to changes. All options are configured in the `composeStabilityAnalyzer` block in your `build.gradle.kts`.

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        enabled.set(true)
        outputDir.set(layout.projectDirectory.dir("stability"))
        includeTests.set(false)

        // Ignore specific packages or classes
        ignoredPackages.set(listOf("com.example.internal"))
        ignoredClasses.set(listOf("PreviewComposables"))

        // Exclude specific modules
        ignoredProjects.set(listOf("benchmarks", "examples"))

        // Fail build on stability changes (default: true)
        failOnStabilityChange.set(true)

        // Only report regressive changes (default: false)
        ignoreNonRegressiveChanges.set(false)

        // Allow checks without a baseline file (default: false)
        allowMissingBaseline.set(false)
    }
}
```

### `failOnStabilityChange`

By default, `stabilityCheck` fails the build when stability changes are detected. This is the right behavior for CI pipelines where you want to catch regressions before they're merged. However, during initial adoption or gradual migration, you may want to see stability issues without blocking builds.

Setting `failOnStabilityChange` to `false` switches to warning-only mode — the task still reports all stability changes in the output, but the build succeeds regardless. This is useful when first adding stability validation to an existing project, allowing your team to see all current issues and fix them incrementally without blocking every PR.

A common pattern is to use environment-based configuration — strict on CI, warning-only for local development:

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        failOnStabilityChange.set(System.getenv("CI") == "true")
    }
}
```

### `ignoreNonRegressiveChanges`

When enabled, the check only flags stability regressions — changes where a parameter or composable became less stable. Non-regressive changes (like a new stable composable being added, or an unstable parameter becoming stable) are not reported. This reduces noise in projects where composables are frequently added or removed, letting you focus exclusively on changes that could harm performance.

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        ignoreNonRegressiveChanges.set(true)
    }
}
```

## Excluding Composables

Some composables shouldn't be included in stability validation — preview composables that only exist for Android Studio previews, debug-only screens, or experimental features still under active development. Use the `@IgnoreStabilityReport` annotation to exclude them.

```kotlin
@IgnoreStabilityReport
@Preview
@Composable
fun UserCardPreview() {
    UserCard(user = User("John", 30))
}
```

Composables annotated with `@IgnoreStabilityReport` are excluded from both the `.stability` baseline files generated by `stabilityDump` and the validation checks performed by `stabilityCheck`. This keeps your baseline clean and your CI checks focused on production code.

## Multi-Module Projects

For projects with multiple modules, each module gets its own `.stability` file. This keeps the baselines scoped to their module, making diffs smaller and easier to review.

```
project/
├── app/stability/app.stability
├── feature-auth/stability/feature-auth.stability
└── feature-profile/stability/feature-profile.stability
```

You can run `stabilityCheck` for all modules at once, which is the typical CI configuration:

```bash
./gradlew stabilityCheck
```

Or target specific modules when you only want to validate a subset of your project:

```bash
./gradlew :app:stabilityCheck
./gradlew :feature-auth:stabilityCheck
```

Each module's baseline is independent, so updating the baseline for one module doesn't affect others. This is important in large projects where different teams own different modules — a stability change in `feature-auth` doesn't require updating `feature-profile`'s baseline.
