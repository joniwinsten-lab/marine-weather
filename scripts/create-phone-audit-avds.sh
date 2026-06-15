#!/usr/bin/env bash
# Create three phone-sized AVDs for UI audit (uses installed android-35 tablet Play image).
set -euo pipefail

AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
SYSIMG="system-images/android-35/google_apis_playstore_tablet/arm64-v8a/"

if [[ ! -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}/$SYSIMG" ]]; then
  echo "Missing system image: $SYSIMG"
  echo "Install via Android Studio → SDK Manager → Android 15 (API 35) Google Play tablet image."
  exit 1
fi

create_avd() {
  local id="$1"
  local name="$2"
  local width="$3"
  local height="$4"
  local density="$5"
  local orientation="$6"
  local dir="$AVD_HOME/${id}.avd"

  mkdir -p "$dir"
  cat >"$AVD_HOME/${id}.ini" <<EOF
avd.ini.encoding=UTF-8
path=$dir
path.rel=avd/${id}.avd
target=android-35
EOF

  cat >"$dir/config.ini" <<EOF
AvdId=${id}
PlayStore.enabled=true
abi.type=arm64-v8a
avd.ini.displayname=${name}
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.audioInput=yes
hw.battery=yes
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.dPad=no
hw.device.manufacturer=Google
hw.device.name=${id}
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.initialOrientation=${orientation}
hw.keyboard=yes
hw.lcd.density=${density}
hw.lcd.height=${height}
hw.lcd.width=${width}
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=yes
image.sysdir.1=${SYSIMG}
runtime.network.latency=none
runtime.network.speed=full
hw.dns1=8.8.8.8
hw.dns2=8.8.4.4
sdcard.size=512M
showDeviceFrame=yes
skin.dynamic=yes
tag.display=Google APIs PlayStore
tag.id=google_apis_playstore
target=android-35
vm.heapSize=256
EOF
  echo "Created $id ($name) ${width}x${height} @${density}dpi"
}

# ~Pixel 4a (small), Pixel 8 (medium), Pixel 8 Pro (large) — portrait defaults
create_avd "Phone_Small" "Phone Small (Pixel 4a class)" 1080 2340 440 Portrait
create_avd "Phone_Medium" "Phone Medium (Pixel 8)" 1080 2400 420 Portrait
create_avd "Phone_Large" "Phone Large (Pixel 8 Pro)" 1344 2992 480 Portrait

echo "Done. List: emulator -list-avds"
