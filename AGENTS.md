# Kairos Android Agent Guide

## Product role
Kairos Android is the native Android client for Kairos. It should feel like a polished Android application, not a web app wrapped in native chrome and not a copy of the iOS UI.

## Architecture
- Keep the app native Android.
- Preserve the existing Kotlin / Jetpack Compose architecture.
- Reuse the existing `data`, `ui`, and `utils` separation.
- Within `ui`, reuse existing `components`, `navigation`, `screens`, `theme`, and `widgets` packages before introducing parallel structures.
- Do not migrate the project to Flutter, React Native, Expo, or another cross-platform framework unless explicitly requested.
- Do not introduce a second state-management or navigation system without a clear requirement.

## Design principles
- Follow Android interaction conventions and Material guidance where appropriate.
- Kairos should have its own visual identity while still feeling native on Android.
- Prefer clear information hierarchy over decorative containers.
- Avoid excessive cards, pills, gradients, shadows, oversized headings, and gratuitous rounded rectangles.
- Do not add fake metrics, filler dashboards, or decorative UI that does not serve a user task.
- Keep density appropriate for a productivity app.
- Use the existing theme system and design tokens rather than hard-coding one-off colors, spacing, shapes, or typography.
- Prefer Material icons or the established icon system; do not mix unrelated icon families.
- Motion should communicate state changes, not exist for decoration.

## Platform behavior
- Respect system back behavior and predictive back where applicable.
- Handle system bars, safe insets, keyboards, configuration changes, dark mode, and different screen sizes correctly.
- Use native Android patterns for sheets, dialogs, menus, snackbars, navigation, haptics, and touch targets.
- Ensure important interactions are accessible with TalkBack and have meaningful content descriptions where needed.

## Data and Firebase
- Preserve existing Firebase authentication and Firestore behavior unless a schema change is explicitly required.
- Treat the web, iOS, and Android clients as clients of the same Kairos product model.
- Do not make Android-only schema changes that would break other clients.
- When changing shared data models, document compatibility implications.
- Avoid duplicate listeners, duplicate repositories, and multiple sources of truth for the same data.

## Tasks, boards, schedule, and AI
- Reuse existing domain models and repositories before creating new ones.
- Keep task, board, section, reminder, and schedule semantics compatible with the other Kairos clients.
- AI-triggered actions must be validated before mutating user data.
- Prefer explicit structured actions such as `task.create`, `task.update`, `task.delete`, `board.create`, `schedule.move`, and similar contracts rather than parsing free-form model output directly in UI code.
- UI should reflect remote changes reliably and avoid duplicate writes or stale state.

## Code quality
- Keep composables focused and reasonably small.
- Extract reusable UI into the existing `components` package when it is truly reusable.
- Keep business and persistence logic out of composables where practical.
- Avoid broad refactors unrelated to the requested task.
- Do not add dependencies unless they materially improve the implementation and there is no suitable existing solution.
- Follow existing naming, package, formatting, and architectural conventions.
- Do not commit generated build outputs, personal IDE state, temporary screenshots, or agent scratch files.

## Verification
Before considering a change complete:
- Build the affected module.
- Fix compile errors and warnings introduced by the change.
- Run relevant tests where available.
- Check the changed screen on at least one phone-sized emulator/device.
- For layout work, verify light/dark appearance and a second relevant screen size when practical.
- Verify loading, empty, error, and populated states for meaningful UI changes.
- Confirm no unrelated feature regressed.

## Scope discipline
- Modify only files needed for the requested feature or fix unless a small supporting refactor is necessary.
- Preserve existing working behavior unless the request explicitly changes it.
- If a change affects Firebase schema, authentication, cross-client data semantics, or the AI action contract, treat it as a cross-platform change and document it clearly.