# Phase 0 — phone UI audit

**Branch:** `feature/phone-audit` only — **`main` / Play production stays 0.2.19 (versionCode 21).**

Audit build: **0.3.0-phone-audit** (versionCode 22) — debug install only until phase 2 sign-off.

## What changed on this branch

| Area | Change |
|------|--------|
| `AndroidManifest.xml` | Removed `requiresSmallestWidthDp=600`; all screen sizes enabled |
| Orientation | `fullUser` — portrait + landscape (removed landscape lock) |
| `MainActivity` | Removed `requestedOrientation = SENSOR_LANDSCAPE` |
| `VeneappiRoot` | `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` |
| Version | 22 / `0.3.0-phone-audit` (not published to Play) |

## Google edge-to-edge (targetSdk 35)

We already call `enableEdgeToEdge()` in `MainActivity`. With **targetSdk 35**, Android 15+ draws behind transparent system bars.

Google’s guidance (2025):

1. **Do not opt out long-term** — `windowOptOutEdgeToEdgeEnforcement` is temporary; Android 16 removes the escape hatch.
2. **Draw backgrounds edge-to-edge**; **inset interactive content** with `WindowInsets.safeDrawing` (status bar, nav bar, display cutout).
3. **Material 3 `Scaffold`** + `contentWindowInsets = WindowInsets.safeDrawing` passes safe padding to content — bottom `NavigationBar` handles its own bar insets.
4. **Maps** can stay full-bleed inside the padded content area; avoid placing tap targets under gesture/nav bars.

References:

- [Edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [Window insets](https://developer.android.com/develop/ui/compose/system/insets)
- [Insets handling tips (Android 15)](https://medium.com/androiddevelopers/insets-handling-tips-for-android-15s-edge-to-edge-enforcement-872774e8839b)

## Emulator matrix

| AVD | Approx. class | Portrait dp | Use |
|-----|---------------|-------------|-----|
| `Phone_Small` | Pixel 4a | ~393×851 | Narrow phone |
| `Phone_Medium` | Pixel 8 | ~411×914 | Common phone |
| `Phone_Large` | Pixel 8 Pro | ~448×935 | Large phone |
| `Medium_Tablet` | Tablet | landscape | Regression vs production layout |

```bash
./scripts/create-phone-audit-avds.sh   # once
./scripts/start-phone-audit-emulators.sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:installDebug
```

## Screenshot checklist (per device × orientation)

For each tab: **Compare**, **Route** (premium), **12-day wind**, **Marine text**, **Storm radar**, **Paywall** (logged out).

Record:

- [ ] Status bar / cutout overlaps text or chips
- [ ] Bottom nav tappable (not under gesture bar)
- [ ] Map controls reachable
- [ ] Weather cards readable (scroll if stacked)
- [ ] Route weather strip / speed field usable
- [ ] Storm radar timeline visible
- [ ] Attribution bar not clipped

Save screenshots to `docs/phone-audit/screenshots/` (gitignored) or attach to issues.

**Status (2026-06-15):** 30 screenshots captured via `scripts/capture-phone-audit-screenshots.py` on `Phone_Small`, `Phone_Medium`, `Phone_Large`. See `docs/phone-audit/screenshots/README.md`.

**P0 (landscape):** On phones with ~1080px landscape height, the 5th nav-rail item (Storm radar) is clipped and not tappable at native resolution.

## Exit gate (phase 0 → phase 1)

- Gap list prioritised (P0 = unusable, P1 = ugly, P2 = polish)
- Tablet `Medium_Tablet` regression: no new breakage vs `main`
- Decision: ship manifest unlock as 0.2.20 internal test vs wait for layout fixes
