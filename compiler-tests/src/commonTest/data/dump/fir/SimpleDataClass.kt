// RUN_PIPELINE_TILL: BACKEND
// FIR_DUMP
// Test FIR dump for a simple stable data class

data class SimpleUser(val name: String, val age: Int)

fun createUser(): SimpleUser {
    return SimpleUser("Alice", 25)
}
