# Marine Weather — feature parity (Android ↔ iOS)

**Canonical document.** Both apps must stay aligned on user-facing behaviour.

| | |
|---|---|
| **Android** | `/Users/Safelight/Veneappi` · `versionName` **0.3.5** (`versionCode` 31+) |
| **iOS** | `/Users/Safelight/marine-weather-ios` · marketing **0.3.5** (build 35) |
| **Technical porting map** | iOS: `docs/ios-porting-inventory.md` (API URLs, file paths) |
| **Last parity audit** | 2026-06-25 |

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
| Storm radar & lightning | ✓ | |
| Marine text (4 countries) | ✓ | |
| Route planning + weather along route | | ✓ |
| 12-day wind outlook | | ✓ |
| AIS overlay (Digitraffic) | | ✓ |
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
| NAV-01 | Main navigation tabs | — | sync | partial | 7 tabs both; iOS phone hamburger parity TBD | 2026-06-21 |
| NAV-02 | 7 tabs incl. Weather + phone hamburger menu | — | sync | sync | Phone: CompactAppBar + sheet; tablet: 7-item rail | 2026-06-21 |
| MAP-01 | OpenFreeMap Liberty basemap | Free | sync | sync | Same style URL | 2026-05-24 |
| MAP-02 | Traficom nautical WMTS (`Merikarttasarjat public`) | Free | sync | sync | zoom 5–15, bounds 17–32°E, 58–71°N | 2026-05-24 |
| MAP-03 | Forecast location pin (long-press) | Free | sync | sync | | 2026-05-24 |
| MAP-04 | Scale bar | Free | sync | sync | | 2026-05-24 |
| MAP-05 | Map tile warmup on launch / splash | Free | sync | sync | Shared 50 MB URL cache with MapLibre tiles | 2026-06-25 |
| MAP-06 | Shared map center across tabs | Free | sync | sync | | 2026-05-24 |
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
| MAR-01 | Marine text 2×2 country grid | Free | sync | sync | MET, SMHI, FMI, EE | 2026-05-24 |
| MAR-02 | Summarizer + alert classifier | Free | sync | sync | | 2026-05-24 |
| MAR-03 | Open full forecast in browser | Free | sync | sync | | 2026-05-24 |
| RTE-01 | Route draw + Väylä fairway routing | Premium | sync | sync | Great-circle fallback both | 2026-05-24 |
| RTE-02 | Weather along route (3 sources) | Premium | sync | sync | | 2026-05-24 |
| RTE-03 | Boat speed / ETA | Premium | sync | sync | | 2026-05-24 |
| RTE-04 | Navigation disclaimer | Premium | sync | sync | | 2026-05-24 |
| RTE-05 | GPX export | Premium | sync | sync | | 2026-05-24 |
| RTE-06 | Route plan PDF export | Premium | sync | sync | | 2026-05-24 |
| RTE-07 | Contextual route hint banner on map | Premium | sync | sync | Capsule banner TopCenter; routeComputingFairway | 2026-06-21 |
| RTE-08 | Route departure time + timed weather slots | Premium | sync | sync | Now / Schedule; local time; 15 min grid; no past; 60s refresh in Now mode; PDF | 2026-07-21 |
| WIND-01 | 12-day extended wind outlook | Premium | sync | sync | | 2026-05-24 |
| WTH-06 | Weather outlook tab (24h + 7d, source picker) | Free | sync | sync | FMI smart-symbol icons via CDN; MET map + SMHI derive | 2026-06-22 |
| AIS-01 | Digitraffic AIS REST (~60s poll) | Premium | sync | sync | Viewport reload | 2026-05-24 |
| AIS-02 | AIS vessel detail sheet | Premium | sync | sync | | 2026-05-24 |
| AIS-03 | AIS MQTT live stream | Premium | sync | sync | Digitraffic MQTT WSS; viewport MMSI subs; dead reckoning tick | 2026-06-25 |
| TRK-01 | AIS watchlist / Seuranta tab (50 vessels, browse, MQTT) | Premium | sync | sync | Map ~65% + panel; StoreKit gate | 2026-06-25 |
| GROWTH-01 | In-app store review prompt | — | sync | sync | 3 launches + 2 weather loads; Play / SKStoreReview | 2026-06-25 |
| LOC-GPS | Fresh GPS auto-center on startup | Free | sync | sync | Best accuracy; reject fixes >2 min | 2026-06-25 |
| OFF-01 | Offline / stale banner (all tabs) | Free | sync | sync | NWPath + cache age | 2026-05-24 |
| OFF-02 | Offline route pack (tiles+weather+marine) | Premium | sync | sync | No Traficom tiles in pack | 2026-05-24 |
| BILL-01 | Lifetime + monthly IAP | Premium | sync | partial | Play live; ASC products not live | 2026-05-24 |
| BILL-02 | Restore purchases | Premium | sync | sync | | 2026-05-24 |
| BILL-03 | Paywall + billing diagnostics | Premium | sync | sync | | 2026-05-24 |
| LOC-01 | en / fi / sv / nb | — | sync | sync | | 2026-05-24 |
| LOC-02 | Attribution dialog (MET, SMHI, FMI, Traficom, AIS) | Free | sync | sync | | 2026-05-24 |
| SPLASH-01 | Branded splash | Free | sync | sync | | 2026-05-24 |
| HARBOR-01 | Harbors (Overpass) on map | — | deferred | deferred | Android code exists; not in nav | 2026-05-24 |
| DIST-01 | Store distribution | n/a | sync | sync | Play + App Store published (`id6787024900`) | 2026-07-21 |
| PLAT-01 | Target devices | n/a | exception | exception | Android tablet≥600dp; iOS iPad-only | 2026-05-24 |
| PLAT-02 | OSRM demo routing | n/a | exception | n/a | Android debug only, disabled in release | 2026-05-24 |

### Quick audit

Count rows where status is **not** `sync` / `deferred` / `exception` / `n/a`:

- `partial`: **1** (BILL-01 — confirm ASC products live if still partial)
- `android-only`: **0**

**Product features:** in sync. Both stores published (DIST-01).

---

## Changelog

Newest first. One line per change per platform.

| Date | Platform | ID | Summary |
|------|----------|-----|---------|
| 2026-07-21 | ios | RTE-08 | Route departure Now/Schedule; local slot times; 15 min snap; clock refresh |
| 2026-07-21 | android | RTE-08 | Route departure Now/Schedule; `RouteDepartureBar`; timed slots + PDF |
| 2026-06-22 | android | WTH-06 | FMI smart-symbol icons on Weather tab (MET/SMHI/FMI); 0.3.11 |
| 2026-06-22 | android | RTE-08 | Fix route weather cards collapsing in scroll (fixed height strips); 0.3.10 |
| 2026-06-22 | android | RTE-08 | Route weather pane always scrolls (scheduled departure fits) |
| 2026-06-22 | android | — | targetSdk/compileSdk 36 (Play API requirement); release 0.3.8 |
| 2026-06-21 | android | RTE-08 | Route departure time picker (future only); timed weather slots + PDF |
| 2026-06-21 | android | — | Plan: route departure time + timed forecast slots (RTE-08) |
| 2026-06-21 | android | NAV-02,WTH-06,RTE-07 | Weather tab, phone hamburger nav, iOS-style route hint banner; v0.3.6 |
| 2026-06-21 | android | — | Plan: Weather tab (WTH-06), hamburger nav (NAV-02), route hint banner (RTE-07) |
| 2026-07-21 | ios | DIST-01 | App Store live; marketing site App Store CTA (`id6787024900`) |
| 2026-06-25 | ios | TRK-01 | Seuranta tab: `AisWatchlistStore`, `AisTrackViewModel`, `AisTrackPane`, nav_track, track_* strings |
| 2026-06-25 | ios | — | App Store review prompt (3 launches + 2 weather loads); GPS auto-center on first fix |
| 2026-06-25 | ios | AIS-03 | Digitraffic MQTT (CocoaMQTT WSS); shared `AisMqttCoordinator`; Live chip; viewport REST + dead reckoning; v0.3.5 |
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
| `docs/android-weather-nav-route-plan.md` | Android | Weather tab, hamburger nav, route hint |
