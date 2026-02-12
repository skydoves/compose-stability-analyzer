# Stability Validation

Stability Validation prevents stability regressions from reaching production. It's like **git diff for composable stability** — it tracks your composables' stability over time and automatically fails your build if stability regresses.

## How It Works

Two Gradle tasks power stability validation:

| Task | Purpose |
|------|---------|
| `stabilityDump` | Creates a snapshot of all composables' stability |
| `stabilityCheck` | Compares current stability against the snapshot |

!!! note "Android variants"

    For Android projects, variant-specific tasks are created, such as `debugStabilityDump` and `releaseStabilityCheck`. You can use those to only compile one variant.

## Step 1: Create a Baseline

Generate a baseline — a snapshot of your current composables' stability:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityDump
```

This creates a human-readable `.stability` file:

```
app/stability/app.stability
```

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

**Commit this file to git:**

```bash
git add app/stability/app.stability
git commit -m "Add stability baseline for app module"
```

## Step 2: Check for Regressions

The `stabilityCheck` task compares your current code against the baseline:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityCheck
```

**If nothing changed:**

```
✅ Stability check passed.
```

**If stability regressed:**

```
❌ Stability check failed!

The following composables have changed stability:

~ com.example.UserCard(user): stability changed from STABLE to UNSTABLE

If these changes are intentional, run './gradlew stabilityDump' to update the stability file.
```

The build **fails**, preventing the regression from being merged.

### Change Types

| Symbol | Change Type | Example |
|--------|-------------|---------|
| `~` | Stability regressed | Parameter changed from STABLE to UNSTABLE |
| `+` | New composable added | `+ com.example.NewScreen(title)` |
| `-` | Composable removed | `- com.example.OldScreen(data)` |

## Configuration

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

By default, `stabilityCheck` fails the build. Set to `false` for warning-only mode:

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        // Fail on CI, warn locally
        failOnStabilityChange.set(System.getenv("CI") == "true")
    }
}
```

### `ignoreNonRegressiveChanges`

Only flag stability regressions. Non-regressive changes (like a new stable parameter being added) are not flagged:

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        ignoreNonRegressiveChanges.set(true)
    }
}
```

## Excluding Composables

Use `@IgnoreStabilityReport` to exclude composables from validation:

```kotlin
@IgnoreStabilityReport
@Preview
@Composable
fun UserCardPreview() {
    UserCard(user = User("John", 30))
}
```

## Multi-Module Projects

Each module gets its own `.stability` file:

```
project/
├── app/stability/app.stability
├── feature-auth/stability/feature-auth.stability
└── feature-profile/stability/feature-profile.stability
```

Run for all modules:

```bash
./gradlew stabilityCheck
```

Or specific modules:

```bash
./gradlew :app:stabilityCheck
./gradlew :feature-auth:stabilityCheck
```
