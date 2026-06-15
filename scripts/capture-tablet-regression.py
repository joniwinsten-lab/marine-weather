#!/usr/bin/env python3
"""Capture tablet regression screenshots on Medium_Tablet (landscape + portrait)."""

from __future__ import annotations

import argparse
import importlib.util
import subprocess
import sys
import time
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "cap_phone",
    Path(__file__).resolve().parent / "capture-phone-audit-screenshots.py",
)
_cap = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(_cap)

ADB = _cap.ADB
ORIENTATIONS = _cap.ORIENTATIONS
TABS = _cap.TABS
capture_tab = _cap.capture_tab
dump_ui = _cap.dump_ui
launch_app = _cap.launch_app
restore_display_size = _cap.restore_display_size
screencap = _cap.screencap
set_rotation = _cap.set_rotation


def adb(serial: str, *args: str, check: bool = True) -> str:
    cmd = [str(ADB), "-s", serial, *args]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb failed: {' '.join(cmd)}\n{result.stderr}")
    return result.stdout.strip()


def find_tablet_serial() -> str:
    out = subprocess.run(
        [str(ADB), "devices"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    candidates: list[str] = []
    for line in out.splitlines()[1:]:
        if "\tdevice" not in line:
            continue
        serial = line.split("\t")[0]
        candidates.append(serial)

    for serial in candidates:
        name = adb(serial, "shell", "getprop", "ro.kernel.qemu.avd_name", check=False)
        if "Medium_Tablet" in name:
            return serial
        size = adb(serial, "shell", "wm", "size", check=False)
        if "2560x1600" in size or "1600x2560" in size:
            return serial

    raise RuntimeError(
        "Medium_Tablet emulator not found (expected 2560×1600). Start:\n"
        "  emulator -avd Medium_Tablet\n"
        "Or configure a proxy: adb shell wm size 2560x1600 && wm density 320\n"
        f"Connected: {candidates or 'none'}",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--label",
        required=True,
        help="Output subfolder name, e.g. audit or production-0.2.19",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "docs/phone-audit/tablet-regression",
        help="Base output directory",
    )
    args = parser.parse_args()

    serial = find_tablet_serial()
    out_dir = args.out / args.label
    out_dir.mkdir(parents=True, exist_ok=True)

    summary: list[str] = [f"device={serial}", f"label={args.label}"]

    for orient_name, rotation in ORIENTATIONS:
        set_rotation(serial, rotation)
        launch_app(serial)
        ui = dump_ui(serial)
        landscape = rotation == 1
        pending_restore: tuple[int, int] | None = None

        for tab_id, pattern in TABS:
            filename = f"Medium_Tablet_{orient_name}_{tab_id}.png"
            path = out_dir / filename
            if tab_id != "compare":
                status, note, restore = capture_tab(
                    serial,
                    ui,
                    pattern,
                    tab_id=tab_id,
                    landscape=landscape,
                )
                if status == "not_found":
                    summary.append(f"MISS {filename}{note}")
                    continue
                if restore is not None:
                    pending_restore = restore
                ui = dump_ui(serial)
            else:
                _, _, restore = capture_tab(
                    serial,
                    ui,
                    pattern,
                    tab_id=tab_id,
                    landscape=landscape,
                )
                if restore is not None:
                    pending_restore = restore
                time.sleep(1)

            screencap(serial, path)
            summary.append(f"OK   {filename}")
            if pending_restore is not None:
                restore_display_size(serial, pending_restore)
                pending_restore = None

    readme = out_dir / "README.md"
    readme.write_text(
        f"# Tablet regression — {args.label}\n\n"
        + "\n".join(f"- {line}" for line in summary)
        + "\n",
        encoding="utf-8",
    )
    print("\n".join(summary))
    print(f"\nSaved to {out_dir}")


if __name__ == "__main__":
    main()
