#!/usr/bin/env bash
# Run inside firebase emulators:exec with a running Android emulator (10.0.2.2 -> host).
set -euo pipefail
if [[ "${GCLOUD_PROJECT:-}" != "demo-kairos-transactions" || -z "${FIRESTORE_EMULATOR_HOST:-}" ]]; then
  echo 'Run with firebase emulators:exec --project demo-kairos-transactions --only firestore --config tools/firestore-emulator/firebase.json "bash tools/verify-firestore.sh"' >&2
  exit 1
fi
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kairos.app.data.repository.FirebaseRepositoryTransactionTest \
  -Pandroid.testInstrumentationRunnerArguments.firestoreEmulator=true \
  --stacktrace
