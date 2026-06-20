# AIS Seuranta — web-prototyyppi

Kokeilu Androidin **Seuranta**-välilehdelle ennen sovellustoteutusta.

**URL:** https://safelight.fi/marine-weather/track/

## Toiminta

- Seurantalista tallennetaan selaimen `localStorage`-avaimeen `mw_ais_watchlist_v1`
- Live-sijainnit: Digitraffic MQTT (`wss://meri.digitraffic.fi:443/mqtt`)
- Metatiedot ja alkusijainti: REST proxy nginxin kautta (`../api/ais/v1/`)
- Kartta: MapLibre GL + OpenFreeMap Liberty -tyyli
- Suuntaviiva: **5 min** nykyisellä nopeudella (COG/SOG, ≥ 0,4 kn); satamassa **lyhyt suuntanuoli** (heading, harmaa)
- Karttapiste **liukuu liveksi** viimeisimmän AIS-sijainnin ja SOG/COG:n perusteella (max 2 min ilman uutta fixiä)
- **Hae**-välilehti: alukset karttanäkymän läheltä + nimi/MMSI-haku koko rekisteristä (≈18k alusta, ladataan kerran)
- **Merikartta**: Traficomin avoin suunnittelukartta (WMTS, zoom 5–15) — valinta tallentuu selaimeen
- “Ei signaalia” = yli 30 min vanha viimeisin AIS-päivitys

## Tiedostot

- `index.html` — sivu
- `track.css` — layout (puhelin: lista ylhäällä, kartta alla; tablet+: sivupaneeli)
- `track.js` — logiikka

## Deploy

Sama kuin muu marketing-sivu:

```bash
./docs/website/deploy.sh
```

**Nginx:** päivitä snippet vain jos haluat same-origin-proxyn. Oletuksena REST kutsuu Digitrafficia suoraan selaimesta (CORS sallittu).

```bash
scp docs/website/nginx/marine-weather-locations.conf root@94.237.38.55:/etc/nginx/snippets/
nginx -t && systemctl reload nginx
```

## Testi-MMSI

- `230982000` — FINNMAID (usein aktiivinen Itämerellä)

## Rajoitukset (MVP)

- Ei kirjautumista / synkkaa laitteiden välillä
- Ei premium-porttia webissä
- MQTT-tunnukset ovat Digitrafficin julkiset (vain client-puoli)
