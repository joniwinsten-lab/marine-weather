# AIS data (Android)

Same source and premium boundary as iOS — see also `marine-weather-ios/docs/ais-data-sources.md`.

## Source

**Fintraffic Digitraffic Marine AIS**

| | |
|---|---|
| REST locations | `GET https://meri.digitraffic.fi/api/ais/v1/locations` |
| REST metadata | `GET https://meri.digitraffic.fi/api/ais/v1/vessels` |
| MQTT (live) | `wss://meri.digitraffic.fi:443/mqtt` — `vessels-v2/<mmsi>/location` + `/metadata` |
| Header | `Digitraffic-User: MarineWeather/0.3.0 (fi.veneappi.app)` |

Hybrid: REST bootstrap on enable/viewport pan; MQTT subscribes viewport MMSIs; REST 60 s fallback when MQTT down; metadata heartbeat 10 min when MQTT live.

## Premium

Bundled with existing route premium (`PremiumAccess.isPremium`) — no separate Play SKU.

| Map | AIS chip |
|-----|----------|
| Map & weather (Compare) | Visible; locked when not premium |
| Route planning | Chip without lock when tab unlocked |
| Storm radar / Extended wind / Marine weather | No AIS layer |

## Android code

| Area | Path |
|------|------|
| Config | `app/.../domain/ais/AisConfig.kt` |
| MQTT plan | `docs/ais-mqtt-plan.md` |
| Models | `app/.../domain/ais/AisModels.kt` |
| Repository | `app/.../data/ais/DigitrafficAisRepository.kt` |
| MQTT client | `app/.../data/ais/DigitrafficAisMqttClient.kt` |
| MQTT parser | `app/.../data/ais/AisMqttMessageParser.kt` |
| Subscriptions | `app/.../data/ais/AisSubscriptionManager.kt` |
| ViewModel | `app/.../ui/ais/AisMapViewModel.kt` |
| Map layer | `app/.../ui/map/MapPane.kt` (`updateAisLayer`) |
| UI chrome | `app/.../ui/ais/MapWithAisChrome.kt`, `AisMapChip.kt` |
