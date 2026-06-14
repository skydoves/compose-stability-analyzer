# Contributing to Compose Stability Analyzer

Thank you for your interest in contributing! This project welcomes bug reports, feature requests, documentation improvements, and pull requests.

## Reporting Issues

- Use the [issue templates](https://github.com/skydoves/compose-stability-analyzer/issues/new/choose) for bug reports and feature requests.
- For IDE plugin freezes or performance problems, please attach the Android Studio thread dumps (**Help → Collect Logs and Diagnostic Data**) — they make issues diagnosable in one pass.
- For Gradle plugin issues, include your Kotlin/AGP versions and the relevant `composeStabilityAnalyzer { ... }` configuration.

## Development Setup

The project requires **JDK 17+** and builds with the bundled Gradle wrapper. Kotlin and AGP versions are pinned in `gradle/libs.versions.toml`.

```bash
# Build everything and publish to Maven Local (required before building the sample app)
./gradlew spotlessApply publishToMavenLocal :app:assembleDebug -x test -PRELEASE_SIGNING_ENABLED=false
```

The IntelliJ/Android Studio plugin is a separate IntelliJ Platform build:

```bash
./gradlew :stability-runtime:publishToMavenLocal -x test -PRELEASE_SIGNING_ENABLED=false
cd compose-stability-analyzer-idea && ../gradlew buildPlugin
```

The sample app resolves the Gradle plugin from Maven Local (it shadows Maven Central for the same version), so always publish locally before building `:app`.

## Module Overview

| Module | What it is |
|--------|-----------|
| `stability-compiler` | K2 compiler plugin (FIR checks + IR instrumentation) |
| `stability-runtime` | KMP runtime emitting recomposition logs |
| `stability-gradle` | Gradle plugin (`composeStabilityAnalyzer {}` DSL, `stabilityDump`/`stabilityCheck`) |
| `stability-lint` | Android Lint rules, packaged into the runtime AAR |
| `compose-stability-analyzer-idea` | IntelliJ/Android Studio plugin (separate build) |
| `compiler-tests` | Kotlin compiler test framework suite |
| `app`, `app-model` | Sample app for end-to-end verification |

## Before Opening a Pull Request

1. **Format**: run `./gradlew spotlessApply` — CI fails on formatting violations, and Spotless also applies the license header to new files.
2. **API dumps**: if you changed any public API in a published module, run `./gradlew apiDump` and commit the updated `api/*.api` files.
3. **Tests**: run the suites relevant to your change:
   - `./gradlew :stability-runtime:jvmTest :stability-gradle:test :stability-compiler:test :compiler-tests:test`
   - IDE plugin: `cd compose-stability-analyzer-idea && ../gradlew test`
4. **Compiler golden data**: if your change affects generated IR, regenerate snapshots with `./gradlew :compiler-tests:test -Pupdate.test.data` (the regenerating run reports failures while writing; the next run passes). Never hand-edit `.txt` snapshots or the generated JUnit classes under `compiler-tests/src/test/java/`.

## Things to Know Before Changing…

- **The logcat output format** (`stability-runtime` loggers): it is a wire protocol parsed by the IDE plugin's `LogcatParser`. Header lines may only gain *trailing* optional tokens; changing or reordering existing tokens breaks older IDE/runtime combinations.
- **Stability inference** (`stability-compiler` / IDE plugin): the compiler's and the IDE's inference must stay in sync, including the known-stable type lists.
- **AGP types in `stability-gradle`**: AGP is `compileOnly` and its types may only be referenced inside `AndroidStabilityTaskRegistrar`, so projects without AGP keep working.
- **Versions**: `gradle.properties` `VERSION_NAME`, the `VERSION` constant in `StabilityAnalyzerGradlePlugin`, `gradle/libs.versions.toml`, and the IDE plugin's version/runtime dependency must move together.

## Code Reviews

All submissions, including submissions by project members, require review. We use GitHub pull requests for this purpose. Consult [GitHub Help](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests) for more information on using pull requests.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
