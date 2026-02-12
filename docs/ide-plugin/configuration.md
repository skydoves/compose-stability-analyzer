# Configuration

You can customize the Compose Stability Analyzer plugin to match your preferences, IDE theme, and project requirements. All settings are accessible via **Settings** > **Tools** > **Compose Stability Analyzer**.

![settings](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview4.png)
![settings-project](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview5.png)

## General Settings

The general settings control which visual features are active and how the plugin analyzes your code.

**Enable stability checks** is the master toggle for the entire plugin. When disabled, no gutter icons, tooltips, inline hints, or inspections will appear. This is useful if you want to temporarily disable the plugin without uninstalling it — for example, when working on a non-Compose part of your project where the analysis overhead isn't needed.

**Strong Skipping mode** controls whether the analyzer accounts for Compose's Strong Skipping mode when determining stability. With Strong Skipping enabled (the default since Compose Compiler 1.5.4+), more composables are considered skippable because unstable parameters are compared by instance equality. If your project doesn't use Strong Skipping, disable this setting so the analysis reflects your actual compiler behavior.

**Show gutter icons**, **Show inline hints**, and **Show warnings** let you independently toggle each visual indicator. For example, you might want gutter icons for a quick overview but find inline hints too noisy — you can disable hints while keeping the gutter icons active. Each indicator serves a different purpose, and the right combination depends on your workflow.

**Show in test source sets** controls whether the plugin analyzes composables in test directories. By default, this is disabled because test composables (previews, test fixtures) typically don't need stability optimization. Enable this if you want to analyze composable stability in your test code as well.

| Setting | Default | Description |
|---------|---------|-------------|
| **Enable stability checks** | `true` | Toggle the entire plugin on/off |
| **Strong Skipping mode** | `true` | Analyze with Strong Skipping mode enabled |
| **Show gutter icons** | `true` | Show colored dots in the editor margin |
| **Show inline hints** | `true` | Show stability badges next to parameters |
| **Show warnings** | `true` | Show warning underlines for unstable composables |
| **Show in test source sets** | `false` | Enable gutter icons in test directories |

## Parameter Hint Colors

The default colors for stability indicators (green for stable, red for unstable, yellow for runtime) work well with most IDE themes, but you can customize them to improve contrast or match your personal color scheme. Navigate to the **Parameter Hint Colors** section in the plugin settings to adjust the colors for each stability level independently.

This is especially useful if you use a high-contrast theme, a colorblind-friendly theme, or simply prefer different colors. The gutter icons, inline hints, and Stability Explorer all respect these color settings.

## Stability Configuration File

The Compose compiler determines stability based on type annotations and property mutability. However, there are cases where a type is effectively stable but the compiler can't infer it — third-party library types without `@Stable` annotations, code-generated types, or legacy classes you know are immutable but can't easily modify. The stability configuration file lets you tell the analyzer to treat these types as stable.

### Global Settings

Global settings apply to all projects on your machine. Create your configuration file anywhere on your system, then go to **Settings** > **Tools** > **Compose Stability Analyzer** and either add patterns directly to the **Ignored Type Patterns** section or reference a file path in the **Stability configuration file** field.

Global settings are ideal for types you frequently encounter across projects, such as common third-party library types (`java.time.LocalDateTime`, `kotlinx.datetime.Instant`, etc.).

### Per-Project Settings (Recommended)

Per-project settings are stored alongside your project and can be shared with your team through version control. Create a configuration file in your project (e.g., `config/stability-config.txt`), then go to **Settings** > **Tools** > **Compose Stability Analyzer** > **Project Configuration** and set the path to your file. Commit the configuration file to version control so every team member uses the same stability rules.

!!! note "Precedence"

    Per-project settings take precedence over global settings. This means you can have global settings for your personal preferences and project-specific settings that your entire team uses. When both exist, the project-level configuration wins.

### Configuration File Format

The configuration file is a plain text file where each line specifies a fully-qualified type name or a wildcard pattern. Lines starting with `//` are comments.

```
// Consider LocalDateTime stable — it's immutable but lacks @Stable annotation
java.time.LocalDateTime

// Consider all types in the datalayer package stable
com.datalayer.*

// Consider all types in datalayer and all sub-packages stable
com.datalayer.**
```

The single wildcard (`*`) matches types within a specific package only. The double wildcard (`**`) matches types within a package and all of its sub-packages recursively. This distinction is important for large packages where you want fine-grained control.

## Ignored Type Patterns

The **Ignored Type Patterns** field in the settings provides a quick way to exclude specific types from stability analysis without creating a separate configuration file. Types matching these patterns are treated as stable by the analyzer.

```
com.example.internal.*
com.thirdparty.model.**
```

This is convenient for quick overrides, but for team-shared configuration, the per-project stability configuration file is the recommended approach since it can be committed to version control and reviewed in pull requests.
