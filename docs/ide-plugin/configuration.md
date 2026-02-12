# Configuration

You can customize the Compose Stability Analyzer plugin to match your preferences and project requirements.

Access the settings via: **Settings** > **Tools** > **Compose Stability Analyzer**

![settings](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview4.png)
![settings-project](https://github.com/skydoves/compose-stability-analyzer/raw/main/art/preview5.png)

## General Settings

| Setting | Default | Description |
|---------|---------|-------------|
| **Enable stability checks** | `true` | Toggle the entire plugin on/off |
| **Strong Skipping mode** | `true` | Analyze with Strong Skipping mode enabled |
| **Show gutter icons** | `true` | Show colored dots in the editor margin |
| **Show inline hints** | `true` | Show stability badges next to parameters |
| **Show warnings** | `true` | Show warning underlines for unstable composables |
| **Show in test source sets** | `false` | Enable gutter icons in test directories |

## Parameter Hint Colors

Customize the colors used for stability indicators to match your IDE theme:

- **Stable color**: Color for stable parameters
- **Unstable color**: Color for unstable parameters
- **Runtime color**: Color for runtime-determined parameters

## Stability Configuration File

You can mark custom types as stable, even if they don't have `@Stable` or `@Immutable` annotations. This is useful when:

- You have immutable data classes from third-party libraries
- You're using code generation tools that produce stable types
- You want to treat certain types as stable without modifying their source code

### Global Settings

1. Create your configuration file anywhere on your system
2. Go to **Settings** > **Tools** > **Compose Stability Analyzer**
3. Add patterns to the **Ignored Type Patterns** section, or reference a file in the **Stability configuration file** field

### Per-Project Settings (Recommended)

1. Create a configuration file in your project (e.g., `config/stability-config.txt`)
2. Go to **Settings** > **Tools** > **Compose Stability Analyzer** > **Project Configuration**
3. Set the path to your configuration file
4. Commit the file to version control so your team shares the same configuration

!!! note "Precedence"

    Per-project settings take precedence over global settings. This means you can have global settings for your personal preferences and project-specific settings that your entire team uses.

### Configuration File Format

The configuration file supports wildcard patterns:

```
// Consider LocalDateTime stable
java.time.LocalDateTime

// Consider all types in datalayer package stable
com.datalayer.*

// Consider all types in datalayer and sub-packages stable
com.datalayer.**
```

## Ignored Type Patterns

Add patterns to exclude specific types from stability analysis. Types matching these patterns will be treated as stable:

```
com.example.internal.*
com.thirdparty.model.**
```
