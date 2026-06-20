# Offline & slow network

## Snapshot rollback

Before offline UI and route pack work, baseline was tagged:

```bash
git reset --hard snapshot/pre-offline-2026-05-19
```

Branch: `snapshot/2026-05-19-pre-offline-features`

## Status banner (all tabs)

- Detects validated internet via `NetworkConnectivityMonitor`
- Shows when offline with cached forecast, stale data (>6 h / >12 h), or load failure
- Weather uses existing Room `forecast_cache` (per location, 3 sources)

## Offline route pack (Route tab, premium)

With start + end and route geometry:

1. **Map tiles** — HTTP cache along route bbox (zoom 8–13, capped tiles/zoom)
2. **Weather** — MET/SMHI/FMI at sample points (~every 25 nm, max 24)
3. **Marine weather** — one overview fetch at route midpoint (session cache only today)
4. **Metadata** — `offline_area_pack` Room row (latest pack)

Use in harbour on Wi‑Fi before departure. AIS, live radar, and harbors still need network.

## Not covered yet

- Marine weather Room cache
- AIS last-known positions offline
- MapLibre true offline regions
- Route geometry persistence across app restart
