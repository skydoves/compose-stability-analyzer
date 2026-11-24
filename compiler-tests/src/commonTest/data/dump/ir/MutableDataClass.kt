// DUMP_KT_IR
// Test IR dump for an unstable data class with mutable properties

data class MutableUser(var name: String, var age: Int)

fun updateUser(user: MutableUser) {
    user.name = "Bob"
    user.age = 30
}

fun printUser(user: MutableUser) {
    println("${user.name} is ${user.age} years old")
}
