pluginManagement {
  includeBuild("build-logic")
  repositories {
    mavenLocal()
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    mavenLocal()
    google()
    mavenCentral()
    maven { url = uri("https://plugins.gradle.org/m2/") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }
  }
}
rootProject.name = "compose-stability-analyzer"

include(
  ":stability-compiler",
  ":stability-runtime",
  ":stability-gradle",
  ":stability-lint",
  ":compiler-tests",
  ":app",
  ":app-model",
)

// The IDE plugin is excluded from the main build because it is an IntelliJ Platform build:
// it resolves the IDE distribution and the bundled Kotlin plugin, and consumes the runtime from
// its published coordinate rather than from this build. It tracks the same Kotlin version.
// Build it separately: ./gradlew -p compose-stability-analyzer-idea buildPlugin
// include(":compose-stability-analyzer-idea")
