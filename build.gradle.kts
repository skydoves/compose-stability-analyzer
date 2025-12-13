import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.jetbrains.compose) apply false
  alias(libs.plugins.compose.stability.analyzer) apply false
  alias(libs.plugins.kotlin.binary.compatibility)
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.spotless)
  alias(libs.plugins.dokka)
}

apiValidation {
  ignoredProjects.addAll(listOf("app", "app-model"))
}

subprojects {
  tasks.withType<KotlinCompile>().all {
    compilerOptions.freeCompilerArgs.addAll(
      listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
          project.layout.buildDirectory.asFile.get().absolutePath + "/compose_metrics"
      )
    )
    compilerOptions.freeCompilerArgs.addAll(
      listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
          project.layout.buildDirectory.asFile.get().absolutePath + "/compose_metrics"
      )
    )
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
  }

  tasks.withType<KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
      freeCompilerArgs.addAll(
        "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
        "-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI"
      )
    }
  }

  apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      targetExclude("**/build/**/*.kt")
      // Exclude test data files (they have special formatting requirements)
      targetExclude("**/src/test/data/**/*.kt")
      // Exclude files using context parameters (ktlint doesn't support them yet)
      targetExclude("**/ComposableStabilityChecker.kt")
      ktlint().editorConfigOverride(mapOf("indent_size" to 2, "continuation_indent_size" to 2))
      licenseHeaderFile(rootProject.file("$rootDir/spotless/copyright.kt"))
    }
    format("kts") {
      target("**/*.kts")
      targetExclude("**/build/**/*.kts")
      // Look for the first line that doesn't have a block comment (assumed to be the license)
      licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
    }
    format("xml") {
      target("**/*.xml")
      targetExclude("**/build/**/*.xml")
      // Look for the first XML tag that isn't a comment (<!--) or the xml declaration (<?xml)
      licenseHeaderFile(rootProject.file("spotless/copyright.xml"), "(<[^!?])")
    }
  }
}
