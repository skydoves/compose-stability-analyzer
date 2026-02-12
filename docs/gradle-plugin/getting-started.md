# Getting Started

The Compose Stability Analyzer Gradle plugin enables runtime recomposition tracing with the `@TraceRecomposition` annotation and stability validation for CI/CD pipelines. It works as a Kotlin compiler plugin that instruments your composable functions at compile time, and this plugin supports Kotlin Multiplatform.

![trace-preview](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview6.png)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/compose-stability-runtime.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%compose-stability-runtime%22)

### Step 1: Add to Version Catalog

Add the plugin to the `[plugins]` section of your `libs.versions.toml` file. This registers the plugin so it can be referenced by alias in your build scripts.

```toml
[plugins]
stability-analyzer = { id = "com.github.skydoves.compose.stability.analyzer", version = "$version" }
```

### Step 2: Apply to Root Project

Apply the plugin to your root `build.gradle.kts` with `apply false`. This makes the plugin available to submodules without applying it to the root project itself — a standard Gradle convention for plugins that should only run in specific modules.

```kotlin
plugins {
    alias(libs.plugins.stability.analyzer) apply false
}
```

### Step 3: Apply to Your Module

Apply the plugin to each app or shared module's `build.gradle.kts` where you want recomposition tracing or stability validation. When applied, the plugin automatically adds the compiler plugin to Kotlin compilation tasks and includes the runtime library as a dependency.

```kotlin
plugins {
    alias(libs.plugins.stability.analyzer)
}
```

Sync your project to complete the setup. The plugin is now active and will instrument your composable functions during compilation.

## Kotlin Version Mapping

The Compose Stability Analyzer compiler plugin is tightly coupled to the Kotlin compiler version because it extends the compiler's internal APIs. Using a mismatched Kotlin version may lead to compilation errors. It is **strongly recommended to use the exact same Kotlin version** as this library.

| Stability Analyzer | Kotlin |
|--------------------|--------|
| 0.6.5+             | 2.3.0  |
| 0.4.0 ~ 0.6.4     | 2.2.21 |

## What's Included

The Gradle plugin provides four capabilities that work together. The **`@TraceRecomposition` annotation** is added to any composable function you want to monitor — at runtime, it logs detailed information about each recomposition event, including which parameters changed and which are unstable. See [TraceRecomposition](trace-recomposition.md) for usage details.

The **`ComposeStabilityAnalyzer` runtime** is the API you use to configure logging behavior. You can enable or disable tracing, set custom loggers, and integrate with analytics platforms like Firebase. See [Custom Logger](custom-logger.md) for advanced configurations.

The **`stabilityDump` task** generates a human-readable baseline file containing every composable's stability status. This baseline serves as your project's stability contract. The **`stabilityCheck` task** compares the current compilation output against that baseline and fails the build if stability has regressed. Together, these tasks enable [Stability Validation](stability-validation.md) in your CI/CD pipeline.

!!! note "Independence"

    This Gradle plugin is completely independent of the IDE plugin. You can use it on its own for runtime tracing and CI validation, without installing the IDE plugin.
