pluginManagement {
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

// The IDE plugin is excluded from the main build because it requires Kotlin 2.3.0
// (the K2 Analysis API uses context receivers, which are removed in Kotlin 2.3.20).
// Build it separately: ./gradlew -p compose-stability-analyzer-idea buildPlugin
// include(":compose-stability-analyzer-idea")
