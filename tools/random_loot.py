#!/usr/bin/env python3
"""Rewrites every chest loot table (common, archive, endgame, dim/*) to everyday vanilla items: logs, candles, torches, bread, string, paper...
Each dimension's table draws from its own subset so chests differ between places. The Golden Cloud Realm's palace treasure (dim/sky_palace) is left as is.
Keys, boss rewards and story items are NOT in loot tables (they are placed explicitly), so progression is unaffected."""
import hashlib, json, os

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src/main/resources/data/lastsignal/loot_tables/chests")
# item, min, max
POOL = [("oak_log", 4, 12), ("spruce_log", 4, 10), ("birch_log", 3, 8), ("candle", 1, 3), ("torch", 2, 8), ("bread", 1, 4), ("string", 1, 5), ("paper", 1, 6),
        ("bone", 1, 4), ("bowl", 1, 3), ("stick", 3, 12), ("feather", 1, 4), ("flint", 1, 4), ("leather", 1, 3), ("coal", 2, 8), ("apple", 1, 3),
        ("glass_bottle", 1, 3), ("book", 1, 2), ("ink_sac", 1, 3), ("white_wool", 1, 4), ("clay_ball", 2, 8), ("lantern", 1, 1), ("rotten_flesh", 1, 4),
        ("cobblestone", 6, 16), ("iron_nugget", 2, 8), ("wheat", 2, 6), ("carrot", 1, 4), ("bucket", 1, 1), ("name_tag", 1, 1), ("compass", 1, 1)]


def table(name, tier):
    seed = int(hashlib.sha256(name.encode()).hexdigest(), 16)
    n = 14 if tier == 0 else 18
    idx = sorted(range(len(POOL)), key=lambda i: (seed >> (i * 3)) % 997 + i * 0.001)[:n]
    entries = []
    for i in idx:
        it, lo, hi = POOL[i]
        w = 3 if it in ("lantern", "bucket", "name_tag", "compass") else 8
        entries.append({"type": "minecraft:item", "name": "minecraft:" + it, "weight": w,
                        "functions": [{"function": "minecraft:set_count", "count": {"min": lo, "max": hi}}]})
    pools = [{"rolls": {"min": 3, "max": 6 if tier < 2 else 8}, "entries": entries}]
    if tier == 2:                                                        # endgame: one slightly better roll
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:golden_apple", "weight": 3}, {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1},
                                              {"type": "minecraft:item", "name": "minecraft:ender_pearl", "weight": 2}]})
    return {"type": "minecraft:chest", "pools": pools}


def main():
    n = 0
    for root, _, files in os.walk(BASE):
        for f in files:
            if not f.endswith(".json") or f == "sky_palace.json":
                continue
            p = os.path.join(root, f)
            tier = 2 if f == "endgame.json" else 1 if f == "archive.json" else 0
            json.dump(table(f, tier), open(p, "w"), indent=2)
            n += 1
    print("rewrote", n, "loot tables")


if __name__ == "__main__":
    main()
