// DUMP_KT_IR
// ENABLE_TRACE_ALL
// Test that trace-all auto-instruments eligible composables and skips ineligible ones.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import com.skydoves.compose.stability.runtime.IgnoreStabilityReport
import com.skydoves.compose.stability.runtime.TraceRecomposition

data class User(val name: String, val age: Int)

// Auto-instrumented: restartable Unit composable with a block body.
// Expected: tracker injected with tag = "", threshold = 2, isAutoTraced = true.
@Composable
fun AutoTracedCard(user: User) {
    println("Rendering: ${user.name}")
}

// Annotation wins over trace-all: tag/threshold come from @TraceRecomposition,
// isAutoTraced = false.
@TraceRecomposition(tag = "explicit", threshold = 5)
@Composable
fun ExplicitlyTracedCard(user: User) {
    println("Rendering: ${user.name}")
}

// Skipped: non-Unit return type (non-restartable).
@Composable
fun rememberLabel(user: User): String {
    return "label: ${user.name}"
}

// Skipped: inline composable.
@Composable
inline fun InlineWrapper(content: () -> Unit) {
    content()
}

// Skipped: @NonRestartableComposable.
@NonRestartableComposable
@Composable
fun NonRestartableCard(user: User) {
    println("Rendering: ${user.name}")
}

// Skipped: @ReadOnlyComposable (and getters in general).
@get:ReadOnlyComposable
@get:Composable
val currentLabel: String
    get() = "label"

// Skipped: @IgnoreStabilityReport.
@IgnoreStabilityReport
@Composable
fun IgnoredCard(user: User) {
    println("Rendering: ${user.name}")
}

// Auto-instrumented: expression bodies are lowered to block bodies by FIR2IR before this
// plugin runs, and a Unit-returning expression-body composable is restartable like any other.
@Composable
fun ExpressionBodyCard(user: User) = println("Rendering: ${user.name}")

// Auto-instrumented: default parameter values; only the function itself is instrumented,
// never a synthetic $default wrapper (special names are excluded).
@Composable
fun CardWithDefaults(user: User, highlighted: Boolean = false) {
    println("Rendering: ${user.name} ($highlighted)")
}

class CardScope

// Auto-instrumented: extension receiver must not appear as a tracked parameter
// (receivers are filtered out; only `user` gets a trackParameter call).
@Composable
fun CardScope.ScopedCard(user: User) {
    println("Rendering in scope: ${user.name}")
}
