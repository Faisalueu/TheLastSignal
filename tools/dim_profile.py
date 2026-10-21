"""Rework data for the 48 dimensions: spec overrides, generation profiles (-> ProfileData.java) and dimension loot tables."""
import json, os, zlib, random
import dim_spec

CELL, HALL, PIT, SHAFT, CRAWL, ORGANIC, COLUMNS = range(7)


def W(cell=0, hall=0, pit=0, shaft=0, crawl=0, organic=0, columns=0):
    return [cell, hall, pit, shaft, crawl, organic, columns]


# ---- spec overrides: kind / time / generation params / feature lists (names must exist in Features.java)
OVR = {
    1: dict(kind="open", time=14500, T=dict(base=66, amp=4, scale=70, feat=.5, mark=.2), feats=["pylon", "platform", "house", "rubble", "towerruin", "chains"]),
    3: dict(T=dict(H=9)),
    4: dict(T=dict(H=8)),
    5: dict(T=dict(H=5)),
    6: dict(T=dict(H=6)),
    19: dict(T=dict(H=6)),
    20: dict(T=dict(H=5)),
    23: dict(kind="void", time=18000, T=dict(base=70), feats=["racks", "conduits", "desks", "pylon"]),
    24: dict(kind="deep", feats=["tracks", "ruinpillars", "rubble", "stalagmites", "machines"], T=dict(feat=.7)),
    25: dict(T=dict(H=5)),
    26: dict(kind="open", time=6000, T=dict(base=66, amp=5, scale=60, feat=.5), feats=["towerruin", "ruinpillars", "machines", "stairs_up", "arches"]),
    27: dict(T=dict(H=6)),
    28: dict(T=dict(H=9)),
    29: dict(T=dict(H=8)),
    34: dict(kind="open", time=18000, T=dict(base=58, amp=12, scale=55, sea=64, feat=.5), feats=["coralspire", "wreck", "towerruin", "arch", "coralspire"]),
    37: dict(kind="open", time=13500, T=dict(base=64, amp=5, scale=60, feat=.5), feats=["altar", "ruinpillars", "towerruin", "tombs", "fence"]),
    38: dict(T=dict(H=4)),
    39: dict(kind="deep", feats=["house", "ruinpillars", "towerruin", "throne", "arches", "rubble"], T=dict(feat=.7)),
    40: dict(kind="open", time=16000, T=dict(base=64, amp=6, scale=60, feat=.5), feats=["altar", "ringstones", "ruinpillars", "rubble", "monolith"]),
    41: dict(kind="open", time=10000, T=dict(base=70, amp=14, scale=60, feat=.5), feats=["cloister", "ruinpillars", "house", "well", "fence"]),
    46: dict(T=dict(H=6)),
    47: dict(kind="deep", feats=["machines", "conduits", "pylon", "pipes", "ruinpillars"], T=dict(feat=.7)),
}


def apply_overrides():
    for d in dim_spec.DIMS48:
        o = OVR.get(d["n"])
        if not o:
            continue
        for k in ("kind", "time", "feats"):
            if k in o:
                d[k] = o[k]
        d["T"].update(o.get("T", {}))


# ---- profiles: sc, arch weights, hallStyle, hallH, landmarks, lmDens, center, cell, scat, chest, loot, fogFar, amb
P = {}


def prof(n, sc=1, arch=None, style="none", hallH=10, lm=(), dens=0.0, center="", cell=24, scat=.5, chest=1.0, fog=224, amb=1.0):
    P[n] = dict(sc=sc, arch=arch or W(cell=1), style=style, hallH=hallH, lm=list(lm), dens=dens, center=center, cell=cell, scat=scat, chest=chest, fog=fog, amb=amb)


prof(1, lm=["mast", "tower", "mast"], dens=.6, center="tower", cell=26, scat=.5, fog=260, amb=1.0)
prof(2, lm=["village"], dens=.3, center="village", cell=20, scat=.4, fog=200)
prof(3, sc=4, arch=W(cell=.25, hall=.5, pit=.1, shaft=.08, columns=.07), style="pews", hallH=26, chest=.9, fog=210, amb=.9)
prof(4, sc=3, arch=W(cell=.3, hall=.4, pit=.05, shaft=.15, columns=.1), style="shelves", hallH=16, fog=190)
prof(5, sc=3, arch=W(cell=.55, hall=.2, crawl=.15, columns=.1), style="beds", hallH=7, chest=1.1, fog=160)
prof(6, sc=4, arch=W(cell=.2, hall=.45, pit=.1, shaft=.05, crawl=.2), style="tracks", hallH=12, fog=200)
prof(7, lm=["monolith", "slab", "arch"], dens=.5, center="arch", chest=.8, fog=420, amb=.8)
prof(8, lm=["monolith", "spires"], dens=.35, cell=22, scat=.5, fog=230)
prof(9, lm=["arch", "tower", "zig"], dens=.55, fog=300)
prof(10, lm=["monolith", "monolith", "ring"], dens=.85, center="monolith", cell=30, scat=.3, fog=340)
prof(11, lm=["dome", "ring", "spires"], dens=.35, cell=34, scat=.3, fog=380, amb=.8)
prof(12, lm=["slab", "arch", "monolith"], dens=.45, fog=320)
prof(13, sc=2, arch=W(cell=.2, hall=.1, crawl=.1, organic=.55, columns=.05), style="none", hallH=9, fog=130)
prof(14, lm=["greenhouse", "ring"], dens=.5, cell=18, scat=.9, fog=210)
prof(15, lm=["trunk", "trunk", "spires"], dens=.8, center="trunk", cell=24, scat=.4, fog=210)
prof(16, arch=W(cell=.15, pit=.1, shaft=.05, crawl=.25, organic=.45), fog=130, chest=1.1)
prof(17, lm=["village", "tower"], dens=.3, cell=18, scat=.6, fog=220)
prof(18, lm=["monolith", "ring"], dens=.3, cell=11, scat=1.1, fog=210)
prof(19, sc=4, arch=W(cell=.2, hall=.5, pit=.1, shaft=.1, columns=.1), style="machines", hallH=20, fog=210)
prof(20, sc=3, arch=W(cell=.4, hall=.25, pit=.1, shaft=.1, crawl=.05, columns=.1), style="cases", hallH=10, fog=180)
prof(21, lm=["mast", "mast", "tower"], dens=.9, cell=16, scat=.6, fog=300)
prof(22, sc=3, arch=W(cell=.4, hall=.25, pit=.1, shaft=.1, crawl=.1, columns=.05), style="colonnade", hallH=9, chest=1.2, fog=170)
prof(23, lm=["slab", "geo"], dens=.5, fog=300)
prof(24, cell=20, scat=.8, fog=170, chest=1.2)
prof(25, sc=4, arch=W(cell=.15, hall=.5, columns=.35), style="colonnade", hallH=12, fog=270, amb=.8)
prof(26, lm=["clock", "tower"], dens=.5, center="clock", cell=26, scat=.45, fog=300)
prof(27, sc=4, arch=W(cell=.2, hall=.55, columns=.25), style="mirrors", hallH=12, fog=240)
prof(28, sc=3, arch=W(cell=.2, hall=.4, pit=.1, shaft=.1, crawl=.2), style="toys", hallH=16, fog=210)
prof(29, sc=4, arch=W(cell=.35, hall=.4, pit=.05, crawl=.2), style="seats", hallH=16, fog=210)
prof(30, fog=200, chest=.9)
prof(31, lm=["arch", "zig", "spires"], dens=.35, cell=40, scat=.3, fog=360, amb=.9)
prof(32, lm=["ring", "chapel"], dens=.3, cell=12, scat=.9, fog=230)
prof(33, lm=["spires", "monolith", "tower"], dens=.4, cell=28, scat=.4, fog=300)
prof(34, lm=["lighthouse", "tower"], dens=.35, center="lighthouse", cell=24, scat=.4, fog=290)
prof(35, lm=["tower", "monolith", "arch"], dens=.5, fog=290)
prof(36, lm=["ring"], dens=.2, cell=9, scat=1.3, fog=200)
prof(37, lm=["chapel"], dens=.3, center="chapel", cell=22, scat=.35, fog=230)
prof(38, sc=3, arch=W(cell=.3, hall=.15, pit=.1, shaft=.05, crawl=.3, columns=.1), style="colonnade", hallH=8, chest=1.3, fog=150)
prof(39, lm=["village", "tower", "zig"], dens=.55, center="village", cell=22, scat=.3, fog=250)
prof(40, lm=["ring", "ring", "monolith"], dens=.8, center="ring", cell=24, scat=.35, fog=270)
prof(41, lm=["monastery", "tower"], dens=.3, center="monastery", cell=22, scat=.35, fog=250)
prof(42, lm=["zig", "ring"], dens=.25, cell=8, scat=1.2, fog=250)
prof(43, lm=["slab", "monolith", "geo"], dens=.6, fog=260)
prof(44, lm=["geo", "geo", "slab"], dens=.5, chest=.6, fog=480, amb=.5)
prof(45, lm=["dome", "tower"], dens=.35, center="dome", cell=30, scat=.3, fog=350)
prof(46, sc=3, arch=W(cell=.25, hall=.3, pit=.05, shaft=.15, crawl=.15, columns=.1), style="monoliths", hallH=14, fog=200)
prof(47, lm=["mast", "tower"], dens=.4, center="core", cell=22, scat=.45, fog=250)
prof(48, lm=["slab", "geo", "arch", "monolith"], dens=.7, center="slab", chest=.8, fog=400, amb=.6)

# ---------------------------------------------------------------- loot
BASIC = [("minecraft:iron_ingot", 6, 1, 4), ("minecraft:cobblestone", 8, 3, 9), ("minecraft:stone", 6, 4, 9), ("minecraft:oak_planks", 8, 4, 10),
         ("minecraft:stick", 6, 2, 8), ("minecraft:torch", 7, 2, 8), ("minecraft:bread", 5, 1, 3), ("minecraft:coal", 6, 2, 6),
         ("minecraft:string", 4, 1, 4), ("minecraft:paper", 4, 1, 4)]
# tag -> {tier: [(item, weight, lo, hi)]}   hi==0 -> not stackable (no count function)
TAGS = {
 "tech": dict(c=[("redstone", 8, 2, 6), ("copper_ingot", 6, 1, 4), ("iron_nugget", 6, 2, 7)], u=[("repeater", 5, 1, 2), ("comparator", 4, 1, 2), ("quartz", 5, 1, 4), ("lightning_rod", 3, 1, 1)],
              r=[("observer", 4, 1, 2), ("ender_pearl", 3, 1, 2), ("amethyst_shard", 4, 2, 5)], v=[("diamond", 3, 1, 2), ("redstone_block", 3, 1, 2)]),
 "industrial": dict(c=[("iron_ingot", 8, 1, 5), ("chain", 5, 1, 4), ("rail", 5, 2, 6)], u=[("hopper", 4, 1, 1), ("piston", 4, 1, 2), ("lever", 4, 1, 2), ("copper_ingot", 5, 2, 5)],
                    r=[("anvil", 2, 1, 1), ("iron_pickaxe", 3, 1, 0), ("bucket", 4, 1, 0)], v=[("diamond", 3, 1, 2)]),
 "household": dict(c=[("bread", 7, 1, 4), ("candle", 5, 1, 3), ("stick", 6, 2, 6)], u=[("bucket", 4, 1, 0), ("lantern", 4, 1, 1), ("apple", 5, 1, 3), ("white_bed", 2, 1, 0)], r=[("golden_apple", 2, 1, 1), ("clock", 3, 1, 0)], v=[("emerald", 3, 1, 2)]),
 "farm": dict(c=[("wheat", 7, 2, 6), ("potato", 6, 1, 4), ("carrot", 6, 1, 4)], u=[("wheat_seeds", 6, 3, 8), ("pumpkin_seeds", 4, 2, 5), ("hay_block", 3, 1, 2)], r=[("golden_carrot", 3, 1, 2)], v=[("emerald", 3, 1, 2)]),
 "church": dict(c=[("candle", 8, 1, 4), ("paper", 5, 1, 4), ("bone", 5, 1, 3)], u=[("book", 5, 1, 2), ("gold_nugget", 6, 2, 6), ("amethyst_shard", 4, 1, 3)], r=[("bell", 3, 1, 1), ("enchanted_book", 3, 1, 0)], v=[("gold_ingot", 4, 2, 5), ("emerald", 3, 1, 2)]),
 "ritual": dict(c=[("candle", 7, 1, 3), ("bone", 6, 1, 4), ("redstone", 6, 1, 4)], u=[("amethyst_shard", 5, 1, 4), ("glow_ink_sac", 3, 1, 2), ("gold_nugget", 5, 2, 5)], r=[("enchanted_book", 3, 1, 0), ("ender_pearl", 3, 1, 1)], v=[("ender_eye", 3, 1, 1), ("totem_of_undying", 1, 1, 0)]),
 "library": dict(c=[("paper", 9, 2, 6), ("book", 6, 1, 3), ("feather", 5, 1, 3)], u=[("ink_sac", 5, 1, 3), ("writable_book", 3, 1, 0), ("name_tag", 3, 1, 0), ("bookshelf", 2, 1, 2)], r=[("enchanted_book", 5, 1, 0), ("spyglass", 2, 1, 0)], v=[("enchanted_book", 3, 1, 0), ("diamond", 2, 1, 1)]),
 "medical": dict(c=[("glass_bottle", 7, 1, 3), ("white_wool", 5, 1, 4), ("sugar", 5, 1, 4), ("paper", 5, 1, 3)], u=[("honey_bottle", 4, 1, 2), ("spider_eye", 4, 1, 3), ("glistering_melon_slice", 3, 1, 2)], r=[("golden_apple", 3, 1, 1), ("milk_bucket", 2, 1, 0)], v=[("enchanted_golden_apple", 1, 1, 1), ("diamond", 2, 1, 1)]),
 "travel": dict(c=[("rail", 8, 3, 9), ("leather", 5, 1, 3), ("bread", 5, 1, 3)], u=[("compass", 4, 1, 0), ("clock", 4, 1, 0), ("powered_rail", 3, 1, 3), ("lantern", 3, 1, 1)], r=[("minecart", 3, 1, 0), ("saddle", 2, 1, 0)], v=[("diamond", 2, 1, 1)]),
 "void": dict(c=[("obsidian", 5, 1, 3), ("end_stone", 5, 2, 6), ("paper", 3, 1, 2)], u=[("end_rod", 4, 1, 3), ("chorus_fruit", 4, 1, 3), ("popped_chorus_fruit", 3, 1, 3)], r=[("ender_pearl", 4, 1, 2), ("ender_eye", 2, 1, 1)], v=[("shulker_shell", 2, 1, 1), ("elytra", 1, 1, 0)]),
 "cave": dict(c=[("coal", 8, 2, 7), ("raw_iron", 6, 1, 4), ("torch", 6, 2, 8)], u=[("glow_berries", 5, 1, 4), ("lantern", 3, 1, 1), ("raw_copper", 5, 2, 5), ("amethyst_shard", 3, 1, 3)], r=[("raw_gold", 4, 1, 3), ("lapis_lazuli", 4, 2, 6)], v=[("diamond", 3, 1, 2)]),
 "water": dict(c=[("kelp", 7, 2, 6), ("cod", 5, 1, 3), ("string", 5, 1, 4), ("clay_ball", 5, 2, 6)], u=[("prismarine_shard", 4, 1, 4), ("ink_sac", 4, 1, 3), ("lily_pad", 4, 1, 3), ("salmon", 4, 1, 3)], r=[("nautilus_shell", 3, 1, 1), ("sponge", 2, 1, 2)], v=[("heart_of_the_sea", 2, 1, 1), ("trident", 1, 1, 0)]),
 "bio": dict(c=[("slime_ball", 6, 1, 4), ("spider_eye", 5, 1, 3), ("bone_meal", 6, 2, 6), ("string", 5, 1, 4)], u=[("fermented_spider_eye", 3, 1, 2), ("brown_mushroom", 5, 1, 4), ("honeycomb", 3, 1, 3), ("cobweb", 3, 1, 3)], r=[("sculk", 3, 1, 3), ("turtle_egg", 1, 1, 1)], v=[("sculk_catalyst", 2, 1, 1)]),
 "garden": dict(c=[("bone_meal", 7, 2, 6), ("wheat_seeds", 6, 3, 8), ("brown_mushroom", 5, 1, 4)], u=[("honeycomb", 4, 1, 3), ("apple", 4, 1, 3), ("red_mushroom", 4, 1, 3), ("fern", 4, 1, 3)], r=[("moss_block", 3, 1, 3), ("glow_berries", 3, 2, 5)], v=[("golden_apple", 2, 1, 1)]),
 "forest": dict(c=[("stick", 8, 2, 8), ("spruce_planks", 6, 3, 9), ("bone", 4, 1, 3)], u=[("apple", 5, 1, 3), ("brown_mushroom", 4, 1, 4), ("string", 4, 1, 3), ("dark_oak_sapling", 3, 1, 2)], r=[("saddle", 2, 1, 0), ("bow", 3, 1, 0)], v=[("emerald", 3, 1, 2)]),
 "stone": dict(c=[("flint", 6, 1, 4), ("coal", 6, 2, 5), ("cobblestone", 8, 3, 9)], u=[("gravel", 4, 2, 6), ("clay_ball", 4, 2, 5), ("iron_nugget", 5, 2, 6)], r=[("iron_ingot", 5, 2, 5), ("flint_and_steel", 2, 1, 0)], v=[("diamond", 2, 1, 1)]),
 "military": dict(c=[("iron_ingot", 7, 1, 5), ("gunpowder", 6, 1, 4), ("arrow", 6, 2, 8), ("bread", 5, 1, 3)], u=[("crossbow", 3, 1, 0), ("shield", 2, 1, 0), ("iron_nugget", 5, 3, 8), ("cooked_beef", 4, 1, 3)], r=[("golden_apple", 3, 1, 1), ("iron_chestplate", 2, 1, 0), ("tnt", 3, 1, 2)], v=[("diamond", 3, 1, 2), ("netherite_scrap", 1, 1, 1)]),
 "mine": dict(c=[("coal", 8, 3, 8), ("raw_iron", 6, 1, 5), ("torch", 7, 3, 9), ("rail", 5, 3, 8)], u=[("raw_copper", 5, 2, 6), ("lantern", 4, 1, 1), ("iron_pickaxe", 3, 1, 0), ("bucket", 3, 1, 0)], r=[("raw_gold", 4, 1, 4), ("lapis_lazuli", 4, 2, 7), ("tnt", 3, 1, 3)], v=[("diamond", 3, 1, 3), ("emerald", 3, 1, 2)]),
 "liminal": dict(c=[("paper", 7, 1, 4), ("honey_bottle", 4, 1, 2), ("yellow_dye", 5, 1, 4), ("candle", 5, 1, 3)], u=[("glowstone_dust", 4, 1, 4), ("sponge", 3, 1, 2), ("clock", 2, 1, 0)], r=[("music_disc_11", 1, 1, 0), ("spyglass", 2, 1, 0)], v=[("sea_lantern", 2, 1, 2)]),
 "time": dict(c=[("gold_nugget", 7, 2, 6), ("redstone", 6, 1, 4), ("iron_nugget", 5, 2, 6)], u=[("clock", 4, 1, 0), ("compass", 3, 1, 0), ("gold_ingot", 4, 1, 3), ("sand", 4, 2, 6)], r=[("repeater", 4, 1, 2), ("comparator", 3, 1, 2)], v=[("diamond", 2, 1, 1), ("gold_block", 2, 1, 1)]),
 "glass": dict(c=[("glass_pane", 8, 2, 7), ("quartz", 5, 1, 4), ("paper", 4, 1, 3)], u=[("amethyst_shard", 5, 1, 4), ("glass_bottle", 4, 1, 3), ("spyglass", 2, 1, 0)], r=[("quartz_block", 3, 1, 3), ("tinted_glass", 3, 1, 3)], v=[("ender_eye", 2, 1, 1)]),
 "toys": dict(c=[("cookie", 8, 2, 7), ("string", 5, 1, 4), ("clay_ball", 5, 2, 6), ("white_wool", 5, 1, 4)], u=[("painting", 3, 1, 1), ("note_block", 3, 1, 1), ("honey_bottle", 3, 1, 2), ("item_frame", 3, 1, 2)], r=[("music_disc_13", 1, 1, 0), ("cake", 3, 1, 1)], v=[("totem_of_undying", 1, 1, 0)]),
 "theatre": dict(c=[("leather", 6, 1, 4), ("feather", 5, 1, 3), ("paper", 5, 1, 4), ("candle", 5, 1, 3)], u=[("painting", 4, 1, 1), ("note_block", 3, 1, 1), ("carved_pumpkin", 3, 1, 1), ("bell", 2, 1, 1)], r=[("music_disc_11", 2, 1, 0), ("enchanted_book", 2, 1, 0)], v=[("music_disc_13", 1, 1, 0), ("totem_of_undying", 1, 1, 0)]),
 "desert": dict(c=[("sand", 7, 3, 9), ("bone", 5, 1, 4), ("dead_bush", 4, 1, 3), ("brick", 5, 1, 5)], u=[("gold_nugget", 6, 2, 6), ("cactus", 3, 1, 3), ("rotten_flesh", 4, 1, 3), ("clay_ball", 4, 1, 4)], r=[("gold_ingot", 4, 1, 3), ("emerald", 3, 1, 1), ("suspicious_sand", 1, 1, 1)], v=[("diamond", 2, 1, 1), ("golden_apple", 2, 1, 1)]),
 "cold": dict(c=[("snowball", 8, 2, 8), ("coal", 6, 2, 5), ("leather", 4, 1, 3)], u=[("blue_ice", 3, 1, 2), ("packed_ice", 4, 1, 3), ("bone", 5, 1, 4), ("iron_nugget", 4, 2, 6)], r=[("gold_ingot", 3, 1, 2), ("iron_ingot", 5, 1, 3)], v=[("diamond", 2, 1, 1), ("goat_horn", 1, 1, 0)]),
 "grave": dict(c=[("bone", 8, 2, 5), ("rotten_flesh", 6, 1, 4), ("candle", 4, 1, 3), ("coal", 4, 1, 3)], u=[("gold_nugget", 6, 2, 6), ("arrow", 5, 2, 6), ("iron_nugget", 5, 2, 6)], r=[("gold_ingot", 3, 1, 2), ("skeleton_skull", 1, 1, 1), ("enchanted_book", 2, 1, 0)], v=[("emerald", 3, 1, 2), ("diamond", 2, 1, 1)]),
 "ash": dict(c=[("charcoal", 8, 2, 6), ("coal", 6, 2, 6), ("flint", 5, 1, 4), ("blackstone", 5, 3, 8)], u=[("iron_nugget", 5, 2, 6), ("gold_nugget", 4, 1, 5), ("basalt", 4, 2, 6)], r=[("magma_cream", 3, 1, 2), ("netherite_scrap", 1, 1, 1)], v=[("obsidian", 3, 2, 4), ("diamond", 2, 1, 1)]),
 "lightning": dict(c=[("copper_ingot", 7, 1, 5), ("iron_ingot", 6, 1, 4), ("redstone", 6, 1, 4)], u=[("lightning_rod", 4, 1, 2), ("fire_charge", 3, 1, 2), ("gunpowder", 5, 1, 4)], r=[("copper_block", 3, 1, 2), ("trident", 1, 1, 0)], v=[("diamond", 2, 1, 1)]),
 "royal": dict(c=[("gold_nugget", 7, 2, 7), ("brick", 5, 2, 6), ("candle", 4, 1, 3)], u=[("gold_ingot", 5, 1, 3), ("emerald", 3, 1, 2), ("bell", 2, 1, 1)], r=[("golden_apple", 3, 1, 1), ("diamond", 3, 1, 1), ("white_banner", 2, 1, 0)], v=[("enchanted_golden_apple", 1, 1, 1), ("diamond", 3, 1, 3)]),
 "monk": dict(c=[("bread", 7, 1, 4), ("wheat", 5, 1, 4), ("candle", 5, 1, 4), ("paper", 4, 1, 3)], u=[("honey_bottle", 4, 1, 2), ("book", 4, 1, 2), ("brown_mushroom", 3, 1, 3), ("honeycomb", 3, 1, 2)], r=[("bell", 3, 1, 1), ("enchanted_book", 2, 1, 0)], v=[("golden_apple", 2, 1, 1)]),
 "glitch": dict(c=[("redstone", 6, 1, 5), ("amethyst_shard", 5, 1, 4), ("poisonous_potato", 4, 1, 3), ("flint", 4, 1, 3)], u=[("note_block", 3, 1, 2), ("ender_pearl", 3, 1, 2), ("observer", 3, 1, 2)], r=[("end_crystal", 1, 1, 1), ("repeater", 3, 1, 3)], v=[("sculk_shrieker", 1, 1, 1), ("diamond", 2, 1, 2)]),
 "pale": dict(c=[("quartz", 6, 1, 4), ("paper", 6, 1, 4), ("white_wool", 4, 1, 3)], u=[("end_rod", 4, 1, 2), ("snowball", 5, 2, 6), ("amethyst_shard", 4, 1, 3)], r=[("ender_pearl", 3, 1, 1)], v=[("shulker_shell", 1, 1, 1)]),
 "astro": dict(c=[("glass_pane", 6, 2, 6), ("paper", 5, 1, 4), ("iron_nugget", 5, 2, 6)], u=[("spyglass", 3, 1, 0), ("compass", 4, 1, 0), ("clock", 4, 1, 0), ("amethyst_shard", 4, 1, 4)], r=[("quartz_block", 3, 1, 2), ("book", 4, 1, 2), ("enchanted_book", 2, 1, 0)], v=[("ender_eye", 3, 1, 1), ("diamond", 2, 1, 1)]),
 "bureau": dict(c=[("paper", 9, 2, 7), ("ink_sac", 5, 1, 3), ("feather", 5, 1, 3)], u=[("book", 5, 1, 3), ("name_tag", 3, 1, 0), ("writable_book", 3, 1, 0), ("chiseled_bookshelf", 2, 1, 1)], r=[("enchanted_book", 4, 1, 0)], v=[("diamond", 2, 1, 1), ("enchanted_book", 3, 1, 0)]),
}
# dim -> (tags, specials[mod items], very-rare mod items)
LOOT = {
 1: (["tech", "industrial"], ["signal_fragment", "frequency_tuner", "signal_fragment"], ["signal_detector"]),
 2: (["household", "farm"], ["field_journal_page", "emergency_flare", "ancient_coin"], ["ancient_coin"]),
 3: (["church", "ritual"], ["silent_bell", "sealed_envelope", "ancient_coin"], ["protective_charm"]),
 4: (["library"], ["archivist_stamp", "memory_shard", "field_journal_page"], ["research_notebook"]),
 5: (["medical", "household"], ["research_notebook", "strange_photograph", "frequency_tuner"], ["protective_charm"]),
 6: (["travel", "household"], ["rusted_keycard", "memory_shard", "ancient_coin"], ["distorted_compass"]),
 7: (["void", "glass"], ["void_lens", "memory_shard", "echo_whistle"], ["buried_crown_shard"]),
 8: (["cave", "water"], ["void_lens", "echo_whistle", "protective_charm"], ["signal_detector"]),
 9: (["time", "tech"], ["distorted_compass", "memory_shard", "ancient_coin"], ["signal_blade"]),
 10: (["ritual", "stone"], ["red_key", "signal_fragment", "signal_fragment"], ["unfinished_effigy"]),
 11: (["pale", "glass"], ["void_lens", "signal_fragment", "memory_shard"], ["signal_detector"]),
 12: (["glass", "glitch"], ["memory_shard", "void_lens", "signal_fragment"], ["buried_crown_shard"]),
 13: (["bio", "medical"], ["protective_charm", "signal_fragment", "strange_photograph"], ["protective_charm"]),
 14: (["garden", "bio"], ["emergency_flare", "field_journal_page", "protective_charm"], ["ancient_coin"]),
 15: (["bio", "cave"], ["signal_fragment", "ancient_coin", "echo_whistle"], ["void_lens"]),
 16: (["bio", "stone"], ["protective_charm", "research_notebook", "frequency_tuner"], ["protective_charm"]),
 17: (["water", "household"], ["emergency_flare", "protective_charm", "memory_shard"], ["ancient_coin"]),
 18: (["forest", "stone"], ["ancient_coin", "ancient_coin", "field_journal_page"], ["buried_crown_shard"]),
 19: (["industrial", "tech"], ["power_cell", "power_cell", "rusted_keycard"], ["signal_blade"]),
 20: (["tech", "medical"], ["research_notebook", "memory_shard", "frequency_tuner"], ["research_notebook"]),
 21: (["tech", "lightning"], ["power_cell", "power_cell", "signal_fragment"], ["signal_detector"]),
 22: (["military", "household"], ["rusted_keycard", "emergency_flare", "research_notebook"], ["protective_charm"]),
 23: (["tech", "glitch"], ["memory_shard", "signal_fragment", "frequency_tuner"], ["broken_radio"]),
 24: (["mine", "stone"], ["emergency_flare", "ancient_coin", "rusted_keycard"], ["signal_blade"]),
 25: (["liminal", "household"], ["frequency_tuner", "strange_photograph", "signal_fragment"], ["memory_shard"]),
 26: (["time", "industrial"], ["distorted_compass", "silent_bell", "memory_shard"], ["echo_whistle"]),
 27: (["glass", "pale"], ["void_lens", "strange_photograph", "memory_shard"], ["distorted_compass"]),
 28: (["toys", "household"], ["memory_shard", "strange_photograph", "ancient_coin"], ["silent_bell"]),
 29: (["theatre", "household"], ["memory_shard", "frequency_tuner", "silent_bell"], ["archivist_stamp"]),
 30: (["stone", "industrial"], ["ancient_coin", "echo_whistle", "distorted_compass"], ["signal_detector"]),
 31: (["desert", "stone"], ["ancient_coin", "protective_charm", "ancient_coin"], ["buried_crown_shard"]),
 32: (["cold", "grave"], ["ancient_coin", "silent_bell", "protective_charm"], ["protective_charm"]),
 33: (["ash", "stone"], ["emergency_flare", "signal_fragment", "emergency_flare"], ["signal_blade"]),
 34: (["water", "ash"], ["protective_charm", "echo_whistle", "void_lens"], ["heart_marker_unused"]),
 35: (["lightning", "tech"], ["power_cell", "emergency_flare", "signal_fragment"], ["signal_detector"]),
 36: (["forest", "grave"], ["field_journal_page", "ancient_coin", "emergency_flare"], ["silent_bell"]),
 37: (["ritual", "church"], ["red_key", "silent_bell", "sealed_envelope"], ["unfinished_effigy"]),
 38: (["grave", "stone"], ["ancient_coin", "silent_bell", "archivist_stamp"], ["buried_crown_shard"]),
 39: (["royal", "stone"], ["ancient_coin", "archive_key", "memory_shard"], ["buried_crown_shard"]),
 40: (["ritual", "stone"], ["protective_charm", "red_key", "signal_fragment"], ["unfinished_effigy"]),
 41: (["monk", "church"], ["silent_bell", "field_journal_page", "sealed_envelope"], ["protective_charm"]),
 42: (["grave", "garden"], ["ancient_coin", "protective_charm", "ancient_coin"], ["silent_bell"]),
 43: (["glitch", "tech"], ["signal_fragment", "memory_shard", "frequency_tuner"], ["void_lens"]),
 44: (["pale", "void"], ["memory_shard", "void_lens", "echo_whistle"], ["buried_crown_shard"]),
 45: (["astro", "glass"], ["void_lens", "distorted_compass", "frequency_tuner"], ["signal_detector"]),
 46: (["bureau", "library"], ["archivist_stamp", "archive_key", "sealed_envelope"], ["research_notebook"]),
 47: (["tech", "industrial"], ["signal_fragment", "power_cell", "frequency_tuner"], ["signal_blade"]),
 48: (["void", "pale"], ["memory_shard", "void_lens", "echo_whistle"], ["unfinished_effigy", "buried_crown_shard"]),
}
STACK1_MOD = {"broken_radio", "signal_detector", "distorted_compass", "research_notebook", "signal_blade", "red_key", "archive_key", "long_hall_key", "protective_charm", "frequency_tuner"}
VALID_MOD = set("broken_radio signal_detector archive_key research_notebook frequency_tuner protective_charm emergency_flare strange_photograph sealed_envelope ancient_coin signal_fragment memory_shard rusted_keycard distorted_compass long_hall_key red_key power_cell void_lens archivist_stamp echo_whistle signal_blade field_journal_page silent_bell buried_crown_shard unfinished_effigy".split())


def entry(item, w, lo=1, hi=0):
    ent = {"type": "minecraft:item", "name": item if ":" in item else "minecraft:" + item, "weight": w}
    if hi and hi >= lo:
        ent["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if item == "enchanted_book":
        ent.setdefault("functions", []).append({"function": "minecraft:enchant_randomly"})
    return ent


def mod_entry(item, w):
    ent = {"type": "minecraft:item", "name": "lastsignal:" + item, "weight": w}
    if item not in STACK1_MOD and item not in ("heart_marker_unused",):
        ent["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]
    return ent


def empty(w):
    return {"type": "minecraft:empty", "weight": w}


def loot_table(n):
    tags, specials, vrare = LOOT[n]
    rnd = random.Random(zlib.crc32(("loot%d" % n).encode()))
    basics = rnd.sample(BASIC, 5)
    common = [entry(i, w, lo, hi) for i, w, lo, hi in basics]
    unc, rare, very = [], [], []
    for t in tags:
        T = TAGS[t]
        common += [entry(*e) for e in T["c"]]
        unc += [entry(*e) for e in T["u"]]
        rare += [entry(*e) for e in T["r"]]
        very += [entry(*e) for e in T["v"]]
    special = [mod_entry(s, 10 - i) for i, s in enumerate(specials) if s in VALID_MOD]
    vmod = [mod_entry(s, 4) for s in vrare if s in VALID_MOD]
    pools = [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": common},
        {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2}, "entries": unc + [empty(14)]},
        {"rolls": 1, "entries": rare + [empty(60)]},
        {"rolls": 1, "entries": special + [empty(26)]},
        {"rolls": 1, "entries": very + vmod + [empty(220)]},
    ]
    return {"type": "minecraft:chest", "pools": pools}


def write_loot(data_dir):
    d = os.path.join(data_dir, "loot_tables/chests/dim")
    os.makedirs(d, exist_ok=True)
    for dd in dim_spec.DIMS48:
        with open(os.path.join(d, dd["slug"] + ".json"), "w") as f:
            json.dump(loot_table(dd["n"]), f, indent=1)


def jstr(s): return json.dumps(s)


def gen_java(path):
    L = ["package com.lastsignal.world.gen;", "", "// GENERATED by tools/dim_profile.py - do not edit by hand.", "public final class ProfileData {", "    public static final Profile[] ALL = {"]
    for d in dim_spec.DIMS48:
        n = d["n"]; p = P[n]
        L.append(f'        new Profile({p["sc"]}, new double[]{{{", ".join(repr(float(x)) for x in p["arch"])}}}, {jstr(p["style"])}, {p["hallH"]}, new String[]{{{", ".join(jstr(x) for x in p["lm"])}}}, {float(p["dens"])}, {jstr(p["center"])}, {p["cell"]}, {float(p["scat"])}, {float(p["chest"])}, {jstr(d["slug"])}, {p["fog"]}, {float(p["amb"])}f),')
    L += ["    };", "    private ProfileData() {}", "}"]
    open(path, "w").write("\n".join(L) + "\n")


if __name__ == "__main__":
    apply_overrides()
    print(len(P), len(LOOT))
