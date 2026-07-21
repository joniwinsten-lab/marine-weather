#!/usr/bin/env python3
"""Generate Google Play and App Store QR codes for print flyers."""

from pathlib import Path

import qrcode

PLAY_URL = "https://play.google.com/store/apps/details?id=fi.veneappi.app"
APP_STORE_URL = "https://apps.apple.com/us/app/marine-weather-finland/id6787024900"

ASSETS = Path(__file__).resolve().parent.parent / "assets"


def write_qr(url: str, out: Path) -> None:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=8,
        border=2,
    )
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"Wrote {out}")


def main() -> None:
    write_qr(PLAY_URL, ASSETS / "qr-play-store.png")
    write_qr(APP_STORE_URL, ASSETS / "qr-app-store.png")


if __name__ == "__main__":
    main()
