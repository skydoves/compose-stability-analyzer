# CI/CD Integration

Adding stability validation to your CI pipeline ensures that every pull request is automatically checked for stability regressions. This creates a safety net that catches performance-impacting changes before they're merged, without requiring manual review of stability reports.

## GitHub Actions

The following workflow runs stability validation on every push and pull request. It's split into two jobs — `build` compiles the project, and `stability_check` runs the validation after compilation completes.

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

The `needs: build` dependency is critical — it ensures that compilation has finished before the stability check runs. The stability analysis reads compiled output, so running it before compilation would produce incorrect results or fail.

!!! warning "Build order matters"

    The stability check must run **after** compilation. The `needs: build` dependency ensures this. If you combine both steps into a single job, make sure `compileDebugKotlin` runs before `stabilityCheck`.

## Workflow for Teams

### Initial Setup

Getting stability validation running for the first time requires four steps. First, add the Gradle plugin to your project following the [Getting Started](getting-started.md) guide. Then run `./gradlew stabilityDump` to create the baseline — this captures the current stability state of all your composables as the "known good" reference point.

Commit the generated `.stability` files to your repository. These files are intentionally human-readable so they can be reviewed in pull requests. Finally, add the `stabilityCheck` task to your CI pipeline using the GitHub Actions workflow above (or its equivalent for your CI system).

From this point forward, every pull request will be automatically validated against the baseline.

### Day-to-Day Development

Once the CI pipeline is configured, the workflow is transparent to developers. A developer makes code changes and opens a pull request. CI automatically runs `stabilityCheck` on the PR. If all composables maintain their stability (or improve), the check passes silently and the PR can be merged.

If a change causes a stability regression — for example, changing a `val` to `var` in a data class that's used as a composable parameter — the CI check fails with a clear message showing exactly which composables were affected and how their stability changed. The developer then has two options: fix the regression (the preferred path), or update the baseline if the stability change is intentional and justified.

### Updating the Baseline

When a stability change is intentional — perhaps you're adding a mutable property that's genuinely needed, or you're refactoring a data model in a way that temporarily reduces stability — you update the baseline by running `stabilityDump` again and committing the updated file.

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityDump
git add app/stability/app.stability
git commit -m "Update stability baseline: justified by [reason]"
```

This creates a **deliberate, documented decision** in git history. Anyone looking at the commit log can see when stability changed, what changed, and (from the commit message) why it was accepted. This is far more valuable than silently accumulating regressions over time.

## Different Behavior Per Environment

A common pattern is to run stability validation in strict mode on CI (where regressions should block the build) but in warning-only mode during local development (where developers want to see stability information without being blocked while iterating).

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        // Strict mode on CI, warning mode locally
        failOnStabilityChange.set(System.getenv("CI") == "true")
    }
}
```

This configuration reads the `CI` environment variable, which is automatically set to `"true"` by GitHub Actions, GitLab CI, CircleCI, and most other CI systems. Locally, the variable is unset, so `failOnStabilityChange` defaults to `false` and stability changes produce warnings instead of build failures.

## Quick Integration Example

For a real-world example of integrating stability validation into an existing project, see the [Landscapist integration PR](https://github.com/skydoves/landscapist/pull/767/files). This PR shows the complete setup — plugin configuration, baseline generation, and CI workflow — applied to a production open-source project.
