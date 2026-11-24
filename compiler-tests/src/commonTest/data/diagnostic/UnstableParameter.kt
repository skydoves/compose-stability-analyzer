// RUN_PIPELINE_TILL: BACKEND
// Test that unstable parameters are correctly identified

data class MutableUser(var name: String, var age: Int)

fun unstableUserCard(user: MutableUser) {
    // user is UNSTABLE because MutableUser has var properties
}

class MutableState {
    var value: String = ""
}

fun componentWithMutableState(state: MutableState) {
    // state is UNSTABLE due to mutable property
}
