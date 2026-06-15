#!/usr/bin/env python3
"""Quick smoke test: cold start, prefetch cache, tab navigation — no crashes."""

from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PACKAGE = "fi.veneappi.app"
ACTIVITY = f"{PACKAGE}/.MainActivity"

DEVICES = {
    "emulator-5554": "Phone_Small",
    "emulator-5556": "Phone_Medium",
    "emulator-5558": "Phone_Large",
    "emulator-5560": "Medium_Tablet",
}

TABS = [
    ("compare", r"Map\s*&"),
    ("route", r"Route planning"),
    ("wind", r"Wind 12\+ days"),
    ("marine", r"Weather forecast"),
    ("storm", r"Radar\s*&"),
]


def adb(serial: str, *args: str, check: bool = True) -> str:
    cmd = [str(ADB), "-s", serial, *args]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb failed: {' '.join(cmd)}\n{result.stderr}")
    return (result.stdout + result.stderr).strip()


def device_online(serial: str) -> bool:
    try:
        return adb(serial, "get-state", check=False) == "device"
    except Exception:
        return False


def cold_start(serial: str) -> None:
    adb(serial, "logcat", "-c", check=False)
    adb(serial, "shell", "am", "force-stop", PACKAGE, check=False)
    time.sleep(0.4)
    adb(serial, "shell", "am", "start", "-W", "-n", ACTIVITY, check=False)


def top_activity(serial: str) -> str:
    out = adb(serial, "shell", "dumpsys", "activity", "activities", check=False)
    for line in out.splitlines():
        if "topResumedActivity" in line:
            return line.strip()
    return "unknown"


def fatal_lines(serial: str) -> list[str]:
    out = adb(serial, "logcat", "-d", check=False)
    hits = []
    for line in out.splitlines():
        if "FATAL EXCEPTION" in line or "AndroidRuntime" in line and "FATAL" in line:
            hits.append(line)
        elif "Process: fi.veneappi.app" in line and "has died" in line:
            hits.append(line)
    return hits[:5]


def map_cache_files(serial: str) -> int:
    out = adb(serial, "shell", f"run-as {PACKAGE} ls cache/map-http 2>&1", check=False)
    if "No such file" in out or "not debuggable" in out:
        return -1
    return len([ln for ln in out.splitlines() if ln and not ln.startswith("total")])


def dump_ui(serial: str) -> str:
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    time.sleep(0.4)
    return adb(serial, "shell", "cat", "/sdcard/ui.xml", check=False)


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        raise ValueError(bounds)
    return tuple(int(g) for g in m.groups())


def center(bounds: tuple[int, int, int, int]) -> tuple[int, int]:
    x1, y1, x2, y2 = bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def rail_clickables(ui: str) -> list[tuple[int, int, int, int]]:
    items: list[tuple[int, int, int, int]] = []
    for m in re.finditer(
        r'clickable="true"[^>]*bounds="(\[(\d+),(\d+)\]\[(\d+),(\d+)\])"',
        ui,
    ):
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        if x2 <= 320:
            items.append((x1, y1, x2, y2))
    items.sort(key=lambda b: b[1])
    return items


def find_tab_tap(ui: str, pattern: str, tab_index: int) -> tuple[int, int] | None:
    for text, bounds in re.findall(
        r'<node[^>]*text="([^"]*)"[^>]*bounds="(\[[^\]]+\]\[[^\]]+\])"[^>]*/?>',
        ui,
    ):
        if re.search(pattern, text.replace("&#10;", "\n")):
            return center(parse_bounds(bounds))

    for block in ui.split("<node "):
        if not re.search(pattern, block.replace("&#10;", " ")):
            continue
        m = re.search(r'clickable="true"[^>]*bounds="(\[[^\]]+\]\[[^\]]+\])"', block)
        if m:
            return center(parse_bounds(m.group(1)))

    rail = rail_clickables(ui)
    if tab_index < len(rail):
        return center(rail[tab_index])
    return None


def tap(serial: str, x: int, y: int) -> None:
    adb(serial, "shell", "input", "tap", str(x), str(y))
    time.sleep(2.0)


def set_portrait(serial: str) -> None:
    adb(serial, "shell", "settings", "put", "system", "accelerometer_rotation", "0", check=False)
    adb(serial, "shell", "settings", "put", "system", "user_rotation", "0", check=False)
    time.sleep(1.0)


def test_device(serial: str, name: str) -> list[str]:
    results: list[str] = []
    if not device_online(serial):
        return [f"SKIP {name}: offline"]

    set_portrait(serial)
    cold_start(serial)
    time.sleep(6.5)

    top = top_activity(serial)
    if PACKAGE not in top:
        results.append(f"FAIL {name}: not in foreground — {top}")
    else:
        results.append(f"OK   {name}: cold start")

    fatals = fatal_lines(serial)
    if fatals:
        results.append(f"FAIL {name}: crash — {fatals[0][:120]}")
    else:
        results.append(f"OK   {name}: no fatal log")

    cache_n = map_cache_files(serial)
    if cache_n < 0:
        results.append(f"WARN {name}: map-http cache not readable")
    elif cache_n < 2:
        results.append(f"WARN {name}: map-http cache sparse ({cache_n} files)")
    else:
        results.append(f"OK   {name}: map-http cache ({cache_n} files)")

    ui = dump_ui(serial)
    nav_ok = 0
    nav_fail: list[str] = []
    for idx, (tab_id, pattern) in enumerate(TABS):
        coords = find_tab_tap(ui, pattern, idx)
        if coords is None:
            nav_fail.append(tab_id)
            continue
        tap(serial, *coords)
        ui = dump_ui(serial)
        if PACKAGE not in top_activity(serial):
            nav_fail.append(f"{tab_id}(died)")
            break
        if fatal_lines(serial):
            nav_fail.append(f"{tab_id}(crash)")
            break
        nav_ok += 1

    if nav_fail:
        results.append(f"FAIL {name}: tabs missing/failed — {', '.join(nav_fail)}")
    else:
        results.append(f"OK   {name}: all {nav_ok} tabs navigated")

    return results


def main() -> int:
    all_lines: list[str] = []
    failures = 0
    for serial, name in DEVICES.items():
        lines = test_device(serial, name)
        all_lines.extend(lines)
        failures += sum(1 for ln in lines if ln.startswith("FAIL"))

    print("\n".join(all_lines))
    print(f"\nSummary: {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
