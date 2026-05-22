# Google Play — Data safety form (draft answers)

Use these when filling **App content → Data safety** for **Marine Weather** (`fi.veneappi.app`). Adjust if the app changes.

## Collects or shares?

| Data type | Collected? | Shared? | Purpose | Notes |
|-----------|------------|---------|---------|-------|
| Approximate location | Yes (optional) | With third-party APIs only | App functionality | User grants permission; sent to FMI/MET/SMHI/OSM tile servers as part of forecast/map requests, not to a developer backend |
| Precise location | Same as above | Same | App functionality | GPS for map centre / route |
| App interactions | No | — | — | No analytics SDK |
| Crash logs | No* | — | — | *Unless you later enable Play Vitals only (Google-collected) |
| Purchase history | Yes | With Google | App functionality | Play Billing for premium |
| Other user-generated content | No | — | — | Routes stay on device unless user exports |

## Security practices

- Data encrypted in transit: **Yes** (HTTPS to public APIs and Play)
- Users can request deletion: **Uninstall / clear app data** (no server account)
- Committed to Play Families: **No** (not a children’s app)

## Ads

**No** — app does not contain ads.

## Account

**No** sign-in required.

## Privacy policy URL

`https://joniwinsten-lab.github.io/marine-weather/privacy.html`
