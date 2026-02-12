# Gutter Icons & Tooltips

Gutter icons and hover tooltips are the primary visual feedback mechanisms of the Compose Stability Analyzer plugin.

## Gutter Icons

Gutter icons appear in the left margin of your editor, giving you instant visual feedback on your composable functions:

![gutter-icons](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview1.png)

This is the fastest way to spot performance problems. Just glance at the left margin — if you see red dots, those composables need attention.

### Icon Colors

| Color | Meaning |
|-------|---------|
| **Green** | Skippable — all parameters are stable |
| **Yellow** | Runtime — stability determined at runtime |
| **Red** | Not skippable — has unstable parameters |

## Hover Tooltips

When you hover your mouse over a composable function name, a detailed tooltip appears showing:

- Whether the composable is **skippable** or **restartable**
- How many parameters are **stable** vs. **unstable**
- Which specific parameters are causing instability
- The **reason** behind each parameter's stability status
- Additional context about receivers (if any)

This gives you the **why** behind the gutter icon color. You don't just see that a composable is unstable — you see exactly which parameters are the problem and why.

## Practical Workflow

1. **Scan**: Look at the gutter icons as you scroll through your code
2. **Identify**: Red/yellow dots indicate composables worth investigating
3. **Diagnose**: Hover over the function name to see which parameters are unstable
4. **Fix**: Address the unstable parameters (use `val` instead of `var`, add `@Stable`/`@Immutable`, use immutable collections, etc.)
5. **Verify**: The gutter icon turns green when all parameters are stable
