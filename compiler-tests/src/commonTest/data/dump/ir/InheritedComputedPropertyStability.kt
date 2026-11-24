// DUMP_KT_IR
// Issue #178: computed getter-only properties (no backing field) store no state and must be
// ignored during stability inference, matching the Compose compiler. A class is not made
// runtime/unstable just because it (or an abstract supertype) exposes a computed property of an
// interface type. Genuine inherited stored `var`/unstable fields must still be detected.
//
// Regression guard via the injected trackParameter(..., isStable = ...) calls:
//   - holder  -> isStable = true  (inherited getter-only computed property, no backing field)
//   - card    -> isStable = true  (own getter-only computed property, no backing field)
//   - counter -> isStable = false (inherited stored `var` — must stay unstable)
//   - profile -> isStable = true  (only computed props + abstract base with no stored state;
//                                  the bare-Unknown superclass must be dropped, not propagated)

import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition

// #178: data class whose only "extra" member is an inherited computed property.
data class DataHolder(val input: String) : AbstractBase()

abstract class AbstractBase {
    open val meta: Metadata?
        get() = null
}

interface Metadata

// Own computed getter-only property of an interface type.
data class CardData(val title: String) {
    val badge: Metadata?
        get() = null
}

// Inherited stored `var` — genuinely unstable, must be preserved.
data class CounterData(val label: String) : CounterBase()

open class CounterBase {
    var count: Int = 0
}

// Only computed properties + an abstract base with no stored state: must stay STABLE.
class ProfileCard : BaseCard() {
    val header: String
        get() = "profile"

    override fun render() {}
}

abstract class BaseCard {
    abstract fun render()
}

@TraceRecomposition(threshold = 1)
@Composable
fun ShowHolder(holder: DataHolder) {
    println(holder.input)
}

@TraceRecomposition(threshold = 1)
@Composable
fun ShowProfile(profile: ProfileCard) {
    println(profile.header)
}

@TraceRecomposition(threshold = 1)
@Composable
fun ShowCard(card: CardData) {
    println(card.title)
}

@TraceRecomposition(threshold = 1)
@Composable
fun ShowCounter(counter: CounterData) {
    println(counter.label)
}
