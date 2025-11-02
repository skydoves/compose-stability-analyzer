# Compose Stability Analyzer IntelliJ Plugin - Changelog

All notable changes to the IntelliJ IDEA plugin will be documented in this file.

## [0.4.1] - 2025-11-02

### Fixed
- Fixed stability analysis for Compose shape types (RoundedCornerShape, CircleShape, etc.) to correctly show as STABLE instead of RUNTIME
- Improved consistency between IDEA plugin and compiler plugin stability inference
- Added Compose Foundation shapes to known stable types list

### Improved
- Enhanced accuracy of stability analysis to match compiler plugin behavior

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
