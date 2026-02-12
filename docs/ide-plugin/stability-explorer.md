# Stability Explorer

The Stability Explorer provides a dedicated tool window in your IDE, allowing you to visually trace which composable functions are skippable or non-skippable across your entire project.

![stability-explorer](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview7.png)

## How to Open

1. Install the [Compose Stability Analyzer Gradle plugin](../gradle-plugin/getting-started.md)
2. Go to **View** > **Tool Windows** > **Compose Stability Analyzer**
3. You'll see the icon on the right side of Android Studio — click it to open the panel
4. Clean & build your project, then click the **Refresh** button on the panel

## Features

### Tree View

The explorer shows all composables in a tree view grouped by:

- **Module** > **Package** > **File** > **Composable**

Each item shows stability counts as **(XS, YNS)** where **S** = Skippable and **NS** = Non-skippable. Zero counts are hidden for a cleaner UI.

### Color Coding

- **Green**: Skippable composables (all parameters stable)
- **Yellow**: Non-skippable composables (has unstable parameters)

### Filtering

Use the filter buttons at the top of the panel to show:

- **All**: Every composable in the project
- **Skippable**: Only composables with all stable parameters
- **Non-skippable**: Only composables with unstable parameters

### Navigation

Double-click any composable in the tree to navigate directly to its source code location.

### Details Pane

Selecting a composable shows detailed information in the details pane:

- Function signature
- Skippable/restartable status
- Parameter list with individual stability statuses
- Stability reasons for each parameter

## Requirements

The Stability Explorer requires the Gradle plugin to be installed because it reads the stability information from JSON files generated during compilation. Without the Gradle plugin, the explorer will show an empty state with setup instructions.
