# Getting Started

The Compose Stability Analyzer IntelliJ Plugin brings **visual stability analysis** directly into your IDE, helping you identify and fix performance issues while you code. Instead of waiting for runtime or build-time reports, you get instant feedback right in Android Studio or IntelliJ IDEA.

## Installation

You can install the plugin from the JetBrains Marketplace. Open **Android Studio** (or IntelliJ IDEA), navigate to **Settings** > **Plugins** > **Marketplace**, and search for **Compose Stability Analyzer**. Click **Install** and restart your IDE when prompted.

![install](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview3.png)

After installation, open any Kotlin file containing `@Composable` functions. If you see colored dots appearing in the left margin of your editor next to composable function declarations, the plugin is working correctly.

## Requirements

The plugin requires **Android Studio or IntelliJ IDEA 2024.2+** (build 242 or later) with **Kotlin 2.0.21+** configured in a Jetpack Compose project. It works with both single-module and multi-module project structures.

## What You'll See

Once installed, the plugin provides four types of visual feedback that work together to give you a complete picture of your composables' stability.

**Gutter Icons** are colored dots that appear in the left margin of your editor. Green means the composable is skippable (all parameters stable), yellow means stability is determined at runtime, and red means the composable has unstable parameters and cannot be skipped. This is the quickest way to scan your code for potential performance issues.

**Hover Tooltips** appear when you move your mouse over a composable function name. They show a detailed stability breakdown including whether the composable is skippable and restartable, how many parameters are stable vs. unstable, and the specific reason behind each parameter's stability status.

**Inline Hints** are small badges that appear directly next to each parameter's type declaration. They let you see the stability of every parameter at a glance without needing to hover, which is particularly useful for composables with many parameters.

**Code Inspections** go beyond passive indicators. When a composable has unstable parameters, the plugin highlights the issue with a warning underline and offers quick fixes through the Alt+Enter menu, such as adding `@TraceRecomposition` for runtime debugging.

## Verification

To verify the plugin is working, open any Kotlin file with `@Composable` functions and look for colored dots in the left margin (the gutter area). Hover over a composable function name; you should see a tooltip with detailed stability information. If nothing appears, the plugin may be disabled.

!!! note "Troubleshooting"

    If the plugin doesn't appear to work, check **Settings > Tools > Compose Stability Analyzer** and make sure **Enable stability checks** is turned on. Also verify that your project uses Kotlin 2.0.21+ and has Jetpack Compose configured.
