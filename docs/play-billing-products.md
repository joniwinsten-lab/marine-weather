# Google Play — premium-tuotteet (hinnat paywallissa)

Sovellus hakee hinnat Play Billing Libraryllä tuotteilla:

| Tyyppi | Product ID (täsmälleen) |
|--------|-------------------------|
| Kertamaksu | `route_premium_lifetime` |
| Tilaus | `marine_weather_premium` (peruspaketti Consolessa: `route-premium-monthly`) |

## Tuotekuvakkeet ja kuvaukset (Play Console)

| Product ID | Tyyppi | Kuvake | Kuvaus (nimi + teksti, 4 kieltä) |
|------------|--------|--------|----------------------------------|
| `route_premium_lifetime` | Kertamaksu | [route_premium_lifetime-icon-512.png](play-billing/route_premium_lifetime-icon-512.png) | [route_premium_lifetime-description.md](play-billing/route_premium_lifetime-description.md) |
| `marine_weather_premium` | Tilaus | [marine_weather_premium-icon-512.png](play-billing/marine_weather_premium-icon-512.png) | [marine_weather_premium-description.md](play-billing/marine_weather_premium-description.md) |

**Kertamaksu:** **Monetize with Play** → **Products** → **One-time products** → `route_premium_lifetime` → **Icon** + **Product details**.

**Tilaus:** **Monetize with Play** → **Products** → **Subscriptions** → `marine_weather_premium` → **Icon** + **Product details** (base plan `route-premium-monthly` erikseen hinnoittelussa).

Molemmissa: 512×512 PNG, max 1 MB, ei tekstiä; nimi ≤55 merkkiä, kuvaus ≤200 merkkiä per kieli.

Jos paywallissa näkyy **…**, Play ei palauttanut hintaa. Syyt ovat lähes aina Console-asetuksissa tai asennuskanavassa — ei sovelluksen “hintakytkimessä”.

## Mistä näen, onko tuote aktiivinen?

### Kertamaksu (one-time product)

1. [Play Console](https://play.google.com/console) → valitse **Marine Weather** (`fi.veneappi.app`)
2. **Monetize with Play** → **Products** → **One-time products**
3. Avaa tuote **`route_premium_lifetime`**
4. Vieritä kohtaan **Purchase options and offers**
5. Jokaisella **Buy**-purchase optionilla pitää olla tila **Active** (ei Draft, ei Inactive)
6. Avaa purchase option → varmista **Price** / **Availability** (esim. Suomi / EU) ja että hinta on tallennettu

**Draft** = tuote on luotu mutta **ei vielä aktivoitu** → sovellus näyttää **…**.

Aktivointi: purchase option -sivulla **Activate** (tai vastaava), kun hinta ja maat on asetettu.

### Tilaus (subscription)

1. **Monetize with Play** → **Products** → **Subscriptions**
2. Avaa **`marine_weather_premium`**
3. **Base plans** — vähintään yksi base plan tila **Active**
4. Base planin alla **Offers** — vähintään yksi offer **Active**, kuukausihinta asetettu

Ilman aktiivista base plania + hintaa Play palauttaa usein virheen **NO_ELIGIBLE_OFFER**.

## Muut pakolliset asiat (usein unohdetaan)

| Tarkista | Missä |
|----------|--------|
| Asennus **Playn suljetun testin** kautta | Ei suora APK, ei `friends`-build (`fi.veneappi.app.friends`) |
| Tabletin Google-tili = **license tester** tai closed test -testaaja | Setup → **License testing** |
| **Payments profile** / kauppiasprofiili valmis | Setup → Payments |
| Suljettu testi **julkaistu** testaajille (ei vain luonnos) | Release → Testing → Closed testing |
| Tuote-ID **kirjain koolla** oikein | `route_premium_lifetime`, ei väliviivoja |
| Uuden tuotteen jälkeen odota | 15 min – 24 h ennen kuin hinnat näkyvät |

## Mitä sovellus tekee

1. Yhdistää Google Play Billingiin käynnistyksessä
2. Kysyy `route_premium_lifetime` (INAPP) ja `marine_weather_premium` (SUBS)
3. Näyttää `formattedPrice` paywallissa

Jos hinnat puuttuvat, paywallissa voi näkyä punainen ohje ja rivi **billingDiagnostic** (esim. `unfetched=route_premium_lifetime:NO_ELIGIBLE_OFFER`).

### Virhekoodien merkitys

| Koodi | Tulkinta |
|-------|----------|
| `PRODUCT_NOT_FOUND` | Väärä ID, tuotetta ei ole, tai ei aktiivinen / väärä sovellus |
| `NO_ELIGIBLE_OFFER` | Tuote löytyy, mutta **purchase option** tai **base plan / offer** ei ole Active + hinnoiteltu |
| `INVALID_PRODUCT_ID_FORMAT` | ID:ssä esim. väliviiva (Play ei salli) |

## Logcat (release-buildissäkin)

```bash
adb logcat -s VeneappiBilling:W
```

Avaa paywall (Route / Premium lukittu) ja katso `unfetched` / `no prices from Play` -rivit.

## Testausosto

License tester -tilillä osto on testiostoja; hinnan pitää silti näkyä paywallissa ennen ostoa.
