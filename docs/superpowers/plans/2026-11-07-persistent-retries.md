# Persistent retries implementation plan

**Goal:** Retain Android edits across process death and retry them explicitly in the owning account with their original conflict baseline and stable IDs.

**Architecture:** Journal immutable raw mutation envelopes in app-private, no-backup files before sending a Firestore transaction. Encode Firestore values without coercion. Restore only the signed-in account's pending list; never replay automatically. Remove an envelope only after acknowledgement or explicit discard.

**Constraints:** Keep existing transaction conflict checks, pin original document, preserve unknown fields, add no shared Firebase schema or dependencies, use existing Kairos styling. Sign-out hides records; switching back restores that account's records. Local staging failure prevents the network write. Corrupt data fails closed with explicit discard recovery.

## Tasks

- [ ] Add tagged value codec and atomic account-partitioned journal. Tests: new store instance restores identical raw values; timestamps/nanoseconds, blobs and references round-trip; foreign UID, corruption, unsupported version/type and full disk/size failure cannot overwrite another edit.
- [ ] Route every repository plan/profile save through immutable `PendingEdit` before server read. Add `observePendingEdits(uid)`, `retryPendingEdit(uid,id)`, `discardPendingEdits(uid)`. Retrying uses original projections/raw baseline and the existing transaction path.
- [ ] Restore pending status in MainViewModel and reuse save feedback for Retry/Discard. Reconcile acknowledged task completions. Preserve AI's stable operation ID and offer existing retry/reload recovery after restart; widgets use the same repository journal.
- [ ] Extend demo Firestore tests: interrupted acknowledgement + retry creates no duplicate, offline failure survives new repository/store instances, concurrent same-field edit conflicts after restart, UID isolation, explicit discard, and original-document pinning.
- [ ] Build, run unit/emulator/UI tests, review immutable patch, merge after checks.

**Verification:** `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`; existing `tools/verify-firestore.sh` and `tools/verify-ui.sh` CI jobs. Inspect restored pending, failure, saving and empty UI states on phone-sized emulator.
