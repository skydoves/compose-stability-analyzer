import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared Kotlin compile and Spotless configuration, applied by every module.
 *
 * This used to live in a `subprojects { }` block in the root build script. Gradle's Isolated
 * Projects feature forbids a project configuring its children, so the block was reported as
 * "Project ':' cannot access 'Project.tasks' functionality on subprojects" (issue #107). A
 * convention plugin each module applies to itself is the supported replacement.
 *
 * This removes the last Isolated Projects violation this repository owns. The build still cannot
 * run with `--isolated-projects`, for three reasons that are all outside this repository:
 *
 *  - `binary-compatibility-validator` 0.18.1 does `allprojects { configurations }`. The plugin is
 *    frozen — its functionality moved into the Kotlin Gradle plugin's `abiValidation` — so the fix
 *    is to migrate off it, not to upgrade.
 *  - Spotless 7.0.2 makes every project reach into the root project's tasks. Partial support
 *    landed in Spotless 8.3.0; that is a major bump and changes the bundled ktlint, so it belongs
 *    in its own change.
 *  - The Kotlin Gradle plugin's `NpmResolverPlugin`, pulled in by `:stability-runtime`'s `js`
 *    target, reads the root project's plugins (KT-71130).
 *
 * The last one gates the other two: until it is fixed upstream, this build cannot pass regardless.
 * The published Gradle plugin is unaffected — it is verified Isolated-Projects-clean against a
 * separate consumer fixture.
 */

plugins {
  id("com.diffplug.spotless")
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
    freeCompilerArgs.addAll(
      "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
      "-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI",
    )
    // Compose compiler reports/metrics land next to the module's other build output.
    val composeMetricsDir = layout.buildDirectory.dir("compose_metrics").get().asFile.absolutePath
    freeCompilerArgs.addAll(
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$composeMetricsDir",
    )
    freeCompilerArgs.addAll(
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$composeMetricsDir",
    )
  }
}

// `isolated.rootProject` exposes only the data that is safe to read across project boundaries,
// which is what keeps these paths legal under Isolated Projects. Plain `rootProject.file(...)`
// would reach into another project's mutable state.
val licenseDir = isolated.rootProject.projectDirectory.dir("spotless")

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**/*.kt")
    // Exclude test data files (they have special formatting requirements)
    targetExclude("**/src/test/data/**/*.kt")
    // Exclude files using context parameters (ktlint doesn't support them yet)
    targetExclude("**/ComposableStabilityChecker.kt")
    ktlint().editorConfigOverride(
      mapOf(
        "indent_size" to 2,
        "continuation_indent_size" to 2,
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      ),
    )
    licenseHeaderFile(licenseDir.file("copyright.kt").asFile)
  }
  format("kts") {
    target("**/*.kts")
    targetExclude("**/build/**/*.kts")
    // Look for the first line that doesn't have a block comment (assumed to be the license)
    licenseHeaderFile(licenseDir.file("copyright.kts").asFile, "(^(?![\\/ ]\\*).*$)")
  }
  format("xml") {
    target("**/*.xml")
    targetExclude("**/build/**/*.xml")
    // Look for the first XML tag that isn't a comment (<!--) or the xml declaration (<?xml)
    licenseHeaderFile(licenseDir.file("copyright.xml").asFile, "(<[^!?])")
  }
}
