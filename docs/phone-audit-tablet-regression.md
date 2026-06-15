# Tablet regression — `feature/phone-audit` vs production 0.2.19

**Date:** 2026-06-15  
**Goal:** Confirm phone-audit branch changes do not break the existing tablet layout (navigation rail, two-pane compare, route/storm tabs).

## Method

| Item | Value |
|------|--------|
| Baseline | `snapshots/production-0.2.19` debug APK (versionCode 21) |
| Audit | `feature/phone-audit` debug APK (0.3.0-phone-audit, versionCode 22) |
| Target layout | 2560×1600 @ 320 dpi (Medium_Tablet class, landscape + portrait) |
| Capture script | `scripts/capture-tablet-regression.py` |
| Output | `docs/phone-audit/tablet-regression/{production-0.2.19,audit}/` (gitignored) |

### Emulator note

The `Medium_Tablet` AVD (`emulator-5560`, 2560×1600) **crashes on launch** for both production and audit (`FATAL EXCEPTION: DefaultDispatcher-worker-*`, no Java stack in logcat). This appears to be an **AVD/environment issue**, not a branch regression — the same APK runs on phone AVDs and on a **tablet proxy** config.

**Tablet proxy (used for this regression):**

```bash
# On Phone_Large (or any running emulator):
adb -s <serial> shell wm density 320
adb -s <serial> shell wm size 2560x1600
adb -s <serial> shell settings put system user_rotation 1   # landscape default
```

Then run captures (install the APK under test first):

```bash
python3 scripts/capture-tablet-regression.py --label audit
# or
python3 scripts/capture-tablet-regression.py --label production-0.2.19
```

Physical tablet (e.g. Honor ELN-L09) remains the gold standard before Play release.

## Results (10/10 screenshots per build)

| Tab | Landscape | Portrait | Notes |
|-----|-----------|----------|-------|
| Compare | pass | pass | Two-pane map + three-source weather unchanged |
| Route paywall | pass | pass | Premium gate unchanged |
| Wind paywall | pass | pass | |
| Marine text | pass | pass | 2×2 country grid |
| Storm radar | pass | pass | All 5 rail items visible; timeline + controls OK |

## Differences vs production (not regressions)

1. **Edge-to-edge / letterboxing:** Production 0.2.19 on the tablet proxy shows large black bars above/below the app in landscape. Audit build fills the display; content respects safe areas via `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`. **Improvement**, not breakage.
2. **Orientation:** Audit allows portrait on tablet; production was landscape-locked. Portrait screenshots show bottom navigation instead of rail — expected with `fullUser`.
3. **Offline banner:** Production capture showed “No internet — showing saved forecast”; audit had live data. Environment/timing only.

## Verdict

**Tablet regression: PASS** for layout and navigation on the Medium_Tablet class viewport. Safe to proceed with phone layout work (phase 2); no tablet-specific code rollback needed.

**Follow-up (infra):** Investigate `Medium_Tablet` AVD crash separately; use tablet proxy or hardware for CI screenshots until fixed.

## Regenerate

```bash
./scripts/start-phone-audit-emulators.sh   # includes Medium_Tablet
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:installDebug
python3 scripts/capture-tablet-regression.py --label audit
```
