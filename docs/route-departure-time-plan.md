# Reitti — valittava lähtöaika ja ajoitettu sää

Parity-rivi: **RTE-08** (uusi)

iOS viite: **0.3.7** — sama rajoitus kuin Android: lähtö = `Date()` / nyt  
Android: `MainViewModel.scheduleRouteWeatherRefresh()` rivi `depart = System.currentTimeMillis()`

---

## Ongelma

Reittisuunnittelussa näytetään sää neljässä pisteessä (lähtö, 33 %, 66 %, maali), mutta **ennusteen aikaikkuna on aina “nyt”**. Käyttäjä ei voi suunnitella esim. huomisen aamuista purjehdusta tai myöhäisiltapäivän lähtöä ja nähdä, miltä tuuli näyttää **juuri silloin** kun hän on kussakin reittipisteessä.

Nykyinen slot-teksti (`12.3 mpk / 2:05`) kertoo vain **matka-aikaa lähdöstä**, ei absoluuttista kellonaikaa eikä päivää.

---

## Tavoite

| # | Toiminto | Kuvaus |
|---|----------|--------|
| 1 | **Lähtöaika** | Valitse “Nyt” tai tuleva päivä + kellonaika (paikallinen aika) |
| 2 | **Ajoitettu sää** | Jokaisessa slotissa haetaan ennuste **lähtö + matka-aika × osuus** -hetkeen |
| 3 | **Selkeät slot-labelit** | Näytä absoluuttinen aika (esim. `ma 07:30`) + matka (mpk) |
| 4 | **Horisontti-varoitus** | Jos jokin slot putoaa ennusteen ulkopuolelle → selkeä viesti (FMI ~2 pv) |
| 5 | **Export** | PDF (ja myöhemmin GPX-metadata) sisältää suunnitellun lähtöajan |

**Ei koske:** Map & weather -vertailupaneelia. Muutokset Route-näkymään + ViewModel + export.

---

## Nykyinen datavirta (molemmat alustat)

```
routeStart + routeEnd + routeGeometry
  → legNm, etaHours = legNm / boatSpeedKn
  → depart = NOW                          ← korvataan
  → targets[i] = depart + etaMillis × fracs[i]   (0, ⅓, ⅔, 1)
  → locs[i] = pointAlongPolyline(geom, fracs[i])
  → loadAll(lat, lon) per leg
  → sampleAtTargetMillis(points, targets[i])
  → RouteSourceWeatherSlots (4 slottia × 3 lähdettä)
```

Slot-label UI: `route_slot_label` = `%1$s mpk / %2$s` (matka + kesto lähdöstä).

---

## Lukitut päätökset

| # | Päätös |
|---|--------|
| 1 | **Oletus = Nyt** — ei ylimääräistä napautusta ennen ensimmäistä suunnitelmaa |
| 2 | **Vain tulevaisuus** — menneisyys ei sallittu; “Nyt” = nykyinen tunti pyöistettynä tai tarkka `System.currentTimeMillis()` |
| 3 | **Paikallinen aika** — date/time picker käyttää laitteen `ZoneId`; tallennus UTC-millis |
| 4 | **Muutos → uudelleenhaku** — lähtöajan tai nopeuden muutos laukaisee saman `scheduleRouteWeatherRefresh()`-debouncen (400 ms) |
| 5 | **Ei uutta API:a** — sama `WeatherRepository.loadAll` + `ForecastSampler.sampleAtTargetMillis`; vain `targets[]` muuttuu |
| 6 | **Premium** — sama gate kuin reittisäällä (RTE-02) |
| 7 | **Pariteetti** — toteutetaan Android + iOS; parity-rivi `RTE-08` |
| 8 | **Persistointi** — `UserPreferencesRepository.routeDepartureMode` valinnainen vaihe 2; MVP: istunto-tila ViewModelissä |

---

## UI-suunnitelma

### Sijainti

**RouteWeatherRightPane** / **RouteWeatherPane** (iOS), nopeus-sliderin yläpuolella tai sen vieressä:

```
┌─────────────────────────────────────┐
│ Lähtöaika                           │
│ [ Nyt ]  [ Valitse… ▼ ]             │  ← Segmented: Now | Pick
│  ti 22.7. klo 06:30                 │  ← näkyy vain Pick-tilassa
│  ⚠ FMI-slot 4 yli 2 pv horisontin   │  ← tarvittaessa
├─────────────────────────────────────┤
│ Nopeus 6 kn  [━━━━●━━━━]            │
│ … slotit MET / SMHI / FMI …         │
└─────────────────────────────────────┘
```

**Puhelin (kapea):** yksi rivi — `Lähtö: Nyt` / `Lähtö: ma 06:30` + napautus avaa **Material DatePicker + TimePicker** (tai yhdistetty dialogi).

**Tabletti:** sama logiikka, enemmän tilaa absoluuttisille ajoille slot-otsikoissa.

### Slot-labelit (uusi muoto)

| Kenttä | Esimerkki (FI) |
|--------|----------------|
| Absoluuttinen aika | `ma 06:30` |
| Matka | `0.0 mpk` … `41.2 mpk` |
| Yhdistetty | `ma 06:30 · 0.0 mpk` tai kaksi riviä |

Uusi string-avain esim. `route_slot_label_at` = `%1$s · %2$s` (aika · matka).

Slot-otsikossa **korostus** jos ennuste puuttuu (`—` + harmaa/huomautus).

---

## Tila ja logiikka

### Uudet kentät (`VeneappiUiState` / `RouteViewModel`)

```kotlin
enum class RouteDepartureMode { Now, Scheduled }

data class VeneappiUiState(
    // …
    val routeDepartureMode: RouteDepartureMode = RouteDepartureMode.Now,
    /** Epoch millis, local→UTC. Käytössä vain Scheduled. */
    val routeDepartureUtc: Long? = null,
)
```

### Muutos `scheduleRouteWeatherRefresh()`

```kotlin
val depart =
    when (snap.routeDepartureMode) {
        RouteDepartureMode.Now -> System.currentTimeMillis()
        RouteDepartureMode.Scheduled ->
            snap.routeDepartureUtc?.coerceAtLeast(System.currentTimeMillis())
                ?: System.currentTimeMillis()
    }
// targets[i] = depart + (etaMillis * fracs[i])  — ennallaan
```

### Horisontti-tarkistus (ilman uutta API:a)

Ennen/n jälkeen haun, jokaiselle slotille:

```kotlin
fun forecastCoversTarget(points: List<UnifiedTimePoint>, targetMillis: Long): Boolean {
    if (points.isEmpty()) return false
    val last = points.maxOf { it.instantUtc }
    return targetMillis <= last
}
```

- Jos **kaikki** lähteet palauttavat `null` slotille ja target > viimeinen piste → `route_slot_beyond_horizon`
- FMI-valinnainen erillinen note kun `selectedSource == FMI` ja ETA > ~48 h (sama copy kuin `weather_fmi_horizon_note`)

### ViewModel API

```kotlin
fun setRouteDepartureNow()
fun setRouteDepartureScheduled(localDate: LocalDate, localTime: LocalTime)
```

Scheduled-valinta pyöristää **minuuttiin** (ei sekunteja).

---

## Tiedostot

| Tiedosto | Muutos |
|----------|--------|
| `ui/MainViewModel.kt` | `routeDepartureMode`, `routeDepartureUtc`, `setRouteDeparture*`, `depart`-laskenta |
| `ui/VeneappiRoot.kt` | `RouteWeatherRightPane`: lähtöaika-UI, päivitetty slot-label |
| `export/RoutePlanPdfWriter.kt` | `RoutePlanPdfInput.departureLine` |
| `data/prefs/UserPreferencesRepository.kt` | (v2) viimeisin tila tai aina Now |
| `res/values*/strings.xml` | `route_departure_*`, `route_slot_*`, horisontti |
| iOS: `RouteViewModel.swift`, `RouteWeatherPane.swift` | Peili |
| `docs/feature-parity.md` | RTE-08 |

---

## Lokalisointi (luonnos)

| Avain | EN | FI |
|-------|----|----|
| `route_departure_title` | Departure | Lähtöaika |
| `route_departure_now` | Now | Nyt |
| `route_departure_pick` | Pick time | Valitse aika |
| `route_departure_at` | Departure %1$s | Lähtö %1$s |
| `route_slot_label_at` | %1$s · %2$s | %1$s · %2$s |
| `route_slot_no_forecast` | No forecast | Ei ennustetta |
| `route_departure_beyond_horizon` | One or more points are beyond the forecast range for this source. | Yksi tai useampi piste on tämän lähteen ennustehorisontin ulkopuolella. |

(+ sv, nb)

---

## Vaiheistus

| Vaihe | ID | Työ | Arvio |
|-------|-----|-----|-------|
| **D0** | RTE-08 | Tila + `depart`-logiikka + horisontti-helper | 0.5 pv |
| **D1** | RTE-08 | Route UI: Now / picker + slot-labelit | 1 pv |
| **D2** | RTE-08 | PDF-lähtöaika; virheilmoitukset | 0.25 pv |
| **D3** | — | iOS-pariteetti (`RouteViewModel`) | 0.5 pv |
| **D4** | — | Testit: `ForecastSampler` + departure target math | 0.25 pv |
| **D5** | (opt.) | Prefs: muista viimeisin lähtötila | 0.25 pv |

**Julkaisu:** `0.3.7` (reitti-pariteetti) — erillinen suljettu testi.

---

## Testaus

| Testi | Odotus |
|-------|--------|
| Oletus | Lähtö = Nyt; sama käyttäytyminen kuin ennen |
| Valitse huomenna 06:00 | Slot 0 ≈ huomenna 06:00, slot 3 ≈ 06:00 + ETA |
| Nopeuden muutos | Slot-ajat päivittyvät (ETA muuttuu), uusi haku |
| FMI, ETA > 48 h | Varoitus; myöhäiset slotit `—` tai partial |
| Lähtö menneisyydessä (picker) | Estetään tai snap to Now |
| PDF export | Sisältää “Lähtö: ma 22.7.2026 klo 06:30” |
| Offline cache | Näytetään cache + stale-banner; tyhjät slotit jos target ei cachessa |
| fi / en / sv / nb | Kaikki uudet stringit |

---

## Rajaukset (MVP)

- Ei **toistuvaa reittiä** (sama reitti useana päivänä)
- Ei **tuulen suuntaan optimoitua ETA:ta** (nopeus vakio koko matkalla)
- Ei **välilaskuja** tai yösatamia
- Ei **push-ilmoitusta** “reittisi lähtee 1 h kuluttua”

Nämä voivat tulla myöhemmin (RTE-09+).

---

## Parity-doc (luonnos)

| ID | Feature | Android | iOS |
|----|---------|---------|-----|
| RTE-08 | Route departure time + timed weather slots | deferred | deferred |

Changelog:

```
2026-06-21 — Plan: selectable route departure time and timed forecast slots (RTE-08)
```
