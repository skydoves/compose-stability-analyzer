<p align="center">
<img src="https://github.com/user-attachments/assets/d3b1b1ae-d4f4-4ab6-8067-376f74721186" width="120px"/>
</p>

<h1 align="center">Compose Stability Analyzer</h1></br>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves/compose-stability-analyzer/actions"><img alt="Build Status" src="https://github.com/skydoves/compose-stability-analyzer/workflows/Android%20CI/badge.svg"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a>
  <a href="https://github.com/doveletter"><img alt="Profile" src="https://skydoves.github.io/badges/dove-letter.svg"/></a><br>
</p>

![preview](art/preview0.png)

Compose Stability Analyzer provides real-time analysis of your Jetpack Compose composable functions' stability directly within Android Studio or IntelliJ. It helps you understand why a composable function is stable or unstable, and offers detailed insights through recomposition tracing and logging. 

Additionally, you can trace the reason of your composable function is triggered recomposition with a `TraceRecomposition` annotation, and export stability compatibility reports using Gradle tasks for reviewing the new stability changes.

You can change the colors used for stability indicators to match your IDE theme, enabling Strong Skipping mode for analyzing, visual indicators (showing gutter icons, warnings, inline hints), change parameter hint colors, enabling analysis in test source sets, set a stability configuration file, add ignored type patterns to exclude from the stability analysis.

## 💝 Sponsors

The sponsors listed below made it possible for this project to be released as open source. Many thanks to all of them for their support!

<a href="https://github.com/GetStream/Vision-Agents/?utm_source=github&utm_medium=devrel&utm_campaign=jaewoong-github"><img alt="Profile" src="art/logo-vision-agents.png" width="300"/></a>

**[Vision Agents (GitHub)](https://github.com/GetStream/Vision-Agents/?utm_source=github&utm_medium=devrel&utm_campaign=jaewoong-github)** is an open-source Video AI framework for building real-time voice and video applications. The framework is edge/transport agnostic, meaning developers can also bring any edge layer they like.

<a href="https://coderabbit.link/Jaewoong" target="_blank"> <img width="300" alt="coderabbit" src="art/logo-coderabbit.png" /></a>

**[CodeRabbit](https://coderabbit.link/Jaewoong)** is an AI-powered code review platform that integrates directly into pull-request workflows and IDEs, examining code changes in context and suggesting improvements.

<a href="https://firebender.com/?utm_source=skydoves"><img alt="Profile" src="art/logo-firebender.png" width="300"/></a>

**[Firebender](https://firebender.com/?utm_source=skydoves)** is the most powerful AI coding agent in Android Studio. It can create entire compose UIs from Figma links, generate UML diagrams, and even understand your voice input.

## Compose Stability Analyzer Plugin

The Compose Stability Analyzer IntelliJ Plugin brings **visual stability analysis** directly into your IDE (Android Studio), helping you identify and fix performance issues while you code. Instead of waiting for runtime or build-time reports, you get instant feedback right in Android Studio or IntelliJ IDEA.

This plugin provides real-time visual feedback about your composables' stability through four main features:

- **1. Gutter Icons**: Colored dots in the editor margin showing if a composable is skippable.
- **2. Hover Tooltips**: Detailed stability information when you hover over composable functions. It also provides the reasons: why it's stable or unstable.
- **3. Inline Parameter Hints**: Badges next to parameters showing their stability status.
- **4. Code Inspections**: Quick fixes and warnings for unstable composables.

> **Note**: You don’t need to make every composable function skippable or all parameters stable, these are not direct indicators of performance optimization. The goal of this plugin isn’t to encourage over-focusing on stability, but rather to help you explore how Compose’s stability mechanisms work and use them as tools for examining and debugging composables that may have performance issues. For more information, check out [Compose Stability Analyzer: Real-Time Stability Insights for Jetpack Compose](https://medium.com/proandroiddev/compose-stability-analyzer-real-time-stability-insights-for-jetpack-compose-1399924a0a64).

### How to Install in Android Studio

You can download the Compose Stability Analyzer Plugin with the steps below:

Open **Android Studio** > **Settings** (or **Preferences**) > **Plugins** > Marketplace > Compose Stability Analyzer > Install

![preview](art/preview3.png)

If you see gutter icons and tooltips, you're all set! 🎉

> **Note**: For now, the plugin is under review on the JetBrains Plugin Marketplace. Once the review is complete, you’ll be able to download it directly from your IDE’s marketplace.

### Stability Mark for Composable Functions

Gutter icons appear in the left margin of your editor, giving you instant visual feedback on your composable functions:

![preview](art/preview1.png)

This is the fastest way to spot performance problems. Just glance at the left margin, if you see red dots, those composables need attention.

Also, when you hover your mouse over a composable function name, a detailed tooltip appears showing:

- Whether it's skippable or restartable
- How many parameters are stable vs. unstable
- Which specific parameters are causing instability
- Additional context about receivers (if any)

This gives you the **why** behind the gutter icon color. You don't just see that a composable is unstable, you see exactly which parameters are the problem.

### Inline Parameter Hints

Inline hints are small badges that appear right next to parameter types, showing the stability of each individual parameter. This is the most detailed level of feedback, you see stability information for every single parameter at a glance.

![preview](art/preview2.png)

### Code Inspections

Code inspections go beyond visual indicators, they actively suggest improvements. When you have an unstable composable, the plugin can:

1. **Highlight the issue** with a warning underline
2. **Suggest quick fixes** via Alt+Enter menu
3. **Add @TraceRecomposition** to help you debug recompositions
4. **Provide suppression options** if the instability is intentional

This is like having an automated code review for Compose performance. The plugin doesn't just tell you about problems, it helps you fix them.

> **Troubleshooting**: If the plugin doesn't appear to work, check **Settings → Tools → Compose Stability Analyzer** and make sure **Enable stability checks** is turned on.

### Stability Explorer

This JetBrains IDE plugin provides a Stability Explorer directly in your IDE, allowing you to visually trace which composable functions are skippable or non-skippable, and identify which parameters are stable or unstable within a specific package hierarchy. 

![preview](art/preview7.png)

You can enable this explorer with the steps below:

1. Install the [Compose Stability Analyzer Gradle plugin](https://github.com/skydoves/compose-stability-analyzer?tab=readme-ov-file#including-in-your-project)
2. On your IDE, go to **View** -> **Tool Windows** -> **Compose Stability Analyzer**, then you will see the icon on the right side of your Android Studio. Click the icon then you'll see a panel.
3. Clean & build your project, and click the refresh button on the panel.

### Plugin Customization

You can change the colors used for stability indicators to match your IDE theme, enabling Strong Skipping mode for analyzing, visual indicators (showing gutter icons, warnings, inline hints), change parameter hint colors, enabling analysis in test source sets, set a stability configuration file, add ignored type patterns to exclude from the stability analysis.

You can change the configuration on the way below:

**Settings → Tools → Compose Stability Analyzer**

![preview](art/preview4.png)
![preview](art/preview5.png)

#### Stability Configuration File

The Compose Stability Analyzer allows you to mark your own custom types as stable, even if they don't have `@Stable` or `@Immutable` annotations. This is useful when:

- You have immutable data classes from third-party libraries that aren't annotated
- You're using code generation tools that produce stable types
- You want to treat certain types as stable without modifying their source code
- You have legacy code that you know is stable but can't easily refactor

**Setting up the configuration file:**

**Global settings (applies to all projects):**

1. Create your configuration file anywhere on your system
2. Go to **Settings → Tools → Compose Stability Analyzer**
3. Scroll to "Ignored Type Patterns" and add your patterns directly
4. Or reference a file path in the "Stability configuration file" field (global)

**Per-project settings (recommended for teams):**

1. Create a configuration file in your project (e.g., `config/stability-config.txt`)
2. Go to **Settings → Tools → Compose Stability Analyzer → Project Configuration**
3. Set the path to your configuration file
4. Commit the file to version control so your team shares the same configuration

**Per-project settings take precedence** over global settings. This means you can have:
- Global settings for your personal preferences
- Project-specific settings that your entire team uses

## Gradle Plugin for Tracking Runtime Recomposition and Stability Validation

You can track the recomposition for specific composable functions with the `@TraceRecomposition` annotation at runtime (KMP supports). You don't need to write any logging code yourself, just add the annotation, run your app, and watch detailed recomposition logs appear in Logcat. This compiler plugin supports Kotlin Multiplatform.

![preview](art/preview6.png)

This is incredibly useful for:
- **Debugging performance issues**: Find out which composables recompose too often, and why it was happen.
- **Monitor stability performance**: Set a threshold (`@TraceRecomposition(threshold = 15)`) and send a Firebase event or any custom analytics event to your cloud service to track which composable functions are experiencing excessive recompositions and examine the problems.
- **Understanding Compose behavior**: Learn how state changes trigger recompositions.
- **Validating optimizations**: Confirm your stability fixes actually work.

> **Note**: This library is completely independent of the Compose Stability Analyzer IntelliJ plugin and is entirely optional. You can choose to integrate it only if you find it suitable for your project.

### Including in your project 

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/compose-stability-runtime.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%compose-stability-runtime%22)

First, add the plugin to the `[plugins]` section of your `libs.versions.toml` file:

```toml
stability-analyzer = { id = "com.github.skydoves.compose.stability.analyzer", version = "0.6.6" }
```

Then, apply it to your root `build.gradle.kts` with `apply false`:

```kotlin
alias(libs.plugins.stability.analyzer) apply false
```

Finally, apply the plugin to your app or shared module's `build.gradle.kts`:

```kotlin
alias(libs.plugins.stability.analyzer)
```
Sync your project to complete the setup.

### Kotlin Version Mapping

It’s **strongly recommended to use the exact same Kotlin version** as this library. Using a different Kotlin version may lead to compilation errors during the build process.

| Stability Analyzer | Kotlin |
|--------------------|-------------|
| 0.6.5+             | 2.3.0 |
| 0.4.0~0.6.4        | 2.2.21 |

### TraceRecomposition Annotation

`@TraceRecomposition` lets you trace the behavior of any composable function. By annotating a composable with `@TraceRecomposition`, you can log parameter changes whenever that function undergoes recomposition.

```kotlin
@TraceRecomposition
@Composable
fun UserProfile(user: User) {
    Column {
        Text("Name: ${user.name}")
        Text("Age: ${user.age}")
    }
}
```

That's it. When this composable recomposes, you'll see logs like:

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

### Annotation Parameters

The `@TraceRecomposition` annotation accepts two optional parameters to help you organize and filter logs:

#### The `threshold` Parameter

You can configure the `threshold` parameter in the `@TraceRecomposition` annotation to log only when the recomposition count exceeds the specified threshold. This helps reduce noise from composables that frequently recompose. Additionally, you can use the recomposition callback for performance monitoring by sending custom events to Firebase or any other analytics platform.

```kotlin
@TraceRecomposition(threshold = 3)
@Composable
fun FrequentlyRecomposingScreen() {
    // Will only start logging after the 3rd recomposition
}
```

**Why thresholds matter**

Many composables recompose 1-2 times during initial setup. These are expected and not performance issues. By using `threshold = 3` or a specific number, you filter out the noise and focus on actual problems, composables that keep recomposing during user interaction.

The real example might be like so:

```kotlin
ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
  override fun log(event: RecompositionEvent) {
    // Track excessive recompositions
    if (event.recompositionCount >= 10) {
      // Example: Send to Firebase Analytics
      FirebaseAnalytics.getInstance(this).logEvent("excessive_recomposition") {
        param("tag", event.tag)
        param("composable", event.composableName)
        param("count", event.recompositionCount)
        param("unstable_params", event.unstableParameters.joinToString())
      }
    }
  }
})
```

#### The `tag` parameter: filter your logs

Use tags to categorize and filter your logs. Tags are especially useful when tracking multiple composables across different screens or features.

```kotlin
@TraceRecomposition(tag = "user-profile")
@Composable
fun UserProfile(user: User) {
    // Your composable code
}
```

Now logs include the tag:

```
D/Recomposition: [Recomposition #1] UserProfile (tag: user-profile)
D/Recomposition:   └─ user: User stable (User@abc123)
```

This is also very useful if you want to set a custom logger for `ComposeStabilityAnalyzer`, to distinguish which composable function should be examined like the example below:

```kotlinp
val tagsToLog = setOf("user-profile", "checkout", "performance")

ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
  override fun log(event: RecompositionEvent) {
    if (!BuildConfig.DEBUG) {
        if (event.tag in tagsToLog || event.tag.isEmpty()) {
        // Example: Send to Firebase Analytics only log events with specific tags
        FirebaseAnalytics.getInstance(this).logEvent("excessive_recomposition") {
          param("tag", event.tag)
          param("composable", event.composableName)
          param("count", event.recompositionCount)
          param("unstable_params", event.unstableParameters.joinToString())
        }
      }
    } else {
        // Log everything on the debug mode
        Log.d(..)
    }
  }
})
```

**Filtering Logcat with tags**

Once you have tags, you can filter Logcat to see only specific composables:

- See all recompositions: Filter by `Recomposition`
- See a tagged recompositions: Filter by `tag: <tag name>`
- See specific composable: Filter by `UserProfile` and `Recomposition`

### Configure Custom Logger & Enable Logging

You need to enable or disable the logging system in your app. Add this to your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable recomposition tracking ONLY in debug builds
        ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
    }
}
```

**Important Note**

- Always wrap with `BuildConfig.DEBUG` to avoid performance overhead in production or filter them clearly on the custom logger.
- If you don't enable `ComposeStabilityAnalyzer`, no logs will appear even with `@TraceRecomposition`.
- This logging has minimal performance impact in debug builds but should still be disabled in release builds for any security reasons of your app.

Also, you can completely redefine the logging behaviors by setting your custom logger like the example below:

```kotlin
ComposeStabilityAnalyzer.setLogger(object : RecompositionLogger {
  override fun log(event: RecompositionEvent) {
    val message = buildString {
      append("🔄 Recomposition #${event.recompositionCount}")
      append(" - ${event.composableName}")
      if (event.tag.isNotEmpty()) {
        append(" [${event.tag}]")
      }
      appendLine()

      event.parameterChanges.forEach { change ->
        append("   • ${change.name}: ${change.type}")
        when {
          change.changed -> append(" ➡️ CHANGED")
          change.stable -> append(" ✅ STABLE")
          else -> append(" ⚠️ UNSTABLE")
        }
        appendLine()
      }

      if (event.unstableParameters.isNotEmpty()) {
        append("   ⚠️ Unstable: ${event.unstableParameters.joinToString()}")
      }
    }

    Log.d("CustomRecomposition", message)
  }
})
```

### Reading the Logs

Let's understand what each log tells you:

#### First Recomposition

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
```

**What this means:**
- `[Recomposition #1]` - This is the first time this composable instance is recomposing
- `UserProfile` - The name of the composable function
- `user: User` - Parameter name and type
- `stable` - This parameter is stable
- `(User@abc123)` - The current value's identity (hashcode)

This log confirms the composable is working correctly. The parameter is stable and the first recomposition is expected.

#### Parameter Changed

```
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

**What this means:**
- `[Recomposition #2]` - Second recomposition
- `changed` - The parameter value changed (this is **why** it recomposed)
- `(User@abc123 → User@def456)` - Shows old value → new value

This is normal behavior. The parameter changed, so the composable recomposed to show the new data. This is exactly what Compose should do.

#### Unstable Parameter

```
D/Recomposition: [Recomposition #1] UserCard (tag: user-card)
D/Recomposition:   ├─ user: MutableUser unstable (MutableUser@xyz789)
D/Recomposition:   └─ Unstable parameters: [user]
```

#### Multiple Parameters (Mixed Stability)

```
D/Recomposition: [Recomposition #5] ProductList (tag: products)
D/Recomposition:   ├─ title: String stable (Products)
D/Recomposition:   ├─ count: Int changed (4 → 5)
D/Recomposition:   ├─ items: List<Product> unstable (List@abc)
D/Recomposition:   └─ Unstable parameters: [items]
```

**What this means**

- `title: String stable` - Not causing recomposition
- `count: Int changed (4 → 5)` - **This is why it recomposed** (count changed from 4 to 5).
- `items: List<Product> unstable` - This list is unstable, causing unnecessary recompositions.
- `Unstable parameters: [items]` - Summary.

### Real-World Example

Let's walk through a complete debugging session using `@TraceRecomposition`:

**Problem:** Your product list screen feels laggy. You suspect excessive recompositions.

**Step 1: Add tracking**

```kotlin
@TraceRecomposition(tag = "product-card", threshold = 3)
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Text(product.name)
        Text("$${product.price}")
    }
}
```

**Step 2: Run your app and check Logcat**

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product unstable (Product@abc)
D/Recomposition:   ├─ onClick: () -> Unit stable (Function@xyz)
D/Recomposition:   └─ Unstable parameters: [product]

D/Recomposition: [Recomposition #4] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product unstable (Product@abc)
D/Recomposition:   ├─ onClick: () -> Unit stable (Function@xyz)
D/Recomposition:   └─ Unstable parameters: [product]

... (logs continue every scroll)
```

**Step 3: Analyze**

The logs reveal:
- `onClick` is stable.
- `product` is unstable.
- `ProductCard` is recomposing 3+ times (that's why we see logs).

**Step 4: Check your `Product` class**

```kotlin
// Current implementation (UNSTABLE)
data class Product(
    var name: String,     // ← var = mutable = unstable!
    var price: Double     // ← var = mutable = unstable!
)
```

**Step 5: Fix it**

```kotlin
// Fixed implementation (STABLE)
data class Product(
    val name: String,     // ← val = read-only = stable
    val price: Double     // ← val = read-only = stable
)
```

**Step 6: Verify the fix**

Run the app again and check Logcat:

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product stable (Product@abc)
D/Recomposition:   └─ onClick: () -> Unit stable (Function@xyz)

(No more excessive recompositions!)
```

### Best Practices

**1. Don't track everything**: Be selective about which composables you track. Focus on:
- Composables you suspect have performance issues.
- List items (they recompose frequently).
- Complex screens with many parameters.

**2. Use meaningful tags**: Tags make filtering easier:

```kotlin
@TraceRecomposition(tag = "auth-flow")       // Track entire feature
@TraceRecomposition(tag = "login-button")    // Track specific component
```

**3. Set appropriate thresholds**: Reduce noise with thresholds:

```kotlin
@TraceRecomposition(threshold = 3)  // Most common—skip initial setup
@TraceRecomposition(threshold = 10) // For very active composables
```

## Stability Validation

Imagine this scenario: You've spent weeks optimizing your app's Compose performance. All your composables are stable, skippable, and lightning-fast. Then someone on your team innocently changes a `val` to `var` in a data class, and suddenly dozens of composables become unstable. The performance regression slips through code review and makes it to production.

**Stability Validation** prevents this nightmare. It's like git diff for composable stability, it tracks your composables' stability over time and automatically fails your CI build if stability regresses. You can check out the [quick integration codes](https://github.com/skydoves/landscapist/pull/767/files).

### How It Works

Stability validation works through two Gradle tasks:

1. **`stabilityDump`**: Creates a snapshot of all composables' stability.
2. **`stabilityCheck`**: Compares current stability against the snapshot.

Think of it like this:

- `stabilityDump` = "Save the current state"
- `stabilityCheck` = "Has anything changed since last save?"

> **Note**: Keep in mind that, all these Gradle tasks should be done **after compile your project**.

### Android

For Android projects, variant-specific tasks will be created, such as `debugStabilityDump`.
You can use those to only compile one variant of your module.

### Step 1: Create a Stability Baseline

First, you need to generate a baseline—a snapshot of your current composables' stability.

Run this command:

```bash
./gradlew :app:stabilityDump
```

This creates a human-readable `.stability` file:

```
app/stability/app.stability
```

**What's in this file?**

It's a complete record of every composable in your module, showing:
- Function signature (name, parameters, return type)
- Whether it's skippable and restartable
- Stability of each parameter

The `.stability` file will be looking like below:

```
@Composable
public fun com.example.UserCard(user: com.example.User): kotlin.Unit
  skippable: true
  restartable: true
  params:
    - user: STABLE (marked @Stable or @Immutable)

@Composable
public fun com.example.ProductList(items: kotlin.collections.List<com.example.Product>): kotlin.Unit
  skippable: true
  restartable: true
  params:
    - items: STABLE (immutable collection with stable elements)

@Composable
public fun com.example.UnstableCard(user: com.example.MutableUser): kotlin.Unit
  skippable: false
  restartable: true
  params:
    - user: UNSTABLE (has mutable properties)
```

This file is your **stability contract**. It says "these are all my composables, and this is how stable they should be."

**Commit this file to git:**

```bash
git add app/stability/app.stability
git commit -m "Add stability baseline for app module"
git push
```

Now everyone on your team has the same baseline. Any changes to composable stability will be detected!

### Step 2: Check for Stability Changes

The `stabilityCheck` task compares your current code against the baseline.

Run this command:

```bash
./gradlew :app:stabilityCheck
```

**If nothing changed:**

```
✅ Stability check passed.
```

Your composables' stability matches the baseline. Everything is good!

**If stability regressed:**

```
❌ Stability check failed!

The following composables have changed stability:

~ com.example.UserCard(user): stability changed from STABLE to UNSTABLE

If these changes are intentional, run './gradlew stabilityDump' to update the stability file.
```

The build **fails**, preventing the regression from being merged!

**Types of changes detected**

The task detects four types of changes:

| Symbol | Change Type | Example |
|--------|-------------|---------|
| `~` | Stability regressed | Parameter changed from STABLE to UNSTABLE |
| `+` | New composable added | `+ com.example.NewScreen(title)` |
| `-` | Composable removed | `- com.example.OldScreen(data)` |
| `~` | Parameter count changed | Function signature changed |

### Real-World Example

Let's walk through a complete example:

```kotlin
data class User(val name: String, val age: Int)

@Composable
fun UserCard(user: User) {
    Text("${user.name}, ${user.age}")
}
```

Generate baseline:

```bash
./gradlew :app:stabilityDump
git add app/stability/app.stability
git commit -m "Add stability baseline"
```

The `.stability` file now contains:

```
@Composable
public fun com.example.UserCard(user: com.example.User): kotlin.Unit
  skippable: true
  params:
    - user: STABLE
```

**If someone makes a change**

A developer modifies the `User` class:

```kotlin
data class User(var name: String, var age: Int)  // Changed val to var
```

They create a pull request. Your CI runs:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:stabilityCheck
```

**CI output:**

```
❌ Stability check failed!

~ com.example.UserCard(user): stability changed from STABLE to UNSTABLE

If these changes are intentional, run './gradlew stabilityDump' to update the stability file.
```

The pull request cannot merge. The developer must either:

1. **Fix the regression** - Change back to `val`.
2. **Update the baseline** - If the change is intentional.

**If the change is intentional**

```bash
./gradlew :app:stabilityDump  # Update baseline
git add app/stability/app.stability
git commit -m "Accept UserCard stability regression (justified by...)"
```

This creates a **deliberate, documented decision** in git history, rather than an accidental regression.

### CI/CD Integration

Add stability validation to your CI pipeline so it runs on every pull request:

**GitHub Actions:**

```yaml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build project
        run: ./gradlew :app:compileDebugKotlin

  stability_check:
    name: Compose Stability Check
    runs-on: ubuntu-latest
    needs: build  <<<<< This is important
    steps:
      - name: Check out code
        uses: actions/checkout@v5.0.0
      - name: Set up JDK
        uses: actions/setup-java@v5.0.0
        with:
          distribution: 'zulu'
          java-version: 21
      - name: compose stability check
        run: ./gradlew stabilityCheck    
```

Now every pull request gets automatically checked for stability regressions!

### Configuration

You can customize what gets tracked and where files are stored in your Gradle file:

```kotlin
// In your build.gradle.kts
composeStabilityAnalyzer {

    stabilityValidation {
        enabled.set(true) // Enable or disable stability validation
        outputDir.set(layout.projectDirectory.dir("stability")) // set the output directory
        includeTests.set(false) // Exclude test code from stability reports (default)

        // Ignore specific packages or classes
        ignoredPackages.set(listOf("com.example.internal"))
        ignoredClasses.set(listOf("PreviewComposables"))

        // Exclude specific sub-projects/modules (useful for multi-module projects)
        ignoredProjects.set(listOf("benchmarks", "examples", "samples"))

        // Control build failure behavior on stability changes (default: true)
        failOnStabilityChange.set(true)
      
        // Do not report any stable changes from the baseline (default: false)
        ignoreNonRegressiveChanges.set(false)
    }
}
```

#### `failOnStabilityChange` Option

By default, `stabilityCheck` will **fail the build** when stability changes are detected. This is ideal for CI/CD pipelines where you want to prevent stability regressions from being merged.

However, in some scenarios you may want to **log warnings instead of failing**:

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        // Log stability changes as warnings instead of failing the build
        failOnStabilityChange.set(false)
    }
}
```

**When to use `failOnStabilityChange.set(false)`:**

- **Initial adoption**: When first adding stability validation to an existing project, you may want to see all stability issues without blocking builds.
- **Gradual migration**: Allow the team to fix stability issues incrementally while still tracking them.
- **Development branches**: Use warnings during development, but enable strict mode for `main` branch.
- **Monitoring only**: Track stability trends without enforcing them as build requirements.

**Example: Different behavior per environment**

```kotlin
composeStabilityAnalyzer {
    stabilityValidation {
        // Fail on CI, warn locally
        failOnStabilityChange.set(System.getenv("CI") == "true")
    }
}
```

**Why ignore packages/classes**

If you don't want to track:
- Preview composables (only used in Android Studio previews)
- Test composables (only used in UI tests)
- Debug screens (only in debug builds)

These composables aren't in production, so their stability doesn't matter.

### Excluding Composables from Reports

Sometimes you have composables that shouldn't be included in stability validation:

- **Preview composables**: Only used for Android Studio previews.
- **Debug/test composables**: Only in debug builds.
- **Experimental composables**: Still under development.

Use the `@IgnoreStabilityReport` annotation to exclude them:

```kotlin
@IgnoreStabilityReport
@Preview
@Composable
fun UserCardPreview() {
    UserCard(user = User("John", 30))
}
```

This composable will be **excluded** from:

- `.stability` files generated by `stabilityDump`
- Stability validation checks by `stabilityCheck`

### Multi-Module Projects

For projects with multiple modules, each module gets its own `.stability` file:

```
project/
├── app/
│   └── stability/
│       └── app.stability
├── feature-auth/
│   └── stability/
│       └── feature-auth.stability
└── feature-profile/
    └── stability/
        └── feature-profile.stability
```

Run `stabilityCheck` for all modules at once:

```bash
./gradlew stabilityCheck
```

Or check specific modules:

```bash
./gradlew :app:stabilityCheck
./gradlew :feature-auth:stabilityCheck
```

## Find this library useful? :heart:

Support it by joining __[stargazers](https://github.com/skydoves/compose-stability-analyzer/stargazers)__ for this repository. :star: <br>
Also __[follow](https://github.com/skydoves)__ me for my next creations! 🤩

# License

```xml
Designed and developed by 2025 skydoves (Jaewoong Eum)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
