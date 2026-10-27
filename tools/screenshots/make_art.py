"""Draws invented posters, backdrops, episode stills and channel logos for the screenshot run.

Usage: python3 make_art.py <output dir>
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalogue import HARBOUR_EPISODES, LIVE_GROUPS, MOVIES, SERIES  # noqa: E402

# (sky top, sky bottom, sun, land) for each made-up title.
PALETTES = [
    ((176, 102, 63), (42, 29, 36), (255, 217, 168), (36, 26, 32)),
    ((20, 40, 80), (90, 160, 150), (210, 255, 230), (12, 22, 36)),
    ((30, 70, 110), (10, 20, 35), (150, 210, 255), (8, 14, 24)),
    ((200, 160, 90), (90, 60, 40), (255, 240, 200), (60, 45, 25)),
    ((15, 15, 40), (60, 40, 90), (240, 220, 255), (10, 10, 20)),
    ((230, 200, 170), (150, 90, 80), (255, 250, 230), (80, 50, 50)),
    ((120, 170, 200), (230, 210, 190), (255, 255, 240), (60, 90, 110)),
    ((70, 60, 60), (30, 25, 25), (200, 150, 110), (20, 18, 18)),
    ((40, 60, 90), (200, 120, 70), (255, 230, 170), (25, 30, 45)),
    ((180, 90, 40), (60, 30, 20), (255, 200, 120), (40, 20, 15)),
    ((90, 90, 100), (40, 40, 50), (200, 200, 210), (30, 30, 35)),
    ((150, 170, 190), (70, 80, 90), (245, 245, 255), (40, 50, 45)),
    ((25, 45, 70), (170, 110, 80), (255, 210, 150), (15, 25, 40)),
    ((100, 70, 50), (200, 150, 100), (255, 235, 200), (60, 40, 30)),
    ((60, 80, 70), (150, 160, 130), (230, 240, 220), (40, 55, 45)),
    ((240, 180, 120), (200, 100, 90), (255, 250, 220), (120, 70, 70)),
    ((10, 20, 50), (80, 30, 90), (255, 200, 255), (5, 10, 25)),
    ((30, 30, 40), (90, 70, 50), (255, 220, 160), (15, 15, 20)),
]

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def scene(width, height, palette, seed):
    top, bottom, sun, land = palette
    rnd = random.Random(seed)
    column = Image.new("RGB", (1, height))
    for y in range(height):
        t = y / max(1, height - 1)
        column.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    img = column.resize((width, height))
    # Sun, with a soft glow.
    sx, sy = width * rnd.uniform(0.55, 0.8), height * rnd.uniform(0.2, 0.38)
    r = min(width, height) * rnd.uniform(0.06, 0.1)
    glow = Image.new("L", (width, height), 0)
    ImageDraw.Draw(glow).ellipse((sx - r * 3, sy - r * 3, sx + r * 3, sy + r * 3), fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(r * 1.5))
    img = Image.composite(Image.new("RGB", (width, height), sun), img, glow)
    draw = ImageDraw.Draw(img)
    draw.ellipse((sx - r, sy - r, sx + r, sy + r), fill=sun)
    # Layered hills or waves.
    for layer in range(3):
        base = height * (0.55 + 0.13 * layer)
        shade = tuple(int(land[i] * (0.6 + 0.2 * (2 - layer)) + bottom[i] * 0.15 * (2 - layer)) for i in range(3))
        amp = height * rnd.uniform(0.03, 0.08)
        freq = rnd.uniform(1.0, 2.6)
        phase = rnd.uniform(0, math.pi * 2)
        points = [(0, height)]
        for x in range(0, width + 8, 8):
            points.append((x, base + amp * math.sin(phase + freq * x / width * math.pi * 2)))
        points.append((width, height))
        draw.polygon(points, fill=shade)
    return img


def poster(path, title, palette, seed):
    img = scene(400, 600, palette, seed)
    shade = Image.new("L", (400, 600), 0)
    ImageDraw.Draw(shade).rectangle((0, 380, 400, 600), fill=170)
    shade = shade.filter(ImageFilter.GaussianBlur(60))
    img = Image.composite(Image.new("RGB", (400, 600), (8, 10, 18)), img, shade)
    draw = ImageDraw.Draw(img)
    words = title.upper().split()
    lines, line = [], ""
    for word in words:
        trial = (line + " " + word).strip()
        if len(trial) > 12 and line:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    f = font(BOLD, 40)
    y = 560 - 48 * len(lines)
    for text in lines:
        w = draw.textlength(text, font=f)
        draw.text(((400 - w) / 2, y), text, font=f, fill=(250, 248, 240))
        y += 48
    img.save(path)


def still(path, palette, seed, width=640, height=360):
    scene(width, height, palette, seed).save(path)


def logo(path, name, seed):
    rnd = random.Random(seed)
    colour = PALETTES[seed % len(PALETTES)][1]
    img = Image.new("RGB", (160, 96), colour)
    draw = ImageDraw.Draw(img)
    initials = "".join(word[0] for word in name.split())[:3]
    f = font(BOLD, 40)
    w = draw.textlength(initials, font=f)
    draw.text(((160 - w) / 2, 22), initials, font=f, fill=(255, 255, 255))
    rnd.random()
    img.save(path)


def main(out):
    img_dir = os.path.join(out, "img")
    os.makedirs(img_dir, exist_ok=True)
    for (mid, title, pal, has_backdrop, *_rest) in MOVIES:
        palette = PALETTES[pal % len(PALETTES)]
        if mid != 2011:  # one title with no artwork at all, to show the fallback
            poster(os.path.join(img_dir, f"poster-{mid}.png"), title, palette, mid)
        if has_backdrop:
            still(os.path.join(img_dir, f"backdrop-{mid}.png"), palette, mid + 7, 1280, 720)
    for (sid, title, pal, has_backdrop, *_rest) in SERIES:
        palette = PALETTES[pal % len(PALETTES)]
        poster(os.path.join(img_dir, f"poster-{sid}.png"), title, palette, sid)
        if has_backdrop:
            still(os.path.join(img_dir, f"backdrop-{sid}.png"), palette, sid + 7, 1280, 720)
    for season, titles in HARBOUR_EPISODES.items():
        for number in range(1, len(titles) + 1):
            if number == 4:  # one episode without a still, to show the fallback
                continue
            still(os.path.join(img_dir, f"still-3001-{season}-{number}.png"), PALETTES[(season * 7 + number) % len(PALETTES)], season * 100 + number)
    n = 0
    for _gid, _name, channels in LIVE_GROUPS:
        for name in channels:
            n += 1
            logo(os.path.join(img_dir, f"logo-{n}.png"), name, n)


if __name__ == "__main__":
    main(sys.argv[1])
