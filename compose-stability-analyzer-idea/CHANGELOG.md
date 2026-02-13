# Compose Stability Analyzer IntelliJ Plugin - Changelog

All notable changes to the IntelliJ IDEA plugin will be documented in this file.

## [0.7.0] - 2026-02-13

### Added
- **Recomposition Cascade Visualizer** (PR #119)
  - Right-click any `@Composable` function and select "Analyze Recomposition Cascade"
  - Traces downstream composables affected by recomposition in a tree view
  - Shows stability status, summary statistics (total/skippable/unskippable/depth), and source navigation
  - Cycle detection and depth limits prevent infinite analysis in recursive call graphs
  - New "Cascade" tab in the tool window with dedicated toolbar (Refresh/Clear)
  - K2-safe reference resolution using `runCatching` pattern
  - Cancellation support via `ProgressIndicator.checkCanceled()`
- **Live Recomposition Heatmap** (PR #120, #121)
  - Reads `@TraceRecomposition` events from ADB logcat on a connected device
  - Overlays real-time recomposition counts above composable functions in the editor
  - Color-coded severity: green (< 10), yellow (10-50), red (50+) -- thresholds configurable
  - Deterministic pre-baked inlay renderers eliminate flickering
  - Click any recomposition count to open the Heatmap tab with detailed event logs and parameter change history
  - Start/Stop toggle via tool window title bar button, Code menu, or editor right-click menu
  - Multi-device support with device picker popup
  - ADB path auto-detection: ANDROID_HOME, local.properties sdk.dir, common paths, PATH
  - Heatmap enabled by default in plugin settings
  - New "Heatmap" tab in the tool window with Refresh/Clear toolbar
  - Configurable settings: severity thresholds, auto-start, show-when-stopped, max recent events
- **Plugin Verifier integration** (PR #118)
  - Extended IDE compatibility range to build 261 (IntelliJ IDEA 2026.1)

### Improved
- Tool window reorganized with three tabs: Explorer, Cascade, and Heatmap
- Heatmap toggle button moved from Explorer toolbar to tool window title bar for visibility across all tabs
- Cascade and Heatmap panels use simplified single-pane tree layout (no split details panel)

## [0.6.7] - 2026-02-10

### Added
- **Android variant-specific stability tasks** (Issue #85, PR #101)
  - Gradle plugin now creates per-variant tasks (e.g., `debugStabilityDump`, `releaseStabilityCheck`) for Android projects
  - Allows running stability analysis on a single variant without compiling others
  - Improved build cache compatibility
- **Non-regressive change filtering for stability validation** (Issue #82, PR #104)
  - New `ignoreNonRegressiveChanges` option to only flag stability regressions
  - New `allowMissingBaseline` option to allow checks without an existing baseline file
- **Stability configuration file wildcard support** (Issue #108, PR #110)
  - Implemented `stabilityPatternToRegex` supporting `*` and `**` wildcard syntax
  - Matches the official Compose compiler stability configuration format

### Fixed
- **`@StabilityInferred` annotation now supported in Gradle plugin** (Issue #102, PR #112)
  - Cross-module classes with `@StabilityInferred(parameters=0)` now correctly treated as stable during `stabilityDump`/`stabilityCheck`
  - Aligns Gradle plugin behavior with the IDEA plugin
- **Skip analysis for `@NonRestartableComposable` and `@NonSkippableComposable`** (Issue #103, PR #111)
  - These composable functions are now excluded from stability analysis as they are not subject to recomposition skipping
- **Improved typealias handling** (Issue #16, PR #106)
  - Typealias to function types (e.g., `typealias ComposableAction = @Composable () -> Unit`) now correctly recognized as stable
  - Added typealias expansion support across PSI, K1, and K2 analysis paths with circular alias recursion guard

### Improved
- **Replaced internal `nj2k.descendantsOfType` with stable `PsiTreeUtil` API** (PR #109)
  - Intelligent caching for typealias resolution with automatic expiration
  - Streamlined function-type detection and composability checking logic
  - Improved IDE responsiveness during analysis

## [0.6.4] - 2025-12-16

### Fixed
- **Fixed "Wrong plugin option format: null" compilation error** (Issue #87)
  - Changed Gradle plugin cross-module detection to use file-based approach
  - Project dependencies now written to `build/stability/project-dependencies.txt` instead of passed as string
  - Resolves build failures in multi-module projects introduced in 0.6.3
  - Gradle/compiler plugin cross-module detection now fully functional
  - IDE plugin cross-module detection continues to work as before

### Improved
- More robust cross-module dependency passing mechanism in Gradle plugin
- Better compatibility with multi-module Android projects

### Known Issues
- Tool window may show "Unknown.kt" for composables defined inside classes/objects
  - This occurs because source file lookup only searches top-level declarations
  - Will be addressed in a future release

## [0.6.3] - 2025-12-13

### Added
- **Cross-module stability detection** - Classes from other modules now require explicit stability annotations
  - Classes from different Gradle modules are marked as UNSTABLE unless annotated with @Stable/@Immutable/@StabilityInferred
  - Gradle plugin automatically collects all subproject packages for cross-module detection
  - IDE plugin uses IntelliJ module system to identify cross-module types
  - Prevents accidentally assuming stability for classes where implementation details aren't visible
  - Both compiler plugin and IDE plugin now consistently handle cross-module boundaries

### Fixed
- **Fixed Gradle compatibility issues**
  - Removed deprecated `getDependencyProject()` usage for broader Gradle version compatibility
  - Implemented portable dependency collection that works across all Gradle versions
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names in tool window now navigates to correct source location
  - Extended source location search to include `KtProperty` declarations in addition to `KtNamedFunction`
- **Fixed compiler tests compatibility with new cross-module detection**
  - Updated test configurator to pass projectDependencies parameter
  - All compiler tests now passing with cross-module detection enabled

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters are now displayed as stable instead of being hidden completely
  - Composable skippability is recalculated based on processed parameters after applying ignore patterns
  - Composables with only ignored unstable parameters now correctly show as skippable
  - Provides better visibility of composable signatures while respecting ignore patterns
- **Compacted code comments** for better readability across all cross-module detection implementations

## [0.6.2] - 2025-12-10

### Fixed
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names in tool window now navigates to correct source location
  - Extended source location search to include `KtProperty` declarations in addition to `KtNamedFunction`

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters are now displayed as stable instead of being hidden completely
  - Composable skippability is recalculated based on processed parameters after applying ignore patterns
  - Composables with only ignored unstable parameters now correctly show as skippable
  - Provides better visibility of composable signatures while respecting ignore patterns

## [0.6.1] - 2025-12-06

### Added
- **Settings icon in tool window toolbar**
  - Added gear icon to tool window toolbar for quick access to plugin settings
  - Opens Settings → Tools → Compose Stability Analyzer when clicked
  - Provides easier access to configuration without navigating through IDE menus

### Fixed
- **Tool window now respects ignored type patterns** (Issue #74)
  - Fixed inconsistency where ignored type patterns worked for inline hints but not tool window
  - Parameters matching ignore patterns are now filtered from tool window display
  - Provides consistent behavior across all plugin features (gutter icons, inline hints, warnings, and tool window)
  - When users configure type patterns to ignore in Settings, those types are now properly excluded from the tool window panel
- **Fixed WASM build failures with Gradle task dependencies** (Issue #70)
  - Excluded WASM infrastructure tasks (sync, webpack, executable, link, assemble) from task dependency matching
  - Resolves errors like "wasmJsBrowserProductionWebpack uses output from wasmJsDevelopmentExecutableCompileSync without declaring dependency"
  - WASM projects now build successfully without task dependency violations
- **Fixed property name display in tool window and stability files** (Issue #67)
  - Properties no longer show as `<get-propertyName>` in tool window and stability reports
  - Correctly extracts property names from getter functions in compiler plugin
  - Improved readability of property-based composables

### Improved
- **Updated tool window icon to monochrome style**
  - Changed from colored icon to monochrome design for better consistency with IntelliJ UI
  - Follows IntelliJ platform design guidelines for tool window icons
- **Updated dependencies**
  - Android Lint API: 31.13.0 → 31.13.1
  - Nexus Plugin: 0.34.0 → 0.35.0
  - Android Gradle Plugin: 8.13.0 → 8.13.1
  - Compose BOM: 2025.10.01 → 2025.12.00
  - JetBrains Compose: 1.9.2 → 1.9.3

## [0.6.0] - 2025-11-24

### Added
- **Per-project stability configuration file support** (Issue #60)
  - Each project can now specify its own stability configuration file path
  - Project-level settings take precedence over global settings
  - Configuration stored in project workspace (not shared across projects)
  - Access via Settings -> Tools -> Compose Stability Analyzer -> Project Configuration
  - Useful for teams with different stability patterns across multiple projects
- **Runtime gutter icon for runtime-only composables**
  - Composables with only RUNTIME parameters now show yellow gutter icon instead of red
  - Clear visual distinction between unstable (red) and runtime (yellow) stability
  - Detailed tooltip explains that stability is determined at runtime
  - Tooltip includes information about potential skippability changes between library versions
- **Generic type argument inference at compile time**
  - Generic types like `UiResult<Unit>` now correctly inferred as STABLE when type arguments are stable
  - Previously, generic types were marked as RUNTIME even with stable type arguments
  - Example: `MyComposable(result: UiResult<Unit>)` - now shows `Unit` is stable, so parameter is stable
  - Supports nested generics and multiple type arguments
  - More accurate stability analysis reduces false positives for generic wrapper types

### Improved
- Enhanced tooltip information for runtime parameters
  - Parameter breakdown now shows stable, runtime, and unstable counts separately
  - Lists runtime parameters with clear explanation of runtime stability behavior
  - Explains that skippability may change between library versions or when implementations change

## [0.5.3] - 2025-11-18

### Fixed
- **Fixed iOS native compilation with kotlinx.serialization** (Issue #48)
  - Compiler plugin now properly bundles kotlinx.serialization dependencies for Kotlin/Native targets
  - Resolves `NoClassDefFoundError: kotlinx/serialization/KSerializer` during iOS compilation
  - Enables Kotlin Multiplatform projects using iOS, macOS, and other native targets to compile successfully
  - kotlinx-serialization classes are now embedded directly in the compiler plugin JAR
- **Fixed Gradle Configuration Cache compatibility** (Issue #41)
  - StabilityDumpTask and StabilityCheckTask now properly capture project name at configuration time
  - Tasks no longer access `Task.project` during execution, preventing "unsupported at execution time" errors
  - Gradle builds with `--configuration-cache` flag now work correctly

## [0.5.2] - 2025-11-13

### Fixed
- **Fixed APK size increase in release builds** (Issue #39)
  - ProGuard rules were keeping entire stability-runtime package unnecessarily
  - Optimized consumer-rules.pro to only keep classes used by compiler-injected code
  - Now only keeps `RecompositionTracker` methods (constructor, trackParameter, logIfThresholdMet)
  - Logger classes only kept if explicitly used via `ComposeStabilityAnalyzer.setLogger()`
  - Compile-time classes (`StabilityInfo`, `ComposableInfo`, `ParameterInfo`) now removed by R8
  - This fix dramatically reduces release APK size when using the plugin

## [0.5.1] - 2025-11-10

### Added
- **wasmJs target support** - Added WebAssembly JavaScript (wasmJs) target to stability-runtime module (Issue #32)
  - Enables Kotlin Multiplatform projects to use wasmJs alongside other targets (Android, iOS, Desktop, macOS)
  - Runtime module now publishes wasmJs artifacts (klib) for Compose Multiplatform web applications
  - Implemented `DefaultRecompositionLogger` for wasmJs using `println()` for browser console output
  - wasmJs target placed directly under common hierarchy (separate from skia group) for proper source set resolution

### Fixed
- **Fixed sealed class stability inheritance** (Issue #31)
  - Abstract classes with `@Immutable` or `@Stable` annotations now correctly analyzed for stability
  - Sealed classes with `@Immutable`/`@Stable` now properly propagate stability to subclasses
  - Example: `@Immutable sealed class StableSealedClass` with `data class Stable(...)` subclass now correctly shows as STABLE instead of RUNTIME
  - Both IDE plugin and compiler plugin now consistently handle sealed class hierarchies with stability annotations

## [0.5.0] - 2025-11-08

### Breaking Changes
- **Minimum IDE version updated**: Now requires IntelliJ IDEA 2024.2+ (build 242+)
  - Previous minimum was 2023.2 (build 232)
  - This aligns with modern K2 Analysis API requirements and ensures better stability

### Added
- **New Compose Stability Tool Window** - View all composables in your project at a glance (Issue #14)
  - Tree view grouped by module -> package -> file
  - Color-coded stability indicators (green for skippable, yellow for non-skippable)
  - Stability counts shown as (XS, YNS) where S=Skippable, NS=Non-skippable (zero counts are hidden for cleaner UI)
  - Details pane showing composable information and parameter stability
  - Double-click navigation to source code location
  - Filter buttons to show All, Skippable, or Unskippable composables
  - Refresh button to re-analyze the project
  - Appears as a dedicated tool window on the right side of the IDE with custom icon
  - Access via View -> Tool Windows -> Compose Stability or click the icon on the right toolbar
- **Interactive empty state guide** - Step-by-step instructions when no composables are found
  - Clear setup instructions for first-time users
  - Clickable GitHub link for documentation and support
- New setting: "Show in test source sets" for gutter icons (Issue #21)
- Gutter icons are now hidden in test directories by default (can be enabled in settings)
- Support for reading @StabilityInferred annotation parameters for cross-module stability detection (Issue #18)

### Improved
- **Enhanced UI/UX for Tool Window**
  - Changed terminology from "runtime count" to more intuitive "S (Skippable)" and "NS (Non-skippable)"
  - Non-skippable items now use yellow color instead of red for better visual hierarchy
  - Zero counts are automatically hidden (e.g., "(2S, 0NS)" displays as "(2S)")
  - Centered stability icon in tool window for better visual alignment
- **Performance optimization** - Tool window now reads pre-computed stability information from JSON files instead of re-analyzing all files
  - Significantly faster load times for large projects
  - Reduced CPU usage when opening the tool window
- **Added IntelliJ Plugin Verifier integration**
  - Automated compatibility testing across multiple IDE versions
  - Prevents API compatibility issues before release
  - Can be run locally with `./gradlew verifyPlugin`

### Fixed
- **Fixed PluginException in IntelliJ IDEA 2025.2.4** (Issue #33)
  - Added explicit `shortName` attribute to inspection registration in plugin.xml
  - Resolved "Short name not matched" error that occurred due to stricter validation in newer IDE versions
- Fixed double-click navigation to source code in tool window
- Fixed typealias detection for Composable function types (Issue #16)
  - Typealiases like `typealias SettingsButtons = @Composable (PlayerUiState) -> Unit` now correctly expand to their underlying function types before stability analysis
- Fixed ImmutableList/ImmutableSet/ImmutableMap showing as unstable in test code (Issue #21)
  - Added fallback type resolution by simple name for immutable collections when FQN resolution fails in test source sets
- Improved cross-module stability detection by reading @StabilityInferred(parameters) annotation (Issue #18)
  - Classes from other modules now correctly marked as UNSTABLE unless annotated with @Stable/@Immutable or @StabilityInferred(parameters=0)
- Extended plugin compatibility range to support IntelliJ IDEA 2025.3 (build 253)

## [0.4.2] - 2025-11-03

### Fixed
- Fixed @Parcelize data classes with stable properties now correctly identified as STABLE (Issue #3)
- Fixed StackOverflowError when analyzing recursive or self-referential types (Issue #11)
- Fixed Compose shape types stability analysis (RoundedCornerShape, CircleShape, etc.)
- Improved @Parcelize analysis to ignore Parcelable interface's runtime stability

### Improved
- Enhanced cycle detection for recursive type analysis
- Better handling of complex function type aliases and deeply nested generics
- Consistent stability analysis behavior with compiler plugin

## [0.4.1] - 2025-11-02

### Fixed
- Fixed stability analysis for Compose shape types (RoundedCornerShape, CircleShape, etc.) to correctly show as STABLE instead of RUNTIME
- Fixed StackOverflowError when analyzing recursive or self-referential types (Issue #11)
- Fixed false positive warnings for @Parcelize data classes (Issue #3)
- Improved consistency between IDEA plugin and compiler plugin stability inference
- Added Compose Foundation shapes to known stable types list
- Added cycle detection to prevent infinite recursion in type stability analysis

### Improved
- Enhanced accuracy of stability analysis to match compiler plugin behavior
- Better handling of complex function type aliases and deeply nested generic types
- @Parcelize-annotated classes with stable properties are now correctly identified as STABLE

## [0.4.0] - 2025-11-02

### Added
- ProGuard consumer rules for automatic R8/ProGuard compatibility in Android projects
- Comprehensive compiler-tests module with FIR/IR dump testing infrastructure
- Enhanced documentation with stability validation guide for CI/CD integration
- Support for Gradle plugin's `includeTests` and `ignoredProjects` configurations

### Improved
- Enhanced @TraceRecomposition visualization with better gutter icons
- Improved stability analysis for complex generic types and nested data classes
- Better handling of composable function analysis with generics

### Fixed
- Fixed stability inference for nested data classes
- Improved analysis accuracy for edge cases

## [0.3.0] - 2025-10-28

### Added
- **@IgnoreStabilityReport annotation** - Exclude specific composables from stability reports
- Runtime and Gradle module unit tests for improved reliability
- Stability validation workflow for CI/CD integration (stabilityDump and stabilityCheck tasks)
- Enhanced IDE quick fixes for adding @TraceRecomposition annotation

### Improved
- Enhanced compiler plugin with better error messages and diagnostics
- Improved RecompositionTracker performance and memory efficiency
- Better type analysis for composable functions

### Fixed
- Fixed analysis of @Composable functions with default parameters
- Improved stability detection for complex parameter types

## [0.2.3] - 2025-10-23

### Changed
- Version bump to align with compiler plugin version
- Code formatting improvements across the codebase

### Fixed
- Fixed compiler test compatibility issues with Kotlin 2.2.21

## [0.2.2] - 2025-10-20

### Changed
- Unified maven publishing configuration
- Updated build configuration to match landscapist project structure
- Improved project consistency and maintainability

## [0.2.1] - 2025-10-15

### Fixed
- Fixed K2 API compatibility for Android Studio AI-243 and older IDE versions
- Improved graceful fallback to PSI analyzer when K2 Analysis API is unavailable
- Better error handling for unsupported IDE versions

## [0.2.0] - 2025-10-10

### Added
- **K2 Analysis API support** - 2-3x faster and more accurate stability analysis
- Enhanced @Preview detection to support meta-annotations (custom preview annotations)
- Support for IntelliJ IDEA 2025.2

### Improved
- Improved type parameter and superclass stability analysis
- Better detection of @Composable function types for all lambda variations
- Enhanced performance for large projects

### Changed
- Migrated from PSI-based analysis to K2 Analysis API (with automatic fallback)

## [0.1.0] - 2025-10-01

### Added
- **Initial release** of Compose Stability Analyzer IntelliJ Plugin
- **Hover documentation** - View detailed stability information for composable functions
- **Gutter icons** - Visual indicators showing stability status (skippable/not skippable)
- **Inline hints** - Parameter-level stability annotations in the editor
- **Code annotations and highlighting** - Unstable parameters highlighted with warnings
- **Code inspections** - Automatic detection of unstable composables
- **Quick fixes** - Add @TraceRecomposition annotation with one click

### Fixed
- Fixed nullable type stability analysis
- Fixed interface type detection
- Improved stability inference accuracy

## Legend

- **Added** - New features
- **Changed** - Changes in existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Improved** - Enhancements to existing features
- **Security** - Security-related changes

## Links

- [Plugin Repository](https://github.com/skydoves/compose-stability-analyzer)
- [Issue Tracker](https://github.com/skydoves/compose-stability-analyzer/issues)
- [Documentation](https://github.com/skydoves/compose-stability-analyzer/blob/main/README.md)