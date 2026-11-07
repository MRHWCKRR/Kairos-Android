# Shared Firebase data contract

Android, iOS and web share the existing `study_plans` and `users` collections. This change adds no collection, migration, or Android-only persisted field. Android's Kotlin models are partial read models, not complete Firestore document schemas.

## Read and write boundary

Each loaded plan/profile carries its original raw snapshot in excluded `sourceFields`; the plan also carries excluded `documentId`. These are local snapshot metadata and must never be sent to Firestore. The explicit `sharedFields()` projections contain only modeled editable fields. Plan projections deliberately omit `userID` and `createdAt`, which identify and order the original document.

The repository must address the document that was loaded, then merge the difference between that baseline projection and the edited projection into the transaction's current raw document. A whole-model `set(plan)` loses fields that Android does not model. A fresh latest-plan lookup during a save can direct an old screen's edits into a different document.

`SharedDataMerge.merge(base, edited, remote, baselineRaw)` retains remote fields and detects conflicting changes. Identity-bearing arrays (`boards`, `sections`, `tasks`, `scheduleEvents`, `notifications`) merge by stable `id`; duplicate/blank IDs fail. Remote-only additions and remote collection order survive. Editing a field changed remotely fails instead of overwriting it. Removing a record checks the original raw record, including unknown fields, so remote changes cannot be silently deleted. Other arrays, including legacy chat history and achievement goals, are atomic values and conflict when both clients edit them differently.

Missing Firestore fields decode to model defaults. An unchanged default must not be written back merely because it appears in the projection; an explicit `null` remains distinct from a missing field. Snapshot identity and ownership must be validated before a write, and errors must reach the calling UI.

## Android projections

| Document | Explicit editable fields |
| --- | --- |
| Plan | `boards`, `dayInsights`, `scheduleEvents` |
| Board / section | Existing identity, title, archived flag and nested sections/tasks |
| Task | `id`, `title`, `completed`, `date`, `archived` |
| Schedule event | `id`, `title`, `category`, `day`, `start`, `end` |
| User | `settings`, `notifications`, `achievements`, `focusData`, `aiChatHistory`, `onboardingCompleted` |

Settings project every existing Android profile, accessibility, appearance and notification setting explicitly. Achievements and focus data include every field currently modeled on Android. Notification and legacy message values are converted to plain maps.

`profilePatch(before, data)` produces a complete edited user projection from the existing update API. It accepts modeled settings/achievement/focus/notification/chat values and recursively converts lists and string-keyed maps. A map replaces its addressed field; dot paths update individual nested fields. Unrelated projected fields remain unchanged. Invalid/overlapping paths, unknown top-level fields and unsupported value types fail before persistence. Neither reflection nor Firestore's POJO serializer is used for writes.

## Raw fields that must survive Android edits

The following examples were checked against the other clients' `main` branches on 2026-11-07. This is a compatibility inventory, not an allowlist: all unmodeled data must survive unrelated Android changes.

| Area | Current fields or semantics outside Android's projection |
| --- | --- |
| Planner metadata | `userID`, `createdAt`, `calendarSources`, and future top-level fields |
| Boards | iOS `boardDescription` |
| Task details | `taskDescription`, `notes`, `priority`, `estimatedMinutes`, `parentTaskID`, `checklist`, `dueDate`, `dueTime`, `startTime`, `endTime`, `reminderMinutes`, `scheduleLocked`, `schedulingPreference` |
| Recurring tasks | The complete `recurrence` rule, occurrence state and exceptions; web treats a recurring series separately from individual occurrences |
| Schedule events | Actual `date`, occurrence exceptions, imported calendar `readOnly`, `calendarSourceId`, `externalId`, `allDay`, `transparent`, and future event metadata |
| Calendar sources | Source `id`, `name`, `kind`, `url`, `timezone`, import range `from`/`to`, `updatedAt`; Android must retain the entire source list |
| AI workspace | `users/{uid}.aiWorkspace` v2, including `activeChatId`, `chats`, `projects`, message IDs/usage IDs, memory summaries/revisions, pending action batches, receipts and unknown metadata |
| Other user data | AI usage statistics and any settings/profile fields added by other clients |

The Android `aiChatHistory` field is the legacy conversation format. Web and iOS use `aiWorkspace` v2 and only migrate legacy history when a workspace is absent. Saving Android legacy history must not reset, replace, migrate or delete an existing workspace. This preservation change does not implement workspace chat/project parity.

Retaining metadata alone does not implement its behavior. In particular, Android's basic completion/date controls do not constitute recurring occurrence editing, calendar import management or shared AI workspace editing. Such features need explicit compatible commands and guards before they can safely change those records.

## Reference sources

- [iOS planner models](https://github.com/MRHWCKRR/Kairos-IOS/blob/main/Kairos/Kairos/Core/KairosPlannerModels.swift): optional task details, checklist, recurrence marker and dated events. File blob `e3659bfac745e91d138f0011e7a28e92f0acb1ea`.
- [iOS AI workspace](https://github.com/MRHWCKRR/Kairos-IOS/blob/main/Kairos/Kairos/Core/KairosAIWorkspace.swift): raw-preserving workspace commands and legacy migration. File blob `ad1739b75e0ebe8e56dbc4509eeece7350eb6b1f`.
- [Web planner persistence](https://github.com/MRHWCKRR/Kairos/blob/main/app-planner-persistence.js): confirmed baseline/document identity, concurrent edit reconciliation and calendar sources. File blob `71e3a3740208a45959ca867b171f2d2c97c246e6`.
- [Web calendar commands](https://github.com/MRHWCKRR/Kairos/blob/main/calendar-commands.js): source and imported event schema. File blob `53c8177c08abfd44393814d7d07628dd0684db60`.
- [Web recurring board behavior](https://github.com/MRHWCKRR/Kairos/blob/main/boards-recurrence.js): series completion/date protection. File blob `c797e005c4e0dbb43cc20a93573750e7d145729e`.
- [Web AI workspace](https://github.com/MRHWCKRR/Kairos/blob/main/ai-workspace.js): v2 workspace, memory/action metadata and migration. File blob `534926dd220919e20ed869946a7bd08d2ed09f5c`.

These references describe inspected source code; they do not establish that every deployed document has the newest shape. Preserve unknown fields and fail closed on an incompatible edit rather than coercing newer data into Android defaults.
