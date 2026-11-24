// RUN_PIPELINE_TILL: BACKEND
// FIR_DUMP
// Test FIR dump for an unstable data class with mutable properties

data class MutableUser(var name: String, var age: Int)

fun updateUser(user: MutableUser) {
    user.name = "Bob"
    user.age = 30
}
