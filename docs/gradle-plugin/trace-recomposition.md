# TraceRecomposition

`@TraceRecomposition` lets you trace the behavior of any composable function. By annotating a composable, you can log parameter changes whenever that function undergoes recomposition — without writing any logging code yourself.

## Basic Usage

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

When this composable recomposes, you'll see logs like:

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

## Annotation Parameters

### The `tag` Parameter

Use tags to categorize and filter your logs:

```kotlin
@TraceRecomposition(tag = "user-profile")
@Composable
fun UserProfile(user: User) {
    // Your composable code
}
```

Logs now include the tag:

```
D/Recomposition: [Recomposition #1] UserProfile (tag: user-profile)
D/Recomposition:   └─ user: User stable (User@abc123)
```

### The `threshold` Parameter

Configure `threshold` to log only when the recomposition count exceeds a specific number. This helps reduce noise from composables that frequently recompose:

```kotlin
@TraceRecomposition(threshold = 3)
@Composable
fun FrequentlyRecomposingScreen() {
    // Will only start logging after the 3rd recomposition
}
```

!!! note "Why thresholds matter"

    Many composables recompose 1-2 times during initial setup. These are expected and not performance issues. By using `threshold = 3` or higher, you filter out the noise and focus on actual problems.

## Reading the Logs

### First Recomposition

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
```

- `[Recomposition #1]` — First time this composable instance is recomposing
- `user: User` — Parameter name and type
- `stable` — This parameter is stable
- `(User@abc123)` — Current value's identity (hashcode)

### Parameter Changed

```
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

- `changed` — The parameter value changed (this is **why** it recomposed)
- `(User@abc123 → User@def456)` — Shows old value → new value

### Unstable Parameter

```
D/Recomposition: [Recomposition #1] UserCard (tag: user-card)
D/Recomposition:   ├─ user: MutableUser unstable (MutableUser@xyz789)
D/Recomposition:   └─ Unstable parameters: [user]
```

### Multiple Parameters (Mixed Stability)

```
D/Recomposition: [Recomposition #5] ProductList (tag: products)
D/Recomposition:   ├─ title: String stable (Products)
D/Recomposition:   ├─ count: Int changed (4 → 5)
D/Recomposition:   ├─ items: List<Product> unstable (List@abc)
D/Recomposition:   └─ Unstable parameters: [items]
```

- `title: String stable` — Not causing recomposition
- `count: Int changed (4 → 5)` — **This is why it recomposed**
- `items: List<Product> unstable` — Unstable, causing unnecessary recompositions

## Enable Logging

You must enable logging in your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
    }
}
```

!!! warning "Production builds"

    Always wrap with `BuildConfig.DEBUG` to avoid performance overhead in production. If you don't enable `ComposeStabilityAnalyzer`, no logs will appear even with `@TraceRecomposition`.

## Real-World Debugging Example

**Problem:** Your product list screen feels laggy.

**Step 1: Add tracking**

```kotlin
@TraceRecomposition(tag = "product-card", threshold = 3)
@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Text(product.name)
        Text("$${product.price}")
    }
}
```

**Step 2: Check Logcat**

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product unstable (Product@abc)
D/Recomposition:   ├─ onClick: () -> Unit stable (Function@xyz)
D/Recomposition:   └─ Unstable parameters: [product]
```

**Step 3: Fix**

```kotlin
// Before (UNSTABLE)
data class Product(var name: String, var price: Double)

// After (STABLE)
data class Product(val name: String, val price: Double)
```

**Step 4: Verify**

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product stable (Product@abc)
D/Recomposition:   └─ onClick: () -> Unit stable (Function@xyz)
```

## Best Practices

1. **Don't track everything** — Be selective. Focus on composables you suspect have performance issues, list items, and complex screens.
2. **Use meaningful tags** — Tags make filtering easier: `@TraceRecomposition(tag = "auth-flow")`.
3. **Set appropriate thresholds** — `threshold = 3` for most cases, `threshold = 10` for very active composables.
