/*
 * Designed and developed by 2025 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform") version "2.10.1"
  id(libs.plugins.spotless.get().pluginId)
}

kotlin {
  explicitApi()
}

group = project.property("GROUP") as String
version = project.property("VERSION_NAME") as String

repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  implementation(project(":stability-runtime"))

  intellijPlatform {
    intellijIdeaCommunity("2025.2")
    bundledPlugin("org.jetbrains.kotlin")
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
  }

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit"))
}

intellijPlatform {
  buildSearchableOptions = false
  instrumentCode = true

  pluginConfiguration {
    ideaVersion {
      sinceBuild = "242"
      untilBuild = "253.*"
    }

    description = """
            Developer: skydoves (Jaewoong Eum)<br/>
            Provides real-time stability analysis for Jetpack Compose functions directly in the IDE.
            <br/><br/>
            <b>Features:</b>
            <ul>
                <li>Hover Tooltips: See detailed stability information</li>
                <li>Gutter Icons: Visual indicators for skippable composables</li>
                <li>Inline Hints: Parameter-level stability annotations</li>
                <li>Code Highlighting: Unstable parameters highlighted</li>
            </ul>
        """.trimIndent()
    changeNotes = """
            <b>0.7.0</b>
            <ul>
                <li><b>New: Recomposition Cascade Visualizer</b> - Right-click any @Composable function to trace downstream composables affected by recomposition. Shows a tree view with stability status, summary statistics, cycle detection, and source navigation.</li>
                <li><b>New: Live Recomposition Heatmap</b> - Real-time recomposition counts from a connected device overlaid directly above composable functions in the editor. Color-coded by severity (green/yellow/red). Click any count to view detailed event logs with parameter change history.</li>
                <li><b>New: Heatmap tab</b> in tool window - Shows recomposition event logs with parameter changes when clicking heatmap counts</li>
                <li><b>New: Cascade tab</b> in tool window - Dedicated panel for cascade analysis results</li>
                <li>Start/Stop Heatmap button moved to tool window title bar for easy access across all tabs</li>
                <li>Extended IDE compatibility to build 253 (IntelliJ IDEA 2025.3)</li>
                <li>Heatmap enabled by default in plugin settings</li>
            </ul>
            <b>0.6.7</b>
            <ul>
                <li><b>Android variant-specific stability tasks</b> (Issue #85) - Per-variant tasks like debugStabilityDump and releaseStabilityCheck</li>
                <li><b>Non-regressive change filtering</b> (Issue #82) - New ignoreNonRegressiveChanges and allowMissingBaseline options</li>
                <li><b>Stability config wildcard support</b> (Issue #108) - Support for * and ** wildcards matching the official Compose compiler format</li>
                <li><b>Fixed @StabilityInferred in Gradle plugin</b> (Issue #102) - Cross-module classes now correctly treated as stable</li>
                <li><b>Fixed @NonRestartableComposable/@NonSkippableComposable analysis</b> (Issue #103) - Now excluded from stability analysis</li>
                <li><b>Improved typealias handling</b> (Issue #16) - Typealias to function types now correctly recognized as stable</li>
            </ul>
            <b>0.6.2</b>
            <ul>
                <li><b>Fixed property source file location and navigation in tool window</b> (Issue #67) - Properties now show correct file name and double-click navigation works</li>
                <li><b>Improved ignored type pattern handling in tool window</b> (Issue #74) - Ignored parameters display as stable and composable skippability is recalculated accordingly</li>
            </ul>
            <b>0.6.1</b>
            <ul>
                <li><b>Added Settings icon in tool window toolbar</b> - Quick access to plugin settings via gear icon</li>
                <li><b>Fixed tool window ignore pattern filtering</b> (Issue #74) - Tool window now respects ignored type patterns</li>
                <li><b>Fixed WASM build failures</b> (Issue #70) - Excluded WASM infrastructure tasks from dependency matching</li>
                <li><b>Fixed property name display</b> (Issue #67) - Properties no longer show as <get-propertyName></li>
                <li>Updated tool window icon to monochrome style for better UI consistency</li>
                <li>Updated dependencies: Lint API, Nexus Plugin, AGP, Compose BOM, JetBrains Compose</li>
            </ul>
            <b>0.5.2</b>
            <ul>
                <li><b>CRITICAL FIX: Resolved APK size bloat in release builds</b> (Issue #39)</li>
                <li>ProGuard rules optimized - only keeps classes actually used by compiler-injected code</li>
                <li>Dramatically reduced release APK size when using the plugin</li>
            </ul>
            <b>0.5.1</b>
            <ul>
                <li><b>Added wasmJs target support</b> - Runtime module now supports WebAssembly JavaScript for Compose Multiplatform web apps (Issue #32)</li>
                <li><b>Fixed sealed class stability inheritance</b> - Sealed classes with @Immutable/@Stable annotations now properly propagate stability to subclasses (Issue #31)</li>
                <li>Abstract classes with stability annotations are now correctly analyzed</li>
                <li>Both IDE plugin and compiler plugin handle sealed class hierarchies consistently</li>
            </ul>
            <b>0.5.0</b>
            <ul>
                <li><b>Breaking: Minimum IDE version updated to 2024.2+ (build 242+)</b></li>
                <li><b>New Compose Stability Tool Window</b> - View all composables in your project at a glance (Issue #14)</li>
                <li>Tree view grouped by module → package → file with color-coded stability indicators</li>
                <li>Details pane with parameter stability, double-click navigation to source</li>
                <li>Filter buttons for All/Skippable/Unskippable composables</li>
                <li>Performance optimization - Tool window reads pre-computed JSON files instead of re-analyzing</li>
                <li>Fixed PluginException in IntelliJ IDEA 2025.2.4 (Issue #33)</li>
                <li>Fixed typealias detection for Composable function types (Issue #16)</li>
                <li>New setting: "Show in test source sets" for gutter icons (Issue #21)</li>
                <li>Improved cross-module stability detection with @StabilityInferred annotation (Issue #18)</li>
                <li>Extended compatibility to IntelliJ IDEA 2025.3 (build 253)</li>
            </ul>
            <b>0.4.0</b>
            <ul>
                <li>Added ProGuard consumer rules for automatic R8/ProGuard compatibility</li>
                <li>Enhanced @TraceRecomposition visualization with better gutter icons</li>
                <li>Added comprehensive compiler-tests module with FIR/IR dump testing</li>
                <li>Improved stability analysis for complex generic types</li>
                <li>Added support for Gradle plugin's includeTests and ignoredProjects configurations</li>
                <li>Fixed stability inference for nested data classes</li>
            </ul>
            <b>0.3.0</b>
            <ul>
                <li>Added @IgnoreStabilityReport annotation to exclude composables from reports</li>
                <li>Enhanced compiler plugin with better error messages and diagnostics</li>
                <li>Added runtime and Gradle module unit tests for improved reliability</li>
                <li>Improved RecompositionTracker performance and memory efficiency</li>
                <li>Fixed analysis of @Composable functions with default parameters</li>
                <li>Enhanced IDE quick fixes for adding @TraceRecomposition annotation</li>
            </ul>
            <b>0.2.3</b>
            <ul>
                <li>Version bump</li>
                <li>Fixed compiler test compatibility issues</li>
                <li>Code formatting improvements</li>
            </ul>
            <b>0.2.2</b>
            <ul>
                <li>Unified maven publishing configuration</li>
                <li>Updated build configuration to match landscapist project structure</li>
            </ul>
            <b>0.2.1</b>
            <ul>
                <li>Fixed K2 API compatibility for Android Studio AI-243 and older IDE versions</li>
                <li>Improved graceful fallback to PSI analyzer when K2 is unavailable</li>
            </ul>
            <b>0.2.0</b>
            <ul>
                <li>Added K2 Analysis API support for 2-3x faster and more accurate stability analysis</li>
                <li>Enhanced @Preview detection to support meta-annotations (custom preview annotations)</li>
                <li>Improved type parameter and superclass stability analysis</li>
                <li>Fixed @Composable function type detection for all lambda variations</li>
                <li>Added support for IntelliJ 2025.2</li>
            </ul>
            <b>0.1.0</b>
            <ul>
                <li>Initial release</li>
                <li>Hover documentation for composable functions</li>
                <li>Gutter icons for stability status</li>
                <li>Inline hints for unstable parameters</li>
                <li>Code annotations and highlighting</li>
                <li>Fixed nullable type stability analysis</li>
                <li>Fixed interface type detection</li>
            </ul>
        """.trimIndent()

  }

}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.addAll(
        listOf(
          "-Xcontext-receivers",
          "-opt-in=org.jetbrains.kotlin.analysis.api.KaExperimentalApi",
          "-opt-in=org.jetbrains.kotlin.analysis.api.KaImplementationDetail",
        ),
      )
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}
