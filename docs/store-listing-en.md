# Google Play — Store listing (English, default)

Use **Marine Weather** as the app name everywhere. Default store language: **English (United States or United Kingdom)**. Add Finnish, Swedish, and Norwegian localisations later if you want.

## Short description (max 80 characters)

```
Marine maps & multi-source weather. Route planning with premium (Play).
```

(79 characters)

## Full description

```
Marine Weather helps you plan trips on Finnish and Baltic waters with an interactive map and forecasts from several national services side by side.

FREE
• Map with open data (landscape tablet friendly)
• Compare point forecasts from MET Norway, SMHI, FMI and related sources
• Storm radar and lightning overlays where open data is available
• National marine text summaries at the map centre
• Wind units (m/s or knots)

PREMIUM (Google Play)
• Route planning with weather along the route
• 12-day wind outlook
• Export route as GPX or PDF
• One-time purchase or monthly subscription — prices shown in Play before you buy

FREE (also included)
• Storm radar and lightning
• National marine text summaries
• Map & multi-source point forecasts

IMPORTANT
Open maps and forecasts are planning aids only. They do not replace official nautical charts, AIS, or regulations. Not for primary navigation.

Data & privacy
Location is used on-device to centre the map and request public weather APIs. No developer login server. See our privacy policy URL in the store listing.

Languages in app: English (default), Finnish, Swedish, Norwegian Bokmål.
```

## Category

Maps & Navigation (or Weather — pick one primary; Maps & Navigation fits route planning)

## Contact

**support@safelight.fi** (required for Play — use in Console store listing and developer contact)

## Device targeting

- **Tablets** (phones filtered via `requiresSmallestWidthDp=600` in manifest)
- Mention in store listing: **designed for landscape use on Android tablets**

## Graphics checklist

| Asset | Size | File in repo |
|-------|------|----------------|
| App icon | 512×512 PNG | `docs/play-store-icon-512.png` |
| Feature graphic | 1024×500 | *capture manually* |
| Phone/tablet screenshots | min 2, landscape | *capture on device* |

## In-app product IDs (must match code exactly)

| Type | Product ID |
|------|------------|
| One-time | `route_premium_lifetime` |
| Subscription | `marine_weather_premium` (base plan `route-premium-monthly`) |

## Privacy policy URL

After GitHub Pages is enabled:

`https://joniwinsten-lab.github.io/marine-weather/privacy.html`
