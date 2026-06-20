# Play Console — `marine_weather_premium` (kuvaus)

Kopioi **Monetize with Play** → **Products** → **Subscriptions** → `marine_weather_premium` → **Product details** (lokalisointi kielen mukaan).

Base plan Consolessa: `route-premium-monthly`.

Vastaa sovelluksen **0.3.x** premium-sopimusta — **sama sisältö** kuin kertaostossa `route_premium_lifetime`, mutta **kuukausitilaus** (uusiutuu automaattisesti, kunnes peruutat Google Playssa).

Playn kenttärajoitus: **nimi max 55 merkkiä**, **kuvaus max 200 merkkiä**.

## Nimi (Name)

| Kieli | Teksti | Merkkejä |
|-------|--------|----------|
| English (default) | `Marine Weather Premium (monthly)` | 30 |
| Finnish | `Marine Weather Premium (kuukausi)` | 31 |
| Swedish | `Marine Weather Premium (månad)` | 29 |
| Norwegian (Bokmål) | `Marine Weather Premium (måned)` | 29 |

## Kuvaus (Description, ≤200 merkkiä)

### English

```
Monthly Premium: route planning with weather along the route, 12-day wind, AIS on map, offline route pack, GPX/PDF. Renews monthly via Google Play until you cancel. Map & radar stay free.
```

(186 merkkiä)

### Finnish

```
Premium kuukausitilaus: reittisuunnittelu, sää reitillä, 12 pv tuuli, AIS, offline-paketti, GPX/PDF. Uusiutuu kuukausittain Google Playssa, kunnes peruutat. Kartta ja sadetutka ilmaisia.
```

(168 merkkiä)

### Swedish

```
Månadsprenumeration Premium: ruttplanering, väder längs rutten, 12-dagars vind, AIS, offline-paket, GPX/PDF. Förnyas via Google Play tills du avslutar. Karta och radar gratis.
```

(158 merkkiä)

### Norwegian (Bokmål)

```
Månedlig premium: ruteplanlegging, vær langs ruten, 12-dagers vind, AIS, offline-pakke, GPX/PDF. Fornyes via Google Play til du avslutter. Kart og radar gratis.
```

(149 merkkiä)

## Laajempi viite (ei Play-kenttään — store / tuki)

**Premium (tilaus `marine_weather_premium`):**

- Reittisuunnittelu sääennusteineen reitin varrella (MET Norway, SMHI, FMI)
- 12 päivän tuuliennuste
- AIS-alusten sijainnit kartalla (Fintraffic Digitraffic)
- Reitin suunnittelu kartta-välilehdeltä (vertailunäkymä)
- Offline-reittipaketti satamassa (karttatiilet + sää reitillä)
- Reitin vienti GPX- tai PDF-muodossa
- Puhelimet ja tabletit, pysty- ja vaakasuunta

**Ilmaiseksi ilman ostoa:** kartta ja säävertailu, sadetutka, merisää, pistemäiset ennusteet.

**Tilaus:** kuukausihinta Google Playn kautta; uusiutuu automaattisesti, kunnes peruutat **Google Play → Tilaukset**. Lopullinen hinta ja tarjoukset näkyvät Playn kassalla. Paywallissa: **Hallitse tilausta Google Playssa**.

## Huomiot

- Älä mainitse hintaa kuvauksessa — Play näyttää hinnan kassalla.
- 3 päivän laitteellinen kokeilu **ei** ole Play-tilaus eikä ilmainen kokeilujakso; se ei käynnistä laskutusta.
- Vastaava kertaosto: `route_premium_lifetime`.
