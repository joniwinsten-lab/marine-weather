# Marine Weather — marketing site

Static landing page with app screenshots. Deployed separately from Sindbad and other services on the VPS.

**Production app:** Android on [Google Play](https://play.google.com/store/apps/details?id=fi.veneappi.app). iOS App Store coming later.

## Live URL

https://safelight.fi/marine-weather/ (primary)

http://94.237.38.55/marine-weather/ (IP fallback)

## Deploy

```bash
./docs/website/deploy.sh
```

Requires SSH as `root@94.237.38.55`. Files land in `/var/www/marine-weather/`.

**Nginx:** snippet in `nginx/marine-weather-locations.conf` → `/etc/nginx/snippets/marine-weather-locations.conf`. Must be included in:

- default IP vhost (`sindbad-web`) — already configured
- `safelight.fi` HTTPS server block — see `nginx/safelight.fi-snippet.txt`

Optional AIS REST proxy in the same snippet (not required for `/track/` — Digitraffic allows browser CORS with `Digitraffic-User`).

## Contents

- `track/` — **AIS Seuranta** web prototype ([README](track/README.md)) at `/marine-weather/track/`
- `index.html` — English (default); Play Store download CTA
- `fi/`, `sv/`, `nb/`, `et/` — Finnish, Swedish, Norwegian, Estonian landing pages
- Language switcher in header on all pages
- `privacy.html` — privacy policy (English; linked from all locales)
- `assets/screenshots/` — tablet screenshots from the app
- `assets/qr-play-store.png` — QR code for Google Play (print flyers)
- `print/` — **A5 printable flyers** with QR code ([FI](print/a5-flyer-fi.html), [EN](print/a5-flyer-en.html))
