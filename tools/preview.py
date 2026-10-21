#!/usr/bin/env python3
"""Rough orthographic silhouette sheet (front + side) of the generated rigs - a sanity check only, not a render."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_assets as GA, monsters as M
from PIL import Image, ImageDraw
ids = sys.argv[2:] or [e["id"] for e in GA.ENT]
out = sys.argv[1]
S = 5; W = 150; H = 230
sheet = Image.new("RGB", (W * 2 * 4, H * ((len(ids) + 3) // 4)), (24, 24, 28))
d = ImageDraw.Draw(sheet)
for n, i in enumerate(ids):
    E = next(e for e in GA.ENT if e["id"] == i)
    R, X, fn = M.build_rig(E)
    gx = (n % 4) * W * 2; gy = (n // 4) * H
    for view in (0, 1):
        ox = gx + view * W + W // 2; oy = gy + H - 18
        for b, cs in R.cubes.items():
            for c in cs:
                o, s = c["origin"], c["size"]
                a0, a1 = (o[0], o[0] + s[0]) if view == 0 else (o[2], o[2] + s[2])
                col = (200, 200, 205) if b.startswith(("head", "body", "chest")) else (150, 150, 160)
                if b.startswith(("sway", "spin")): col = (230, 190, 90)
                d.rectangle([ox + a0 * S / 1.0 * 0.8, oy - (o[1] + s[1]) * S * 0.8, ox + a1 * S * 0.8, oy - o[1] * S * 0.8], fill=col, outline=(40, 40, 45))
    d.text((gx + 4, gy + 4), i, fill=(255, 255, 255))
sheet.save(out)
