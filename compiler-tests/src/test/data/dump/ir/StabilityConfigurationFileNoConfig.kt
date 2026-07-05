// DUMP_KT_IR
// ENABLE_TRACE_ALL

import androidx.compose.runtime.Composable

interface ToBeStable

data class Wrapper(val value: ToBeStable)

// Baseline for StabilityConfigurationFile.kt: same Wrapper/ToBeStable setup, but WITHOUT
// STABILITY_CONFIGURATION_FILES. Confirms default behavior is unchanged — because ToBeStable is
// an unannotated interface, Wrapper stays unstable and the injected call reads:
//   _tracker.trackParameter(name = "wrapper", type = "<root>.Wrapper", value = wrapper, isStable = false)
@Composable
fun ComposableToBeSkippable(wrapper: Wrapper) {
  println(wrapper.toString())
}
