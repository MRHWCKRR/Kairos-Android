# Implementation Plan - Fix Runtime Crash (Immediate Shutdown)

The app builds and installs but crashes immediately upon opening. This is likely due to a package name mismatch in `MainActivity.kt`.

## Analysis

- **Manifest**: Specifies the activity as `.ui.navigation.MainActivity`. Given the namespace `com.kairos.app`, this resolves to `com.kairos.app.ui.navigation.MainActivity`.
- **File Location**: `C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/navigation/MainActivity.kt`.
- **Code Content**: The file currently declares `package com.kairos.app`.
- **Result**: The Android system cannot find the class `com.kairos.app.ui.navigation.MainActivity` because the class actually compiled as `com.kairos.app.MainActivity`. This causes a `ClassNotFoundException` at runtime.

## Proposed Changes

### UI Navigation

#### [MODIFY] [MainActivity.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/navigation/MainActivity.kt)
- Update package declaration from `package com.kairos.app` to `package com.kairos.app.ui.navigation`.
- Remove the redundant import: `import com.kairos.app.ui.navigation.KairosDestination` (since it will now be in the same package).
- Ensure all other imports (like `KairosTheme` and screens) are still correct.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure it still compiles.

### Manual Verification
- Ask the user to run the app again and verify it stays open.
- (Self-Correction/Note): Since I cannot directly see the crash log without `adb` in path, fixing this obvious manifest mismatch is the priority.
