# Android — Sää-välilehti, hampurilaisvalikko, reitti-ohje (iOS-pariteetti)

iOS viite: **0.3.7** · 7 välilehteä · `MainTabView.swift`, `WeatherOutlookPane.swift`, `NavigationMenuSheet.swift`, `RoutePane.swift`

Parity-rivit: **NAV-02** (7 tabs), **WTH-06** (Weather outlook), **RTE-07** (route hint banner)

---

## Tavoite

| # | Ominaisuus | iOS | Android nyt |
|---|------------|-----|-------------|
| 1 | **Sää-välilehti** (24 h + 7 pv, lähdevalinta) | `WeatherOutlookPane` | Ei — vain Kartta & sää -vertailu |
| 2 | **Hampurilaisvalikko** (puhelin) | `CompactNavigationBar` + sheet | 6 kohdan alapalkki (ahdas 7 tabilla) |
| 3 | **Reitti-ohje** kartalla | Lyhyt tilannekohtainen banneri | Pitkä `route_hint_long_press` -laatikko vasemmassa yläkulmassa |

**Ei koske:** Map & weather -vertailupaneelin layoutia (stable-views), paitsi jaettu ViewModel/data.

---

## Lukitut päätökset

| # | Päätös |
|---|--------|
| 1 | **7. välilehti** `MainDest.WEATHER` — ilmainen, kuten iOS |
| 2 | **Puhelin (<600 dp leveys):** alapalkki → **hampurilaisvalikko** + yläpalkki (nykyinen tab + ikoni); tabletti säilyttää **NavigationRail** (7 kohdetta) |
| 3 | Sää-näkymä **ei karttaa** — taulukko, sama karttakeskus kuin muualla (`MainViewModel.ui`) |
| 4 | Reitti-ohje: **pieni capsule banneri** kartan yläreunassa (keskellä), ei iso overlay-laatikko |
| 5 | Lokalisointi: **en / fi / sv / nb** |
| 6 | FMI-sääsymbolit: **vaihe 2** (taulukossa ensin teksti/°C/tuuli/sade; symbolit myöhemmin) |

---

## 1. Sää-välilehti (WTH-06)

### iOS-malli

```
WeatherOutlookPane
├── Otsikko + karttakeskus (lat/lon)
├── Lähdevalinta: MET | SMHI | FMI (yksi kerrallaan)
├── "Seuraavat 24 h" — taulukko (aika, symboli, °C, tuuli, mm)
└── "Seuraavat 7 pv" — päivärivit
```

Data: `CompareViewModel.sourceStates` ← sama `refresh()` kuin kartalla.

### Android-toteutus

**Uudet / muokattavat tiedostot:**

| Tiedosto | Tehtävä |
|----------|---------|
| `ui/weather/WeatherOutlookPane.kt` | Uusi Compose-näkymä |
| `domain/ForecastSampler.kt` | `sampleHourlyNext(points, hours=24)` (porttaus iOS:stä) |
| `domain/ForecastSampler.kt` | `sampleDailyWithLabels(points, numDays=7, skipToday=true)` |
| `data/prefs/UserPreferencesRepository.kt` | `weatherSource: Flow<WeatherSource>` (MET/SMHI/FMI) |
| `ui/VeneappiRoot.kt` | `MainDest.WEATHER` → `WeatherOutlookPane` |
| `res/values*/strings.xml` | `nav_weather`, `tab_weather`, `weather_*` (iOS-avaimet) |

**Datavirta:**

```
MainViewModel.ui (latitude, longitude, forecastsBySource, loading)
  └── WeatherOutlookPane
        ├── SegmentedButtonRow / SingleChoiceSegmentedButtonRow (lähde)
        ├── sampleHourlyNext → 24 h LazyColumn / Table
        └── sampleDailyWithLabels → 7 pv osio
```

- `onAppear`: käytä olemassa olevaa `vm.refreshWeather()` (sama guard kuin iOS `didInitialLoad` tarvittaessa).
- Tuuliyksikkö: lue `windUnit` VM:stä (vaihda Kartta & sää -tabilla — ei uutta pickeria Sää-välilehdellä, kuten iOS).
- FMI-horisontti: näytä `weather_fmi_horizon_note` kun lähde = FMI.

### Vaiheistus

| Vaihe | Sisältö |
|-------|---------|
| W1 | `ForecastSampler` hourly + daily helpers, stringit |
| W2 | `WeatherOutlookPane` UI (ilman FMI-kuvia) |
| W3 | `UserPreferencesRepository.weatherSource`, nav + VM-kytkentä |
| W4 | (Myöhemmin) FMI symbol PNG -cache, sarake taulukkoon |

---

## 2. Hampurilaisvalikko (NAV-02)

### iOS-malli

```
width >= 600: NavigationRail (pysyvä)
width < 600:  CompactNavigationBar (☰ + tab icon + title)
              → sheet: lista kaikista MainTab.allCases + premium-badge + checkmark
```

### Android-toteutus

**Puhelin (< `NAVIGATION_RAIL_MIN_WIDTH_DP` = 600):**

```
Scaffold
├── TopBar: CompactAppBar
│     ├── IconButton(☰) → avaa ModalBottomSheet tai ModalNavigationDrawer
│     ├── Icon(currentDest)
│     └── Text(currentDest title)
├── Content: when(destination) { ... }
└── (poista NavigationBar — 7 tabia ei mahdu)
```

**Tabletti (≥600 dp, rail):**

- Lisää **Weather** `ScrollableDestinationRail`-listaan (Comparen ja Routen väliin, kuten iOS).
- Säilytä nykyinen rail-logiikka.

**Uudet tiedostot:**

| Tiedosto | Tehtävä |
|----------|---------|
| `ui/nav/CompactAppBar.kt` | ☰ + ikoni + otsikko |
| `ui/nav/NavigationMenuSheet.kt` | ModalBottomSheet: kaikki `MainDest` + premium-lock + valinta |

**MainDest-järjestys (iOS):**

1. Compare  
2. **Weather** ← uusi  
3. Route  
4. Track  
5. Extended wind  
6. Marine text  
7. Storm radar  

**Stringit:** `nav_menu` = "Sections" / "Osiot" / …, `tab_*` lyhyet otsikot yläpalkkiin.

### Huomiot

- Attribution-footer säilyy alareunassa (kuten nyt).
- Premium-välilehdet: lukko-ikoni listassa (sama `NavPremiumBadgeIcon`-logiikka).
- Landscape-phone: hampurilaisvalikko myös kun korkeus <480 dp (nykyinen rail pois päältä).

---

## 3. Reitti-ohje iOS-tyyliin (RTE-07)

### iOS vs Android nyt

| | iOS | Android |
|---|-----|---------|
| Sijainti | Kartan **yläkeskellä**, capsule | **Vasen yläkulma**, iso `Surface` + pitkä teksti |
| Teksti | Tilannekohtainen 1 lause | Aina `route_hint_long_press` (4 lausetta) |
| Disclaimer | Erillinen ℹ-nappi | Samassa overlayssa otsikon vieressä |

### Tilakone (banneri)

| Tila | String (uusi) |
|------|----------------|
| Ei alku | `route_hint_set_start` |
| Alku, ei loppu | `route_hint_set_end` |
| Reitti lasketaan | `route_computing_fairway` |
| Väylä ei onnistu | `route_fairway_fallback` |
| Väylä OK | `route_fairway_ok` |

**Poistetaan käytöstä:** pitkä `route_hint_long_press` overlaysta (string voi jäädä docciin / paywalliin).

### Toteutus

| Tiedosto | Muutos |
|----------|--------|
| `VeneappiUiState` | `routeComputingFairway: Boolean` (true fairway-laskennan ajan) |
| `MainViewModel.recomputeRouteGeometry()` | aseta computing true/false |
| `RouteMapTopOverlay` → **`RouteMapHintBanner`** | Yksi rivi, `Alignment.TopCenter`, `Surface(shape=Capsule)` |
| `RoutePane` | ℹ-nappi erilliseksi chipiksi (kuten AIS/Traficom), disclaimer-dialogi ennallaan |

**Layout:**

```kotlin
Box(map) {
  MapWithAisChrome(...)
  RouteMapHintBanner(state = routeHintState(ui), modifier = Modifier.align(TopCenter).padding(top = 44.dp))
  Row(Modifier.align(TopStart)) { InfoButton(disclaimer); TraficomChip? }
}
```

Poista vanha `RouteMapTopOverlay`-otsikko + iso laatikko.

---

## Vaiheistus (kokonaisuus)

| Vaihe | ID | Työ | Arvio |
|-------|-----|-----|-------|
| **A0** | RTE-07 | Reitti-banneri + `routeComputingFairway` | 0.5 pv |
| **A1** | NAV-02 | Hampurilaisvalikko puhelimelle + Weather rail-kohta (tyhjä placeholder) | 1 pv |
| **A2** | WTH-06 | `WeatherOutlookPane` + sampler + prefs | 1–1.5 pv |
| **A3** | — | Yhdistä: 7 tabia, testaus phone + tablet | 0.5 pv |
| **A4** | WTH-06b | FMI-sääsymbolit (valinnainen) | 1 pv |

**Julkaisu:** bump `0.3.6` (nav + weather + route hint) — erillinen build suljettuun testiin ennen tuotantoa.

---

## Testaus

| Testi | Odotus |
|-------|--------|
| Puhelin portrait | ☰ avaa 7 kohdan listan; yläpalkki näyttää aktiivisen tabin |
| Tabletti | Rail 7 kohdalla; ei hampurilaista |
| Sää-tab | Vaihda MET→FMI; 24 h + 7 pv päivittyy; karttakeskus sama kuin Compare |
| Reitti, ei pisteitä | Banneri: "Paina karttaa pitkään: alku" |
| Reitti, alku asetettu | Banneri: "… loppu" |
| Reitti laskussa | Banneri: "Lasketaan väyläreittiä…" |
| Reitti valmis | Banneri: "Reitti väyläverkossa" tai fallback-teksti |
| Premium-lukot | Route/Track/12 pv listassa lukko; paywall toimii |
| nb/fi/sv/en | Kaikki uudet stringit |

---

## Parity-doc (päivitys toteutuksen jälkeen)

| ID | Feature | Android | iOS |
|----|---------|---------|-----|
| NAV-02 | 7 tabs incl. Weather | android-only → sync | sync |
| WTH-06 | Weather outlook tab (24h+7d) | android-only → sync | sync |
| RTE-07 | Contextual route hint banner | android-only → sync | sync |

Päivitä myös **NAV-01**-rivi tai merkitse superseded → NAV-02.

---

## Changelog (luonnos)

```
2026-06-21 — Plan: Weather tab, hamburger nav (phone), iOS-style route hint banner
```
