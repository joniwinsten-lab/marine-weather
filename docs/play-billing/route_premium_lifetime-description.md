# Play Console — `route_premium_lifetime` (kuvaus)

Kopioi **Monetize with Play** → **Products** → **One-time products** → `route_premium_lifetime` → **Product details** (lokalisointi kielen mukaan).

Vastaa sovelluksen **0.3.x** premium-sopimusta (`docs/store-listing-en.md`, paywall `route_premium_body` + AIS/offline/vienti). Sama sisältö kuin kuukausitilauksessa; tämä on **kertaosto ilman uusintaa**.

Playn kenttärajoitus: **nimi max 55 merkkiä**, **kuvaus max 200 merkkiä**.

## Nimi (Name)

| Kieli | Teksti | Merkkejä |
|-------|--------|----------|
| English (default) | `Marine Weather Premium (lifetime)` | 31 |
| Finnish | `Marine Weather Premium (elinikäinen)` | 34 |
| Swedish | `Marine Weather Premium (livstid)` | 28 |
| Norwegian (Bokmål) | `Marine Weather Premium (livstid)` | 28 |

## Kuvaus (Description, ≤200 merkkiä)

### English

```
Unlock Premium forever: route planning with weather along the route, 12-day wind outlook, AIS vessel positions, offline route pack, GPX/PDF export. Map, radar & forecasts stay free.
```

(199 merkkiä)

### Finnish

```
Premium elinikäinen: reittisuunnittelu ja sää reitillä, 12 pv tuuliennuste, AIS kartalla, offline-reittipaketti, GPX/PDF-vienti. Kartta, sadetutka ja ennusteet pysyvät ilmaisina.
```

(168 merkkiä)

### Swedish

```
Livstids-Premium: ruttplanering med väder längs rutten, 12-dagars vind, AIS-fartyg på kartan, offline-ruttpaket, GPX/PDF-export. Karta, radar och prognoser är gratis.
```

(155 merkkiä)

### Norwegian (Bokmål)

```
Livstidspremium: ruteplanlegging med vær langs ruten, 12-dagers vind, AIS-fartøy på kartet, offline-rutepakke, GPX/PDF-eksport. Kart, radar og prognoser er gratis.
```

(154 merkkiä)

## Laajempi viite (ei Play-kenttään — store / tuki)

Jos tarvitset pidemmän tekstin (esim. verkkosivu, tukiviesti), sama lista kuin store listingissa:

**Premium (kertaosto `route_premium_lifetime`):**

- Reittisuunnittelu sääennusteineen reitin varrella (MET Norway, SMHI, FMI)
- 12 päivän tuuliennuste
- AIS-alusten sijainnit kartalla (Fintraffic Digitraffic)
- Reitin suunnittelu kartta-välilehdeltä (vertailunäkymä)
- Offline-reittipaketti satamassa (karttatiilet + sää reitillä)
- Reitin vienti GPX- tai PDF-muodossa
- Puhelimet ja tabletit, pysty- ja vaakasuunta

**Ilmaiseksi ilman ostoa:** kartta ja säävertailu, sadetutka, merisää, pistemäiset ennusteet.

**Osto:** kertamaksu Google Playn kautta; ei kuukausitilausta. Palauta ostot paywallissa uudella laitteella (sama Google-tili).

## Huomiot

- Älä mainitse hintaa kuvauksessa — Play näyttää hinnan kassalla.
- 3 päivän laitteellinen kokeilu **ei** ole tämä tuote; se ei käynnistä Play-ostoa.
- Vastaava tilaus: `marine_weather_premium` (kuukausi).
