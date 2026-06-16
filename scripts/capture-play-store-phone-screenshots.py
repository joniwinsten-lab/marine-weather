#!/usr/bin/env python3
"""Capture portrait phone screenshots for Play Store (premium unlocked via friends build)."""

from __future__ import annotations

import importlib.util
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
_audit = Path(__file__).resolve().parent / "capture-phone-audit-screenshots.py"
_spec = importlib.util.spec_from_file_location("audit_cap", _audit)
_mod = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(_mod)

adb = _mod.adb
capture_tab = _mod.capture_tab
dump_ui = _mod.dump_ui
restore_rotation = _mod.restore_rotation
screencap = _mod.screencap
set_rotation = _mod.set_rotation

SERIAL = "emulator-5556"
OUT = ROOT / "docs/play-store-phone-screenshots"
# friends variant: DEBUG_ROUTE_PREMIUM_UNLOCKED=true (same UI as release, premium gates open)
PACKAGE = "fi.veneappi.app.friends/fi.veneappi.app.MainActivity"
PKG = "fi.veneappi.app.friends"

TABS = [
    ("01_compare_map_weather", r"Map\s*&", "Map & weather compare", 2.0),
    ("02_storm_radar", r"Radar\s*&", "Storm radar & lightning", 2.5),
    ("03_marine_text", r"Weather forecast", "National marine text", 2.0),
    ("04_route_planning", r"Route planning", "Route planning (premium unlocked)", 3.0),
    ("05_wind_12day", r"Wind 12\+ days", "12-day wind outlook (premium unlocked)", 4.0),
]


def grant_permissions(serial: str) -> None:
    for perm in (
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
    ):
        adb(serial, "shell", "pm", "grant", PKG, perm, check=False)


def install_friends_build(serial: str) -> None:
    print("Uninstalling release/debug package so friends build launches…")
    adb(serial, "shell", "pm", "uninstall", "fi.veneappi.app", check=False)
    print("Installing friends build (premium unlocked)…")
    subprocess.run(
        [
            "bash",
            "-lc",
            "export JAVA_HOME=$(/usr/libexec/java_home -v 21) && "
            f"cd {ROOT} && ./gradlew :app:installFriends -q",
        ],
        check=True,
    )


def top_package(serial: str) -> str:
    out = adb(serial, "shell", "dumpsys", "activity", "activities", check=False)
    for line in out.splitlines():
        if "topResumedActivity" in line and "veneappi" in line:
            m = re.search(r"(fi\.veneappi\.app(?:\.friends)?)/", line)
            if m:
                return m.group(1)
    return "unknown"


def launch_app(serial: str) -> None:
    adb(serial, "shell", "am", "force-stop", PKG, check=False)
    time.sleep(0.4)
    adb(serial, "shell", "am", "start", "-n", PACKAGE, check=False)
    time.sleep(6.5)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    state = adb(SERIAL, "get-state", check=False)
    if state != "device":
        raise SystemExit(f"{SERIAL} not ready ({state})")

    install_friends_build(SERIAL)
    grant_permissions(SERIAL)
    set_rotation(SERIAL, 0)
    launch_app(SERIAL)
    time.sleep(2)
    running = top_package(SERIAL)
    if running != PKG:
        raise SystemExit(f"Expected {PKG}, got {running}")
    ui = dump_ui(SERIAL)
    lines: list[str] = [
        "Premium: `friends` debug build (`DEBUG_ROUTE_PREMIUM_UNLOCKED=true`)",
        f"Package: `{PKG}` (screenshots match release UI)",
    ]

    for idx, (file_id, pattern, title, settle_s) in enumerate(TABS):
        filename = f"phone_portrait_{file_id}.png"
        path = OUT / filename
        if idx > 0:
            status, note, _ = capture_tab(
                SERIAL,
                ui,
                pattern,
                tab_id=file_id,
                landscape=False,
            )
            if status == "not_found":
                lines.append(f"MISS {filename}: {title}{note}")
                continue
            ui = dump_ui(SERIAL)
            time.sleep(settle_s)
        screencap(SERIAL, path)
        lines.append(f"OK   {path} — {title}")

    restore_rotation(SERIAL)

    readme = OUT / "README.md"
    readme.write_text(
        "# Play Store — phone screenshots (portrait)\n\n"
        f"Captured on **Phone_Medium** AVD (`{SERIAL}`), portrait, English UI.\n\n"
        + "\n".join(f"- {line}" for line in lines)
        + "\n\nRegenerate: `python3 scripts/capture-play-store-phone-screenshots.py`\n",
        encoding="utf-8",
    )
    print("\n".join(lines))
    print(f"\nSaved to {OUT}")


if __name__ == "__main__":
    main()
