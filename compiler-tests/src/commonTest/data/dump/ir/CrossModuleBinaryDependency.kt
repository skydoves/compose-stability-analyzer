// DUMP_KT_IR
// Issue #18 / #107: a type declared in another Gradle module reaches this compilation as a
// compiled binary on the classpath, so Fir2Ir gives it IR_EXTERNAL_DECLARATION_STUB
// (FirClass.irOrigin(): no container FirFile in this module's firProvider). That origin is the
// only signal needed to treat it as cross-module — the same one androidx's StabilityInferencer
// uses — so no Gradle-side sibling-project package scanning is required.
//
// `// MODULE: main(lib)` produces a real binary dependency here: the codegen/IR-dump
// configuration defaults to DependencyKind.Binary, so `lib` is compiled to .class files and put
// on `main`'s compile classpath.
//
// Regression guard via the injected trackParameter(..., isStable = ...) calls:
//   - model  -> isStable = false (binary dependency, no @Stable and no @StabilityInferred)
//   - stable -> isStable = true  (binary dependency, but marked @Stable)
//   - local  -> isStable = true  (declared in this module; must NOT be swept up as cross-module)

// MODULE: lib
// SKIP_KT_DUMP
// FILE: ThirdPartyModel.kt
package lib

import androidx.compose.runtime.Stable

data class ThirdPartyModel(val name: String, val count: Int)

@Stable
data class ThirdPartyModelStable(val name: String, val count: Int)

// MODULE: main(lib)
// FILE: main.kt
import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition
import lib.ThirdPartyModel
import lib.ThirdPartyModelStable

data class LocalModel(val name: String)

@TraceRecomposition(threshold = 1)
@Composable
fun ThirdPartyCard(
    model: ThirdPartyModel,
    stable: ThirdPartyModelStable,
    local: LocalModel,
) {
    println(model.name + stable.name + local.name)
}
