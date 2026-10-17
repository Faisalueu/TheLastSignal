"""16x16 block textures for the 192 dimension blocks, by style + palette color (+ glyph overlay for marking blocks)."""
import random
from PIL import Image, ImageDraw


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mul(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def base_noise(img, c, rng, amp=14, dark=0.0, dens=0.0, dc=None):
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = 1 + rng.uniform(-amp, amp) / 100.0 * 3
            col = mul(c, t)
            if dens and rng.random() < dens:
                col = mix(col, dc or mul(c, 0.5), 0.5)
            px[x, y] = col + (255,)


def make(style, color, accent, seed, glyph=None, light=0):
    rng = random.Random(seed)
    c, a = rgb(color), rgb(accent)
    img = Image.new("RGBA", (16, 16), c + (255,))
    d = ImageDraw.Draw(img)
    dk = mul(c, 0.55)
    lt = mul(c, 1.35)
    s = style
    if s == "stone" or s == "concrete":
        base_noise(img, c, rng, 12 if s == "stone" else 6, dens=0.2 if s == "stone" else 0.05)
        for _ in range(3 if s == "stone" else 1):
            x, y = rng.randint(0, 14), rng.randint(0, 12)
            d.line([(x, y), (x + rng.randint(-2, 2), y + rng.randint(2, 5))], dk + (255,))
        if s == "concrete":
            d.line([(0, 0), (15, 0)], dk + (255,)); d.line([(0, 8), (15, 8)], dk + (255,)); d.line([(8, 0), (8, 8)], dk + (255,)); d.line([(4, 8), (4, 15)], dk + (255,))
    elif s == "brick":
        base_noise(img, c, rng, 10, dens=0.1)
        for y in range(0, 16, 4):
            d.line([(0, y), (15, y)], dk + (255,))
            off = 0 if (y // 4) % 2 == 0 else 4
            for x in range(off, 16, 8): d.line([(x, y), (x, y + 3)], dk + (255,))
    elif s == "wood":
        base_noise(img, c, rng, 6)
        for x in range(0, 16, 4): d.line([(x, 0), (x, 15)], dk + (255,))
        for _ in range(8): d.point((rng.randint(0, 15), rng.randint(0, 15)), lt + (255,))
        d.line([(0, 15), (15, 15)], dk + (255,))
    elif s == "tile":
        base_noise(img, c, rng, 5)
        d.line([(0, 0), (15, 0)], dk + (255,)); d.line([(0, 8), (15, 8)], dk + (255,)); d.line([(0, 0), (0, 15)], dk + (255,)); d.line([(8, 0), (8, 15)], dk + (255,))
        for _ in range(5): d.point((rng.randint(1, 14), rng.randint(1, 14)), mul(c, 0.8) + (255,))
    elif s == "glass":
        for y in range(16):
            for x in range(16):
                img.putpixel((x, y), c + (120,))
        d.rectangle([0, 0, 15, 15], outline=mul(c, 0.7) + (255,))
        d.line([(2, 3), (4, 1)], mul(c, 1.8) + (230,)); d.line([(3, 6), (6, 3)], mul(c, 1.6) + (200,))
        d.line([(0, 8), (15, 8)], mul(c, 0.8) + (200,)); d.line([(8, 0), (8, 15)], mul(c, 0.8) + (200,))
    elif s == "ore":
        base_noise(img, mul(c, 1.0), rng, 12, dens=0.2)
        for _ in range(9):
            x, y = rng.randint(1, 13), rng.randint(1, 13)
            d.rectangle([x, y, x + 1, y + rng.randint(0, 1)], fill=a + (255,))
    elif s == "metal":
        base_noise(img, c, rng, 7)
        d.rectangle([0, 0, 15, 15], outline=dk + (255,)); d.line([(0, 7), (15, 7)], dk + (255,))
        for p in ((2, 2), (13, 2), (2, 13), (13, 13)): d.point(p, lt + (255,))
        for _ in range(4):
            x = rng.randint(1, 14); d.line([(x, rng.randint(0, 5)), (x, rng.randint(7, 15))], mix(c, (120, 60, 20), 0.5) + (255,))
    elif s == "soil":
        base_noise(img, c, rng, 10, dens=0.3)
        for _ in range(14): d.point((rng.randint(0, 15), rng.randint(0, 15)), mul(c, 1.3) + (255,))
    elif s == "sand":
        base_noise(img, c, rng, 7)
        for y in range(2, 16, 5): d.line([(0, y), (15, y + 1)], mul(c, 0.9) + (255,))
    elif s == "fabric":
        base_noise(img, c, rng, 6)
        for y in range(0, 16, 2): d.line([(0, y), (15, y)], mul(c, 0.88) + (255,))
        for x in range(0, 16, 2): d.line([(x, 0), (x, 15)], mul(c, 0.94) + (255,))
    elif s == "organic":
        base_noise(img, c, rng, 12, dens=0.25)
        for _ in range(3):
            x, y = rng.randint(0, 15), rng.randint(0, 15)
            pts = [(x, y)]
            for _ in range(4): x = max(0, min(15, x + rng.randint(-3, 3))); y = max(0, min(15, y + rng.randint(-3, 3))); pts.append((x, y))
            d.line(pts, mul(c, 0.55) + (255,))
    elif s == "ice":
        base_noise(img, c, rng, 6)
        d.line([(0, 15), (15, 0)], mul(c, 1.9) + (255,)); d.line([(3, 0), (9, 9)], mul(c, 1.6) + (255,)); d.line([(10, 5), (15, 12)], mul(c, 1.5) + (255,))
    elif s == "crystal":
        base_noise(img, c, rng, 8)
        for pts in ([(8, 1), (13, 8), (8, 15), (3, 8)], [(8, 4), (11, 8), (8, 12), (5, 8)]):
            d.polygon(pts, outline=mix(a, (255, 255, 255), 0.4) + (255,))
        d.polygon([(8, 5), (10, 8), (8, 11), (6, 8)], fill=a + (255,))
    elif s == "shelf":
        base_noise(img, c, rng, 6)
        for y in (0, 5, 10): d.line([(0, y), (15, y)], mul(c, 0.5) + (255,))
        for y0 in (1, 6, 11):
            x = 0
            while x < 15:
                w = rng.randint(1, 3); col = rng.choice([mul(c, 1.5), mix(c, a, .4), mul(c, 0.7), mix(c, (90, 40, 30), .5)])
                d.rectangle([x, y0, x + w - 1, y0 + 3], fill=col + (255,)); x += w + (1 if rng.random() < .2 else 0)
    elif s == "panel":
        base_noise(img, c, rng, 5)
        d.rectangle([1, 1, 14, 14], outline=dk + (255,)); d.rectangle([3, 3, 12, 8], outline=mul(c, 1.3) + (255,))
        for x in (4, 7, 10): d.point((x, 11), a + (255,))
    elif s == "shingle":
        base_noise(img, c, rng, 8)
        for y in range(0, 16, 4):
            d.line([(0, y + 3), (15, y + 3)], dk + (255,))
            for x in range((y // 4) % 2 * 2, 16, 4): d.line([(x, y), (x, y + 3)], dk + (255,))
    elif s == "bone":
        base_noise(img, c, rng, 7, dens=0.12)
        d.line([(2, 12), (12, 2)], mul(c, 1.25) + (255,)); d.ellipse([0, 11, 3, 14], fill=mul(c, 1.2) + (255,))
        d.line([(0, 4), (15, 4)], dk + (255,))
    elif s == "resin":
        base_noise(img, c, rng, 10)
        for _ in range(4):
            x, y = rng.randint(2, 12), rng.randint(2, 12); d.ellipse([x, y, x + 3, y + 3], outline=mul(c, 1.6) + (255,))
        d.point((5, 4), mul(c, 2.0) + (255,))
    elif s == "coral":
        base_noise(img, c, rng, 10)
        for x in (3, 8, 12):
            d.line([(x, 15), (x, 6)], mul(c, 1.5) + (255,)); d.line([(x, 9), (x + 2, 6)], mul(c, 1.5) + (255,)); d.line([(x, 8), (x - 2, 5)], mul(c, 1.5) + (255,))
    elif s == "basalt":
        base_noise(img, c, rng, 6)
        for x in (0, 5, 10): d.line([(x, 0), (x, 15)], mul(c, 0.55) + (255,))
        for _ in range(6): d.point((rng.randint(0, 15), rng.randint(0, 15)), mul(c, 1.8) + (255,))
    elif s == "paper":
        base_noise(img, c, rng, 4)
        for y in range(2, 16, 3): d.line([(2, y), (rng.randint(8, 13), y)], mul(c, 0.75) + (255,))
    elif s == "wallpaper":
        base_noise(img, c, rng, 5)
        for x in range(0, 16, 4): d.line([(x, 0), (x, 15)], mul(c, 0.9) + (255,))
        for x in range(2, 16, 4):
            for y in range(1, 16, 5): d.point((x, y), mul(c, 0.7) + (255,)); d.point((x, y + 1), mul(c, 0.7) + (255,))
        for _ in range(3):
            x = rng.randint(0, 15); d.line([(x, 0), (x, rng.randint(4, 9))], mul(c, 0.7) + (255,))
    elif s == "rack":
        base_noise(img, c, rng, 5)
        for y in range(1, 15, 3):
            d.rectangle([1, y, 14, y + 1], fill=mul(c, 0.7) + (255,))
            for x in (3, 5, 11): d.point((x, y), a + (255,)) if rng.random() < 0.6 else None
    elif s == "pipe":
        base_noise(img, c, rng, 6)
        d.rectangle([0, 4, 15, 11], fill=mul(c, 1.2) + (255,)); d.line([(0, 4), (15, 4)], mul(c, 0.6) + (255,)); d.line([(0, 11), (15, 11)], mul(c, 0.6) + (255,))
        d.line([(5, 4), (5, 11)], mul(c, 0.6) + (255,)); d.line([(11, 4), (11, 11)], mul(c, 0.6) + (255,)); d.line([(0, 6), (15, 6)], mul(c, 1.6) + (255,))
    elif s == "reed":
        base_noise(img, c, rng, 8)
        for x in range(1, 16, 3): d.line([(x, 0), (x + rng.randint(-1, 1), 15)], mul(c, 1.6) + (255,))
    elif s == "obsidian":
        base_noise(img, c, rng, 5)
        d.line([(0, 12), (12, 0)], mix(c, a, 0.5) + (255,)); d.line([(4, 15), (15, 4)], mul(c, 2.0) + (255,))
    else:
        base_noise(img, c, rng, 10)
    if glyph:
        # engraved marking: dark shadow offset + bright accent line
        mask = Image.new("L", (11, 11), 0)
        for y, row in enumerate(glyph):
            for x, ch in enumerate(row):
                if ch == "#": mask.putpixel((x, y), 255)
        mask = mask.resize((16, 16), Image.NEAREST)
        px = img.load()
        for y in range(16):
            for x in range(16):
                if mask.getpixel((x, y)) and x + 1 < 16 and y + 1 < 16 and not mask.getpixel((x + 1, y + 1)):
                    px[x + 1, y + 1] = mul(c, 0.35) + (255 if style != "glass" else 220,)
        for y in range(16):
            for x in range(16):
                if mask.getpixel((x, y)):
                    px[x, y] = mix(a, (255, 255, 255), 0.15) + (255,)
    if light >= 3 and not glyph:
        for _ in range(6): img.putpixel((rng.randint(0, 15), rng.randint(0, 15)), mix(a, (255, 255, 255), 0.4) + (img.getpixel((0, 0))[3],))
    return img
