// RUN_PIPELINE_TILL: BACKEND
// Test that stable parameters are correctly identified

data class StableUser(val name: String, val age: Int)

fun stableUserCard(user: StableUser) {
    // All parameters are stable:
    // - user: StableUser is a data class with val properties
}

fun primitiveParameters(
    text: String,
    count: Int,
    enabled: Boolean,
) {
    // All primitive types are stable
}
