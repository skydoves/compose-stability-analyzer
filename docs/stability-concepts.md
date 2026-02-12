# Stability Concepts

Understanding how Compose stability works is key to writing performant composable functions. This page explains the core concepts that the Compose Stability Analyzer helps you visualize and debug.

## What Is Stability?

In Jetpack Compose, **stability** refers to whether the Compose compiler can guarantee that a type's value will not change between recompositions. This guarantee is central to Compose's performance model: when the compiler knows a parameter is stable, it can safely **skip** re-executing a composable function if the parameter's value hasn't changed since the last composition. This skipping mechanism is one of the most important performance optimizations in Compose.

```kotlin
// STABLE: all properties are val and primitive/stable types
data class User(val name: String, val age: Int)

// UNSTABLE: has mutable property
data class MutableUser(var name: String, var age: Int)
```

The `User` class is stable because the compiler can see that both properties are `val` (read-only) and their types (`String`, `Int`) are themselves stable. The `MutableUser` class is unstable because `var` properties can be changed after construction, so the compiler can't guarantee the value will be the same between recompositions.

## Stable vs Unstable Types

### Stable Types

The Compose compiler considers a type stable when it can prove the type's public state won't change after construction. Primitive types (`Int`, `String`, `Float`, `Boolean`) are always stable because they're immutable by nature. Data classes with only `val` properties of stable types are also stable, since the compiler can verify this by inspecting the class definition. Types explicitly annotated with `@Stable` or `@Immutable` are treated as stable because the developer has made a contract with the compiler that the type behaves stably.

| Type | Stable? | Reason |
|------|---------|--------|
| `String` | Yes | Primitive/immutable |
| `Int`, `Float`, `Boolean` | Yes | Primitive |
| `data class Foo(val x: Int)` | Yes | Immutable properties of stable types |
| `List<String>` | Depends | `kotlin.collections.List` is an interface, so stability depends on the Compose compiler version and strong skipping mode |
| `MutableList<String>` | No | Mutable collection |
| `data class Bar(var x: Int)` | No | Mutable property |

### Unstable Types

A type becomes unstable when the compiler can't guarantee immutability. The most common cause is `var` properties; even a single `var` in a data class makes the entire type unstable. Mutable collections (`MutableList`, `MutableMap`, `MutableSet`) are always unstable because their contents can change at any time.

Types that contain properties of unstable types are themselves unstable, because instability propagates upward. A `data class Order(val items: MutableList<Item>)` is unstable even though `items` is `val`, because `MutableList` itself is mutable.

Types from external modules that were **not compiled with the Compose compiler** are also treated as unstable by default, because the compiler has no way to inspect their stability. This is a common source of unexpected instability in multi-module projects.

## Skippability and Restartability

### Skippable

A composable is **skippable** when Compose can skip its execution entirely if all parameters are stable and unchanged since the last composition. This is the primary performance benefit of stability. During recomposition, Compose checks each parameter and, if nothing has changed, avoids re-executing the function body entirely.

```kotlin
// Skippable: all parameters are stable
@Composable
fun UserCard(name: String, age: Int) {
    Text("$name, $age")
}
```

When `UserCard` is called during recomposition, Compose checks whether `name` and `age` have the same values as the previous composition. If they do, the entire function is skipped: no `Text` calls, no layout, no rendering. This adds up quickly in lists or complex UIs where many composables might not need updating.

### Non-Skippable

A composable is **non-skippable** when it has at least one unstable parameter. Because the compiler can't guarantee the parameter's value is unchanged, Compose must always re-execute the function during recomposition, even if the values happen to be identical.

```kotlin
// Non-skippable: List<Product> may be unstable
@Composable
fun ProductList(items: List<Product>) {
    items.forEach { Text(it.name) }
}
```

This doesn't mean the composable has a performance problem. It depends on how often the parent recomposes and how expensive the function body is. But it does mean Compose can't optimize this composable, so it's worth investigating if you notice performance issues.

### Restartable

A composable is **restartable** when Compose can independently restart its execution when state it reads changes. Most composables are restartable. The distinction matters because a composable that is restartable but not skippable will always re-execute when its parent recomposes. It can restart independently but can never be skipped.

## Recomposition

**Recomposition** is the process of Compose re-executing composable functions when the data they depend on changes. Compose's runtime is designed to be efficient about this. It tracks which composables read which state, and when state changes, it only re-executes the composables that actually depend on that state.

Within a recomposition, Compose applies three optimizations. First, it only recomposes functions whose inputs have changed; if a composable's parameters are the same, there's no reason to re-execute it. Second, it skips functions where all parameters are stable and unchanged (this is the skippability optimization). Third, it restarts only the smallest scope necessary, so a state change deep in the UI tree doesn't trigger recomposition of the entire tree.

### Unnecessary Recompositions

When a composable has unstable parameters, Compose's second optimization (skipping) is disabled for that composable. The result is unnecessary recompositions: the composable re-executes even when its parameters haven't actually changed.

```kotlin
// This recomposes every time the parent recomposes,
// even if the user object hasn't actually changed
@Composable
fun UserCard(user: MutableUser) {  // MutableUser is unstable
    Text(user.name)
}
```

In isolation, a single unnecessary recomposition is rarely noticeable. But in a `LazyColumn` with 50 items, or in a screen with deeply nested composables, the cumulative cost of re-executing functions that don't need to run can cause visible jank: dropped frames, sluggish scrolling, or delayed response to user input.

## Fixing Instability

### Use `val` Instead of `var`

The most common and simplest fix is making properties immutable. If a property doesn't need to be mutable after construction, use `val` instead of `var`. This single change is often enough to make a type stable.

```kotlin
// Before (UNSTABLE)
data class Product(var name: String, var price: Double)

// After (STABLE)
data class Product(val name: String, val price: Double)
```

### Use `@Stable` or `@Immutable`

For types where the compiler can't infer stability but you know the type behaves stably, you can add the `@Stable` or `@Immutable` annotation. `@Immutable` is the stronger contract: it promises that all public properties will never change after construction. `@Stable` is more permissive, promising that if a public property changes, the composition will be notified (via Compose's snapshot system).

```kotlin
@Stable
class UserRepository(private val api: UserApi) {
    // Even though UserApi might be unstable,
    // you're guaranteeing this class behaves stably
}

@Immutable
data class Theme(val primary: Color, val secondary: Color)
```

!!! warning "@Stable is a contract"

    `@Stable` and `@Immutable` are promises to the compiler. If you annotate a type as stable but mutate it outside of Compose's snapshot system, you may get incorrect UI behavior. Compose will skip recompositions that should happen, resulting in stale UI.

### Use Immutable Collections

Standard Kotlin collections (`List`, `Map`, `Set`) are interfaces, and the compiler can't guarantee that the underlying implementation is immutable. Replacing them with immutable alternatives from `kotlinx.collections.immutable` gives the compiler the guarantee it needs.

```kotlin
// Before (UNSTABLE)
@Composable
fun ItemList(items: MutableList<Item>) { ... }

// After (STABLE with kotlinx.collections.immutable)
@Composable
fun ItemList(items: ImmutableList<Item>) { ... }
```

### Stability Configuration File

For types you can't modify (third-party library classes, generated code, or Java types), you can create a stability configuration file that tells the Compose compiler to treat specific types as stable. This is a project-level solution that doesn't require modifying the source of the types themselves.

```
// stability-config.txt
com.example.ThirdPartyModel
com.google.firebase.auth.FirebaseUser
```

## Cross-Module Stability

Types from other modules that were **not compiled with the Compose compiler** are treated as unstable by default. This is a conservative decision, because the Compose compiler in your module can't inspect the source code of external modules, so it can't verify stability.

The `@StabilityInferred` annotation helps resolve this. When a class is compiled with the Compose compiler, this annotation is automatically added to the class's bytecode, recording whether the compiler inferred it as stable. When another module encounters this class as a parameter type, it reads the `@StabilityInferred` annotation to determine stability without needing access to the source code.

This mechanism is transparent: you don't need to add the annotation manually. It's added automatically by the Compose compiler during compilation. However, it only works for modules that are compiled with the Compose compiler; modules compiled with plain `kotlinc` won't have this annotation.

## Strong Skipping Mode

**Strong Skipping Mode** (introduced in Compose Compiler 1.5.4+ and enabled by default in newer versions) relaxes the stability requirements for skipping. In standard mode, a composable can only be skipped if all parameters are stable and unchanged. In Strong Skipping mode, unstable parameters are compared using instance equality (`===`), so if the exact same object instance is passed, the composable can still be skipped.

This means more composables become skippable in practice, because even unstable parameters can be skipped if the same instance is reused. Lambda parameters are also automatically remembered in Strong Skipping mode, eliminating a common source of unnecessary recomposition.

With Strong Skipping Mode enabled, the stability of individual parameters matters less for skippability. However, understanding stability is still valuable for debugging performance issues. If a composable is recomposing excessively, knowing whether parameters are stable helps you understand whether the issue is a genuinely changing value or an unnecessarily recreated object.

## How the Analyzer Helps

The Compose Stability Analyzer provides tools at every stage of your development workflow. The **IDE Plugin** gives you real-time feedback while coding. Gutter icons, tooltips, and inline hints show stability information as you write, so you can catch issues before they're even committed.

The **`@TraceRecomposition` annotation** operates at runtime, logging detailed recomposition events that show you exactly which parameters changed and which are unstable. This is invaluable for diagnosing performance issues in running apps, where static analysis alone can't capture the full picture.

The **`stabilityCheck` Gradle task** runs in CI/CD, comparing your current code against a baseline and failing the build if stability has regressed. This prevents regressions from reaching production and creates a documented history of stability decisions in your git log.

| Tool | When | What It Shows |
|------|------|---------------|
| **IDE Plugin** | While coding | Gutter icons, tooltips, inline hints showing stability |
| **`@TraceRecomposition`** | At runtime | Logs showing which parameters caused recomposition |
| **`stabilityCheck`** | In CI/CD | Detects stability regressions before they reach production |

Together, these tools give you complete visibility into your composables' stability, from writing code to shipping it.
