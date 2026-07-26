# Fix `:app:processDebugGoogleServices` Build Error

The error `:app:processDebugGoogleServices` occurs because the Google Services Gradle plugin cannot find the `google-services.json` file in the expected location.

## Current Situation
- `google-services.json` is located in the project root directory: `C:/Users/koolk/OneDrive - Department of Education/Tech/VS Code/Kairos-Android/google-services.json`.
- The Google Services plugin is applied in `app/build.gradle.kts`.
- The `applicationId` in `app/build.gradle.kts` is `com.kairos.app`, which matches the `package_name` in the JSON file.

## Proposed Changes

### Build Configuration
The `google-services.json` file must be placed in the **module** directory (the `app` folder) for the plugin to process it correctly.

#### [MOVE] [google-services.json](file:///C:/Users/koolk/OneDrive - Department of Education/Tech/VS Code/Kairos-Android/google-services.json) to `app/google-services.json`

## Verification Plan

### Automated Tests
- Run `./gradlew :app:processDebugGoogleServices` to verify the task now succeeds.
- Run a full build: `./gradlew assembleDebug`.

### Manual Verification
- Sync the project with Gradle files in Android Studio.
