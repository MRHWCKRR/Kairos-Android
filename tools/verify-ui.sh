#!/usr/bin/env bash
# Collect while android-emulator-runner still has a running emulator.
set -u
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p app/build/visual-parity
if adb exec-out run-as com.kairos.app sh -c 'cd files && tar -cf - visual-parity' > /tmp/kairos-visual-parity.tar; then
  tar -xf /tmp/kairos-visual-parity.tar -C app/build/visual-parity
fi
exit "$test_status"
