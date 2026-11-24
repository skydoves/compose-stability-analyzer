// Test simple function compilation without Compose annotations

fun tracedFunction(count: Int) {
    // Simple function test
}

fun box(): String {
    // Test that the function compiles and runs
    tracedFunction(42)
    return "OK"
}
