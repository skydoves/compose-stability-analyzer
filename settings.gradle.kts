pluginManagement {
  repositories {
    mavenLocal()
    gradlePluginPortal()
    google()
    mavenCentral()
    mavenLocal()
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
  ":compose-stability-analyzer-idea",
  ":compiler-tests",
  ":app",
)
