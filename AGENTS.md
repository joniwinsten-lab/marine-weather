# Agent instructions (Marine Weather Android)

- Work in `/Users/Safelight/Veneappi` unless the user explicitly asks about iOS.
- Do not edit `/Users/Safelight/marine-weather-ios` unless the user asks (iOS agents maintain parity doc from their side).
- **Parity doc lives here:** `docs/feature-parity.md` (canonical).

## Feature parity (required)

Before and after any **user-facing** change:

1. Read **`docs/feature-parity.md`**
2. Update the feature matrix + changelog in that file
3. If iOS must follow, leave status `android-only` until ported (or ask user to port iOS)

Premium contract (must match iOS):

- **Free:** map & weather compare, rain radar, marine weather
- **Premium:** route, 12-day wind, AIS, route on compare map, offline route pack

## Stable views (user rule)

Do not change Map & weather or Route UI unless the user asks explicitly — see `.cursor/rules/veneappi-stable-views.mdc`.

## Reference

- `docs/ios-porting-inventory.md` (in iOS repo) — technical mirror
- `docs/offline-features.md`, `docs/play-billing-products.md`
- `.cursor/rules/terminology.mdc` — user-facing names (Sadetutka, merisää, …)
