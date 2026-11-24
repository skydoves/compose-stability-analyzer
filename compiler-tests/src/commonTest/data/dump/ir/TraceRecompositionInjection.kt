// DUMP_KT_IR
// Test that @TraceRecomposition injects tracking code into the IR

import com.skydoves.compose.stability.runtime.TraceRecomposition

data class User(val name: String, val age: Int)

@TraceRecomposition(tag = "user-card", threshold = 1)
fun UserCard(user: User) {
    println("Rendering user: ${user.name}")
}

fun testFunction() {
    val user = User("Alice", 25)
    UserCard(user)
}
