// ENABLE_TRACE_ALL
// Test that trace-all compiles a module with auto-instrumented composables and
// leaves non-composable functions untouched.

import androidx.compose.runtime.Composable

data class Product(var name: String, var price: Double)

// Auto-instrumented (unstable param); only compiled, never executed here —
// verifies the generated IR survives codegen and verification.
@Composable
fun ProductCard(product: Product) {
    println("Product: ${product.name}")
}

// Non-composable function must be untouched by trace-all.
fun plainFunction(count: Int): Int {
    return count + 1
}

fun box(): String {
    if (plainFunction(41) != 42) {
        return "FAIL: plainFunction was altered"
    }
    return "OK"
}
