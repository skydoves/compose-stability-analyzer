# Compose Stability Analyzer IntelliJ Plugin - Changelog

All notable changes to the IntelliJ IDEA plugin will be documented in this file.

## [Unreleased]

---

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

### Fixed
- **Kotlin 2.3.0-RC compatibility** (Issue #59)
  - Fixed `AbstractMethodError` for `getPluginId()` when using Kotlin 2.3.0-RC
  - Added `pluginId` property to `StabilityAnalyzerPluginRegistrar` for forward compatibility
  - Plugin now works with both Kotlin 2.2.x and upcoming Kotlin 2.3.x

### Improved
- Enhanced tooltip information for runtime parameters
  - Parameter breakdown now shows stable, runtime, and unstable counts separately
  - Lists runtime parameters with clear explanation of runtime stability behavior
  - Explains that skippability may change between library versions or when implementations change

---

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

---

## [0.5.2] - 2025-11-13

### Fixed
- **Fixed APK size increase in release builds** (Issue #39)
  - ProGuard rules were keeping entire stability-runtime package unnecessarily
  - Optimized consumer-rules.pro to only keep classes used by compiler-injected code
  - Now only keeps `RecompositionTracker` methods (constructor, trackParameter, logIfThresholdMet)
  - Logger classes only kept if explicitly used via `ComposeStabilityAnalyzer.setLogger()`
  - Compile-time classes (`StabilityInfo`, `ComposableInfo`, `ParameterInfo`) now removed by R8
  - This fix dramatically reduces release APK size when using the plugin

---

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

---

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

---

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

---

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

---

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

---

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

---

## [0.2.3] - 2025-10-23

### Changed
- Version bump to align with compiler plugin version
- Code formatting improvements across the codebase

### Fixed
- Fixed compiler test compatibility issues with Kotlin 2.2.21

---

## [0.2.2] - 2025-10-20

### Changed
- Unified maven publishing configuration
- Updated build configuration to match landscapist project structure
- Improved project consistency and maintainability

---

## [0.2.1] - 2025-10-15

### Fixed
- Fixed K2 API compatibility for Android Studio AI-243 and older IDE versions
- Improved graceful fallback to PSI analyzer when K2 Analysis API is unavailable
- Better error handling for unsupported IDE versions

---

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

---

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

---

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
