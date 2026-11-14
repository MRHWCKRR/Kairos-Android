# Android AI Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task-by-task.

**Goal:** Match the iOS AI workspace UI and supported workflow on Android, sharing web/iOS chats and projects safely.
**Architecture:** Pure raw-map workspace commands and validated proposal engine; existing FirebaseRepository owns fresh transactions and durable retained edits. AiHelperViewModel coordinates per-chat work; Compose reuses house components.
**Tech Stack:** Existing Kotlin/Compose, Firebase, coroutines, kotlinx.serialization, JUnit.
**Spec:** ../specs/2026-11-14-android-ai-workspace-design.md

## Global constraints
No new dependencies. Preserve aiWorkspace v2 and unknown data. Native Android UI with iOS hierarchy. Never publish automatically. No destructive or speculative schema migration.

## Review focus
Cross-device edits/deletion, account changes mid-request, failed save/retry after process death, corrupt workspace/pending legacy changes, stale/double Apply and Undo.

### Task 1 — Workspace domain
Create data/repository/AiWorkspace.kt and unit tests. Nested Chat/Project/Message/Snapshot and Command; read(user,ownerId), apply(user,command,ownerId,nowMillis) returns aiWorkspace map. Preserve unknown metadata and refuse unsafe inputs. Test migration, limits, deduplication, independent chats/projects, deletion and malformed schemas.

### Task 2 — Reviewed actions
Create data/repository/AiActions.kt and tests. Strict iOS-supported action JSON and exact ID validation; raw-map mutations preserving unrelated fields; immutable previews and guarded apply/undo. Test invalid args, read-only/locked targets, recurrence safety, stale preview and replay. Keep helpers pure; no Firebase or UI edits.

### Task 3 — Repository and state
Extend existing FirebaseRepository with narrow fresh workspace transactions and saved replies/proposals; retain failed writes without old-account replay. Replace legacy single-chat coordinator with per-chat requests/drafts/errors and retry. Keep auth transport. Reuse latest raw planner and existing pending-edit journal. Test transaction conflicts and account boundaries.

### Task 4 — Native UI and integration
Match iOS history sheet, All chats, project home/editor, chat menus, review/apply/discard/Undo and composer. Reuse ChatBubble/theme/icons and improve Markdown with stdlib. Keep old composable fixture entry points for existing callers/tests or update those callers coherently.

### Task 5 — Verify and review
Run focused unit tests, Android build, existing retained-edit tests; phone emulator UI journeys for multiple chats/projects/editing and recovery in light/dark. Review full diff and fix concrete failures. Document external App Check acceptance limits; do not publish.
