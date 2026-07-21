# Print flyers (Marine Weather)

Handouts with **dual QR codes** — Google Play (Android) and App Store (iPhone/iPad).

| File | Format | Size |
|------|--------|------|
| [flyer-a5-fi.html](flyer-a5-fi.html) | **A5** portrait | 148×210 mm |
| [flyer-a4-fi.html](flyer-a4-fi.html) | **A4** portrait | 210×297 mm |
| [flyer-10x15-fi.html](flyer-10x15-fi.html) | **Postcard** | 100×150 mm (10×15 cm) |

**Styles:** [print-flyer.css](print-flyer.css) (shared)

**QR targets:**

| Store | URL |
|-------|-----|
| Google Play | `https://play.google.com/store/apps/details?id=fi.veneappi.app` |
| App Store | `https://apps.apple.com/us/app/marine-weather-finland/id6787024900` |

**QR images:** `../assets/qr-play-store.png`, `../assets/qr-app-store.png`

## Print (all sizes)

1. Open the HTML in Chrome or Safari (local file or [safelight.fi/marine-weather/print/](https://safelight.fi/marine-weather/print/)).
2. **Print** → choose paper size (A5 / A4 / custom 100×150 mm).
3. Orientation: **Portrait**.
4. Margins: **Minimum** or as noted in the on-screen hint box.
5. Enable **Background graphics**.
6. Optional: **Save as PDF** from the print dialog.

### Size-specific notes

| Format | Paper / custom size | Margins |
|--------|---------------------|---------|
| A5 | A5 | ~7 mm |
| A4 | A4 | ~10 mm |
| 10×15 cm | Custom **100 mm × 150 mm** | 0–4 mm, borderless OK on photo printers |

## Regenerate QR codes

```bash
python3 docs/website/print/generate-qr.py
```

Then redeploy the site if the PNGs changed.

## Legacy files

| File | Notes |
|------|-------|
| [a5-flyer-fi.html](a5-flyer-fi.html) | Old single-QR A5 (Play only) — use **flyer-a5-fi.html** instead |
| [a5-flyer-en.html](a5-flyer-en.html) | English A5, Play only |
| [selphy-postcard-fi.html](selphy-postcard-fi.html) | Canon SELPHY 100×148 mm, Play only |

## PNG export (10×15 cm, 300 DPI)

1181×1772 px at 300 DPI for 100×150 mm:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu \
  --screenshot="/tmp/marine-weather-10x15.png" \
  --window-size=1181,1772 \
  --force-device-scale-factor=1 \
  "file://$PWD/docs/website/print/flyer-10x15-fi.html?export=png"
```
