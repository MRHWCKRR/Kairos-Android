# iOS visual parity implementation plan

Spec: ../specs/2026-11-06-ios-visual-parity.md (explicitly requested by the product owner).

- [ ] Remove Discovery across model/repository/ViewModel/onboarding and remove UI callers.
- [ ] Set exact iOS brand purple, separate contrast-safe accent text, system-like surfaces and rounded typography.
- [ ] Rebuild shared dock, app background and page headers; match iOS route order and wire real shortcuts/search.
- [ ] Rebuild Dashboard and Tasks content from existing live data, keeping persistence unchanged.
- [ ] Align AI feed/composer; unify Schedule ranges and date controls using existing data.
- [ ] Align settings/login/statistics/goals independently, preserving their working controls.
- [ ] Add regression and production-composable screenshot coverage, inspect diff and request independent review.
- [ ] Publish review PR, run build/unit/emulator checks and record screenshot/functional limits.

No new app dependencies, frameworks or shared Firestore schema. Main agent owns shell/theme/dashboard/tasks/AI/tests/docs; independent agents own Discovery retirement, secondary screens and Schedule respectively to avoid overlapping edits.
