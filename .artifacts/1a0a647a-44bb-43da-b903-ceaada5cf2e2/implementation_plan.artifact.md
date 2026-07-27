# Implementation Plan - Phase 2: Authentication (Login/Sign-up)

I will implement the authentication flow for the Android app, matching the web app's capability to sign in via email and password. This will allow the app to fetch the correct user data from Firestore.

## User Review Required

> [!NOTE]
> I will be implementing a simple, clean Login UI. For now, I'll focus on **Email/Password** authentication. Google Sign-In requires additional configuration (SHA-1 keys in Firebase Console) which I recommend we tackle in a follow-up step.

## Proposed Changes

### 1. Data Layer (Repository)

#### [MODIFY] [AuthRepository.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/data/repository/AuthRepository.kt)
- Add `signIn(email, password)` method using `auth.signInWithEmailAndPassword`.
- Add `signUp(email, password)` method using `auth.createUserWithEmailAndPassword`.

### 2. UI Layer (ViewModels & Screens)

#### [NEW] [LoginViewModel.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/screens/login/LoginViewModel.kt)
- Manage email/password input states.
- Handle loading and error states during authentication.
- Provide `login()` and `register()` actions.

#### [NEW] [LoginScreen.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/screens/login/LoginScreen.kt)
- Implement a modern Compose UI for signing in and switching to "Create Account".
- Use `TextField` for inputs and a primary action button.

### 3. App Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/navigation/MainActivity.kt)
- Observe the `user` state from `MainViewModel`.
- **Conditional Layout**:
    - If `user == null`: Render the `LoginScreen`.
    - If `user != null`: Render the main `KairosApp` content (Scaffold + NavHost).
- This ensures the user is forced to authenticate before seeing their routine data.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure compilation.

### Manual Verification
- Launch the app and verify the Login screen appears.
- Test signing in with your existing web credentials.
- Verify that once logged in, the app automatically switches to the Dashboard.
