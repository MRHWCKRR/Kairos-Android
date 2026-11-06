# iOS-led Android visual redesign

The product owner explicitly requests nearly 1:1 iOS visual parity, authorises broad redesign of the stale Android app, and permanently retires Discovery. This supersedes the earlier web-led palette and the old AGENTS prohibition on copying iOS presentation.

Reference: MRHWCKRR/Kairos-IOS commit 795981e6365077f7dc0b134d0d81a937124b4bc6. Android baseline: 56d33b6c06f77393e3e6154fae53146cb24927d4.

## Design

- Use the exact iOS RGB accent (0.49, 0.27, 0.96), approximately #7D45F5, for brand fills. Keep accessible text accent separate so contrast adaptation never changes the brand colour.
- Match Home / Boards / AI / Schedule / Goals order, compact rounded dock, page margins, rounded headline hierarchy, quiet grouped surfaces, header/action placement and empty states.
- Mirror the iOS dashboard greeting/date, Today summary, focus session, momentum and workspace shortcuts using actual Android planner/profile data.
- Mirror Tasks header, checklist summary, progress, show/hide completed, board/section hierarchy, archive and circular creation control.
- Match iOS AI conversation presentation and composer using the current Android chat data. Do not present fake chats/projects or unsupported actions as implemented.
- Bring Schedule, settings, statistics, goals and login closer to their corresponding iOS screens. Keep working Android Back, permission, keyboard and inset behaviour.
- Use opaque surfaces instead of Apple Liquid Glass. Keep the existing native Kotlin/Compose architecture and Android icon family.

## Retirement

Remove Discovery screen/ViewModel/routes, shared routine model and repository operations, board sharing/import actions, tutorial text and success notifications. Do not delete remote Firestore documents or any user's planner data.

## Boundaries

This is a visual redesign and feature retirement. Shared planner schema, write behaviour, AI API migration and full feature parity remain the roadmap's functional phases. Do not add unsafe whole-document mutations to simulate missing functionality. The screenshots/previews must exercise production composables, not a separate fake design.

## Acceptance

Build APK and test APK; run contrast and navigation tests, emulator interaction checks and capture representative production UI in light/dark and at large text sizes. Compare source-derived screenshots against the pinned iOS layout references. Record remaining functional gaps honestly. This broad redesign is delivered on a review branch for visual review.
