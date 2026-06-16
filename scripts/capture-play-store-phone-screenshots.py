#!/usr/bin/env python3
"""Capture portrait phone screenshots for Play Store listing (Phone_Medium AVD)."""

from __future__ import annotations

import importlib.util
import sys
import time
from pathlib import Path

_audit = Path(__file__).resolve().parent / "capture-phone-audit-screenshots.py"
_spec = importlib.util.spec_from_file_location("audit_cap", _audit)
_mod = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(_mod)

adb = _mod.adb
capture_tab = _mod.capture_tab
dump_ui = _mod.dump_ui
launch_app = _mod.launch_app
restore_rotation = _mod.restore_rotation
screencap = _mod.screencap
set_rotation = _mod.set_rotation

SERIAL = "emulator-5556"
OUT = Path(__file__).resolve().parent.parent / "docs/play-store-phone-screenshots"

TABS = [
    ("01_compare_map_weather", r"Map\s*&", "Map & weather compare"),
    ("02_storm_radar", r"Radar\s*&", "Storm radar & lightning"),
    ("03_marine_text", r"Weather forecast", "National marine text"),
    ("04_route_premium", r"Route planning", "Route planning (premium)"),
    ("05_wind_12day", r"Wind 12\+ days", "12-day wind outlook (premium)"),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    state = adb(SERIAL, "get-state", check=False)
    if state != "device":
        raise SystemExit(f"{SERIAL} not ready ({state})")

    set_rotation(SERIAL, 0)
    launch_app(SERIAL)
    time.sleep(2)
    ui = dump_ui(SERIAL)
    lines: list[str] = []

    for idx, (file_id, pattern, title) in enumerate(TABS):
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
            time.sleep(1)
        screencap(SERIAL, path)
        lines.append(f"OK   {path} — {title}")

    restore_rotation(SERIAL)

    readme = OUT / "README.md"
    readme.write_text(
        "# Play Store — phone screenshots (portrait)\n\n"
        f"Captured on **Phone_Medium** AVD (`{SERIAL}`), portrait, English UI.\n\n"
        + "\n".join(f"- {line}" for line in lines)
        + "\n",
        encoding="utf-8",
    )
    print("\n".join(lines))
    print(f"\nSaved to {OUT}")


if __name__ == "__main__":
    main()
