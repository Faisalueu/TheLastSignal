"""Phase 12: textures/models/loot/lang for the Black and White fracture blocks."""
import os, random
from PIL import Image
import generate_assets as GA

MOD, ASSETS, DATA = GA.MOD, GA.ASSETS, GA.DATA
BLOCKS = {"black_fracture": ("Black Fracture", True), "white_fracture": ("White Fracture", False)}


def tex(black, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = r.randint(0, 10) if black else r.randint(244, 255)
            px[x, y] = (v, v, v + (6 if black else 0), 255)
    # jagged cracks radiating from two points; high-contrast so the block reads as "broken" at a glance
    col = (150, 110, 230, 255) if black else (110, 110, 125, 255)
    glow = (235, 225, 255, 255) if black else (40, 40, 55, 255)
    for k in range(5):
        x, y = r.choice([(2, 3), (13, 12), (8, 8)])
        dx, dy = r.choice([(1, 0), (0, 1), (1, 1), (-1, 1), (1, -1), (-1, 0), (0, -1)])
        for _ in range(r.randint(7, 13)):
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = col if _ % 3 else glow
            if r.random() < .45:
                dx, dy = max(-1, min(1, dx + r.choice([-1, 0, 1]))), max(-1, min(1, dy + r.choice([-1, 0, 1])))
                if dx == 0 and dy == 0: dx = 1
            x += dx; y += dy
    # chipped corners
    for (cx, cy) in [(0, 0), (15, 0), (0, 15), (15, 15), (1, 0), (0, 1)]:
        if r.random() < .7: px[cx, cy] = (0, 0, 0, 255) if not black else (200, 190, 255, 255)
    return img


def gen():
    lang = {}
    for i, (bid, (name, black)) in enumerate(BLOCKS.items()):
        p = os.path.join(ASSETS, "textures/block", bid + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        tex(black, 1201 + i).save(p)
        GA.wjson(os.path.join(ASSETS, "models/block", bid + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{bid}"}})
        GA.wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
        GA.wjson(os.path.join(ASSETS, "models/item", bid + ".json"), {"parent": f"{MOD}:block/{bid}"})
        # never drops as an item (survival cannot farm transit blocks); empty loot table
        GA.wjson(os.path.join(DATA, "loot_tables/blocks", bid + ".json"), {"type": "minecraft:block", "pools": []})
        lang[f"block.{MOD}.{bid}"] = name
    return lang
