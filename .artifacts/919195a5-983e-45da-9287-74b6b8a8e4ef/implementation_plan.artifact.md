# Fix Build Error: Plugin Alias Type Mismatch

The project is failing to build because `libs.plugins.android.application` and `libs.plugins.kotlin.android` are being referenced in `build.gradle.kts` but are missing from the `[plugins]` section in `gradle/libs.versions.toml`. This causes a type mismatch error when used with the `alias()` function.

## Proposed Changes

### [Component Name]

#### [MODIFY] [libs.versions.toml](file:///C:/Users/koolk/OneDrive%20-%20Department%20of%20Education/Tech/VS%20Code/Kairos-Android/gradle/libs.versions.toml)

- Add `agp` and `kotlin` versions.
- Add `android-application` and `kotlin-android` plugin definitions to the `[plugins]` section.

## Verification Plan

### Automated Tests
- Run `./gradlew help` to verify that the build configuration is now valid.
