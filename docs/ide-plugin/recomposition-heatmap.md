# Live Recomposition Heatmap

The Live Recomposition Heatmap bridges runtime behavior with your IDE by displaying actual recomposition counts from a connected device directly above composable functions in the editor. While static analysis tells you which composables *could* recompose unnecessarily, the heatmap shows you which ones *actually are* recomposing in real time, with color-coded severity indicators that highlight the hottest spots in your UI.

![heatmap](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/heatmap.gif)

## Prerequisites

The heatmap reads `@TraceRecomposition` events from your running app via ADB. To use it, you need:

1. The [Compose Stability Analyzer Gradle plugin](../gradle-plugin/getting-started.md) applied to your project.
2. Composable functions annotated with [`@TraceRecomposition`](../gradle-plugin/trace-recomposition.md).
3. Logging enabled via `ComposeStabilityAnalyzer.setEnabled(true)` in your `Application` class.
4. A device or emulator connected via ADB.

!!! note "ADB Path"

    The plugin looks for ADB in your `ANDROID_HOME` or `ANDROID_SDK_ROOT` environment variable under `platform-tools/adb`. If neither is set, it falls back to `adb` on your system PATH. If ADB is not found, the plugin shows a notification with instructions.

## Starting and Stopping

Open the Compose Stability Analyzer tool window (**View** > **Tool Windows** > **Compose Stability Analyzer**) and click the **Start/Stop Recomposition Heatmap** button in the tool window title bar. This button is visible regardless of which tab (Explorer, Cascade, or Heatmap) is currently selected.

When you click start, the plugin checks for connected ADB devices. If exactly one device is connected, monitoring begins immediately. If multiple devices are found, a picker popup appears so you can select which device to monitor. If no devices are connected, a notification prompts you to connect one.

You can also access the toggle via the **Code** menu in the main menu bar.

While running, the plugin reads logcat output in real time, filtering for recomposition events emitted by the `@TraceRecomposition` annotation. To stop monitoring, click the same button again.

## Reading the Heatmap

When the heatmap is active and your app is running with `@TraceRecomposition` composables, recomposition counts appear as inline annotations directly above each composable function in the editor. Each annotation shows the total number of recompositions observed for that composable since monitoring started.

The annotations are color-coded by severity to help you quickly identify hot spots:

| Color | Recomposition Count | Meaning |
|-------|-------------------|---------|
| **Green** | Fewer than 10 | Normal recomposition frequency |
| **Yellow** | 10 to 50 | Moderate; worth investigating |
| **Red** | More than 50 | Excessive; likely a performance issue |

These thresholds are configurable in **Settings** > **Tools** > **Compose Stability Analyzer** under the **Recomposition Heatmap** section.

!!! note "Name matching"

    The heatmap matches recomposition events to composable functions by simple name (e.g., `UserProfile`). If multiple composable functions share the same name across different files, they will share the same recomposition data. The annotation tooltip notes this when applicable.

## Inspecting Recomposition Details

Clicking on a heatmap annotation in the editor opens the **Heatmap** tab in the Compose Stability Analyzer tool window, pre-populated with detailed recomposition data for that composable. The detail view shows:

- **Total recomposition count** and the maximum count from a single event.
- **Recent recomposition events** listed chronologically, each showing the recomposition number and timestamp.
- **Parameter changes** for each event, including which parameters changed, which were stable, and which were unstable.

This lets you go from a high-level "this composable recomposed 47 times" observation directly to the specific parameter changes that triggered each recomposition, without switching to Logcat or searching through logs manually.

## Configuration

The heatmap settings are available in **Settings** > **Tools** > **Compose Stability Analyzer** under the **Recomposition Heatmap** section.

| Setting | Default | Description |
|---------|---------|-------------|
| **Enable heatmap** | `true` | Master toggle for the heatmap feature |
| **Auto-start** | `false` | Automatically start monitoring when a single device is connected |
| **Show when stopped** | `true` | Keep displaying the last heatmap data after stopping monitoring |
| **Green threshold** | `10` | Recomposition count below which the annotation is green |
| **Red threshold** | `50` | Recomposition count above which the annotation is red |

## Practical Workflow

The heatmap is most effective as a complement to static analysis. Use [gutter icons](gutter-icons.md) and the [Stability Explorer](stability-explorer.md) to identify composables that *could* have performance issues, then use the heatmap to confirm which ones *actually do* during real user interactions.

A typical workflow looks like this: annotate your screen-level composables with `@TraceRecomposition`, start the heatmap, and then interact with your app normally (scrolling, navigating, entering data). Watch the editor for red annotations appearing. When you spot a hot composable, click its annotation to see the parameter change history and identify whether the recompositions are caused by actual data changes or by unstable parameters triggering unnecessary work.

After fixing stability issues, clear the heatmap data using the clear button in the Heatmap tab toolbar and repeat your interactions. The recomposition counts should drop, confirming that your optimizations are working at runtime, not just in static analysis.
