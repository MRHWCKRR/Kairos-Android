# Android task editor plan

**Goal:** Match iOS TaskDetailView with native solid Android surfaces and safe shared-data edits.

**Reference:** Kairos-IOS `TasksWorkspaceView.swift` TaskDetailView, `KairosTaskMutation.swift`, `KairosChecklist.swift` on main. Preserve the shared purple and exact shared icon artwork.

## Deliverables

- Rich task read models and known-field projection: description, notes, priority, estimate, checklist, scheduled date/start/end, deadline/due time, reminder, schedule lock and preferred time. Recurrence stays raw except its enabled marker.
- Stable-ID checklist merging preserves unknown item metadata and disjoint concurrent edits. Validate maximum 100 entries, nonblank IDs <=120 and titles <=180 characters. Notes <=3000 UTF-16 units; estimate 1–1440; reminder 1–10080; known priority/preference values only when changed.
- Fresh transaction guards reject moved/archived tasks, remote locks and recurrence activation, including retained replay. Existing unrelated metadata and checklist properties survive.
- Native full-height details editor with iOS card order, field labels/icons, accessibility, keyboard insets, validation, save feedback, remembered draft and dirty-dismiss protection. Capture original plan/account scope once and retain it through remote updates/configuration changes.
- Open details from task rows/menu and search target requests. Save through existing durable journal; failure keeps the form open. Recurring occurrence/rule editing remains a separate milestone.
- Verify pure mutation/validation, rich Firestore round trips/replay/conflicts, form save/clear/error/back/draft behavior, and light/dark phone screenshots before review and merge.
