# A5 print flyers

Handout-sized pages with a QR code to Google Play.

| File | Language | Format |
|------|----------|--------|
| [a5-flyer-fi.html](a5-flyer-fi.html) | Finnish | A5 portrait (148×210 mm) |
| [a5-flyer-en.html](a5-flyer-en.html) | English | A5 portrait |
| [selphy-postcard-fi.html](selphy-postcard-fi.html) | Finnish | **Canon SELPHY postikortti** (100×148 mm) |

**QR target:** `https://play.google.com/store/apps/details?id=fi.veneappi.app`  
**Image:** `../assets/qr-play-store.png`

## A5 print

1. Open the HTML file in a browser (local file or after deploy).
2. **Print** → paper size **A5**, orientation **Portrait**.
3. Margins: **Default** or **Minimum**.
4. Enable **Background graphics** so the dark theme and screenshots print correctly.

Save as PDF from the print dialog if you want a file to share.

## Canon SELPHY (postikortti P, 100×148 mm)

Use [selphy-postcard-fi.html](selphy-postcard-fi.html) — layout is sized for SELPHY **Postcard / P size** after perforation ([Canon specs](https://cam.start.canon/en/P001/manual/html/UG-07_Reference_0070.html): 100.0×148.0 mm borderless).

**Paper & ink:** KP-36IP or KC-36IP postcard cassette.

### Option A — Canon SELPHY Photo Layout (recommended)

1. Open `selphy-postcard-fi.html` in Chrome/Safari → Print → **Save as PDF** (custom size 100×148 mm, margins 0, background graphics on).
2. Import PDF into **Canon SELPHY Photo Layout** (or print directly from the app to the printer).
3. Paper: **Postcard (P)**, finish per cassette, **Borderless** if available.

### Option B — Print from browser to SELPHY

1. Load cassette and select **Postcard** on the printer.
2. Open `selphy-postcard-fi.html`, Print.
3. Paper: custom **100 mm × 148 mm** (or Postcard P), **Portrait**, margins **None**, scale **100%**, **Background graphics** on, **Borderless** in printer dialog if offered.
Borderless SELPHY crops ~3–5 mm per edge; layout uses **5.5 mm safe inset** on `selphy-postcard-fi.html`. If still clipped, increase `--safe-inset` in `print-selphy-postcard.css`.

### PNG export (300 DPI, optional)

For exact pixel dimensions (1181×1748 px at 300 DPI):

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu \
  --screenshot="/tmp/selphy-marine-weather.png" \
  --window-size=1181,1748 \
  --force-device-scale-factor=1 \
  "file://$PWD/docs/website/print/selphy-postcard-fi.html?export=png"
```

Import the PNG in SELPHY Photo Layout → Postcard, borderless.

## Regenerate QR code

```bash
python3 docs/website/print/generate-qr.py
```

Then redeploy the site if the PNG changed.
