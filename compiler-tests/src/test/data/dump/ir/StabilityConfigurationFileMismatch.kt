// DUMP_KT_IR
// ENABLE_TRACE_ALL
// STABILITY_CONFIGURATION_FILES: compiler-tests/src/test/data/dump/ir/stability_config_mismatch.conf

import androidx.compose.runtime.Composable

interface ToBeStable

data class Wrapper(val value: ToBeStable)

// Negative counterpart to StabilityConfigurationFile.kt: a config file IS supplied, but it lists a
// different type (not ToBeStable). The parser runs and produces a matcher, yet nothing matches
// Wrapper/ToBeStable, so Wrapper stays unstable and the injected call reads:
//   _tracker.trackParameter(name = "wrapper", type = "<root>.Wrapper", value = wrapper, isStable = false)
@Composable
fun ComposableToBeSkippable(wrapper: Wrapper) {
  println(wrapper.toString())
}
