# Production-ready snapshot (0.2.14)

**Date:** 2026-05-27  
**Status:** Verified working in closed testing — suitable baseline for production release.

This snapshot captures Marine Weather Android when **billing prices, AIS, offline route pack, and paywall diagnostics** were confirmed working on a Play closed-test install.

## Version identifiers

| Field | Value |
|-------|--------|
| `versionName` | **0.2.14** |
| `versionCode` | **16** |
| Package | `fi.veneappi.app` |
| Git tag | `snapshot/production-ready-0.2.14` |
| Git branch | `snapshot/2026-05-27-production-ready-0.2.14` |

## What works in this build

- **Play Billing:** `route_premium_lifetime` + `marine_weather_premium` prices load; paywall shows diagnostics on failure
- **3-day local trial** unlocks Route premium (including AIS)
- **AIS** on map (Digitraffic REST; gzip fix applied)
- **Offline route pack** download (compact button, no extra explanatory text)
- **Offline / stale weather** banner and cache fallback
- **Map & weather**, **Route**, storm radar, marine text, 12-day wind (premium)
- **Website** locales (EN/FI/SV/NB/ET) deployed separately

## Play Console products (must match)

| Type | Product ID |
|------|------------|
| One-time | `route_premium_lifetime` |
| Subscription | `marine_weather_premium` (base plan `route-premium-monthly`) |

See [play-billing-products.md](play-billing-products.md).

## Rebuild release AAB from this snapshot

```bash
cd /path/to/Veneappi
git checkout snapshot/production-ready-0.2.14
# Ensure keystore.properties exists (not in git)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Restore entire codebase to this snapshot

**Hard reset** (discards local changes):

```bash
git fetch --all
git checkout main
git reset --hard snapshot/production-ready-0.2.14
```

**Or checkout the snapshot branch:**

```bash
git checkout snapshot/2026-05-27-production-ready-0.2.14
```

**Or only read files without moving branch:**

```bash
git show snapshot/production-ready-0.2.14:app/build.gradle
```

## Earlier snapshots

| Snapshot | Purpose |
|----------|---------|
| `snapshot/pre-offline-2026-05-19` | Before offline banner + route area pack |
| `snapshot/production-ready-0.2.14` | **Current — go-to for production** |

## Notes before production

- Install testers **only via Play** (not adb APK) for billing
- Same Google account as license tester / closed test
- Privacy URL and Data safety form must stay aligned with [privacy.html](privacy.html) and [data-safety-play-console.md](data-safety-play-console.md)
- Do not publish `friends` build (`fi.veneappi.app.friends`)
