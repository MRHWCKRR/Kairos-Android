# Implementation Plan - Premium & Interactive Widgets

The initial widget implementation was static and minimal. I will now transform them into interactive, live-syncing tools with a premium "Glassmorphism" look that matches the flagship Kairos aesthetic.

## Proposed Changes

### 1. Infrastructure (Live Sync)

#### [MODIFY] [libs.versions.toml](file:///C:/Dev/Kairos-Android/gradle/libs.versions.toml) & [build.gradle.kts](file:///C:/Dev/Kairos-Android/app/build.gradle.kts)
- Add `androidx.datastore:datastore-preferences` to share state between the app and Widgets.
- Add `androidx.glance:glance-material3` for better UI components in widgets.

#### [NEW] [WidgetSyncWorker.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/utils/WidgetSyncWorker.kt)
- Create a utility to push app state (Focus Timer status, Top Tasks) into the DataStore so Widgets can react to changes instantly.

### 2. Focus Widget Overhaul (Interactive)

#### [MODIFY] [FocusWidget.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/widgets/FocusWidget.kt)
- **Live Timer**: Display the actual current focus time.
- **Action Button**: Implement `ActionCallback` to start/stop the timer directly from the home screen.
- **Premium UI**:
    - Semi-translucent background with rounded corners.
    - Progress ring showing daily focus goal progress.
    - Vibrantly styled buttons.

### 3. Tasks Widget Overhaul (Dynamic)

#### [MODIFY] [TasksWidget.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/widgets/TasksWidget.kt)
- **Real Data**: Fetch the top 3 uncompleted tasks from your actual routines.
- **Quick Check**: Add clickable checkboxes that mark tasks as complete in Firestore immediately.
- **Empty State**: A beautiful "All Caught Up" message if no tasks are pending.

### 4. ViewModel Connection

#### [MODIFY] [MainViewModel.kt](file:///C:/Dev/Kairos-Android/app/src/main/java/com/kairos/app/ui/navigation/MainViewModel.kt)
- Trigger a widget sync whenever a task is toggled or the timer starts/stops.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug`.
- Verify DataStore writes don't block the UI thread.

### Manual Verification
1.  **Interaction**: Tap "Start" on the Focus Widget. Open the app and verify the timer is running.
2.  **Task Sync**: Check a task on the Tasks Widget. Verify it is marked complete inside the app.
3.  **Visuals**: Verify the widgets look "Premium" (rounded corners, correct colors) and update within seconds of app changes.
