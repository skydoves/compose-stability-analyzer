# Compose Stability Analyzer - Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.7.0] - 2026-02-13

### Added
- **Recomposition Cascade Visualizer** (PR #119)
  - Right-click any `@Composable` function and select "Analyze Recomposition Cascade" to trace downstream composables affected by recomposition
  - Tree view showing each downstream composable with stability status (skippable vs. non-skippable)
  - Summary statistics: total downstream count, skippable/unskippable counts, and max depth
  - Cycle detection and configurable depth limits (max 10) prevent infinite analysis
  - Double-click any node to navigate directly to its source code
  - Available via editor right-click context menu
  - New "Cascade" tab in the Compose Stability Analyzer tool window
- **Live Recomposition Heatmap** (PR #120, #121)
  - Real-time recomposition counts from a connected device overlaid directly above composable functions in the editor
  - Reads `@TraceRecomposition` events from ADB logcat and aggregates per-composable data
  - Color-coded severity: green (< 10 recompositions), yellow (10-50), red (50+)
  - Click any recomposition count to open the Heatmap tab with detailed event logs and parameter change history
  - Start/Stop toggle button in the tool window title bar and Code menu
  - Multi-device support with device picker when multiple ADB devices are connected
  - Flicker-free rendering using deterministic pre-baked inlay renderers
  - Heatmap enabled by default in plugin settings
  - Configurable severity thresholds in Settings > Tools > Compose Stability Analyzer
  - New "Heatmap" tab in the Compose Stability Analyzer tool window
### Improved
- Tool window now has three tabs: Explorer, Cascade, and Heatmap
- Start/Stop Recomposition Heatmap button moved to tool window title bar for visibility across all tabs
- K2-safe reference resolution using `runCatching` pattern in cascade analyzer
- Cancellation support in cascade background analysis via `ProgressIndicator.checkCanceled()`

## [0.6.7] - 2026-02-10

### Added
- **Android variant-specific stability tasks** (Issue #85, PR #101)
  - Gradle plugin now creates per-variant tasks (e.g., `debugStabilityDump`, `releaseStabilityCheck`) for Android projects
  - Allows running stability analysis on a single variant without compiling others
  - Aggregate tasks (`stabilityDump`, `stabilityCheck`) still available for all variants
  - Improved build cache compatibility for Kotlin compile output registration
- **Non-regressive change filtering for stability validation** (Issue #82, PR #104)
  - New `ignoreNonRegressiveChanges` option: only flag stability regressions, ignore non-regressive changes (e.g., new stable parameters)
  - New `allowMissingBaseline` option: allow stability checks to run even without an existing baseline file
  - With both flags enabled, the Gradle plugin reports all unstable composables found in the module
- **Stability configuration file wildcard support** (Issue #108, PR #110)
  - Implemented `stabilityPatternToRegex` to support `*` and `**` wildcard syntax in stability configuration files
  - Matches the format used by the official Compose compiler stability configuration
  - Example: `com.datalayer.*`, `com.example.**`

### Fixed
- **`@StabilityInferred` annotation now supported in Gradle plugin** (Issue #102, PR #112)
  - Immutable classes from other modules annotated with `@StabilityInferred(parameters=0)` are now correctly treated as stable during `stabilityDump`/`stabilityCheck`
  - Previously, cross-module classes with `@StabilityInferred` were incorrectly marked as UNSTABLE
  - Aligns Gradle plugin behavior with the IDEA plugin, which already handled this correctly
- **Skip analysis for `@NonRestartableComposable` and `@NonSkippableComposable`** (Issue #103, PR #111)
  - Composable functions annotated with `@NonRestartableComposable` or `@NonSkippableComposable` are now excluded from stability analysis
  - These functions are not subject to recomposition skipping, so stability analysis is not applicable
- **Improved typealias handling in IDEA plugin** (Issue #16, PR #106)
  - Parameters using a typealias to a function type (e.g., `typealias ComposableAction = @Composable () -> Unit`) are now correctly recognized as stable
  - Added typealias expansion support across PSI fallback, K1, and K2 analysis paths
  - Includes circular alias recursion guard to prevent infinite loops

### Improved
- **Replaced internal `nj2k.descendantsOfType` with stable `PsiTreeUtil` API** (PR #109)
  - Implemented intelligent caching mechanism for typealias resolution with automatic expiration
  - Streamlined function-type detection and composability checking logic
  - Improved IDE responsiveness during analysis

## [0.6.6] - 2025-12-24

### Fixed
- **Fixed stabilityDump task incorrectly marked as UP-TO-DATE**
  - Task now properly tracks the `stability-info.json` input file for up-to-date checks
  - Changed from `@Internal` to `@InputFiles` annotation on input file property
  - Ensures stability files are regenerated when compiler output changes
  - Fixes issue where running `./gradlew stabilityDump` would skip execution even when stability files were missing
  - Task now correctly runs after `clean` or when stability output is deleted

## [0.6.5] - 2025-12-17

### Added
- **Quiet mode for stability validation** (Issue #83)
  - New `quietCheck: Boolean = false` option in `stabilityValidation` configuration
  - Suppresses "✅ Stability check passed." messages for modules that pass checks
  - Reduces log noise in multi-module projects where many modules pass validation
  - Errors and warnings still shown normally
  - Example: `stabilityValidation { quietCheck.set(true) }`

### Changed
- **Upgraded to Kotlin 2.3.0**

## [0.6.4] - 2025-12-16

### Fixed
- **Fixed "Wrong plugin option format: null" compilation error** (Issue #87)
  - Changed cross-module detection to use file-based approach instead of string-based SubpluginOption
  - Project dependencies now written to `build/stability/project-dependencies.txt` (one package per line)
  - Compiler plugin reads dependencies from file instead of parsing comma-separated string
  - Resolves build failures in multi-module projects introduced in 0.6.3
  - Users experiencing compilation errors with 0.6.3 should upgrade to 0.6.4

### Improved
- More robust cross-module dependency passing mechanism
- Better handling of empty dependency lists
- Follows common patterns used by other Kotlin compiler plugins

## [0.6.3] - 2025-12-13

### Added
- **Cross-module stability detection** - Classes from other Gradle modules now require explicit stability annotations
  - Compiler plugin: Detects cross-module types via IR origins and package matching
  - Gradle plugin: Automatically collects all subproject packages for cross-module detection
  - IDE plugin: Uses IntelliJ module system to identify cross-module boundaries
  - Classes from different modules marked as UNSTABLE unless annotated with @Stable/@Immutable/@StabilityInferred
  - Prevents accidentally assuming stability for types where implementation details aren't visible
  - Provides consistent behavior across compiler plugin, IDE plugin, and stability validation

### Fixed
- **Fixed Gradle compatibility issues**
  - Removed deprecated `getDependencyProject()` usage for broader Gradle version compatibility
  - Implemented portable dependency collection that works across all Gradle versions
- **Fixed compiler tests compatibility**
  - Updated StabilityTestConfigurator to pass new projectDependencies parameter
  - All compiler tests now passing with cross-module detection enabled
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names navigates to correct source location

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters displayed as stable instead of hidden
  - Composable skippability recalculated based on processed parameters
  - Better visibility of composable signatures while respecting ignore patterns
- **Compacted code comments** for better readability across all cross-module detection implementations

## [0.6.2] - 2025-12-10

### Fixed
- **Fixed property source file location and navigation in tool window** (Issue #67)
  - Tool window now correctly identifies source file for composable properties
  - Properties no longer show "Unknown.kt" as file name
  - Double-clicking on property names navigates to correct source location
  - Extended source location search to include `KtProperty` declarations

### Improved
- **Enhanced tool window handling of ignored type patterns** (Issue #74)
  - Ignored parameters now displayed as stable instead of being hidden completely
  - Composable skippability is recalculated based on processed parameters
  - Provides better visibility of composable signatures while respecting ignore patterns

## [0.6.1] - 2025-12-06

### Added
- Settings icon in IDE plugin tool window toolbar for quick access to configuration
- Support for ignored type patterns in tool window

### Fixed
- Tool window now respects ignored type patterns (Issue #74)
- WASM build failures with Gradle task dependencies (Issue #70)
- Property name display showing as `<get-propertyName>` (Issue #67)

### Improved
- Updated tool window icon to monochrome style
- Updated dependencies (Android Lint, Nexus Plugin, AGP, Compose BOM)

## [0.6.0] - 2025-11-24

### Added
- Per-project stability configuration file support (Issue #60)
- Runtime gutter icon for runtime-only composables
- Generic type argument inference at compile time

### Improved
- Enhanced tooltip information for runtime parameters
- Better visual distinction between unstable and runtime stability

## [0.5.3] - 2025-11-18

### Fixed
- iOS native compilation with kotlinx.serialization (Issue #48)
- Gradle Configuration Cache compatibility (Issue #41)

## [0.5.2] - 2025-11-13

### Fixed
- APK size increase in release builds (Issue #39)
- Optimized ProGuard rules to reduce APK size

## [0.5.1] - 2025-11-10

### Added
- wasmJs target support for Kotlin Multiplatform

### Fixed
- Sealed class stability inheritance (Issue #31)

## [0.5.0] - 2025-11-08

### Breaking Changes
- Minimum IDE version updated to IntelliJ IDEA 2024.2+ (build 242+)

### Added
- New Compose Stability Tool Window (Issue #14)
- Interactive empty state guide
- Show in test source sets setting (Issue #21)
- @StabilityInferred annotation parameter support (Issue #18)

### Improved
- Enhanced UI/UX for Tool Window
- Performance optimization using pre-computed JSON files
- Added IntelliJ Plugin Verifier integration

### Fixed
- PluginException in IntelliJ IDEA 2025.2.4 (Issue #33)
- Typealias detection for Composable function types (Issue #16)
- ImmutableList/Set/Map detection in test code (Issue #21)

## [0.4.2] - 2025-11-03

### Fixed
- @Parcelize data classes stability detection (Issue #3)
- StackOverflowError with recursive types (Issue #11)
- Compose shape types stability analysis

## [0.4.1] - 2025-11-02

### Fixed
- Stability analysis for Compose shape types
- StackOverflowError with recursive types
- False positive warnings for @Parcelize classes

## [0.4.0] - 2025-11-02

### Added
- ProGuard consumer rules for R8/ProGuard compatibility
- Comprehensive compiler-tests module
- Enhanced documentation for stability validation

### Improved
- @TraceRecomposition visualization
- Stability analysis for complex generics

## [0.3.0] - 2025-10-28

### Added
- @IgnoreStabilityReport annotation
- Runtime and Gradle module unit tests
- Stability validation workflow (stabilityDump and stabilityCheck tasks)
- IDE quick fixes for @TraceRecomposition

## [0.2.3] - 2025-10-23

### Fixed
- Compiler test compatibility with Kotlin 2.2.21

## [0.2.2] - 2025-10-20

### Changed
- Unified maven publishing configuration

## [0.2.1] - 2025-10-15

### Fixed
- K2 API compatibility for Android Studio AI-243
- Graceful fallback to PSI analyzer

## [0.2.0] - 2025-10-10

### Added
- K2 Analysis API support
- Enhanced @Preview detection
- IntelliJ IDEA 2025.2 support

## [0.1.0] - 2025-10-01

### Added
- Initial release
- Hover documentation
- Gutter icons
- Inline hints
- Code inspections and quick fixes

## Legend

- **Added** - New features
- **Changed** - Changes in existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Improved** - Enhancements to existing features
- **Security** - Security-related changes
- **Breaking Changes** - Breaking changes requiring migration

## Links

- [GitHub Repository](https://github.com/skydoves/compose-stability-analyzer)
- [Issue Tracker](https://github.com/skydoves/compose-stability-analyzer/issues)
- [Documentation](https://github.com/skydoves/compose-stability-analyzer/blob/main/README.md)
- [IDE Plugin Changelog](compose-stability-analyzer-idea/CHANGELOG.md)
