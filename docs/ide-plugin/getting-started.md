# Getting Started

The Compose Stability Analyzer IntelliJ Plugin brings **visual stability analysis** directly into your IDE, helping you identify and fix performance issues while you code.

## Installation

You can install the plugin from the JetBrains Marketplace:

1. Open **Android Studio** (or IntelliJ IDEA)
2. Go to **Settings** > **Plugins** > **Marketplace**
3. Search for **Compose Stability Analyzer**
4. Click **Install** and restart your IDE

![install](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview3.png)

If you see gutter icons and tooltips on your composable functions, you're all set!

## Requirements

- Android Studio or IntelliJ IDEA 2024.2+ (build 242+)
- Kotlin 2.0.21+
- Jetpack Compose project

## What You'll See

Once installed, the plugin provides four types of visual feedback:

| Feature | Description |
|---------|-------------|
| **Gutter Icons** | Colored dots in the left margin showing skippability |
| **Hover Tooltips** | Detailed stability breakdown on mouse hover |
| **Inline Hints** | Parameter-level stability badges |
| **Code Inspections** | Warnings and quick fixes for unstable composables |

## Verification

To verify the plugin is working:

1. Open any Kotlin file with `@Composable` functions
2. Look for colored dots in the left margin (gutter)
3. Hover over a composable function name to see the tooltip

!!! note "Troubleshooting"

    If the plugin doesn't appear to work, check **Settings > Tools > Compose Stability Analyzer** and make sure **Enable stability checks** is turned on.
