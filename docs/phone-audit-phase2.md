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
| PHN-04 | P1 | Portrait compare: weather cards need scroll (partially visible MET card) | Improved via PHN-03 |
| PHN-05 | P2 | Landscape rail labels truncated ("Radar & lightni…") | Icon-only mode on short height |
| PHN-06 | P2 | Route pane portrait controls density | Deferred |
| PHN-07 | infra | `Medium_Tablet` AVD crash on launch | Use tablet proxy — see tablet-regression doc |

## Verify

```bash
./gradlew :app:installDebug
python3 scripts/capture-phone-audit-screenshots.py
```

Check `Phone_Small_landscape_storm_radar.png` — storm tab reachable without `wm size` workaround.

## Tablet regression

Re-run after layout changes:

```bash
./scripts/run-tablet-regression.sh audit
```

Expected: still **PASS** (rail scroll is transparent on tall tablets).
