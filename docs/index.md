# Overview

<p align="center">
  <img src="https://github.com/user-attachments/assets/d3b1b1ae-d4f4-4ab6-8067-376f74721186" width="120px"/>
</p>

**Compose Stability Analyzer** provides real-time analysis of your Jetpack Compose composable functions' stability directly within Android Studio or IntelliJ. It helps you understand why a composable function is stable or unstable, and offers detailed insights through recomposition tracing and logging.

Instantly see which composable parameters cause unnecessary recompositions right in your IDE, and automatically catch stability regressions in CI before they reach production.

![preview](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview0.png)

## Key Features

- **Gutter Icons**: Colored dots in the editor margin showing if a composable is skippable
- **Hover Tooltips**: Detailed stability information when you hover over composable functions
- **Inline Parameter Hints**: Badges next to parameters showing their stability status
- **Code Inspections**: Quick fixes and warnings for unstable composables
- **Stability Explorer**: Tree view of all composables' stability in your project
- **Recomposition Tracing**: Runtime tracking with `@TraceRecomposition` annotation
- **Stability Validation**: CI-ready `stabilityDump`/`stabilityCheck` Gradle tasks to prevent regressions

## Two Components

Compose Stability Analyzer consists of two independent, complementary components:

| Component | Purpose | Integration |
|-----------|---------|-------------|
| **IDE Plugin** | Visual stability indicators directly in Android Studio | Install from JetBrains Marketplace |
| **Gradle Plugin** | Runtime recomposition tracing and stability validation | Add to your `build.gradle.kts` |

!!! note "Independence"

    The IDE plugin and Gradle plugin are completely independent. You can use either one alone, or both together for the full experience.

## Quick Start

### IDE Plugin

Open **Android Studio** > **Settings** > **Plugins** > **Marketplace** > search **Compose Stability Analyzer** > **Install**

![install](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview3.png)

### Gradle Plugin

Add the plugin to `libs.versions.toml`:

```toml
[plugins]
stability-analyzer = { id = "com.github.skydoves.compose.stability.analyzer", version = "0.6.7" }
```

Apply to your root `build.gradle.kts`:

```kotlin
alias(libs.plugins.stability.analyzer) apply false
```

Apply to your app or module's `build.gradle.kts`:

```kotlin
alias(libs.plugins.stability.analyzer)
```

## Why Use This?

Jetpack Compose's declarative paradigm brings hidden complexity: **understanding recomposition behavior**. When a composable recomposes unnecessarily, you pay a performance cost — CPU cycles spent re-rendering UI that didn't actually change.

The challenge isn't just identifying these issues — it's understanding **why** they happen. Compose Stability Analyzer bridges this gap by giving you real-time visibility into stability at every level: from parameter hints in your editor to CI-enforced regression checks.

!!! note "Not about making everything stable"

    You don't need to make every composable function skippable or all parameters stable — these are not direct indicators of performance optimization. The goal of this plugin is to help you **explore how Compose's stability mechanisms work** and use them as tools for examining and debugging composables that may have performance issues.
