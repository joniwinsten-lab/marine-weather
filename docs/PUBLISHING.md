# Publishing Marine Weather on Google Play

**App name:** Marine Weather  
**Package:** `fi.veneappi.app`  
**Default language:** English  

## Quick links

| Item | Location |
|------|----------|
| Privacy policy (public URL) | [privacy.html](privacy.html) → `https://joniwinsten-lab.github.io/marine-weather/privacy.html` |
| Store listing copy (EN) | [store-listing-en.md](store-listing-en.md) |
| Data safety answers | [data-safety-play-console.md](data-safety-play-console.md) |
| In-app products & prices | [play-billing-products.md](play-billing-products.md) |
| **Production-ready snapshot (0.2.14)** | [snapshot-production-ready.md](snapshot-production-ready.md) |
| Play icon 512×512 | [play-store-icon-512.png](play-store-icon-512.png) |

## Release signing (one-time setup)

1. Copy `keystore.properties.example` → `keystore.properties` (never commit).
2. Create keystore:

   ```bash
   keytool -genkey -v -keystore release-keystore.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias marine-weather
   ```

3. Build signed AAB:

   ```bash
   ./gradlew bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`

## Do not publish

- `friends` build (`fi.veneappi.app.friends`) — local sharing only.

## GitHub Pages

Enable **Settings → Pages → Deploy from branch → main → /docs** so `privacy.html` is public.
