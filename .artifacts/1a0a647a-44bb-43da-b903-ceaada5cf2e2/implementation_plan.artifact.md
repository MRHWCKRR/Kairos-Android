# Implementation Plan - Phase 4: Logo Integration & Google Sign-In

I will replace the text-based logo with the PNG provided and implement the Google Sign-In feature to match the web app's functionality.

## User Review Required

> [!IMPORTANT]
> **Logo Image**: Please save the logo PNG you provided as `app/src/main/res/drawable/ic_kairos_logo.png` in your project directory. I cannot "save" the image from the chat directly into your source code.
>
> **Google Sign-In**: To make Google Sign-In work, you will need to:
> 1. Add your Android app's **SHA-1 fingerprint** to your Firebase project settings in the Firebase Console.
> 2. Download the updated `google-services.json` and replace the existing one in the `app` folder.

## Proposed Changes

### 1. Project Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Dev/Kairos-Android/gradle/libs.versions.toml)
- Add `play-services-auth` library definition.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Dev/Kairos-Android/app/build.gradle.kts)
- Add `play-services-auth` dependency.

### 2. Data Layer (Auth)

#### [MODIFY] [AuthRepository.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/data/repository/AuthRepository.kt)
- Add `signInWithGoogle(idToken)` method using `GoogleAuthProvider.getCredential`.

### 3. UI Layer (Login)

#### [MODIFY] [LoginViewModel.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/screens/login/LoginViewModel.kt)
- Add `onGoogleSignInResult(idToken)` logic.
- Handle state for Google Sign-In process.

#### [MODIFY] [LoginScreen.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/screens/login/LoginScreen.kt)
- **Logo**: Replace the `//` Text with an `Image` component using `painterResource(id = R.drawable.ic_kairos_logo)`.
- **Google Button**: Implement the actual Google Sign-In launcher using `rememberLauncherForActivityResult`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify compilation with new dependencies.

### Manual Verification
- Verify the logo displays correctly on the Login screen.
- Test the Google Sign-In button (requires SHA-1 configuration in Firebase).
