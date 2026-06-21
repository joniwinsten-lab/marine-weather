# AIS MQTT — toteutussuunnitelma (Android)

Parity: **AIS-03** (`deferred` → `android-only` kun Phase 1 valmis).

Referenssi: web-prototyyppi [`docs/website/track/`](../website/track/README.md).

## Päätökset (2026-06-09)

| # | Päätös |
|---|--------|
| 1 | MQTT vain nykyisen karttakerroksen päälle (Compare + Route). **Ei** erillistä Seuranta-välilehteä vielä. |
| 2 | Suuntaviiva **2 min** @ SOG/COG — pidetään Android-arvona. |
| 3 | **Viewport-MMSI-tilaukset**: REST bootstrap näkymään → MQTT `vessels-v2/<mmsi>/location` (+ metadata uusille). Ei globaalia `vessels-v2/+/location`. |
| 4 | Dead reckoning kartalla: max **2 min** (`MAX_DRIFT_MS`), stale **30 min** (`STALE_MS`). |

## Digitraffic MQTT

| | |
|---|---|
| Broker | `wss://meri.digitraffic.fi:443/mqtt` |
| Auth | `digitraffic` / `digitrafficPassword` (julkinen client-tunnus) |
| Location topic | `vessels-v2/<mmsi>/location` |
| Metadata topic | `vessels-v2/<mmsi>/metadata` |

Topic-nimi vahvistettu web-prototyypistä (`track.js`); Digitraffic-dokumentaatio mainitsee myös `locations`-muodon — tuotantotesti Phase 1:ssä.

## Arkkitehtuuri

```
AisMapViewModel (orkestraattori)
    ├── DigitrafficAisRepository      REST bootstrap + fallback
    ├── DigitrafficAisMqttClient      WSS-yhteys (Phase 1)
    ├── AisSubscriptionManager        viewport MMSI sync (Phase 1)
    └── fleetByMmsi → MapPane.updateAisLayer
```

**Kerrokset (uudet tiedostot):**

| Tiedosto | Vaihe | Vastuu |
|----------|-------|--------|
| `domain/ais/AisMqttConfig.kt` | 0 | Broker, topicit, reconnect-asetukset |
| `domain/ais/AisMqttUpdate.kt` | 0 | Sealed update + merge `AisVesselDisplay`:iin |
| `domain/ais/AisVesselMotion.kt` | 0 | `lastSeen`, stale, dead reckoning |
| `data/ais/AisMqttMessageParser.kt` | 0 | Topic + JSON → `AisMqttUpdate` |
| `data/ais/DigitrafficAisMqttClient.kt` | 1 | HiveMQ client, reconnect |
| `data/ais/AisSubscriptionManager.kt` | 1 | subscribe/unsubscribe delta |

## Hybrid-logiikka (Phase 1)

```
AIS päälle → REST fullFetch → MQTT connect → subscribe viewport-MMSIt
MQTT message → parse → merge fleet → throttle publish (200–500 ms)
Viewport muuttuu → debounce 450 ms → REST + syncSubscriptions
MQTT katkeaa → streamMode Error/RestOnly → REST 60 s
ON_PAUSE → disconnect MQTT (jo olemassa setSceneActive)
```

**REST live-tilassa:**

| Tapahtuma | REST |
|-----------|------|
| AIS päälle / merkittävä pan | fullFetch |
| MQTT live | metadata ~10 min |
| MQTT live positions | pois (tai 5 min heartbeat) |
| MQTT pois | 60 s poll |

## StreamMode (chip)

| Tila | Väri | Merkitys |
|------|------|----------|
| `Connecting` | oranssi | käynnissä |
| `Live` | vihreä | MQTT yhdistetty |
| `RestOnly` | tumma vihreä | vain REST |
| `Error` | punainen | virhe |

## Vaiheistus

### Phase 0 — Domain & parser ✅ (käynnissä)

- [x] `lastSeenEpochMs` REST-parsintaan
- [x] `AisMqttMessageParser` + unit-testit
- [x] `AisVesselMotion` (displayPosition, stale)
- [x] `AisMqttConfig` topic-helperit
- [x] Tämä dokumentti

### Phase 1 — MQTT-yhteys + viewport-tilaukset ✅

- [x] HiveMQ MQTT Client riippuvuus
- [x] `DigitrafficAisMqttClient`
- [x] `AisSubscriptionManager`
- [x] `AisMapViewModel` hybrid
- [x] `AisStreamMode.Live` + chip
- [x] `docs/ais-data-sources.md` + parity AIS-03

### Phase 2 — Live-kartta & polish ✅

- [x] `displayPosition()` MapPane-piirtoon
- [x] 1 s map tick kun live + liikkuvia aluksia
- [x] Throttled refresh (Phase 1)
- [x] Stale-tyylit (harmaa piste + himmeä viiva)
- [x] Viimeisin AIS -rivi detail-sheetissä
- [ ] Fyysinen laite -testi (manuaalinen)

### Phase 3 — Seuranta-välilehti (seuraava iso feature)

Katso **`docs/ais-track-tab-plan.md`** — watchlist-tab, web-pariteetti, jaettu MQTT-coordinator.

- DataStore watchlist, oma UI — **ei osa AIS-03 MVP:tä** (erillinen AIS-04)

### Phase 4 — iOS

- Porttaus kun Android vakaa → parity `sync`

## Testaus

| Testi | Miten |
|-------|-------|
| Parser | JUnit fixtures webistä |
| Yhteys | MMSI `230982000` (FINNMAID) |
| Reconnect | lentotila / WiFi |
| Viewport churn | pan Helsinki → Turku, tilauslaskuri logissa |
| Fallback | MQTT down → REST 60 s |

## Changelog

- 2026-06-09 — Phase 2: dead reckoning kartalla, 1 s tick, stale-tyylit, viimeisin AIS detail-sheetissä.
- 2026-06-09 — Phase 1: HiveMQ MQTT, viewport MMSI subscriptions, Live chip (AIS-03 android-only).
- 2026-06-09 — Suunnitelma lukittu; Phase 0 aloitettu (domain + parser).
