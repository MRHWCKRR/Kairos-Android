# Icons, motion and search implementation plan

**Goal:** Match icon artwork between platforms and match Android tab motion and search to iOS.

**Architecture:** Shared Phosphor SVG sources and explicit semantic manifest generate iOS template assets and Android ImageVectors. Compose keeps existing navigation and repositories. Search adds a bounded pure model index and contextual sheets, without changing schemas.

**Spec:** ../specs/2026-11-06-icons-motion-search.md

## Tasks

- [ ] Replace explicit app icons with a shared catalog; preserve labels, sizes, RTL directions and widget rendering. Verify every symbol maps to an asset, each SVG hash matches the Android path source and both target asset catalogs contain required files.
- [ ] Add Android selected-tab bounce/spring, compact iOS measurements, always-visible search and reduced-motion handling in KairosTabDock.kt. Verify no animation on initial mount/reselection and all five tabs remain accessible at narrow widths.
- [ ] Replace PlannerSearchDialog.kt with iOS-style modal search, pure PlannerSearch index/status/result models, board/task detail sheets and single dock entry wiring in MainActivity.kt. Test empty query, multitone case/diacritics, all statuses, overdue boundaries, archived/duplicate identities and result limits.
- [ ] Review combined changes, build Android and iOS through CI, run emulator regression tests and inspect exported light/dark/large-text screenshots. Merge normal updates after checks, per owner preference; report any unavailable verification accurately.

## Review focus

- Search must not dismiss an unsaved draft or navigate to an unrelated same-ID task.
- Account/loading/remote changes must not expose stale search results.
- Large text and a narrow screen retain all tab hit targets and search.
- Reduced motion disables decorative bounce and fade.
- Asset images retain template tint and scalable sizing; no SF Symbols remain at explicit cross-platform icon call sites.
