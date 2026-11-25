// Test that data classes with immutable properties are stable

data class User(val name: String, val age: Int)

fun testUser(user: User) {
    // User should be stable because all properties are val
}

fun box(): String {
    val user = User("John", 30)
    if (user.name != "John") return "FAIL: name"
    if (user.age != 30) return "FAIL: age"

    // If we got here, the test passed
    return "OK"
}
