# Inline Parameter Hints

Inline hints are small badges that appear right next to parameter types in your composable function declarations, showing the stability of each individual parameter. While gutter icons give you a function-level summary and tooltips provide detailed breakdowns on hover, inline hints let you see parameter-level stability information directly in your code, without any interaction required.

## How It Works

Each parameter in your composable function gets a small inline badge indicating whether it is **STABLE**, **UNSTABLE**, or **RUNTIME**. The badges appear immediately after the parameter's type annotation, making them visible as you read through function signatures.

![inline-hints](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview2.png)

This level of detail is particularly valuable when working with composables that have many parameters. Instead of hovering over the function name and scanning a tooltip for a specific parameter, you can see the stability status of every parameter at once while reading the code naturally. If a composable has five parameters and only one is unstable, the inline hint tells you exactly which one without any extra interaction.

## Hint Colors

The hint colors match the gutter icon colors to maintain a consistent visual language across the plugin. You can customize these colors in the plugin settings to match your IDE theme or personal preferences.

| Stability | Default Color | Meaning |
|-----------|---------------|---------|
| **STABLE** | Green | This parameter won't cause unnecessary recompositions. It is either a primitive type, annotated with `@Stable`/`@Immutable`, or a data class with only `val` properties of stable types. |
| **UNSTABLE** | Red | This parameter may trigger unnecessary recompositions. It likely has mutable properties (`var`), uses mutable collections, or comes from a module not compiled with the Compose compiler. |
| **RUNTIME** | Yellow | This parameter's stability is determined at runtime, typically because it involves generics or interface types whose concrete implementation may or may not be stable. |

## Customizing Colors

To change the hint colors, go to **Settings** > **Tools** > **Compose Stability Analyzer** and find the **Parameter Hint Colors** section. You can adjust the colors for each stability level independently. This is especially useful if you're using a custom IDE theme where the default green/red/yellow colors don't provide enough contrast or don't match your color scheme.

## When This Is Useful

Inline hints shine in several scenarios. When you're **reviewing code** (whether your own or a teammate's), they give you an immediate sense of which parameters might cause performance issues without requiring you to interact with the editor. When you're **refactoring** a composable's parameters, the hints update in real time, confirming whether your changes improved or worsened stability. And when you're **learning** how Compose stability works, seeing the stability status directly next to each type helps build intuition about which types Compose considers stable and why.

If you find the inline hints too noisy for everyday coding, you can disable them independently from gutter icons in the plugin settings while still keeping the other visual indicators active.
