"""Assets for the two lake-entry dimensions (indices 49/50): textures, models, lang, worldgen JSON and procedurally synthesised ambience.
Deliberately separate from dim_gen.py so the 48 existing dimensions' files are not regenerated. Run: python3 tools/extra_dims.py"""
import json, os
import generate_assets as GA
import dim_tex, dim_snd

MOD, ASSETS, DATA = GA.MOD, GA.ASSETS, GA.DATA

# id, display name, style, colour, light
BLOCKS = [
    ("d49_cloud_bank", "Cloud Bank", "fabric", 0xF1EFE8, 0),
    ("d49_skystone", "Skystone", "stone", 0xA9B8CC, 0),
    ("d49_gilded_marble", "Gilded Marble", "tile", 0xE6D79A, 0),
    ("d49_aurelian_lamp", "Aurelian Lamp", "crystal", 0xFFE7A0, 15),
    ("d50_ashrock", "Ashrock", "stone", 0x2C2826, 0),
    ("d50_cinder_gravel", "Cinder Gravel", "sand", 0x3B3531, 0),
    ("d50_scorched_basalt", "Scorched Basalt", "basalt", 0x24222B, 0),
    ("d50_ember_vent", "Ember Vent", "ore", 0x4A1A0C, 11),
    ("d50_cooled_rim", "Cooled Rim", "stone", 0x6A3A2A, 5),
]

DIMS = [
    dict(n=49, slug="golden_cloud_realm", en="The Golden Cloud Realm", ar="مملكة الغيوم الذهبية", sky=0x8FC4F0, fog=0xF3E6C2, grass=0xCFD96A, foliage=0xB9CC5A,
         water=0x8FD0F0, temp=0.7, rain=False, fx="overworld", time=1000, ceil=False, ambient=0.1, acc=0xE6C65A, part=None),
    dict(n=50, slug="infernal_volcano", en="The Infernal Volcano", ar="البركان المحاصر", sky=0x1C0806, fog=0x4A1408, grass=0x3A2A22, foliage=0x3A2A22,
         water=0x3A1408, temp=2.0, rain=False, fx="nether", time=18000, ceil=False, ambient=0.1, acc=0xFF5A1A, part="minecraft:ash"),
]


def blocks():
    lang = {}
    for i, (bid, name, style, color, light) in enumerate(BLOCKS):
        img = dim_tex.make(style, color, 0xE6C65A if bid.startswith("d49") else 0xFF5A1A, 4900 + i, None, light)
        p = os.path.join(ASSETS, "textures/block", bid + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        GA.wjson(os.path.join(ASSETS, "models/block", bid + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{bid}"}})
        GA.wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
        GA.wjson(os.path.join(ASSETS, "models/item", bid + ".json"), {"parent": f"{MOD}:block/{bid}"})
        GA.wjson(os.path.join(DATA, "loot_tables/blocks", bid + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}],
                                                 "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        lang[f"block.{MOD}.{bid}"] = name
    return lang


def world():
    for d in DIMS:
        s = d["slug"]
        biome = {"has_precipitation": d["rain"], "temperature": d["temp"], "downfall": 0.0,
                 "effects": {"sky_color": d["sky"], "fog_color": d["fog"], "water_color": d["water"], "water_fog_color": max(0, d["water"] - 0x101010),
                             "grass_color": d["grass"], "foliage_color": d["foliage"]},
                 "spawners": {k: [] for k in ["monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc"]},
                 "spawn_costs": {}, "carvers": {}, "features": [[] for _ in range(11)]}
        if d["part"]:
            biome["effects"]["particle"] = {"options": {"type": d["part"]}, "probability": 0.012}
        GA.wjson(os.path.join(DATA, "worldgen/biome", s + ".json"), biome)
        dt = {"ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": d["ceil"], "ambient_light": d["ambient"],
              "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False, "has_raids": False, "logical_height": 256, "min_y": 0, "height": 256,
              "infiniburn": "#minecraft:infiniburn_overworld",
              "effects": {"overworld": "minecraft:overworld", "nether": "minecraft:the_nether"}[d["fx"]],
              "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0, "fixed_time": d["time"]}
        GA.wjson(os.path.join(DATA, "dimension_type", s + ".json"), dt)
        GA.wjson(os.path.join(DATA, "dimension", s + ".json"), {"type": f"{MOD}:{s}", "generator": {
            "type": f"{MOD}:themed", "theme": s, "biome_source": {"type": "minecraft:fixed", "biome": f"{MOD}:{s}"}}})


def sounds():
    from dim_gen import write_ogg
    out = os.path.join(ASSETS, "sounds/dim")
    os.makedirs(out, exist_ok=True)
    sj_path = os.path.join(ASSETS, "sounds.json")
    sj = json.load(open(sj_path))
    written = []
    for d in DIMS:
        s = d["slug"]
        res = dim_snd.render_dim(s)
        files = {"amb": f"amb_{s}", "amb_b": f"amb_{s}_b", "acc_1": f"acc_{s}_1", "acc_2": f"acc_{s}_2", "acc_3": f"acc_{s}_3", "acc_4": f"acc_{s}_4"}
        for k, fn in files.items():
            path = os.path.join(out, fn + ".ogg")
            # never overwrite a file somebody dropped in to replace the generated ambience (see docs/AUDIO_REPLACEMENT.md)
            if os.path.exists(path) and os.environ.get("EXTRA_FORCE") != "1":
                written.append(path); continue
            assert write_ogg(path, res[k]), "ffmpeg failed for " + fn
            written.append(path)
        sj[f"amb_{s}"] = {"sounds": [{"name": f"{MOD}:dim/amb_{s}", "stream": True}, {"name": f"{MOD}:dim/amb_{s}_b", "stream": True}], "subtitle": f"subtitles.{MOD}.amb_{s}"}
        for i in (1, 2, 3, 4):
            n = f"acc_{s}_{i}"
            sj[n] = {"sounds": [{"name": f"{MOD}:dim/{n}", "stream": False}], "subtitle": f"subtitles.{MOD}.{n}"}
    GA.wjson(sj_path, sj)
    return written


def lang(block_lang):
    en_p = os.path.join(ASSETS, "lang/en_us.json")
    en = json.load(open(en_p))
    ar_p = os.path.join(ASSETS, "lang/ar_sa.json")
    ar = json.load(open(ar_p))
    en.update(block_lang)
    for d in DIMS:
        en[f"lastsignal.dimension.{d['slug']}"] = d["en"]
        ar[f"lastsignal.dimension.{d['slug']}"] = d["ar"]
        names = [f"amb_{d['slug']}"] + [f"acc_{d['slug']}_{i}" for i in (1, 2, 3, 4)]
        for n in names:
            en[f"subtitles.{MOD}.{n}"] = f"{d['en']} ambience"
    GA.wjson(en_p, en)
    GA.wjson(ar_p, ar)


def main():
    bl = blocks()
    world()
    files = sounds()
    lang(bl)
    print("extra dims:", len(DIMS), "dimensions,", len(BLOCKS), "blocks,", len(files), "sound files")


if __name__ == "__main__":
    main()
