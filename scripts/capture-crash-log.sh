#!/usr/bin/env bash
# Capture Veneappi crash log from a single connected device.
# Usage: ./scripts/capture-crash-log.sh
set -euo pipefail

OUT="${1:-$HOME/Desktop/veneappi-crash.txt}"
ADB="${ADB:-adb}"

devices="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
count=$(echo "$devices" | grep -c . || true)

if [ "$count" -eq 0 ]; then
  echo "No adb device found. Connect tablet via USB and enable USB debugging."
  exit 1
fi

if [ "$count" -gt 1 ]; then
  echo "Multiple devices — pick tablet (ELN-L09), not emulator:"
  echo "$devices" | nl
  # Prefer physical Honor tablet over emulator / duplicate wireless
  SERIAL=$(echo "$devices" | grep -v emulator | grep -v '_adb-tls-connect' | head -1)
  if [ -z "$SERIAL" ]; then
    SERIAL=$(echo "$devices" | head -1)
  fi
  echo "Using: $SERIAL"
else
  SERIAL=$(echo "$devices" | head -1)
fi

echo "Clearing log on $SERIAL …"
"$ADB" -s "$SERIAL" logcat -c

echo ""
echo ">>> Now open Veneappi, go to Storm radar, wait for crash <<<"
echo ">>> Press Enter when the app has crashed …"
read -r _

echo "Saving log to $OUT"
"$ADB" -s "$SERIAL" logcat -d > "$OUT"
echo "Done. Search for FATAL or 'Fatal signal' in the file."
