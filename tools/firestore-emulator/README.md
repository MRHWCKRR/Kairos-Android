# Repository transaction tests

Start an Android emulator using the standard Android Studio emulator networking
(`10.0.2.2` routes to the host). Install Firebase CLI 14 and use Java 21, then run
from the repository root:

```sh
firebase emulators:exec --project demo-kairos-transactions --only firestore \
  --config tools/firestore-emulator/firebase.json \
  "bash tools/verify-firestore.sh"
```

The script requires the demo project and emulator environment provided by
`emulators:exec`. The Android tests independently opt in through the
`firestoreEmulator=true` instrumentation argument, construct a named Firebase app
with this demo project, disable disk persistence, and point it at
`10.0.2.2:8080` before their first operation. Each test uses unique document IDs.
No Firebase login, production credentials, or deployed backend is required.

The rules here deliberately allow all reads and writes **for the local test
emulator only**. Keep them separate from deployed Firebase rules; do not deploy
this configuration. The emulator binds only the host loopback interface.

Normal UI instrumentation skips this class. CI runs it in the separate
`firestore-transactions` job and uploads Android test reports and emulator logs.
