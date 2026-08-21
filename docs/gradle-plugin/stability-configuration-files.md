# Stability configuration files

You can provide stability configuration files to tell compiler plugin which types should be treated as stable, even if the compiler marks them as unstable. This uses the same format as the [Compose compiler's stability configuration file](https://developer.android.com/develop/ui/compose/performance/stability/fix#configuration-file), so you can reuse the same file for both the compiler and stability validation.

```kotlin
composeStabilityAnalyzer {
    stabilityConfigurationFiles.add(
        isolated.rootProject.projectDirectory.file("stability_config.conf")
    )
}
```

`isolated.rootProject` exposes only the data that is safe to read across project boundaries, so a file shared from the root of the build stays resolvable with [Isolated Projects](getting-started.md#configuration-cache-and-isolated-projects) enabled. `rootProject.layout.projectDirectory` reads another project's mutable state and fails with *"Project ':app' cannot access 'Project.layout' functionality on another project ':'"*. On Gradle 8.13 and later, `layout.settingsDirectory` works too. A file inside the module itself needs neither: use plain `layout.projectDirectory`.

The configuration file contains fully-qualified type names, one per line. Lines starting with `//` are treated as comments. Wildcard patterns are supported: `*` matches a single package segment and `**` matches across package boundaries.

```
// stability_config.conf
com.google.firebase.auth.FirebaseUser
com.example.generated.*
com.example.models.**
```

When these files are configured, the compiler plugin treats any parameter type matching these patterns as stable.

This is particularly useful when your project already uses a stability configuration file for the Compose compiler. By pointing `stabilityConfigurationFiles` to the same file, the stability validation respects the same overrides, keeping the two in sync.
