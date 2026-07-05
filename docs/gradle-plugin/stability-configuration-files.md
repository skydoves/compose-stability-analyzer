# Stability configuration files

You can provide stability configuration files to tell compiler plugin which types should be treated as stable, even if the compiler marks them as unstable. This uses the same format as the [Compose compiler's stability configuration file](https://developer.android.com/develop/ui/compose/performance/stability/fix#configuration-file), so you can reuse the same file for both the compiler and stability validation.

```kotlin
composeStabilityAnalyzer {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("stability_config.conf")
    )
}
```

The configuration file contains fully-qualified type names, one per line. Lines starting with `//` are treated as comments. Wildcard patterns are supported: `*` matches a single package segment and `**` matches across package boundaries.

```
// stability_config.conf
com.google.firebase.auth.FirebaseUser
com.example.generated.*
com.example.models.**
```

When these files are configured, the compiler plugin treats any parameter type matching these patterns as stable.

This is particularly useful when your project already uses a stability configuration file for the Compose compiler. By pointing `stabilityConfigurationFiles` to the same file, the stability validation respects the same overrides, keeping the two in sync.
