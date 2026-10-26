"""Draws the WIPTV launcher icon (square) and TV banner (16:9) as PNGs."""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
SCREEN = (31, 142, 241)        # bright TV blue
SCREEN_SHADE = (24, 120, 214)  # lower part of the screen
BEZEL = (46, 74, 104)          # dark strip along the bottom
WIFI = (34, 58, 86)
TILES = [
    (249, 168, 37), (79, 195, 247), (236, 236, 236),
    (0, 188, 212), (197, 225, 122), (255, 107, 107),
]

def draw_tv(w, h, scale, radius):
    """A TV screen filling w x h: tiles on the left, wifi on the right, WIPTV on the bezel."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bezel_h = int(h * 0.17)
    # Screen + bezel as one rounded shape, then the bezel strip over the bottom.
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=BEZEL)
    d.rounded_rectangle([0, 0, w - 1, h - bezel_h], radius=radius, fill=SCREEN)
    d.rectangle([0, h - bezel_h - radius, w - 1, h - bezel_h], fill=SCREEN)

    # 3 x 2 grid of app tiles on the left.
    area_h = h - bezel_h
    tile = int(area_h * 0.25)
    gap = int(tile * 0.32)
    grid_w = 3 * tile + 2 * gap
    grid_h = 2 * tile + gap
    x0 = int(w * 0.09)
    y0 = int((area_h - grid_h) / 2)
    for i, colour in enumerate(TILES):
        col, row = i % 3, i // 3
        x = x0 + col * (tile + gap)
        y = y0 + row * (tile + gap)
        d.rounded_rectangle([x, y, x + tile, y + tile], radius=int(tile * 0.2), fill=colour)

    # Wifi waves on the right: three arcs over a dot.
    cx = x0 + grid_w + int((w - x0 - grid_w) * 0.5)
    cy = y0 + int(grid_h * 0.78)
    stroke = max(2, int(tile * 0.17))
    for k, r in enumerate([tile * 0.55, tile * 0.95, tile * 1.35]):
        r = int(r)
        d.arc([cx - r, cy - r, cx + r, cy + r], start=225, end=315, fill=WIFI, width=stroke)
    dot = int(stroke * 0.85)
    d.ellipse([cx - dot, cy - dot, cx + dot, cy + dot], fill=WIFI)

    # WIPTV bottom right, on the bezel.
    font = ImageFont.truetype(FONT, int(bezel_h * 0.62))
    text = "WIPTV"
    box = d.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    tx = w - tw - int(w * 0.05) - box[0]
    ty = h - bezel_h + (bezel_h - th) / 2 - box[1]
    d.text((tx, ty), text, font=font, fill=(255, 255, 255))
    return img

def make(path, w, h, pad_x, pad_y, background, rounded=True):
    S = 4  # draw big, then shrink, for smooth edges
    W, H = w * S, h * S
    canvas = Image.new("RGBA", (W, H), background)
    tv_w, tv_h = W - 2 * pad_x * S, H - 2 * pad_y * S
    tv = draw_tv(tv_w, tv_h, S, radius=int(min(tv_w, tv_h) * 0.07) if rounded else 1)
    canvas.alpha_composite(tv, (pad_x * S, pad_y * S))
    canvas.resize((w, h), Image.LANCZOS).save(path)

out = sys.argv[1]
# Square app icon: the TV centred on a transparent square.
make(f"{out}/ic_launcher.png", 512, 512, 24, 96, (0, 0, 0, 0))
# 16:9 banner for the Fire TV / Android TV app row: the screen fills it edge to edge.
make(f"{out}/banner.png", 640, 360, 0, 0, (22, 17, 30, 255), rounded=False)
print("ok")
