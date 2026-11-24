// DUMP_KT_IR
// Test that @TraceRecomposition injects tracking code into @Composable functions

import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition

data class User(val name: String, val age: Int)

@TraceRecomposition(tag = "user-card", threshold = 1)
@Composable
fun UserCard(user: User) {
    // This should have tracking code injected by the plugin
    println("Rendering: ${user.name}")
}

// Also test without @TraceRecomposition for comparison
@Composable
fun SimpleCard(text: String) {
    // This should NOT have tracking code
    println(text)
}
