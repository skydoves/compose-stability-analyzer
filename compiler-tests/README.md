# compiler-tests

Compiler tests using JetBrains' official Kotlin compiler testing infrastructure.

## Test Categories

### 1. Diagnostic Tests

Create `.kt` files under `src/commonTest/data/diagnostic/` to test compiler diagnostics (errors, warnings).

- Fast (frontend-only execution)
- Diagnostics are injected as comments in the test file
- Good for testing FIR analysis and error reporting

**Example:**
```kotlin
// FILE: unstable.kt
import androidx.compose.runtime.Composable

data class MutableUser(var name: String)

@Composable
fun UserCard(user: MutableUser) {
    // Plugin should detect unstable parameter
}
```

### 2. Box Tests

Create `.kt` files under `src/commonTest/data/box/` to test end-to-end compilation and execution.

- Full compiler execution (FIR + IR + codegen)
- Must have a `box()` function that returns `"OK"` on success
- Can use `kotlin.test.assert*` functions
- Good for testing IR transformations and runtime behavior

**Example:**
```kotlin
// FILE: stable.kt
import androidx.compose.runtime.Composable

data class StableUser(val name: String)

@Composable
fun UserCard(user: StableUser) {
    // Plugin should detect stable parameter
}

fun box(): String {
    // Test that the plugin correctly identifies stability
    return "OK"
}
```

## Writing Tests

### 1. Create Test File

Add a `.kt` file to the appropriate directory:
- `src/commonTest/data/diagnostic/` for diagnostic tests
- `src/commonTest/data/box/` for box tests

### 2. Generate Test Classes

Test classes are generated automatically into `build/test-gen/` (under the target directory) when the tests are compiled. To regenerate them without running the tests:

```bash
./gradlew :compiler-tests:generateTests
```

### 3. Run Tests

```bash
./gradlew :compiler-tests:allTests
```

## Test Directives

Use directives as line comments to control test behavior:

```kotlin
// RENDER_DIAGNOSTICS_FULL_TEXT
// FILE: main.kt
```

Common directives:
- `FILE: <name>` - Define a separate file
- `RENDER_DIAGNOSTICS_FULL_TEXT` - Show full diagnostic messages
- `FIR_DUMP` - Dump the entire FIR tree
- `DIAGNOSTICS: -<diagnostic_name>` - Disable specific diagnostics

See [Kotlin compiler test directives](https://github.com/JetBrains/kotlin/tree/master/compiler/test-infrastructure#directives) for more options.

## Plugin Configuration

Tests can enable plugin options using the custom directives defined in `StabilityTestDirectives`:

- `ENABLE_TRACE_ALL` - Enable trace-all auto-instrumentation (`composeStabilityAnalyzer.traceAll`) for this test
- `STABILITY_CONFIGURATION_FILES: <files>` - Point the test at stability configuration file(s); paths are resolved relative to the test data file's directory

## Recommended Tools

The [Kotlin Compiler DevKit](https://github.com/JetBrains/kotlin-compiler-devkit) IDE plugin is declared as a project dependency (`.idea/externalDependencies.xml`), so IntelliJ suggests installing it automatically when the project is opened. It provides test data navigation and debugging support.

## Examples

See existing test files in:
- `src/commonTest/data/diagnostic/` - Diagnostic test examples
- `src/commonTest/data/box/` - Box test examples
