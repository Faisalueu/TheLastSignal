#!/usr/bin/env python3
"""Phase 1 generator: 48 dimensions -> blocks (192), textures, models, loot, lang, sounds (144 OGG), biomes, dimension types,
dimensions, Java theme table and documentation. Run via generate_assets.py (called last) or directly."""
import json, os, re, subprocess, sys, wave
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_assets as GA
import dim_spec, dim_glyphs, dim_tex, dim_snd, dim_profile, transit_gen
from PIL import Image

MOD = GA.MOD
ASSETS, DATA, JAVA, ROOT = GA.ASSETS, GA.DATA, GA.JAVA, GA.ROOT
DOCS = os.path.join(ROOT, "docs")


def slug(s):
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def block_id(d, i):
    return f"d{d['n']:02d}_{slug(d['blocks'][i][0])}"


def role_id(d, v):
    if v is None:
        return ""
    if isinstance(v, str):
        return v[2:]
    return block_id(d, v - 1)


def kind_flags(d):
    return d["kind"] in ("rooms", "deep", "stairs")


def gen_blocks():
    ids, rows = set(), []
    for d in dim_spec.DIMS48:
        marks = [i for i, b in enumerate(d["blocks"]) if b[1].startswith("g:")]
        assert len(marks) == 1, (d["slug"], "needs exactly one marking block")
        assert d["roles"]["mark"] == marks[0] + 1, (d["slug"], "mark role must be the glyph block")
        lr = d["roles"].get("light")
        if isinstance(lr, int):
            assert d["blocks"][lr - 1][3] > 0, (d["slug"], "light role block must emit light")
        glyph = dim_glyphs.bitmap(d["glyph"], 0)
        for i, (name, style, color, light) in enumerate(d["blocks"]):
            bid = block_id(d, i)
            assert bid not in ids and not os.path.exists(os.path.join(ASSETS, "textures/block", bid + ".png")) or True
            ids.add(bid)
            base = style[2:] if style.startswith("g:") else style
            img = dim_tex.make(base, color, d["acc"], d["n"] * 100 + i, glyph if style.startswith("g:") else None, light)
            p = os.path.join(ASSETS, "textures/block", bid + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            img.save(p)
            glass = base == "glass"
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{bid}"}}
            if glass:
                model["render_type"] = "minecraft:translucent"
            GA.wjson(os.path.join(ASSETS, "models/block", bid + ".json"), model)
            GA.wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
            GA.wjson(os.path.join(ASSETS, "models/item", bid + ".json"), {"parent": f"{MOD}:block/{bid}"})
            GA.wjson(os.path.join(DATA, "loot_tables/blocks", bid + ".json"), {
                "type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}],
                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
            rows.append((bid, light, "glass" if glass else "plain", name))
    assert len(rows) == 192 and len({r[0] for r in rows}) == 192
    return rows


def sounds_names(d):
    s = d["slug"]
    return f"amb_{s}", f"acc_{s}_1", f"acc_{s}_2", f"acc_{s}_3", f"acc_{s}_4"


def write_ogg(path, data):
    wav = path[:-4] + ".wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(dim_snd.SR); w.writeframes(data.tobytes())
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "2", path])
    os.remove(wav)
    return r.returncode == 0


def gen_sounds():
    out = os.path.join(ASSETS, "sounds/dim")
    os.makedirs(out, exist_ok=True)
    sj_path = os.path.join(ASSETS, "sounds.json")
    sj = json.load(open(sj_path)) if os.path.exists(sj_path) else {}
    ok = 0
    names = []
    for d in dim_spec.DIMS48:
        s = d["snd"]
        res = dim_snd.render_dim(s)
        files = {"amb": f"amb_{s}", "amb_b": f"amb_{s}_b", "acc_1": f"acc_{s}_1", "acc_2": f"acc_{s}_2", "acc_3": f"acc_{s}_3", "acc_4": f"acc_{s}_4"}
        for k, fn in files.items():
            if write_ogg(os.path.join(out, fn + ".ogg"), res[k]):
                ok += 1
        beds = [{"name": f"{MOD}:dim/amb_{s}", "stream": True}, {"name": f"{MOD}:dim/amb_{s}_b", "stream": True}]
        sj[f"amb_{s}"] = {"sounds": beds, "subtitle": f"subtitles.{MOD}.amb_{s}"}
        names.append(f"amb_{s}")
        for i in (1, 2, 3, 4):
            n = f"acc_{s}_{i}"
            sj[n] = {"sounds": [{"name": f"{MOD}:dim/{n}", "stream": False}], "subtitle": f"subtitles.{MOD}.{n}"}
            names.append(n)
    GA.wjson(sj_path, sj)
    assert ok == 288, f"only {ok}/288 OGG files were encoded (ffmpeg missing?)"
    return names


def gen_world_json():
    for d in dim_spec.DIMS48:
        s = d["slug"]
        features = [[] for _ in range(11)]
        biome = {"has_precipitation": bool(d["rain"]), "temperature": d["temp"], "downfall": 0.8 if d["rain"] else 0.0,
                 "effects": {"sky_color": d["sky"], "fog_color": d["fog"], "water_color": d["water"], "water_fog_color": max(0, d["water"] - 0x101010),
                             "grass_color": d["blocks"][0][2], "foliage_color": d["blocks"][1][2]},
                 "spawners": {"monster": [], "creature": [], "ambient": [], "axolotls": [], "underground_water_creature": [], "water_creature": [],
                              "water_ambient": [], "misc": []},
                 "spawn_costs": {}, "carvers": {}, "features": features}
        if d["part"]:
            biome["effects"]["particle"] = {"options": {"type": d["part"]}, "probability": 0.012}
        GA.wjson(os.path.join(DATA, "worldgen/biome", s + ".json"), biome)
        ceil = kind_flags(d)
        dt = {"ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": not ceil, "has_ceiling": ceil, "ambient_light": 0.0,
              "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False, "has_raids": False, "logical_height": 256, "min_y": 0,
              "height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
              "effects": {"overworld": "minecraft:overworld", "nether": "minecraft:the_nether", "end": "minecraft:the_end"}[d["fx"]],
              "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0}
        if d["time"] is not None:
            dt["fixed_time"] = d["time"]
        GA.wjson(os.path.join(DATA, "dimension_type", s + ".json"), dt)
        GA.wjson(os.path.join(DATA, "dimension", s + ".json"), {"type": f"{MOD}:{s}", "generator": {
            "type": f"{MOD}:themed", "theme": s, "biome_source": {"type": "minecraft:fixed", "biome": f"{MOD}:{s}"}}})


PARAMS = ["H", "light", "mark", "mural", "feat", "base", "amp", "scale", "sea", "open", "roof", "va", "vb"]


def jstr(s):
    return json.dumps(s, ensure_ascii=False)


def gen_java(rows):
    L = ["package com.lastsignal.world.gen;", "", "// GENERATED by tools/dim_gen.py - do not edit by hand.",
         "public final class ThemeData {",
         "    /** id, light level, kind (plain|glass) */",
         "    public static final String[][] BLOCKS = {"]
    for bid, light, sp, _ in rows:
        L.append(f'        {{"{bid}", "{light}", "{sp}"}},')
    L.append("    };")
    L.append("    public static final Theme[] ALL = {")
    for d in dim_spec.DIMS48:
        roles = [role_id(d, d["roles"].get(r)) for r in dim_spec.ROLE_ORDER]
        variants = [dim_glyphs.bitmap(d["glyph"], v) for v in (range(4) if d["glyph"] == "final" else [0])]
        am, a1, a2 = sounds_names(d)[:3]
        pr = ", ".join(repr(float(d["T"][k])) for k in PARAMS)
        L.append(f'        new Theme({d["n"]}, "{d["slug"]}", {jstr(d["en"])}, "{d["kind"]}",')
        L.append("            new String[]{" + ", ".join(jstr(r) for r in roles) + "},")
        L.append("            new String[]{" + ", ".join(jstr(f) for f in d["feats"]) + "},")
        L.append("            new String[][]{" + ", ".join("{" + ", ".join(jstr(r) for r in v) + "}" for v in variants) + "},")
        L.append(f"            new double[]{{{pr}}},")
        L.append(f'            "{",".join(d["flags"])}", 0x{d["fog"]:06X}, {float(d["fogfar"])}f, "{am}", "{a1}", "{a2}", {float(dim_snd.BED_SECS)}f),')
    L.append("    };")
    L.append("    private ThemeData() {}")
    L.append("}")
    GA.wtext(os.path.join(JAVA, "world/gen/ThemeData.java"), "\n".join(L) + "\n")


def gen_lang(rows):
    en_p = os.path.join(ASSETS, "lang/en_us.json")
    lang = json.load(open(en_p)) if os.path.exists(en_p) else {}
    ar = {}
    lang.update(transit_gen.gen())
    lang["lastsignal.transit.black"] = "The floor gives way."
    for bid, light, sp, name in rows:
        lang[f"block.{MOD}.{bid}"] = name
    for d in dim_spec.DIMS48:
        lang[f"lastsignal.dimension.{d['slug']}"] = d["en"]
        ar[f"lastsignal.dimension.{d['slug']}"] = d["ar"]
        for n in sounds_names(d):
            lang[f"subtitles.{MOD}.{n}"] = f"{d['en']} ambience"
    GA.wjson(en_p, lang)
    GA.wjson(os.path.join(ASSETS, "lang/ar_sa.json"), ar)


def main():
    dim_profile.apply_overrides()
    rows = gen_blocks()
    gen_sounds()
    gen_world_json()
    gen_java(rows)
    dim_profile.gen_java(os.path.join(JAVA, "world/gen/ProfileData.java"))
    dim_profile.write_loot(DATA)
    gen_lang(rows)
    write_docs(rows)
    print("phase1:", len(dim_spec.DIMS48), "dimensions,", len(rows), "blocks, 288 sounds")


def write_docs(rows):
    os.makedirs(DOCS, exist_ok=True)
    L = ["# Phase 1 dimensions (generated list)", "", "Enter: `/lastsignal dimension <id|number>` (op). Exit: Signal Gate on the arrival pad, `/lastsignal recover`, or fall into the void (auto-rescue).", "",
         "| # | id | name | Arabic | terrain | features | blocks | sounds |", "|---|----|------|--------|---------|----------|--------|--------|"]
    for d in dim_spec.DIMS48:
        L.append(f"| {d['n']:02d} | `{MOD}:{d['slug']}` | {d['en']} | {d['ar']} | {d['kind']} | {', '.join(sorted(set(d['feats']))) or '(structural)'} | "
                 + ", ".join(f"`{block_id(d, i)}`" for i in range(4)) + " | " + ", ".join(f"`{n}`" for n in sounds_names(d)) + " |")
    open(os.path.join(DOCS, "DIMENSIONS.md"), "w", encoding="utf-8").write("\n".join(L) + "\n")


if __name__ == "__main__":
    main()
