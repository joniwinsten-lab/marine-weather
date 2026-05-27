# Marine Weather — marketing site (English)

Static landing page with app screenshots. Deployed separately from Sindbad and other services on the VPS.

## Live URL

http://94.237.38.55/marine-weather/

## Deploy

```bash
./docs/website/deploy.sh
```

Requires SSH as `root@94.237.38.55`. Files land in `/var/www/marine-weather/`. Nginx snippet: `/etc/nginx/snippets/marine-weather-locations.conf`.

## Contents

- `index.html` — English (default)
- `fi/`, `sv/`, `nb/`, `et/` — Finnish, Swedish, Norwegian, Estonian landing pages
- Language switcher in header on all pages
- `privacy.html` — privacy policy (English; linked from all locales)
- `privacy.html` — privacy policy (same text as `docs/privacy.html`)
- `assets/screenshots/` — tablet screenshots from the app
