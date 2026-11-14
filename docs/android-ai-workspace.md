# Android AI workspace

Android now uses the same `users/{uid}.aiWorkspace` v2 chats/projects as web and iOS. The native screen follows the iOS history sheet, All chats, project home/editor, chat menus, composer and saved change-review flow, using existing Android theme controls and icons.

- Chat/project edits start from the latest transaction document and preserve web memory, usage IDs and unknown metadata. Deleting a project keeps its chats. Legacy `aiChatHistory` is imported only when no workspace exists; existing larger histories remain readable. New appends use the same rolling 80-message window as iOS.
- Drafts, requests and errors are per chat. Switching chats does not cancel another chat's reply. Account changes clear all previous-account state. Provider failures are shown outside conversation history and cannot pollute model context.
- Existing app-private retained edits now include narrow AI operations. Replies are saved before being presented as successful; retries use immutable operation IDs and never recreate explicitly discarded records. Pending legacy history edits remain recoverable through the existing global retained-edit controls.
- AI proposals use the iOS `aiNativeProposals` v1 payload, known-field fingerprints and native receipts/history. Kotlin canonical hashes have Foundation fixture coverage. Apply validates the saved payload and latest planner, then changes the planner, progress and preview state atomically. Unsupported/newer payloads stop safely.
- Session-local Undo uses guarded before/after changes and preserves unrelated edits. It is unavailable after restarting the app. Unfinished web action batches must be resolved on the web before continuing that chat.
- No provider keys, backend limits, billing settings or live Firebase configuration changed. Reminder actions save compatible planner metadata; actual OS reminder delivery is not covered by these checks.

## Verification

- 92 JVM unit tests passed, including workspace migration/commands, action validation/recurrence/Undo, native fingerprint fixtures, save retry routes and safe Markdown.
- 3 Firestore-emulator transaction journeys passed: independent chat preservation/idempotence, offline journal restart/discard safety, atomic preview application and retirement.
- 4 actual ViewModel journeys passed against demo Auth/Firestore emulators with controlled AI replies: concurrent chats, typing during save, account switch/late reply isolation, and discarded reply recovery.
- 3 Compose journeys passed for project editors, failed saves, chat/draft switching and before/after review. Conversation, populated history and project home were captured and inspected in light/dark on Pixel 7 Pro.
- Debug APK build, release Kotlin compilation and shared icon verification passed. Debug-only cleartext access is limited to local emulator host `10.0.2.2`; release has no such exception.

These tests use local demo data, not production accounts or a real AI provider. Real-device App Check registration, authenticated production AI, notification delivery and cross-device live acceptance remain separate release checks.
