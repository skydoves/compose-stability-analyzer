# CI/CD Integration

Add stability validation to your CI pipeline so it runs on every pull request, preventing stability regressions from being merged.

## GitHub Actions

```yaml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'
      - name: Build project
        run: ./gradlew :app:compileDebugKotlin

  stability_check:
    name: Compose Stability Check
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'
      - name: Run stability check
        run: ./gradlew stabilityCheck
```

!!! warning "Build order matters"

    The stability check must run **after** compilation. The `needs: build` dependency ensures this.

## Workflow for Teams

### Initial Setup

1. Add the Gradle plugin to your project
2. Run `./gradlew stabilityDump` to create the baseline
3. Commit the `.stability` files to your repository
4. Add `stabilityCheck` to your CI pipeline

### Day-to-Day Development

1. Developer makes code changes
2. CI runs `stabilityCheck` on the pull request
3. If stability regressed, CI fails with a clear message showing what changed
4. Developer either fixes the regression or updates the baseline if the change is intentional

### Updating the Baseline

When a stability change is intentional:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityDump
git add app/stability/app.stability
git commit -m "Update stability baseline: justified by [reason]"
```

This creates a **deliberate, documented decision** in git history rather than an accidental regression.

## Different Behavior Per Environment

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        // Strict mode on CI, warning mode locally
        failOnStabilityChange.set(System.getenv("CI") == "true")
    }
}
```

## Quick Integration Example

For a real-world example of integrating stability validation into an existing project, see the [Landscapist integration PR](https://github.com/skydoves/landscapist/pull/767/files).
