# Stability Concepts

Understanding how Compose stability works is key to writing performant composable functions. This page explains the core concepts that the Compose Stability Analyzer helps you visualize and debug.

## What Is Stability?

In Jetpack Compose, **stability** refers to whether the Compose compiler can guarantee that a type's value will not change between recompositions. When a parameter is stable, Compose can **skip** recomposing a function if the parameter hasn't changed — this is a major performance optimization.

```kotlin
// STABLE: all properties are val and primitive/stable types
data class User(val name: String, val age: Int)

// UNSTABLE: has mutable property
data class MutableUser(var name: String, var age: Int)
```

## Stable vs Unstable Types

### Stable Types

A type is considered **stable** if:

- All public properties are `val` (read-only)
- All property types are themselves stable
- It is a primitive type (`Int`, `String`, `Float`, etc.)
- It is annotated with `@Stable` or `@Immutable`

| Type | Stable? | Reason |
|------|---------|--------|
| `String` | Yes | Primitive/immutable |
| `Int`, `Float`, `Boolean` | Yes | Primitive |
| `data class Foo(val x: Int)` | Yes | Immutable properties of stable types |
| `List<String>` | Depends | `kotlin.collections.List` is an interface — stability depends on the Compose compiler version and strong skipping mode |
| `MutableList<String>` | No | Mutable collection |
| `data class Bar(var x: Int)` | No | Mutable property |

### Unstable Types

A type is **unstable** if:

- It has `var` properties
- It uses mutable collections (`MutableList`, `MutableMap`, etc.)
- It contains properties of unstable types
- It comes from a module not compiled with the Compose compiler (cross-module)

## Skippability and Restartability

### Skippable

A composable is **skippable** when Compose can skip its execution if all parameters are stable and unchanged. This avoids unnecessary work during recomposition.

```kotlin
// Skippable: all parameters are stable
@Composable
fun UserCard(name: String, age: Int) {
    Text("$name, $age")
}
```

### Non-Skippable

A composable is **non-skippable** when it has at least one unstable parameter. Compose must always re-execute it during recomposition, even if the values haven't changed.

```kotlin
// Non-skippable: List<Product> may be unstable
@Composable
fun ProductList(items: List<Product>) {
    items.forEach { Text(it.name) }
}
```

### Restartable

A composable is **restartable** when Compose can independently restart its execution when state it reads changes. Most composables are restartable. A composable that is restartable but not skippable will always re-execute when its parent recomposes.

## Recomposition

**Recomposition** is the process of Compose re-executing composable functions when the data they depend on changes. Compose tries to be smart about recomposition:

1. Only recompose functions whose inputs have changed
2. Skip functions where all parameters are stable and unchanged
3. Restart only the smallest scope necessary

### Unnecessary Recompositions

When a composable has unstable parameters, Compose cannot determine whether the values actually changed, so it **always recomposes**. This leads to unnecessary work:

```kotlin
// This recomposes every time the parent recomposes,
// even if the user object hasn't actually changed
@Composable
fun UserCard(user: MutableUser) {  // MutableUser is unstable
    Text(user.name)
}
```

## Fixing Instability

### Use `val` Instead of `var`

The most common fix — make properties immutable:

```kotlin
// Before (UNSTABLE)
data class Product(var name: String, var price: Double)

// After (STABLE)
data class Product(val name: String, val price: Double)
```

### Use `@Stable` or `@Immutable`

For types you know are stable but the compiler can't infer:

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

    `@Stable` and `@Immutable` are promises to the compiler. If you annotate a type as stable but mutate it, you may get incorrect UI behavior — Compose will skip recompositions that should happen.

### Use Immutable Collections

Replace mutable collections with immutable alternatives:

```kotlin
// Before (UNSTABLE)
@Composable
fun ItemList(items: MutableList<Item>) { ... }

// After (STABLE with kotlinx.collections.immutable)
@Composable
fun ItemList(items: ImmutableList<Item>) { ... }
```

### Stability Configuration File

You can tell the Compose compiler to treat certain types as stable using a stability configuration file:

```
// stability-config.txt
com.example.ThirdPartyModel
com.google.firebase.auth.FirebaseUser
```

This is useful for types from third-party libraries that you cannot annotate.

## Cross-Module Stability

Types from other modules that were **not compiled with the Compose compiler** are treated as unstable by default. This is because the Compose compiler cannot verify their stability.

The `@StabilityInferred` annotation (added automatically by the Compose compiler) helps resolve this — when a class is compiled with the Compose compiler, this annotation records whether the class was inferred as stable, allowing other modules to read this information.

## Strong Skipping Mode

**Strong Skipping Mode** (introduced in Compose Compiler 1.5.4+) relaxes stability requirements:

- Unstable parameters are compared using instance equality (`===`) instead of being skipped
- Lambda parameters are automatically remembered
- More composables become skippable

With Strong Skipping Mode enabled, the stability of individual parameters matters less, but understanding stability is still valuable for debugging performance issues.

## How the Analyzer Helps

The Compose Stability Analyzer provides tools at every stage:

| Tool | When | What It Shows |
|------|------|---------------|
| **IDE Plugin** | While coding | Gutter icons, tooltips, inline hints showing stability |
| **`@TraceRecomposition`** | At runtime | Logs showing which parameters caused recomposition |
| **`stabilityCheck`** | In CI/CD | Detects stability regressions before they reach production |

Together, these tools give you complete visibility into your composables' stability — from writing code to shipping it.
