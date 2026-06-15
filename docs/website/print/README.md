# A5 print flyers

Handout-sized pages with a QR code to Google Play.

| File | Language |
|------|----------|
| [a5-flyer-fi.html](a5-flyer-fi.html) | Finnish |
| [a5-flyer-en.html](a5-flyer-en.html) | English |

**QR target:** `https://play.google.com/store/apps/details?id=fi.veneappi.app`  
**Image:** `../assets/qr-play-store.png`

## Print

1. Open the HTML file in a browser (local file or after deploy).
2. **Print** → paper size **A5**, orientation **Portrait**.
3. Margins: **Default** or **Minimum**.
4. Enable **Background graphics** so the dark theme and screenshots print correctly.

Save as PDF from the print dialog if you want a file to share.

## Regenerate QR code

```bash
python3 docs/website/print/generate-qr.py
```

Then redeploy the site if the PNG changed.
