# AIS Seuranta — välilehden suunnitelma (Android)

Web-prototyyppi: [`docs/website/track/`](../website/track/README.md) · live: https://safelight.fi/marine-weather/track/

MQTT-karttakerros (valmis): [`docs/ais-mqtt-plan.md`](ais-mqtt-plan.md)

Parity: uusi rivi **AIS-04** (toteutuksen jälkeen).

## Tavoite

**Premium-välilehti Seuranta**, jossa käyttäjä seuraa **itse valitsemiaan aluksia** (MMSI-watchlist), kuten web-versiossa — ei sama asia kuin Compare/Route-kartan AIS-kerros (viewport, max 600 alusta).

| | Karttakerros (Compare/Route) | Seuranta-välilehti |
|---|---|---|
| Mitä näkyy | Kaikki alukset näkymässä | Vain watchlist |
| Rajaus | Viewport + 600 cap | Ei viewport-cap |
| MQTT-tilaukset | Viewport-MMSI delta | Watchlist-MMSI (koko lista) |
| Lisää aluksia | Ei (vain napautus detail) | Hae / MMSI / detail-sheet |
| Karttakesitys | Käyttäjän sijainti | Auto-fit watchlistiin |

## Päätökset (lukittu 2026-06-21)

| # | Päätös |
|---|--------|
| 1 | **Premium** — sama kuin reitti + AIS (`PremiumAccess.isPremium`) |
| 2 | **Oma päävälilehti** `MainDest.TRACK` (6. kohta navissa, Reitin jälkeen) |
| 3 | **Tallennus:** DataStore JSON, avain `mw_ais_watchlist_v1` (web-yhteensopiva) |
| 4 | **Ei pilveä** MVP:ssä — ei tiliä, ei synkkaa laitteiden välillä |
| 5 | **Yksi MQTT-yhteys** — `AisMqttCoordinator`, tilausten unioni |
| 6 | **Suuntaviiva 2 min** — app-wide (web 5 min) |
| 7 | **Hae mukana MVP:ssä** — lähellä + globaali metadata-haku |
| 8 | **Max 50 alusta** watchlistissä |
| 9 | Compare/Route **detail-sheet**: "Lisää seurantaan" — Phase T2 (ei MVP) |

## Päätökset (vanha luonnos — korvattu yllä)

| # | Ehdotus |
|---|---------|
| 1 | **Premium** — sama kuin reitti + AIS (`PremiumAccess.isPremium`) |
| 2 | **Oma päävälilehti** `MainDest.TRACK` (6. kohta navissa), ei alinäkymä Reitti-tabissa |
| 3 | **Tallennus:** DataStore JSON, avain `mw_ais_watchlist_v1` (yhteensopiva web-formaatin kanssa) |
| 4 | **Ei pilveä** MVP:ssä — ei tiliä, ei synkkaa laitteiden välillä |
| 5 | **Yksi MQTT-yhteys** sovelluksessa — jaettu coordinator, tilausten unioni |
| 6 | **Suuntaviiva 2 min** — sama kuin karttakerros (web käyttää 5 min; sovellus yhtenäinen) |
| 7 | Compare/Route **detail-sheet**: "Lisää seurantaan" (Phase 2) |

## Web → sovellus (ominaisuuspariteetti)

| Web | Android MVP | Myöhemmin |
|-----|-------------|-----------|
| Seuranta-lista + localStorage | DataStore watchlist | Export/import JSON |
| Hae-välilehti (lähellä + globaali) | MVP (Seuranta \| Hae) | |
| Lisää MMSI + lempinimi | MVP dialogi | |
| MQTT live watchlist-MMSI | MVP (jaettu coordinator) | |
| Suodata: kaikki / aktiiviset / stale | MVP | |
| Merikartta-toggle | MVP (Traficom WMTS) | |
| MQTT-status pill | MVP status-teksti | |
| Detail-paneeli | MVP bottom sheet | |
| Dead reckoning 2 min | MVP (`AisVesselMotion`) | |

## Arkkitehtuuri

```
VeneappiRoot
  └── MainDest.TRACK → AisTrackPane (uusi)

AisTrackPane
  ├── lista (Seuranta | Hae) — Phase 2: Hae
  ├── kartta (MapPane tai kevyt wrapper)
  ├── chip: MQTT status, Merikartta
  └── FAB / dialog: Lisää alus

AisTrackViewModel
  ├── AisWatchlistRepository (DataStore)
  ├── DigitrafficAisRepository (REST bootstrap)
  ├── AisMqttCoordinator (jaettu, ks. alla)
  └── fleet: Map<Int, AisVesselDisplay>  // vain watchlist

AisMapViewModel (olemassa)
  └── viewport MMSI → coordinator

AisMqttCoordinator (uusi, AppContainer)
  ├── DigitrafficAisMqttClient (yksi instanssi)
  ├── AisSubscriptionManager
  ├── message fan-out → Map VM + Track VM
  └── sync(viewportMmsis ∪ watchlistMmsis)
```

### Miksi jaettu MQTT-coordinator?

Ilman sitä Compare-AIS + Seuranta-tab voisi avata **kaksi WSS-yhteyttä** → turha akku/data. Coordinator:

- Yhdistää kun **jompikumpi** tarvitsee live-datan
- Tilausten **unioni** (viewport + watchlist)
- Katkaisee kun **molemmat** pois (Compare/Route pause + Track tab pois)
- Jakaa `SharedFlow`-viestit molemmille VM:ille (suodata `mmsi ∈ oma joukko`)

Refaktorointi Phase Track-1 alussa: irrota MQTT `AisMapViewModel`:stä coordinatoriin (pieni regressioriski → testaa Compare-AIS uudelleen).

## Tietomalli

```kotlin
data class AisWatchlistEntry(
    val mmsi: Int,           // 9 numeroa, PK
    val nickname: String?,   // käyttäjän nimi
    val addedAtEpochMs: Long,
    val addedSource: AddedSource, // MAP_TAP | SEARCH | MANUAL
)

enum class AddedSource { MAP_TAP, SEARCH, MANUAL }
```

**DataStore JSON** (web-yhteensopiva tallennus):

```json
[
  { "mmsi": 230982000, "nickname": "Kaverin venhe", "name": "FINNMAID", "callSign": "…" }
]
```

Sovellus voi tallentaa ylimääräiset kentät (`addedAt`, `source`); web jättää ne huomiotta.

## REST / MQTT datavirta (Seuranta)

```
Tab avautuu (premium)
  │
  ├─ Lue watchlist DataStoresta
  ├─ REST: per-MMSI location (tai batch) + metadata cache
  ├─ Coordinator: connect + subscribe kaikki watchlist-MMSIt
  └─ MQTT message → merge → publishMap (throttle 300 ms)

Tab sulkeutuu / ON_PAUSE
  └─ Coordinator: poista watchlist-MMSIt tilauksista (viewport voi pitää omat)

Ei signaalia (>30 min)
  └─ Harmaa piste (sama kuin karttakerros), rivi listassa ei piilotu
```

**Browse/Hae (Phase 2):**

- Lähellä: suodata `/locations` viewport bbox (kuten web `fetchNearbyVessels`)
- Globaali haku: lataa `/vessels` metadata-kerran (~18k), cache muistiin (kuten web `ensureVesselMetaCache`)
- Esikatselu kartalla → "Lisää seurantaan"

## UI-layout

### Puhelin (portrait)

Webin malli: **lista ylhäällä, kartta alhaalla** (ei sivupaneelia).

```
┌─────────────────────────┐
│ Seuranta │ Hae          │  tab + MQTT + Merikartta
│ [haku] [Kaikki|Aktiiv|Stale]
│ stats                   │
│ ┌─ FINNMAID ──────────┐ │
│ │ MMSI · 12 kn · 2 min │ │  LazyColumn ~40% korkeus
│ └─────────────────────┘ │
├─────────────────────────┤
│                         │
│        Kartta           │  MapPane ~60%
│                         │
└─────────────────────────┘
        [+] Lisää alus
```

### Tabletti / landscape

Web: sivupaneeli + kartta (`track.css`). Android: `Row` — paneeli `widthIn(max = 360.dp)`, kartta `weight(1f)`.

### Navigaatio (6. tab)

- **Phone:** `NavigationBar` — 6 itemiä; label `"Seuranta"` / `"Track"` (fi/en), premium-badge kuten Reitti
- **Tablet:** `NavigationRail` — sama kohde
- Ikoni: esim. `Icons.Outlined.DirectionsBoat` tai `Radar` (ei sekoitu sadetutkaan)

**Riski:** 6 tabia ahtauttaa phone bottom baria → label 2 riviä / `labelSmall` (jo käytössä).

## Jaetut komponentit (uudelleenkäyttö)

| Komponentti | Käyttö Seurannassa |
|-------------|-------------------|
| `MapPane` | Kartta + AIS-layer (sama `updateAisLayer`) |
| `AisVesselDetailSheet` | Napautus listasta/kartalta |
| `AisVesselMotion` | Dead reckoning, stale |
| `AisMqttMessageParser` | MQTT JSON |
| `DigitrafficAisRepository` | REST |
| `Traficom` raster | `traficomPlanningRasterEnabled` toggle |

**Ei uudelleenkäytä:** `AisMapViewModel` suoraan — erillinen `AisTrackViewModel` (eri fleet-logiikka, ei viewport-pollia).

## Vaiheistus

### Phase T0 — Suunnitelma & coordinator-refaktor ✅

- [x] Erottele karttakerros vs Seuranta
- [x] Lukitse päätökset (premium, DataStore, 6. tab, Hae MVP, max 50, 2 min)
- [x] Refaktoroi MQTT → `AisMqttCoordinator`

### Phase T1 — MVP Seuranta-välilehti ✅

- [x] `AisWatchlistRepository` (DataStore CRUD)
- [x] `AisTrackViewModel` + `AisTrackPane`
- [x] `MainDest.TRACK` + nav (premium gate)
- [x] Watchlist lista + kartta (vain listan alukset)
- [x] Lisää alus (MMSI + nickname dialogi)
- [x] MQTT coordinator + REST bootstrap
- [x] Suodattimet: kaikki / aktiiviset / stale
- [x] Hae-välilehti (lähellä + globaali metadata-haku)
- [x] Tyhjä tila: "Ei aluksia — lisää MMSI"
- [x] Parity **AIS-04** → `android-only`

### Phase T2 — Integraatio & polish

- [ ] "Lisää seurantaan" Compare/Route `AisVesselDetailSheet`:stä
- [ ] Auto-fit bounds kun tab avautuu / "Keskitä alukseen" listasta
- [ ] Poista listasta (swipe tai detail)

### Phase T3 — Polish

- [ ] Lokalisointi fi/en/sv/nb (nav + strings)
- [ ] Offline: näytä viimeisin tunnettu + "Ei verkkoa"
- [ ] iOS-spec parity doc sibling-repossa

### Myöhemmin (ei MVP)

- Jaettu lista (QR / JSON export-import)
- Push kun kaverin alus lähtee satamasta
- Widget / live notification

## Testaus

| Testi | Odotus |
|-------|--------|
| Tyhjä watchlist | Tyhjä tila, kartta Suomi-keski |
| Lisää 230982000 | REST + MQTT, live-liike |
| Poista viimeinen | MQTT unsubscribe, tyhjä tila |
| Compare AIS päällä + Seuranta tab | Yksi MQTT-yhteys, unioni-tilaukset |
| Vain Seuranta (Compare AIS off) | Vain watchlist-MMSI tilattu |
| Stale alus | Harmaa piste, "Ei signaalia" listassa |
| Premium off | Paywall, ei dataa |
| DataStore säilyy | Uudelleenkäynnistyksen jälkeen lista tallessa |

## Avoimet kysymykset

Kaikki MVP-päätökset lukittu 2026-06-21 (oma tab, Hae MVP:ssä, max 50, 2 min vector).

## Changelog

- 2026-06-21 — MVP toteutus: coordinator, watchlist, Seuranta/Hae UI, 6. nav-tab.
- 2026-06-21 — Päätökset lukittu (Hae MVP, max 50, 2 min).
- 2026-06-21 — Ensimmäinen suunnitelma (web-prototyyppi + MQTT-infra 0.3.4).
