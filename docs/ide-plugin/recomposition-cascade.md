# Recomposition Cascade

The Recomposition Cascade Visualizer lets you trace the downstream impact of a composable's recomposition. Starting from any `@Composable` function, it analyzes the call tree to show which child composables would be affected when the parent recomposes, along with each one's stability status. This helps you understand the ripple effect of a single unstable composable across your UI hierarchy.

![cascade](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/cascade.png)

## How to Use

Right-click any `@Composable` function in the editor and select **Analyze Recomposition Cascade** from the context menu. The plugin performs a background analysis of the function's call tree and displays the results in the **Cascade** tab of the Compose Stability Analyzer tool window.

The analysis runs in the background with cancellation support, so you can continue working in the editor while it processes. For large call trees, a progress indicator shows the current analysis status.

## Tree View

The results appear as a hierarchical tree rooted at the composable you selected. Each node represents a downstream composable that the root calls, directly or transitively. Nodes are nested to reflect the actual call structure: if `ScreenA` calls `Header` which calls `Title`, the tree shows `ScreenA` > `Header` > `Title`.

Each node displays the composable's name and its stability status. **Skippable** composables appear with a green indicator, meaning all their parameters are stable and Compose can skip them during recomposition. **Non-skippable** composables appear with a red indicator, meaning they have at least one unstable parameter and will always re-execute when their parent recomposes.

## Summary Statistics

At the top of the cascade tree, a summary node shows aggregate statistics for the entire call tree: the total number of downstream composables found, how many are skippable, how many are non-skippable, and the maximum depth of the tree. This gives you a quick sense of the blast radius. A root composable with 15 downstream composables and 8 of them non-skippable has a much larger performance impact than one with 3 downstream composables that are all skippable.

## Cycle Detection

Compose UIs can have recursive or mutually recursive composable calls (for example, a tree-rendering composable that calls itself for child nodes). The cascade analyzer detects these cycles and marks them in the tree rather than entering an infinite loop. When a cycle is detected, the node shows a cycle indicator and stops expanding further along that path.

The analysis also enforces a configurable maximum depth limit (default: 10 levels) to keep results manageable and prevent excessively deep trees from slowing down the analysis.

## Navigation

Double-clicking any node in the cascade tree navigates directly to that composable's source code in the editor. This makes the cascade view an effective tool for tracing performance issues through your UI hierarchy: spot a non-skippable composable deep in the tree, double-click to jump to its source, and investigate why its parameters are unstable.

## Practical Workflow

The cascade visualizer is most useful when you've identified a composable with performance issues and want to understand the full impact. For example, if your main screen composable recomposes frequently, running a cascade analysis shows you every downstream composable that gets affected. You might discover that a single unstable data class parameter at the root causes dozens of child composables to re-execute unnecessarily.

Start by analyzing high-level screen composables to get a broad view of your recomposition impact. Focus on branches where non-skippable composables cluster together, as these represent the highest-impact areas for optimization. After fixing stability issues, re-run the cascade analysis to confirm that the downstream composables are now skippable.
