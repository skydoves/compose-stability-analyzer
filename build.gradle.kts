plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.jetbrains.compose) apply false
  alias(libs.plugins.kotlin.binary.compatibility)
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.dokka)
}

apiValidation {
  ignoredProjects.addAll(listOf("app", "app-model"))
}

// Shared Kotlin/Spotless configuration lives in the `compose-stability.conventions` convention
// plugin (build-logic), which each module applies to itself. It used to be a `subprojects { }` block
// here, which Gradle's Isolated Projects feature rejects — a project may not configure its
// children (issue #107).
