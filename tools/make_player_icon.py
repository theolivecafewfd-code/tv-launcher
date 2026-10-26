"""Draws the WIPTV Player icon (square) and TV banner (16:9): the WIPTV screen with a play button."""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
SCREEN = (126, 87, 224)        # purple, so it stands apart from the launcher (blue) and YouTube (red)
BEZEL = (46, 74, 104)          # same dark strip as the launcher
PLAY = (255, 255, 255)

def draw_tv(w, h, radius):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bezel_h = int(h * 0.17)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=BEZEL)
    d.rounded_rectangle([0, 0, w - 1, h - bezel_h], radius=radius, fill=SCREEN)
    d.rectangle([0, h - bezel_h - radius, w - 1, h - bezel_h], fill=SCREEN)

    # A play triangle in the middle of the screen.
    area_h = h - bezel_h
    size = area_h * 0.46
    cx, cy = w / 2 + size * 0.08, area_h / 2
    d.polygon(
        [(cx - size * 0.42, cy - size / 2), (cx - size * 0.42, cy + size / 2), (cx + size * 0.5, cy)],
        fill=PLAY,
    )

    # WIPTV bottom right, on the bezel, as on the launcher.
    font = ImageFont.truetype(FONT, int(bezel_h * 0.62))
    text = "WIPTV"
    box = d.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    tx = w - tw - int(w * 0.05) - box[0]
    ty = h - bezel_h + (bezel_h - th) / 2 - box[1]
    d.text((tx, ty), text, font=font, fill=(255, 255, 255))
    return img

def make(path, w, h, pad_x, pad_y, background, rounded=True):
    S = 4
    W, H = w * S, h * S
    canvas = Image.new("RGBA", (W, H), background)
    tv_w, tv_h = W - 2 * pad_x * S, H - 2 * pad_y * S
    tv = draw_tv(tv_w, tv_h, radius=int(min(tv_w, tv_h) * 0.07) if rounded else 1)
    canvas.alpha_composite(tv, (pad_x * S, pad_y * S))
    canvas.resize((w, h), Image.LANCZOS).save(path)

out = sys.argv[1]
make(f"{out}/ic_launcher.png", 512, 512, 24, 96, (0, 0, 0, 0))
make(f"{out}/banner.png", 640, 360, 0, 0, (22, 17, 30, 255), rounded=False)
print("ok")
