#!/usr/bin/env bash
# Start phone-audit AVDs (and optionally Medium_Tablet). Run create-phone-audit-avds.sh first.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR="$ANDROID_HOME/emulator/emulator"

if [[ ! -x "$EMULATOR" ]]; then
  echo "Emulator not found at $EMULATOR"
  exit 1
fi

start_avd() {
  local avd="$1"
  if ! "$EMULATOR" -list-avds | grep -qx "$avd"; then
    echo "Skip $avd (not found — run scripts/create-phone-audit-avds.sh)"
    return
  fi
  if pgrep -f "emulator.*-avd $avd" >/dev/null 2>&1; then
    echo "$avd already running"
    return
  fi
  echo "Starting $avd..."
  nohup "$EMULATOR" -avd "$avd" -dns-server 8.8.8.8,8.8.4.4 >/tmp/emulator-"$avd".log 2>&1 &
}

for avd in Phone_Small Phone_Medium Phone_Large Medium_Tablet; do
  start_avd "$avd"
  sleep 3
done

echo "Emulators starting. Logs: /tmp/emulator-Phone_*.log"
echo "Wait ~60s then: adb devices"
echo ""
echo "After boot, reset stuck landscape (e.g. after screenshot capture):"
echo '  adb -s emulator-5554 shell settings put system accelerometer_rotation 1'
echo '  adb -s emulator-5554 shell settings put system user_rotation 0'
