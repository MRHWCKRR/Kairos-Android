# Phase 0: reference and visual foundation

> Updated direction: iOS is now the primary Android visual reference. Use its exact RGB purple (0.49, 0.27, 0.96), screen hierarchy and layouts with solid surfaces. Discovery and sharing are permanently retired. The baseline comparison below is historical; see the iOS visual parity specification for current design choices.

## Baseline and scope

Recorded 6 November 2026. Source references are immutable commits, not moving branch names.

| Client | Repository / branch | Commit |
|---|---|---|
| Android | MRHWCKRR/Kairos-Android, master | 2dca2aa271bc22a78e523c9b123a02447074767e |
| iOS | MRHWCKRR/Kairos-IOS, main | 795981e6365077f7dc0b134d0d81a937124b4bc6 |
| Web | MRHWCKRR/Kairos, main | a9c443ce43e5da3e62b869df7f80918b3e91d882 |

The user requires the same Kairos styling and visual identity across clients. Existing Android AGENTS.md asks for native Android interactions; match colour, hierarchy and components while retaining Material navigation, sheets, system Back and permission flows. No schema, repository, authentication or AI transport changes belong in this phase.

This baseline is a source inventory. Live authenticated screenshots have not been captured here. Debug Compose previews are review fixtures, not screenshots of completed cross-platform features. Final visual acceptance remains open until the capture checklist below has been exercised.

## Screen and behaviour map

Web references below are modules in MRHWCKRR/Kairos at the pinned commit. iOS references are relative to Kairos/Kairos/. Android references are relative to app/src/main/java/com/kairos/app/ui/screens/.

| Area | Android baseline | Web reference | iOS reference | Remaining functional gap / owner |
|---|---|---|---|---|
| Dashboard / Today | dashboard/DashboardScreen.kt: greeting, timer, goals, basic progress and focus list | today-view.js, today-model.js, task-focus.js | Views/DashboardView.swift, Views/DashboardTodayView.swift | Today projections and direct planning actions, phase 3 |
| Boards / sections | tasks/TasksScreen.kt: create, rename, archive, sharing | task-list-workspace.js, task-section-model.js | Views/TasksView.swift, Views/TasksWorkspaceView.swift | Rich editing, sections and shared save feedback, phase 3 |
| Task details | Rename dialog and completion only | task-detail-pane.js, docs/task-details.md | Views/BoardDetailsView.swift, Views/OccurrenceDetailsView.swift | Notes, deadlines, estimates, priority, checklist, reminder and recurrence editing, phase 3/4 |
| Schedule | plan/PlanScreen.kt switches Calendar/Schedule; weekly fixed events | schedule-workspace.js, schedule-events.js, schedule-calendar.css | Views/ScheduleView.swift, Views/MobileScheduleEditors.swift | Unified range navigation, backlog, scheduled tasks, occurrence actions, move/resize/Undo, phase 4 |
| AI | ai/AiHelperScreen.kt: legacy single conversation, board generation | ai-workspace.js, ai-client.js, ai-actions.js | Views/AIHelperView.swift, Core/KairosAIWorkspace.swift, Core/KairosAIProposal.swift | Chats/projects and durable reviewed actions, phase 5; current transport phase 2 |
| Global search | No global workspace search | global-search.js | Views/PlannerSearchView.swift, Core/KairosPlannerSearch.swift | Cross-board navigation and stale-result checks, phase 3 |
| Focus | Dashboard timer, audio and widgets | task-focus.js, focus-breakdown.js | Views/FocusTimerViewModel.swift, Services/FocusTimerCoordinator.swift | Shared attribution/recovery and statistics, phase 6 |
| Statistics | statistics/StatisticsScreen.kt | analytics modules, focus-breakdown.js | Views/StatisticsView.swift | Align calculations and useful drill-downs, phase 6 |
| Settings | settings/SettingsScreen.kt | settings modules in app, planning settings | Views/SettingsView.swift, Views/PlanningAccessibilityView.swift | Planning availability/defaults, export/recovery and complete account actions, phase 2/6 |
| Login / verification | login/LoginScreen.kt; no main verified-email gate | login/verification flow, verify-email.js | Views/LoginView.swift, Views/EmailVerificationView.swift | Verification and complete deletion, phase 2 |
| Notifications | MainActivity notification sheet, NotificationHelper | notifications/settings | Views/NotificationSettingsView.swift, Services/KairosTaskReminderScheduler.swift | Scheduling/permission parity and deep links, phase 6 |
| Onboarding | onboarding/OnboardingScreen.kt | getting-started modules | ContentView.swift / initial session flow | Match shared first-run semantics, phase 6 |
| Goals | achievements/AchievementsScreen.kt | achievements | Views/AchievementsView.swift | Main navigation now matches iOS Goals; Discovery and all Android sharing code have been removed |
| Widgets | ui/widgets/FocusWidget.kt, TasksWidget.kt | No launcher widget equivalent | KairosWidgets targets | Safe shared data and account isolation, phase 1/6 |

## Shared data contract to preserve

Primary evidence: Android data/models/KairosModels.kt and data/repository/FirebaseRepository.kt; iOS Core/KairosPlannerModels.swift, Models/StudyPlanRepository.swift and Core/KairosAIWorkspace.swift; web planner-contracts.d.ts and docs/task-details.md.

| Location | Fields / semantics | Android compatibility requirement |
|---|---|---|
| study_plans/{documentID} | userID owner, createdAt, boards, dayInsights, scheduleEvents; additional metadata includes calendarSources | Keep document identity, ownership and unknown fields. Do not serialise the small Android read model over the entire document. |
| boards[] | id, title, archived, sections; optional description/metadata | Preserve nested maps and fields Android does not edit. |
| sections[] | id, title, archived, tasks | Keep ordering and untouched sibling entries. |
| tasks[] | id/title, completed/archived, date; description/notes, dueDate/dueTime, parentTaskID, priority, estimatedMinutes, checklist, startTime/endTime, reminderMinutes, scheduleLocked and recurrence | Wire names must be confirmed against raw web mutations before implementing writes; Swift property names such as taskDescription are not by themselves a schema definition. Missing optional flags default false; unreadable identity is not an empty planner. |
| checklist[] | id, title, completed | Stable IDs; occurrence completion must not replace the recurring template. |
| recurrence | enabled, frequency, interval, weekdays, startDate, mode, monthlyMode/monthDay/weekdayPosition/weekday/month, exceptions and endType/endDate/count | Preserve full rule and exceptions even when Android cannot edit them. Do not reduce the wire value to an enabled Boolean. |
| scheduleEvents[] | id/title/category/start/end; either a date or weekday; allDay, transparent, reminderMinutes; weekly bounds/intervalWeeks/anchorDate and occurrence metadata | Keep dated and repeating events distinct. weekday is 0–6; date-only keys must not shift through UTC conversions. |
| users/{uid} | settings/profile/accessibility/appearance/notifications, notifications, achievements, focusData, onboardingCompleted and AI data | Update supported paths only. Clear account-owned state and listeners on account changes. |
| users/{uid}.aiWorkspace | version 2, activeChatId, chats[], projects[] | Retain message IDs, usage IDs, summaries/memory, pending actions/receipts and unknown fields; migrate legacy aiChatHistory only if the workspace is absent. |

Baseline risk: FirebaseRepository.updatePlan queries the newest plan then calls set(plan), while KairosTask contains only id/title/completed/date/archived. This can discard richer data. Phase 0 deliberately does not change this flow; phase 1 must resolve it before richer writable features are introduced.

Timestamps: Android currently assumes a Firestore Timestamp for createdAt and orders the query by it. iOS accepts legacy timestamp forms and preserves a raw snapshot. Phase 1 must define deterministic newest-plan selection and fail visibly on unreadable data, rather than silently selecting an older plan.

## AI transport contract

The reference is iOS Core/KairosAIContract.swift and web cloudflare/worker.js / ai-client.js at the commits above.

- POST https://kairos-web.kirosapp.workers.dev/api/ai
- Content-Type: application/json; Authorization: Bearer <Firebase ID token>; X-Firebase-AppCheck: <App Check token>.
- Request body: messages array of role/content objects. iOS allows 1–40 messages, nonblank content and roles system/user/assistant; encoded body limit is 128 KiB. iOS timeout is 100 seconds.
- Success: choices[0].message.content, nonempty; a length-truncated completion is rejected by iOS.
- Errors: HTTP status plus error, optional code and resetsAt; display bounded text and recover appropriately. Requests require a verified account.
- Android still uses its legacy transport. Switching it, removing the compiled relay credential and validating current server policy are phase 2 work.
- AI-generated mutations require validated shared actions; do not invent Android-only action names or apply arbitrary generated fields. Durable proposals and retry receipts are phase 5 work.

## Visual specification and provenance

The original references differed. The product owner has since selected iOS as the primary visual reference, approximately #7D45F5, with solid Android surfaces. The following table records the earlier baseline and is superseded by the iOS parity specification.

| Token | Android target | Provenance / adaptation |
|---|---|---|
| Dark background / surface | #0f0f0f / #161616 | web app.css :root |
| Dark border / text / muted | #262626 / #f5f5f5 / #929292 | web app.css :root |
| Light background / surface | #f4f4f6 / #ffffff | web body.mode-light |
| Light border / text / muted | #e3e3e8 / #17171b / #6b6b76 | web body.mode-light |
| Default accent | #a855f7 | web theme-default; semantic primary is contrast-adjusted when necessary |
| Named web accents | fairyfloss #ff8fc9; poseidon #38bdf8; peacefulplains #4ade80 | web named themes; darken semantic primary on light surfaces to meet text contrast |
| iOS accent aliases | blue, green, orange, pink, red | accept shared profile names; maintain readable semantic roles |
| Page margin | 20dp | iOS KairosMetrics.pageHorizontal = 20 |
| Section / row gaps | 24dp / 12dp, compact 8dp | current iOS view spacing, adapted for Android density |
| Card / control radius | 26dp / 12dp; small 8dp | iOS card radius; restrained web/Material control treatment |
| Touch target | minimum 48dp | native Android interaction requirement; icon glyph may be smaller |
| Typography | system sans by default; serif/mono aliases; title 18–32sp, body 14–16sp | same hierarchy, native typeface and scalable sp; full Material type scale supplied |
| Font licensing | no new font files copied | web uses system stack and optional Inter/Quicksand/JetBrains Mono; iOS uses Apple system fonts. Android uses installed system families. round/rounded currently falls back to system sans. |
| Surface treatment | quiet borders, no default elevation/gradient on shared cards and buttons | content-first product direction; native dialogs/sheets retain platform treatment |
| Motion | no new decorative animation | existing transitions remain; later motion must honour reduced-motion settings |

Colour roles: all onPrimary/secondary/tertiary roles must match their container. Pastel themes cannot inherit Material's unrelated default purple/white labels. The contrast unit test checks text/surface combinations for all nine supported theme names in light/dark mode.

Keep existing logo artwork. Do not recreate it or add emoji/sparkle decorations. Legacy Android screens still have some local styling; migrate those with their owning functional phase after the representative views have been accepted.

## Components and integration

- ui/theme: Color, Theme, Type and Metrics provide source-backed tokens and contrast-safe roles.
- ui/components/KairosComponents.kt: measured scaffold, card, button, field, task row, input dialog, overflow menu, sheet, state message and error feedback.
- PremiumComponents preserves existing function signatures and delegates to the same visual roles; buttons grow for large text and keep disabled styling.
- MainActivity uses measured Scaffold content padding. NavHost consumes it once; navigation is a labelled Material bar with selected semantics. Icons react to the light/dark appearance.
- Dashboard/Tasks/Plan drop the old 100–180dp shell offsets. Settings drops its duplicate status inset. Tasks' inner Scaffold uses zero additional system insets and applies its own padding to real content.
- AI feed, error and composer are in one measured Column; the shell owns IME padding. No floating composer lift is required.
- Existing task rows and rename dialogs use shared components. Rich task-details previews are a visual contract only; production persistence remains phase 3.

## State and visual acceptance matrix

Render app/src/debug/.../KairosFoundationPreviews.kt in Android Studio. Each of DashboardReference, TaskDetailsReference and AIReference takes FoundationStates: populated, empty, loading, error and editing. Each has light/dark phone, 320dp phone at 200% font scale and landscape previews. Fixtures are debug-only and do not access Firebase.

| Surface | Populated | Empty | Loading | Error | Editing |
|---|---|---|---|---|---|
| Dashboard | greeting/focus/task hierarchy | explicit no-work state | labelled progress | readable retry | disabled/in-progress controls |
| Task details | title/notes/checklist | no-content message | labelled progress | readable retry | labelled fields and enabled Save |
| AI | user/assistant messages | conversation prompt | pending response | visible error | multiline draft/composer |

Preview fixtures illustrate component states; production states must be driven by real repository/request state, not inferred from absent data.

### Capture checklist (still required)

Use the same synthetic assignment, date, completion and conversation across all clients. Use desktop web at 1440×900; iOS and Android at comparable phone widths. Compare actual screenshots against the references, not only against these previews.

- [ ] Capture web/iOS populated, empty, loading, error and editing states for the three reference surfaces.
- [ ] Capture Android equivalents in light/dark, plus one smaller phone and 200% text.
- [ ] Exercise keyboard open/close, multiline drafts, gesture/button navigation and landscape cutouts.
- [ ] Check long names, trailing action reachability, last list item/FAB visibility and TalkBack reading order.
- [ ] Confirm disabled controls, save/error feedback, contrast and selected navigation are understandable without colour alone.
- [ ] Review representative screens with the product owner before broad visual migration.

## Verification record

Run ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest and ./gradlew :app:connectedDebugAndroidTest. CI runs those in build and emulator jobs. UI tests cover content bounds between measured bars, separate task/open actions and disabled Save. These checks do not replace the screenshot acceptance checklist.

Local limitations recorded during implementation: normal git clone cannot resolve GitHub; ADB cannot connect to its local server. Gradle can run in-process using a workspace cache, but aapt2 and new UI-test artifacts are unavailable offline. No successful build, emulator run or screenshot parity approval is claimed from these attempts. See the pull request checks for subsequent verification results.
