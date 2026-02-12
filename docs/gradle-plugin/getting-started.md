# Getting Started

The Compose Stability Analyzer Gradle plugin enables runtime recomposition tracing with the `@TraceRecomposition` annotation and stability validation for CI/CD pipelines. This plugin supports Kotlin Multiplatform.

![trace-preview](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview6.png)

## Installation

### Step 1: Add to Version Catalog

Add the plugin to the `[plugins]` section of your `libs.versions.toml` file:

```toml
[plugins]
stability-analyzer = { id = "com.github.skydoves.compose.stability.analyzer", version = "0.6.7" }
```

### Step 2: Apply to Root Project

Apply the plugin to your root `build.gradle.kts` with `apply false`:

```kotlin
plugins {
    alias(libs.plugins.stability.analyzer) apply false
}
```

### Step 3: Apply to Your Module

Apply the plugin to your app or shared module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.stability.analyzer)
}
```

Sync your project to complete the setup.

## Kotlin Version Mapping

It's **strongly recommended to use the exact same Kotlin version** as this library. Using a different Kotlin version may lead to compilation errors.

| Stability Analyzer | Kotlin |
|--------------------|--------|
| 0.6.5+             | 2.3.0  |
| 0.4.0 ~ 0.6.4     | 2.2.21 |

## What's Included

The Gradle plugin provides:

- **`@TraceRecomposition` annotation**: Add to any composable to log parameter changes during recomposition
- **`ComposeStabilityAnalyzer` runtime**: Configure logging behavior and custom loggers
- **`stabilityDump` task**: Generate stability baseline files
- **`stabilityCheck` task**: Compare current stability against the baseline

!!! note "Independence"

    This Gradle plugin is completely independent of the IDE plugin. You can use it on its own for runtime tracing and CI validation, without installing the IDE plugin.
