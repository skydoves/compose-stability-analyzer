# TraceRecomposition

`@TraceRecomposition` lets you trace the behavior of any composable function. By annotating a composable, you can log parameter changes whenever that function undergoes recomposition — without writing any logging code yourself. The compiler plugin instruments the function at compile time, injecting the necessary tracking code automatically.

## Basic Usage

Add the `@TraceRecomposition` annotation to any composable function you want to monitor. No other changes are needed — the compiler plugin handles the rest.

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

When this composable recomposes, detailed logs appear in Logcat showing the recomposition count, each parameter's stability status, and whether the parameter's value changed since the last composition.

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

The first log entry shows the initial composition — the `user` parameter is stable and the log includes its identity hash. The second entry shows a recomposition triggered by the `user` parameter changing to a different instance, with both the old and new identity hashes displayed.

## Annotation Parameters

### The `tag` Parameter

Tags let you categorize and filter your logs, which is especially useful in large projects where many composables might have tracing enabled simultaneously. By assigning meaningful tags, you can filter Logcat to show only the composables you're currently investigating.

```kotlin
@TraceRecomposition(tag = "user-profile")
@Composable
fun UserProfile(user: User) {
    // Your composable code
}
```

Logs now include the tag, making it easy to filter in Logcat:

```
D/Recomposition: [Recomposition #1] UserProfile (tag: user-profile)
D/Recomposition:   └─ user: User stable (User@abc123)
```

Tags are also valuable when using a [custom logger](custom-logger.md) — you can route events differently based on their tag, for example sending only critical flow recompositions (checkout, authentication) to your analytics platform while logging everything else to Logcat during development.

### The `threshold` Parameter

The `threshold` parameter controls when logging begins. By setting a threshold, the composable only produces log output after it has recomposed the specified number of times. This is essential for reducing noise — many composables recompose once or twice during initial layout setup, which is completely normal and not a performance concern.

```kotlin
@TraceRecomposition(threshold = 3)
@Composable
fun FrequentlyRecomposingScreen() {
    // Will only start logging after the 3rd recomposition
}
```

!!! note "Why thresholds matter"

    Many composables recompose 1-2 times during initial setup. These are expected and not performance issues. By using `threshold = 3` or higher, you filter out the noise and focus on actual problems — composables that keep recomposing during user interaction, scrolling, or state updates.

A threshold of `3` works well for most composables. For composables in scrolling lists or frequently updating screens where some recomposition is expected, a higher threshold (e.g., `10` or `20`) helps focus on truly excessive recomposition.

## Reading the Logs

Understanding the log output is key to diagnosing recomposition issues. Each log entry contains several pieces of information that, together, tell you exactly what happened and why.

### First Recomposition

```
D/Recomposition: [Recomposition #1] UserProfile
D/Recomposition:   └─ user: User stable (User@abc123)
```

The `[Recomposition #1]` counter tells you this is the first time this composable instance is recomposing. `user: User` identifies the parameter by name and type. The `stable` label means the Compose compiler considers this parameter stable — it won't cause unnecessary recompositions. The identity hash `(User@abc123)` lets you track whether the same instance is being passed across recompositions.

This log confirms the composable is working correctly. A stable parameter on the first recomposition is expected behavior.

### Parameter Changed

```
D/Recomposition: [Recomposition #2] UserProfile
D/Recomposition:   └─ user: User changed (User@abc123 → User@def456)
```

The `changed` label is the most important signal — it tells you this parameter's value is different from the last composition, which is the **reason** this composable recomposed. The arrow notation `(User@abc123 → User@def456)` shows the old and new identity hashes, confirming the value actually changed.

This is normal behavior. The parameter changed, so the composable recomposed to reflect the new data. This is exactly what Compose should do.

### Unstable Parameter

```
D/Recomposition: [Recomposition #1] UserCard (tag: user-card)
D/Recomposition:   ├─ user: MutableUser unstable (MutableUser@xyz789)
D/Recomposition:   └─ Unstable parameters: [user]
```

The `unstable` label means the Compose compiler cannot guarantee this parameter won't change between recompositions. Because of this, Compose must always re-execute the composable — it can never be skipped. The `Unstable parameters: [user]` summary at the end lists all unstable parameters for quick reference.

### Multiple Parameters (Mixed Stability)

```
D/Recomposition: [Recomposition #5] ProductList (tag: products)
D/Recomposition:   ├─ title: String stable (Products)
D/Recomposition:   ├─ count: Int changed (4 → 5)
D/Recomposition:   ├─ items: List<Product> unstable (List@abc)
D/Recomposition:   └─ Unstable parameters: [items]
```

This log tells a complete story. The `title` parameter is stable and hasn't changed — it's not contributing to this recomposition. The `count` parameter changed from `4` to `5` — this is **why** the composable recomposed. The `items` parameter is unstable, meaning it will trigger recomposition regardless of whether its value actually changed. Even though `count` changing is the immediate cause here, the unstable `items` parameter is a latent performance issue that should be addressed.

## Enable Logging

The `@TraceRecomposition` annotation instruments your code at compile time, but the runtime logging system must be explicitly enabled. Add this to your `Application` class to activate logging:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
    }
}
```

!!! warning "Production builds"

    Always wrap with `BuildConfig.DEBUG` to avoid performance overhead in production. The instrumented code is still present in release builds, but when logging is disabled, the overhead is minimal — just a boolean check on each recomposition. If you don't call `ComposeStabilityAnalyzer.setEnabled()`, no logs will appear even with `@TraceRecomposition`.

## Real-World Debugging Example

Consider a common scenario: your product list screen feels laggy during scrolling. You suspect excessive recompositions but aren't sure which composable is the culprit or what's causing it.

**Add tracking to the composable you suspect.** Use a meaningful tag and a threshold to filter out initial setup recompositions.

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

**Run the app and check Logcat.** Filter by `product-card` to see only this composable's events. After scrolling through the list, you see logs appearing rapidly:

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product unstable (Product@abc)
D/Recomposition:   ├─ onClick: () -> Unit stable (Function@xyz)
D/Recomposition:   └─ Unstable parameters: [product]
```

The logs reveal the problem clearly. The `onClick` lambda is stable (no issue there), but the `product` parameter is unstable. Every time the parent recomposes — even if the product data hasn't changed — this `ProductCard` must re-execute because Compose can't verify that `product` is the same value.

**Fix the root cause.** Check the `Product` class and you'll likely find mutable properties:

```kotlin
// Before (UNSTABLE)
data class Product(var name: String, var price: Double)

// After (STABLE)
data class Product(val name: String, val price: Double)
```

**Verify the fix** by running the app again. The logs now show stable parameters:

```
D/Recomposition: [Recomposition #3] ProductCard (tag: product-card)
D/Recomposition:   ├─ product: Product stable (Product@abc)
D/Recomposition:   └─ onClick: () -> Unit stable (Function@xyz)
```

The `ProductCard` is now skippable. During scrolling, Compose will skip recomposing cards whose `product` and `onClick` values haven't changed, resulting in noticeably smoother performance.

## Best Practices

Be selective about which composables you track. Adding `@TraceRecomposition` to every composable creates log noise that makes it harder to find real issues. Focus on composables you suspect have performance problems — list items, complex screens with many parameters, and composables at the boundary between state management and UI.

Use meaningful tags that reflect your app's feature structure. Tags like `"auth-flow"`, `"checkout-item"`, and `"feed-card"` are immediately understandable when scanning logs, while generic tags like `"card1"` or `"screen"` provide no useful context.

Set appropriate thresholds based on the composable's expected behavior. A threshold of `3` works for most cases, filtering out initial layout. For composables in scrolling lists or animated screens where frequent recomposition is normal, use `10` or higher so you only see truly excessive recomposition events.
