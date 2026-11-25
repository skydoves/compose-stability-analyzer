// RUN_PIPELINE_TILL: BACKEND
// FIR_DUMP
// Test that @TraceRecomposition annotation is preserved in FIR

import com.skydoves.compose.stability.runtime.TraceRecomposition

data class User(val name: String, val age: Int)

@TraceRecomposition(tag = "profile", threshold = 3)
fun UserProfile(user: User) {
    println("User: ${user.name}, Age: ${user.age}")
}
