plugins {
  `kotlin-dsl`
}

dependencies {
  // The convention plugin needs KotlinCompile/JvmTarget and SpotlessExtension to compile, and both
  // plugins at execution time. This is an included build (see `pluginManagement.includeBuild` in
  // the root settings), so its dependencies stay isolated instead of being prepended to every
  // build script's classpath the way `buildSrc` would — which would clash with the version-carrying
  // `alias(libs.plugins.kotlin...)` declarations in the main build.
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.spotless.gradle.plugin)
}
