#!/usr/bin/env python3
"""
Zrzut ekranu w ramce telefonu do README (docs/screenshots).

Wejście: zrzut 1080x2400 z emulatora (adb exec-out screencap -p). Wyjście: obraz 1080x1920 z przezroczystym tłem -
ciemna obudowa z metaliczną krawędzią, otwór aparatu, przyciski z prawej i miękki cień. Zrzuty dla F-Droid
(fastlane/.../phoneScreenshots) są bez ramki - sam zrzut z emulatora.

Użycie:  python3 docs/screenshots/ramka.py surowy.png wynik.png
Wymaga: Pillow.
"""
import sys

from PIL import Image, ImageDraw, ImageFilter

W, H = 1080, 1920
BODY = (102, 21, 978, 1899)          # obudowa (lewa, góra, prawa, dół), włącznie
BODY_RADIUS = 96
RIM = [70, 128, 165, 150, 118, 88, 58, 30]   # metaliczna krawędź od zewnątrz do środka
BEZEL = (8, 8, 8)
SCREEN = (130, 49, 820, 1822)        # x, y, szerokość, wysokość ekranu
SCREEN_RADIUS = 76
CAMERA = (540.5, 79.5)
BUTTONS = [(975, 381, 984, 492), (975, 581, 984, 802)]
SS = 2                               # rysowanie w podwójnej rozdzielczości - gładkie krawędzie


def rounded(draw, box, radius, fill):
    x0, y0, x1, y1 = (v * SS for v in box)
    draw.rounded_rectangle((x0, y0, x1, y1), radius=radius * SS, fill=fill)


def frame(raw: Image.Image) -> Image.Image:
    big = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))

    # Cień w prawo i w dół, jak dawniej.
    shadow = Image.new("RGBA", big.size, (0, 0, 0, 0))
    l, t, r, b = BODY
    rounded(ImageDraw.Draw(shadow), (l + 8, t + 12, r + 8, b + 12), BODY_RADIUS, (0, 0, 0, 140))
    big = Image.alpha_composite(big, shadow.filter(ImageFilter.GaussianBlur(18 * SS)))

    draw = ImageDraw.Draw(big)
    for button in BUTTONS:
        rounded(draw, button, 3, (70, 70, 70, 255))
    for inset, shade in enumerate(RIM):
        rounded(draw, (l + inset, t + inset, r - inset, b - inset), BODY_RADIUS - inset, (shade, shade, shade, 255))
    rounded(draw, (l + len(RIM), t + len(RIM), r - len(RIM), b - len(RIM)), BODY_RADIUS - len(RIM), BEZEL + (255,))

    out = big.resize((W, H), Image.LANCZOS)

    # Ekran: zrzut przeskalowany do okna ekranu, z zaokrąglonymi rogami (maska rysowana w 4x).
    x, y, w, h = SCREEN
    screen = raw.convert("RGBA").resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w * 4, h * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), radius=SCREEN_RADIUS * 4, fill=255)
    out.paste(screen, (x, y), mask.resize((w, h), Image.LANCZOS))

    # Otwór aparatu na wierzchu ekranu.
    cam = Image.new("RGBA", (W * 4, H * 4), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cam)
    cx, cy = (v * 4 for v in CAMERA)
    for radius, color in ((17, (28, 28, 30)), (14.5, (4, 4, 6))):
        cd.ellipse((cx - radius * 4, cy - radius * 4, cx + radius * 4, cy + radius * 4), fill=color + (255,))
    cd.ellipse((cx - 7 * 4, cy - 7 * 4, cx - 1 * 4, cy - 1 * 4), fill=(40, 48, 60, 255))
    return Image.alpha_composite(out, cam.resize((W, H), Image.LANCZOS))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    frame(Image.open(sys.argv[1])).save(sys.argv[2], optimize=True)
