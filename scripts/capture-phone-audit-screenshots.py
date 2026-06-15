#!/usr/bin/env python3
"""Capture phase-0 phone audit screenshots via adb."""

from __future__ import annotations

import re
import subprocess
import time
from pathlib import Path

ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
OUT = Path(__file__).resolve().parent.parent / "docs/phone-audit/screenshots"
PACKAGE = "fi.veneappi.app/.MainActivity"

DEVICES = {
    "emulator-5554": "Phone_Small",
    "emulator-5556": "Phone_Medium",
    "emulator-5558": "Phone_Large",
}

# Bottom-nav labels (English UI) — portrait phone layout
TABS = [
    ("compare", r"Map\s*&"),
    ("route_paywall", r"Route planning"),
    ("wind_paywall", r"Wind 12\+ days"),
    ("marine_text", r"Weather forecast"),
    ("storm_radar", r"Radar\s*&"),
]

ORIENTATIONS = [
    ("portrait", 0),
    ("landscape", 1),
]


def adb(serial: str, *args: str, check: bool = True) -> str:
    cmd = [str(ADB), "-s", serial, *args]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb failed: {' '.join(cmd)}\n{result.stderr}")
    return result.stdout.strip()


def screencap(serial: str, dest: Path) -> None:
    remote = "/sdcard/audit_cap.png"
    adb(serial, "shell", "screencap", "-p", remote)
    adb(serial, "pull", remote, str(dest))
    adb(serial, "shell", "rm", remote, check=False)


def set_rotation(serial: str, rotation: int) -> None:
    adb(serial, "shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb(serial, "shell", "settings", "put", "system", "user_rotation", str(rotation))
    time.sleep(1.5)


def launch_app(serial: str) -> None:
    adb(serial, "shell", "am", "force-stop", PACKAGE.split("/")[0], check=False)
    time.sleep(0.5)
    adb(serial, "shell", "am", "start", "-n", PACKAGE)
    time.sleep(5)


def dump_ui(serial: str) -> str:
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    time.sleep(0.3)
    return adb(serial, "shell", "cat", "/sdcard/ui.xml")


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        raise ValueError(bounds)
    return tuple(int(g) for g in m.groups())


def center(bounds: tuple[int, int, int, int]) -> tuple[int, int]:
    x1, y1, x2, y2 = bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def screen_size(ui: str) -> tuple[int, int]:
    m = re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', ui)
    if m:
        return int(m.group(1)), int(m.group(2))
    return 1080, 2340


def rail_clickables(ui: str) -> list[tuple[int, int, int, int]]:
    """Left navigation-rail items (landscape, width >= 600dp)."""
    items: list[tuple[int, int, int, int]] = []
    for m in re.finditer(
        r'clickable="true"[^>]*bounds="(\[(\d+),(\d+)\]\[(\d+),(\d+)\])"',
        ui,
    ):
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        if x2 <= 260:
            items.append((x1, y1, x2, y2))
    items.sort(key=lambda b: b[1])
    return items


def find_tab_tap(ui: str, pattern: str, tab_index: int | None = None) -> tuple[int, int] | None:
    """Find clickable nav item containing label text, or rail index in landscape."""
    nodes = re.findall(
        r'<node[^>]*text="([^"]*)"[^>]*bounds="(\[[^\]]+\]\[[^\]]+\])"[^>]*/?>',
        ui,
    )
    for text, bounds in nodes:
        if re.search(pattern, text.replace("&#10;", "\n")):
            b = parse_bounds(bounds)
            return center(b)

    for block in ui.split("<node "):
        if not re.search(pattern, block.replace("&#10;", " ")):
            continue
        m = re.search(r'clickable="true"[^>]*bounds="(\[[^\]]+\]\[[^\]]+\])"', block)
        if m:
            return center(parse_bounds(m.group(1)))
        m = re.search(r'bounds="(\[[^\]]+\]\[[^\]]+\])"', block)
        if m:
            return center(parse_bounds(m.group(1)))

    if tab_index is not None:
        rail = rail_clickables(ui)
        if tab_index < len(rail):
            return center(rail[tab_index])
    return None


def physical_display_size(serial: str) -> tuple[int, int] | None:
    out = adb(serial, "shell", "wm", "size")
    m = re.search(r"Physical size:\s*(\d+)x(\d+)", out)
    if m:
        return int(m.group(1)), int(m.group(2))
    return None


def bump_landscape_height(serial: str, extra_px: int = 220) -> tuple[int, int] | None:
    """Temporarily increase landscape height so clipped rail items appear."""
    phys = physical_display_size(serial)
    if phys is None:
        return None
    pw, ph = phys
    rotation = adb(serial, "shell", "settings", "get", "system", "user_rotation").strip()
    if rotation in ("1", "3"):
        # wm size is portrait-oriented; bump the short edge so landscape height grows.
        short_edge, long_edge = min(pw, ph), max(pw, ph)
        adb(serial, "shell", "wm", "size", f"{short_edge + extra_px}x{long_edge}")
    else:
        adb(serial, "shell", "wm", "size", f"{pw}x{ph + extra_px}")
    time.sleep(1)
    return pw, ph


def restore_display_size(serial: str, size: tuple[int, int] | None) -> None:
    if size is None:
        return
    adb(serial, "shell", "wm", "size", "reset")
    time.sleep(0.5)


def tap(serial: str, x: int, y: int) -> None:
    adb(serial, "shell", "input", "tap", str(x), str(y))
    time.sleep(2.5)



def capture_tab(
    serial: str,
    ui: str,
    pattern: str,
    *,
    tab_id: str,
    landscape: bool,
) -> tuple[str, str, tuple[int, int] | None]:
    """Return (status, note, display_size_to_restore_after_screencap)."""
    coords = find_tab_tap(ui, pattern)
    note = ""
    pending_restore: tuple[int, int] | None = None

    if coords is None and landscape and tab_id == "storm_radar":
        phys = bump_landscape_height(serial, extra_px=220)
        launch_app(serial)
        ui = dump_ui(serial)
        coords = find_tab_tap(ui, pattern)
        if coords is not None:
            note = " (landscape rail clipped — +220px height workaround)"
            pending_restore = phys
        else:
            restore_display_size(serial, phys)

    if coords is None:
        return "not_found", note, None
    tap(serial, *coords)
    time.sleep(1)
    return "ok", note, pending_restore


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    summary: list[str] = []

    for serial, device_name in DEVICES.items():
        try:
            state = adb(serial, "get-state")
        except RuntimeError:
            summary.append(f"{device_name}: offline — skipped")
            continue
        if state != "device":
            summary.append(f"{device_name}: {state} — skipped")
            continue

        for orient_name, rotation in ORIENTATIONS:
            set_rotation(serial, rotation)
            launch_app(serial)
            ui = dump_ui(serial)

            landscape = rotation == 1
            pending_restore: tuple[int, int] | None = None
            for tab_id, pattern in TABS:
                filename = f"{device_name}_{orient_name}_{tab_id}.png"
                path = OUT / filename
                if tab_id != "compare":
                    status, note, restore = capture_tab(
                        serial,
                        ui,
                        pattern,
                        tab_id=tab_id,
                        landscape=landscape,
                    )
                    if status == "not_found":
                        summary.append(f"MISS {filename}: nav not found{note}")
                        continue
                    if restore is not None:
                        pending_restore = restore
                    ui = dump_ui(serial)
                else:
                    status, note, restore = capture_tab(
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
                summary.append(f"OK   {filename}{note}")
                if pending_restore is not None:
                    restore_display_size(serial, pending_restore)
                    pending_restore = None

    readme = OUT / "README.md"
    readme.write_text(
        "# Phone audit screenshots (auto-captured)\n\n"
        + "\n".join(f"- {line}" for line in summary)
        + "\n",
        encoding="utf-8",
    )
    print("\n".join(summary))
    print(f"\nSaved to {OUT}")


if __name__ == "__main__":
    main()
