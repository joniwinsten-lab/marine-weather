#!/usr/bin/env python3
"""Generate Google Play QR code for print flyers."""

from pathlib import Path

import qrcode

PLAY_URL = "https://play.google.com/store/apps/details?id=fi.veneappi.app"
OUT = Path(__file__).resolve().parent.parent / "assets" / "qr-play-store.png"


def main() -> None:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=8,
        border=2,
    )
    qr.add_data(PLAY_URL)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
