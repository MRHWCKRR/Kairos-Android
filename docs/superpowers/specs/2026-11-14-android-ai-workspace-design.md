# Android AI workspace parity

Approved scope: match the current iOS AI workspace in native Kotlin/Compose, including multiple chats and projects, with web-compatible shared data.

Reference: Kairos-IOS-work AIHelperView, AIHelperViewModal, KairosAIWorkspace, KairosChatActions and KairosAIProposal; web aiWorkspace v2. Keep existing theme, icons, Firebase transport, account isolation, retained edit recovery and native Android interactions. No new dependencies, billing changes or schema replacement.

- History sheet: New chat, All chats, Projects, Chats, pending indicators, rename/delete/move chat, project editor (name, description, shared context), preserved chats after project deletion, plans link.
- Conversation: per-chat draft, pending/error/retry state, reply completion in original chat when user switches, readable Markdown/code, planner/project/memory context, Create plan from chat.
- Raw workspace commands use fresh Firestore transactions, preserve unknown web fields, migrate legacy aiChatHistory only when workspace absent, deduplicate message IDs, refuse corrupt/newer schemas and capacity overflow.
- AI outputs are untrusted. Validate iOS action fields and exact IDs; save immutable before/after preview, require explicit Apply or Discard; prevent stale/duplicate application. Preserve imported/read-only/locked schedules and recurrence history. Guard Undo against intervening edits.
- Account switch clears drafts, requests, errors and previews belonging to the old account. Failed persistence remains retryable; never claim success before acknowledgement. Existing retained legacy changes remain visible until resolved.
- UI must have loading, empty, error and recovery states; keyboard-safe composer, selectable replies, labels and accessible touch targets; phone light/dark verification.

Reference baselines: Android a3870c4, web c7c121c, iOS 1648fd6. Android-only implementation; upstream apps remain unchanged. Implement on isolated feature branch. No publishing or deploying as part of implementation.
