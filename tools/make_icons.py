#!/usr/bin/env python3
"""Generate SnapGrab launcher icons: OLED-black rounded square + crimson circle + white arrow.

Writes res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png and the
adaptive foreground into res/mipmap-xxxhdpi/ic_launcher_fg.png.
"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res"))

RED = (255, 51, 85, 255)
WHITE = (255, 255, 255, 255)


def draw_glyph(size):
    """Legacy launcher icon: rounded black square + red circle + white download arrow."""
    S = 8
    px = size * S
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # rounded black square
    d.rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.22), fill=(0, 0, 0, 255))

    # red circle
    cx = cy = px / 2
    cr = px * 0.34
    d.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=RED)

    # white arrow (shaft, head, base line)
    shaft_w = max(1, int(px * 0.070))
    top = cy - px * 0.135
    bot = cy + px * 0.075
    d.line([cx, top, cx, bot], fill=WHITE, width=shaft_w)
    hw = px * 0.115
    d.polygon([(cx - hw, bot - px * 0.105), (cx + hw, bot - px * 0.105), (cx, bot + px * 0.045)],
              fill=WHITE)
    base_w = max(1, int(px * 0.055))
    d.line([cx - px * 0.145, cy + px * 0.205, cx + px * 0.145, cy + px * 0.205],
           fill=WHITE, width=base_w)

    return img.resize((size, size), Image.LANCZOS)


def draw_fg(size):
    """Adaptive-icon foreground: transparent canvas, glyph inside the 66% safe zone."""
    S = 4
    px = size * S
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    cx = cy = px / 2
    cr = px * 0.235
    d.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=RED)

    d.line([cx, cy - px * 0.095, cx, cy + px * 0.06], fill=WHITE, width=max(1, int(px * 0.048)))
    hw = px * 0.078
    d.polygon([(cx - hw, cy + px * 0.015), (cx + hw, cy + px * 0.015), (cx, cy + px * 0.095)],
              fill=WHITE)
    d.line([cx - px * 0.095, cy + px * 0.135, cx + px * 0.095, cy + px * 0.135],
           fill=WHITE, width=max(1, int(px * 0.038)))

    return img.resize((size, size), Image.LANCZOS)


def main():
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in densities.items():
        outdir = os.path.join(ROOT, "mipmap-" + name)
        os.makedirs(outdir, exist_ok=True)
        img = draw_glyph(size)
        img.save(os.path.join(outdir, "ic_launcher.png"))
        img.save(os.path.join(outdir, "ic_launcher_round.png"))
        print("wrote", outdir, size)

    fgdir = os.path.join(ROOT, "mipmap-xxxhdpi")
    draw_fg(432).save(os.path.join(fgdir, "ic_launcher_fg.png"))
    print("wrote adaptive foreground")


if __name__ == "__main__":
    main()
