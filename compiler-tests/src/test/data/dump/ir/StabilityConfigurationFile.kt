// DUMP_KT_IR
// ENABLE_TRACE_ALL
// STABILITY_CONFIGURATION_FILES: compiler-tests/src/test/data/dump/ir/stability_config.conf

import androidx.compose.runtime.Composable

interface ToBeStable

data class Wrapper(val value: ToBeStable)

// The following line in StabilityConfigurationFile.fir.kt.txt confirms the stability_config.conf was taken into account:
//   _tracker.trackParameter(name = "wrapper", type = "<root>.Wrapper", value = wrapper, isStable = true)
// The test will fail if isStable = false (meaning ToBeStable was not marked as stable, hence Wrapper is also not stable).
@Composable
fun ComposableToBeSkippable(wrapper: Wrapper) {
  println(wrapper.toString())
}
