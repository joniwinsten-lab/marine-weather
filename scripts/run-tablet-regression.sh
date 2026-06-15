#!/usr/bin/env bash
# Capture tablet regression screenshots (audit vs production) on a 2560×1600 proxy.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

SERIAL="${TABLET_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  for s in $($ADB devices | awk 'NR>1 && $2=="device" {print $1}'); do
    size=$($ADB -s "$s" shell wm size 2>/dev/null || true)
    if [[ "$size" == *"2560x1600"* ]] || [[ "$size" == *"1600x2560"* ]]; then
      SERIAL="$s"
      break
    fi
  done
fi
if [[ -z "$SERIAL" ]]; then
  echo "No 2560×1600 device found. Configure proxy on Phone_Large:"
  echo "  adb -s emulator-5558 shell wm density 320"
  echo "  adb -s emulator-5558 shell wm size 2560x1600"
  exit 1
fi

echo "Using $SERIAL"
$ADB -s "$SERIAL" shell wm density 320
$ADB -s "$SERIAL" shell wm size 2560x1600
$ADB -s "$SERIAL" shell pm grant fi.veneappi.app android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
$ADB -s "$SERIAL" shell pm grant fi.veneappi.app android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true

LABEL="${1:-audit}"
cd "$ROOT"
python3 scripts/capture-tablet-regression.py --label "$LABEL"
