# Stability Explorer

The Stability Explorer provides a dedicated tool window in your IDE, allowing you to visually trace which composable functions are skippable or non-skippable across your entire project. While gutter icons and inline hints work at the file level, the Stability Explorer gives you a project-wide overview — making it easy to identify modules, packages, or files that have the most stability issues.

![stability-explorer](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview7.png)

## How to Open

The Stability Explorer requires the [Compose Stability Analyzer Gradle plugin](../gradle-plugin/getting-started.md) to be installed, because it reads stability information from JSON files generated during compilation. Without the Gradle plugin, the explorer will show an empty state with setup instructions.

To open the explorer, go to **View** > **Tool Windows** > **Compose Stability Analyzer**. You'll see an icon appear on the right side of Android Studio — click it to open the panel. After a clean build of your project, click the **Refresh** button on the panel to populate the tree with your composables' stability data.

## Tree View

The explorer organizes all composables in a hierarchical tree view grouped by **Module** > **Package** > **File** > **Composable**. This structure mirrors your project's organization, making it easy to navigate to the area you're interested in.

Each node in the tree displays a stability summary in the format **(XS, YNS)**, where **S** is the count of skippable composables and **NS** is the count of non-skippable composables within that scope. Zero counts are hidden to keep the UI clean. For example, a package showing **(3S, 1NS)** tells you that three composables are skippable and one is not — you can expand the package node to find which one needs attention.

## Color Coding

The tree uses a simple two-color system. **Green** nodes represent skippable composables where all parameters are stable. **Yellow** nodes represent non-skippable composables that have at least one unstable parameter. Parent nodes (modules, packages, files) inherit the color of their most critical child — if any composable within a package is non-skippable, the package node appears yellow.

This color propagation means you can quickly scan the top-level module nodes to find which modules have stability issues, then drill down into the specific packages and files without expanding every node.

## Filtering

The filter buttons at the top of the panel let you focus on the composables that matter most. The **All** filter shows every composable in the project, giving you a complete overview. The **Skippable** filter shows only composables with all stable parameters — useful for confirming that your optimized composables are still stable. The **Non-skippable** filter shows only composables with unstable parameters, which is the most useful filter when you're actively working on improving performance.

## Navigation

Double-clicking any composable in the tree navigates directly to its source code location in the editor. This makes the Stability Explorer an effective starting point for a performance improvement session — filter to non-skippable composables, scan the list, and double-click to jump straight to the code that needs attention.

## Details Pane

Selecting a composable in the tree populates the details pane at the bottom of the explorer. The details pane shows the complete function signature, whether the composable is skippable and restartable, the full parameter list with individual stability statuses, and the specific reason each parameter is stable or unstable. This is the same information available in the hover tooltip, but presented in a persistent panel that doesn't disappear when you move your mouse.

The details pane is particularly useful when comparing multiple composables — you can click through different composables in the tree while keeping the details visible, making it easy to spot patterns (e.g., multiple composables that are unstable because they all share the same unstable data class).
