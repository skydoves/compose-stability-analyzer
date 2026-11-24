// DUMP_KT_IR
// Test IR dump for a simple stable data class

data class SimpleUser(val name: String, val age: Int)

fun createUser(): SimpleUser {
    return SimpleUser("Alice", 25)
}

fun processUser(user: SimpleUser) {
    println(user.name)
}
