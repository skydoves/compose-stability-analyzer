// DUMP_KT_IR
// Issue #107 follow-up: `@StabilityInferred` is trusted only for declarations from OUTSIDE this
// compilation unit, where it is baked into the binary and is the intended cross-module channel.
//
// On a class in the module being compiled, the annotation only exists once the Compose compiler
// plugin's IR lowering has run, and whether that happens before or after this extension is decided
// by the resolved order of `kotlinCompilerPluginClasspath` — nothing pins it. Honouring it there
// made the verdict depend on artifact ordering: the same source produced STABLE in a build where
// the analyzer happened to run second and RUNTIME where it ran first. Our own property analysis is
// authoritative for source classes, so the annotation is ignored on them.
//
// `LoweredLocal` below stands in for a class the Compose plugin has already lowered in this module:
// property analysis yields RUNTIME (a standard collection), and the annotation claims
// `parameters = 0`, which used to be promoted to STABLE.
//
// Regression guard via the injected trackParameter(..., isStable = ...) calls:
//   - lowered -> isStable = false (source class; the annotation must not promote it to STABLE)
//   - plain   -> isStable = false (identical class without the annotation — the two must agree)

import androidx.compose.runtime.Composable
import androidx.compose.runtime.internal.StabilityInferred
import com.skydoves.compose.stability.runtime.TraceRecomposition

@StabilityInferred(parameters = 0)
data class LoweredLocal(val names: List<String>)

data class PlainLocal(val names: List<String>)

@TraceRecomposition(threshold = 1)
@Composable
fun ShowLocals(lowered: LoweredLocal, plain: PlainLocal) {
    println(lowered.names.size + plain.names.size)
}
