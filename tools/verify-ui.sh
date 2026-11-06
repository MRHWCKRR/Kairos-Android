#!/usr/bin/env bash
# Collect while android-emulator-runner still has a running emulator.
set -u
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p app/build/visual-parity
# Avoid a nested shell: adb otherwise loses the quoting around sh -c.
if adb exec-out run-as com.kairos.app tar -C files -cf - visual-parity > /tmp/kairos-visual-parity.tar; then
  tar -xf /tmp/kairos-visual-parity.tar -C app/build/visual-parity || exit 1
else
  echo "Unable to collect visual parity screenshots" >&2
  exit 1
fi
exit "$test_status"
