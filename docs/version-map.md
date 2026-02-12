# Kotlin Version Map

It is **strongly recommended to use the exact same Kotlin version** as this library. Using a different Kotlin version may lead to compilation errors during the build process.

## Version Compatibility

| Stability Analyzer | Kotlin |
|--------------------|--------|
| 0.6.5+             | 2.3.0  |
| 0.4.0 ~ 0.6.4     | 2.2.21 |

!!! warning "Version mismatch"

    The Compose Stability Analyzer compiler plugin is tightly coupled to the Kotlin compiler version. If your project uses a different Kotlin version, you may see errors like:

    ```
    e: This version of the Compose Stability Analyzer compiler plugin
       requires Kotlin version X.X.X but you are using Y.Y.Y
    ```

    Upgrade or downgrade the plugin version to match your Kotlin version.

## Compose Compiler Compatibility

The Compose Stability Analyzer works alongside the Compose compiler. Ensure your Compose compiler version is compatible with your Kotlin version:

| Kotlin | Compose Compiler |
|--------|-----------------|
| 2.3.0  | Bundled with Kotlin |
| 2.2.21 | Bundled with Kotlin |

!!! note "Compose Compiler bundled with Kotlin"

    Starting with Kotlin 2.0, the Compose compiler is bundled as a Kotlin compiler plugin. You no longer need to specify a separate Compose compiler version — it is automatically matched to your Kotlin version.

## Maven Coordinates

```kotlin
// Gradle plugin
id("com.github.skydoves.compose.stability.analyzer") version "0.6.7"

// Runtime (added automatically by the Gradle plugin)
implementation("com.github.skydoves:compose-stability-runtime:<version>")
```

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/compose-stability-runtime.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%compose-stability-runtime%22)
