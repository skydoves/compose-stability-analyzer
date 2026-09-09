// DUMP_KT_IR
// Companion to StabilityInferredIsBinaryOnly.kt, which pins the *source* half of the rule. This
// pins the binary half: for a class from outside this compilation unit, `@StabilityInferred` is
// baked into the binary by the Compose compiler and is the intended cross-module channel, so its
// `parameters` bitmask refines a RUNTIME property verdict (0 -> STABLE, non-zero -> RUNTIME).
//
// `// MODULE: main(lib)` produces a real binary dependency: the IR-dump configuration defaults to
// DependencyKind.Binary, so `lib` is compiled to .class files and put on `main`'s compile
// classpath. `@StabilityInferred` has AnnotationRetention.BINARY, so the hand-written annotation
// survives into those .class files and is visible while `main` is compiled.
//
// This is the only path that reads an annotation *argument* off a non-source class, so it also
// guards the by-name `argumentMapping` lookup that replaced the by-position `arguments[index]`
// read when Kotlin 2.4.20 deprecated `IrAnnotation.symbol`.
//
// Regression guard via the injected trackParameter(..., isStable = ...) calls:
//   - stable  -> isStable = true  (parameters = 0 promotes the RUNTIME property verdict to STABLE)
//   - runtime -> isStable = false (a non-zero bitmask leaves the verdict at RUNTIME)

// MODULE: lib
// SKIP_KT_DUMP
// FILE: InferredModels.kt
package lib

import androidx.compose.runtime.internal.StabilityInferred

@StabilityInferred(parameters = 0)
data class InferredStable(val names: List<String>)

@StabilityInferred(parameters = 1)
data class InferredRuntime(val names: List<String>)

// MODULE: main(lib)
// FILE: main.kt
import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition
import lib.InferredRuntime
import lib.InferredStable

@TraceRecomposition(threshold = 1)
@Composable
fun ShowInferred(stable: InferredStable, runtime: InferredRuntime) {
    println(stable.names.size + runtime.names.size)
}
