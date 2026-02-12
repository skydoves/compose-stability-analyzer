# Gutter Icons & Tooltips

Gutter icons and hover tooltips are the primary visual feedback mechanisms of the Compose Stability Analyzer plugin. Together, they let you quickly scan your code for stability issues and then drill into the details of any composable that needs attention.

## Gutter Icons

Gutter icons appear in the left margin of your editor next to every `@Composable` function declaration. They provide an instant, at-a-glance summary of each composable's stability status — you can scan an entire file in seconds just by looking at the colors in the margin.

![gutter-icons](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview1.png)

### Icon Colors

The icon color tells you whether a composable can be skipped during recomposition.

A **green** dot means the composable is **skippable** — all of its parameters are stable, so Compose can safely skip re-executing the function when its parent recomposes, as long as the parameter values haven't changed. This is the ideal state for performance.

A **yellow** dot means the composable's stability is **determined at runtime**. This typically happens with generic types or types whose stability depends on their type arguments. The composable may or may not be skipped depending on the actual values passed at runtime.

A **red** dot means the composable is **not skippable** — it has one or more unstable parameters, which means Compose must always re-execute it during recomposition, even if the parameter values haven't actually changed. These composables are worth investigating if you're experiencing performance issues.

| Color | Meaning |
|-------|---------|
| **Green** | Skippable — all parameters are stable |
| **Yellow** | Runtime — stability determined at runtime |
| **Red** | Not skippable — has unstable parameters |

## Hover Tooltips

When you hover your mouse over a composable function name, a detailed tooltip appears with a complete stability breakdown. This is where you get the **why** behind the gutter icon color — you don't just see that a composable is unstable, you see exactly which parameters are the problem and the specific reason each one is stable or unstable.

The tooltip shows whether the composable is **skippable** (can be skipped during recomposition) and **restartable** (can independently restart when state changes). It lists the total number of stable vs. unstable parameters, then breaks down each parameter individually — showing the parameter name, type, stability status, and the reason for that status (e.g., "has mutable properties", "marked @Stable", "primitive type").

If the composable has receivers (such as extension functions on a scope), those are also included in the tooltip with their stability information.

## Practical Workflow

The most effective way to use gutter icons and tooltips is as a two-step process. First, **scan** your code by scrolling through and looking at the gutter icons. Red and yellow dots stand out visually, drawing your attention to composables that may need investigation. You don't need to read any code at this stage — the colors tell you everything.

When you spot a red or yellow dot, **hover** over the composable function name to see the detailed tooltip. The tooltip tells you exactly which parameters are unstable and why, giving you the information you need to decide whether to fix the instability (by making properties `val`, adding `@Stable`/`@Immutable`, or using immutable collections) or to accept it as intentional.

After making changes, the gutter icon updates in real time. When a red dot turns green, you know the composable is now fully stable and skippable — Compose will be able to skip it during recomposition whenever its parameters haven't changed.
