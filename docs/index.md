# Overview

<p align="center">
  <img src="https://github.com/user-attachments/assets/d3b1b1ae-d4f4-4ab6-8067-376f74721186" width="120px"/>
</p>

**Compose Stability Analyzer** provides real-time analysis of your Jetpack Compose composable functions' stability directly within Android Studio or IntelliJ. It helps you understand why a composable function is stable or unstable, and offers detailed insights through recomposition tracing and logging.

Instantly see which composable parameters cause unnecessary recompositions right in your IDE, and automatically catch stability regressions in CI before they reach production.

![preview](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview0.png)

## Key Features

The Compose Stability Analyzer equips you with visual and analytical tools across your entire development workflow. **Gutter Icons** place colored dots in the editor margin so you can see at a glance whether a composable is skippable. **Hover Tooltips** provide detailed stability breakdowns — including the specific reason each parameter is stable or unstable — when you hover over any composable function. **Inline Parameter Hints** go even further, placing badges next to each parameter so you can read stability information without leaving the code.

Beyond visual feedback, the plugin includes **Code Inspections** that actively suggest improvements with quick fixes, and a **Stability Explorer** that gives you a project-wide tree view of all composables organized by module, package, and file. For runtime analysis, the **`@TraceRecomposition` annotation** lets you trace exactly which parameters change during recomposition, and the **`stabilityDump`/`stabilityCheck` Gradle tasks** bring CI-ready stability validation to prevent regressions from reaching production.

## Two Components

Compose Stability Analyzer consists of two independent, complementary components. The **IDE Plugin** provides visual stability indicators — gutter icons, tooltips, inline hints, inspections, and the Stability Explorer — directly in Android Studio. You install it from the JetBrains Marketplace and it works immediately with no build configuration changes.

The **Gradle Plugin** operates at build time and runtime. It powers the `@TraceRecomposition` annotation for runtime recomposition logging, and provides the `stabilityDump`/`stabilityCheck` tasks for CI-ready stability validation. You add it to your `build.gradle.kts`.

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

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/compose-stability-runtime.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%compose-stability-runtime%22)

Add the plugin to `libs.versions.toml`:

```toml
[plugins]
stability-analyzer = { id = "com.github.skydoves.compose.stability.analyzer", version = "$version" }
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

The challenge isn't just identifying these issues — it's understanding **why** they happen. A composable might recompose because a parameter changed, because a parameter is unstable, or because a parent recomposed and the composable couldn't be skipped. Without tooling, diagnosing the root cause means adding manual logging, reading compiler metrics reports, or guessing.

Compose Stability Analyzer bridges this gap by giving you real-time visibility into stability at every level: from parameter hints in your editor to CI-enforced regression checks. Instead of discovering performance issues after they've shipped, you catch them the moment they're introduced.

!!! note "Not about making everything stable"

    You don't need to make every composable function skippable or all parameters stable — these are not direct indicators of performance optimization. The goal of this plugin is to help you **explore how Compose's stability mechanisms work** and use them as tools for examining and debugging composables that may have performance issues.
