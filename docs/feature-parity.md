# Marine Weather — feature parity (Android ↔ iOS)

**Canonical document.** Both apps must stay aligned on user-facing behaviour.

| | |
|---|---|
| **Android** | `/Users/Safelight/Veneappi` · open beta **0.3.1** (`versionCode` 27); Play production **0.2.19** |
| **iOS** | `/Users/Safelight/marine-weather-ios` · marketing **0.3.0** |
| **Technical porting map** | iOS: `docs/ios-porting-inventory.md` (API URLs, file paths) |
| **Last parity audit** | 2026-06-15 |

---

## For agents and developers (required)

Whenever you change **user-facing** behaviour, premium gates, APIs, offline rules, billing, or localization in **either** repository:

1. **Read this file first** if the change touches an existing row.
2. **Update the matrix** below (status + notes + “Last updated”).
3. **Append one line** to [Changelog](#changelog).
4. If only one platform is done, set status to `android-only` or `ios-only` and note the sibling task — do **not** mark `sync` until both match.
5. If the change is intentionally platform-specific, set status to `exception` and explain in Notes.

**Definition of done (feature work):**

- [ ] Behaviour matches premium table (free vs premium)
- [ ] Locales en / fi / sv / nb (if user-visible strings changed)
- [ ] `feature-parity.md` updated
- [ ] Sibling platform ported or tracked as `android-only` / `ios-only`

---

## Premium boundaries (product contract)

| Feature | Free | Premium |
|---------|:----:|:-------:|
| Map + 3-source weather compare | ✓ | |
| Rain radar & lightning | ✓ | |
| Marine weather (4 countries) | ✓ | |
| Route planning + weather along route | | ✓ |
| 12-day wind outlook | | ✓ |
| AIS overlay (Digitraffic) | | ✓ |
| AIS vessel watchlist (Seuranta tab) | | ✓ |
| Route line on Compare map | | ✓ |
| Offline route pack | | ✓ |

Billing intent (store IDs differ by platform):

| | Android (Play) | iOS (App Store) |
|---|----------------|-----------------|
| Lifetime | `route_premium_lifetime` | `route-premium-lifetime` |
| Monthly | `marine_weather_premium` | `route-premium-monthly` |
| Local trial | 3 days, device-local | 3 days, device-local |

---

## Status legend

| Status | Meaning |
|--------|---------|
| `sync` | Same user-facing behaviour on both platforms |
| `android-only` | Done on Android; iOS not yet (or not planned) |
| `ios-only` | Done on iOS; Android not yet |
| `partial` | Both have it; material differences remain |
| `deferred` | Agreed backlog on **both** |
| `exception` | Intentionally different; documented in Notes |
| `n/a` | Not applicable (e.g. Play Console vs App Store Connect) |

---

## Feature matrix

| ID | Feature | Tier | Android | iOS | Notes | Updated |
|----|---------|------|---------|-----|-------|---------|
| NAV-01 | 6 tabs: Compare, Route, Track, 12-day wind, Marine weather, Rain radar | — | android-only | sync | Premium badges on Route + Track + 12-day; iOS still 5 tabs | 2026-06-21 |
| MAP-01 | OpenFreeMap Liberty basemap | Free | sync | sync | Same style URL | 2026-05-24 |
| MAP-02 | Traficom nautical WMTS (`Merikarttasarjat public`) | Free | sync | sync | zoom 5–15, bounds 17–32°E, 58–71°N | 2026-05-24 |
| MAP-03 | Forecast location pin (long-press) | Free | sync | sync | | 2026-05-24 |
| MAP-04 | Scale bar | Free | sync | sync | | 2026-05-24 |
| MAP-05 | Map tile warmup on launch / splash | Free | sync | sync | | 2026-05-24 |
| MAP-06 | Shared map center across tabs | Free | sync | android-only | Fresh Fused Location on my-location + startup (not stale cache); iOS port pending | 2026-06-17 |
| MAP-07 | Route overlay on Compare map | Premium | sync | sync | Premium gate | 2026-05-24 |
| WTH-01 | MET + SMHI + FMI compact compare | Free | sync | sync | User-Agent on all HTTP | 2026-05-24 |
| WTH-02 | Forecast time strip / slot picker | Free | sync | sync | | 2026-05-24 |
| WTH-03 | Wind m/s ↔ knots | Free | sync | sync | | 2026-05-24 |
| WTH-04 | Two-pane layout (~65/35) on wide screens | Free | sync | sync | Android: width≥600dp; iOS: iPad | 2026-05-24 |
| WTH-05 | SwiftData / Room offline forecast cache | Free | sync | sync | | 2026-05-24 |
| STORM-01 | FMI WMS radar + timeline ±3h / 30min | Free | sync | sync | | 2026-05-24 |
| STORM-02 | MET/SMHI geo radar fallback | Free | sync | sync | | 2026-05-24 |
| STORM-03 | Lightning FMI WFS + SMHI CSV | Free | sync | sync | Filtered by frame time | 2026-05-24 |
| STORM-04 | FMI HARMONIE GRIB on timeline | Free | sync | sync | | 2026-05-24 |
| STORM-05 | Animation play/pause/step + slider | Free | sync | sync | | 2026-05-24 |
| MAR-01 | Marine weather 2×2 country grid | Free | sync | sync | MET, SMHI, FMI, EE | 2026-05-24 |
| MAR-02 | Summarizer + alert classifier | Free | sync | sync | | 2026-05-24 |
| MAR-03 | Open full forecast in browser | Free | sync | sync | | 2026-05-24 |
| RTE-01 | Route draw + Väylä fairway routing | Premium | sync | sync | Great-circle fallback both | 2026-05-24 |
| RTE-02 | Weather along route (3 sources) | Premium | sync | sync | | 2026-05-24 |
| RTE-03 | Boat speed / ETA | Premium | sync | sync | | 2026-05-24 |
| RTE-04 | Navigation disclaimer | Premium | sync | sync | | 2026-05-24 |
| RTE-05 | GPX export | Premium | sync | sync | | 2026-05-24 |
| RTE-06 | Route plan PDF export | Premium | sync | sync | | 2026-05-24 |
| WIND-01 | 12-day extended wind outlook | Premium | sync | sync | | 2026-05-24 |
| AIS-01 | Digitraffic AIS REST (~60s poll) | Premium | sync | sync | Viewport reload | 2026-05-24 |
| AIS-02 | AIS vessel detail sheet | Premium | sync | sync | | 2026-05-24 |
| AIS-03 | AIS MQTT live stream | Premium | android-only | deferred | Viewport MQTT + dead reckoning map (2 min) | 2026-06-09 |
| AIS-04 | AIS watchlist / Seuranta tab | Premium | android-only | deferred | DataStore watchlist (max 50), Hae browse, shared MQTT coordinator | 2026-06-21 |
| OFF-01 | Offline / stale banner (all tabs) | Free | sync | sync | NWPath + cache age | 2026-05-24 |
| OFF-02 | Offline route pack (tiles+weather+marine) | Premium | sync | sync | No Traficom tiles in pack | 2026-05-24 |
| BILL-01 | Lifetime + monthly IAP | Premium | sync | partial | Play live; ASC products not live | 2026-05-24 |
| BILL-02 | Restore purchases | Premium | sync | sync | | 2026-05-24 |
| BILL-03 | Paywall + billing diagnostics | Premium | sync | sync | Play trial/sub terms on paywall (0.2.19) | 2026-06-09 |
| LOC-01 | en / fi / sv / nb | — | sync | sync | | 2026-05-24 |
| LOC-02 | Attribution dialog (MET, SMHI, FMI, Traficom, AIS) | Free | sync | sync | | 2026-05-24 |
| SPLASH-01 | Branded splash | Free | sync | sync | | 2026-05-24 |
| HARBOR-01 | Harbors (Overpass) on map | — | deferred | deferred | Android code exists; not in nav | 2026-05-24 |
| DIST-01 | Store distribution | n/a | sync | android-only | Play published; App Store pending dev account | 2026-05-24 |
| PLAT-01 | Target devices | n/a | partial | exception | Android 0.3.1: phones + tablets; iOS iPad-only | 2026-06-16 |
| PLAT-02 | OSRM demo routing | n/a | exception | n/a | Android debug only, disabled in release | 2026-05-24 |
| GROWTH-01 | Play in-app review prompt | Free | android-only | deferred | 3 launches + 2 weather loads; prompt after splash; once per install; friends off | 2026-06-20 |

### Quick audit

Count rows where status is **not** `sync` / `deferred` / `exception` / `n/a`:

- `partial`: **2** (BILL-01 — iOS billing not live; PLAT-01 — Android phone audit branch)
- `android-only`: **1** (DIST-01 — expected until App Store)

**Product features:** in sync. **Ship blocker:** App Store Connect + TestFlight (DIST-01).

---

## Changelog

Newest first. One line per change per platform.

| Date | Platform | ID | Summary |
|------|----------|-----|---------|
| 2026-06-21 | android | AIS-03 | Viewport pan: zoom-aware refresh + radius REST; dead reckoning tick without Live MQTT |
| 2026-06-21 | android | NAV-01 | 6th nav tab Track (Seuranta), premium-gated |
| 2026-06-09 | android | AIS-03 | Phase 2: dead reckoning on map, 1 s live tick, stale styling + last-seen sheet |
| 2026-06-09 | android | AIS-03 | Phase 1: Digitraffic MQTT (viewport MMSI), Live chip, REST fallback |
| 2026-06-20 | android | GROWTH-01 | Harden in-app review: check eligibility when splash ends; 0.3.3 |
| 2026-06-17 | android | MAP-06 | Release 0.3.2 AAB (versionCode 28): fresh GPS on my-location + startup |
| 2026-06-16 | android | GROWTH-01 | Play In-App Review after engagement thresholds; non-blocking, friends build disabled |
| 2026-06-16 | android | PLAT-01 | Play store listing EN: phones + tablets, portrait/landscape (0.3.1) |
| 2026-06-15 | android | PLAT-01 | Release 0.3.0 AAB (versionCode 26) for Play internal/open phone testing |
| 2026-06-15 | android | — | Fix rain radar prefetch crash when FMI host unreachable (network/DNS) |
| 2026-06-15 | android | PLAT-01 | Phase 2b: route weather scroll on phones, paywall padding, rail height 480dp, audit screenshots |
| 2026-06-15 | android | PLAT-01 | Phase 0 phone audit: manifest/orientation unlock, safe window insets (branch only) |
| 2026-06-09 | android | BILL-03 | Paywall: explicit local trial + subscription terms (Play policy) |
| 2026-05-24 | iOS | MAP-02 | Traficom overlay → `Merikarttasarjat public` mosaic (was `Merikarttasarja B`) |
| 2026-05-24 | both | — | Parity doc created; baseline audit at Android 0.2.18 / iOS 0.3.0 |

---

## How to add a new feature

1. Add a row to the matrix with a new `ID` (e.g. `MAP-08`).
2. Set both platform columns to `android-only` or `ios-only` while in progress.
3. Implement on first platform; append changelog.
4. Port to sibling; set both to `sync`; append changelog.
5. Update `docs/ios-porting-inventory.md` if APIs or file paths changed.

---

## Related docs

| Doc | Repo | Purpose |
|-----|------|---------|
| `docs/offline-features.md` | Android | Offline pack behaviour |
| `docs/play-billing-products.md` | Android | Play Console IDs |
| `docs/ios-porting-inventory.md` | iOS | API URLs, Kotlin→Swift map |
| `docs/ROADMAP.md` | iOS | iOS release phases |
| `MarineWeather/Configuration/Products.storekit` | iOS | Local StoreKit testing |
