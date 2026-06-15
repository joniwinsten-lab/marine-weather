# Phase 2 — phone layout polish

**Branch:** `feature/phone-audit`  
**Build:** 0.3.1-phone-layout (versionCode 23)

## Fixes in this phase

| ID | Priority | Issue | Fix |
|----|----------|-------|-----|
| PHN-01 | P0 | Storm tab clipped in landscape `NavigationRail` on ~1080px-tall phones | Bottom bar when `screenHeightDp < 420`; scrollable + icon-only rail on marginal heights |
| PHN-02 | P1 | Compare weather column clipped in phone landscape (dense 3-stack in short column) | Require `minHeight 420dp` for dense layout; fall back to scroll cards |
| PHN-03 | P1 | Weather scroll column ignored parent height in side column | `fillMaxSize()` + `verticalScroll` on scroll branch |

## Gap list (remaining)

| ID | Priority | Issue | Status |
|----|----------|-------|--------|
| PHN-04 | P1 | Portrait compare: weather cards need scroll | Fixed via PHN-03 (0.3.1) |
| PHN-05 | P2 | Landscape rail labels truncated | Fixed — bottom bar on phones (0.3.2) |
| PHN-06 | P2 | Route pane portrait controls density | Fixed 0.3.2 — scroll route weather + paywall padding |
| PHN-07 | infra | `Medium_Tablet` AVD crash on launch | Emulator had no DNS; fixed prefetch crash on network errors (0.3.3) |

## Verify

```bash
./gradlew :app:installDebug
python3 scripts/capture-phone-audit-screenshots.py
```

Check `Phone_Small_landscape_storm_radar.png` — storm tab reachable without `wm size` workaround (0.3.2+).

## Tablet regression (re-run 2026-06-15)

`./scripts/run-tablet-regression.sh audit` — **10/10 OK** on 2560×1600 proxy.

## Tablet regression

Re-run after layout changes:

```bash
./scripts/run-tablet-regression.sh audit
```

Expected: still **PASS** (rail scroll is transparent on tall tablets).
