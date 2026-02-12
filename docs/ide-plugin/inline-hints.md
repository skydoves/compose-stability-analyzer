# Inline Parameter Hints

Inline hints are small badges that appear right next to parameter types, showing the stability of each individual parameter.

## How It Works

This is the most detailed level of feedback — you see stability information for every single parameter at a glance:

![inline-hints](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview2.png)

Each parameter in your composable function gets a small inline badge indicating whether it is **STABLE**, **UNSTABLE**, or **RUNTIME**.

## Hint Colors

You can customize the colors used for each stability level in the plugin settings. The default colors are:

| Stability | Default Color | Meaning |
|-----------|---------------|---------|
| **STABLE** | Green | Parameter won't cause unnecessary recompositions |
| **UNSTABLE** | Red | Parameter may trigger unnecessary recompositions |
| **RUNTIME** | Yellow | Stability determined at runtime |

## Customizing Colors

To change the hint colors:

1. Go to **Settings** > **Tools** > **Compose Stability Analyzer**
2. Find the **Parameter Hint Colors** section
3. Adjust colors to match your IDE theme

## When This Is Useful

Inline hints shine when you have composable functions with many parameters. Instead of hovering over the function name to see the tooltip, you can immediately see which parameters are stable and which aren't — all at a glance while reading the code.
