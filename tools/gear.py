#!/usr/bin/env python3
"""Textures, models, recipes and lang for the Lantern Warden armor and the three weapons (item/GearItems.java).
Merges into lang/en_us.json and lang/ar_sa.json (never regenerates anything else). Run AFTER tools/generate_assets.py if that is ever run."""
import json, os, random
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/lastsignal")
D = os.path.join(RES, "data/lastsignal")
ARMOR = ["helmet", "chestplate", "leggings", "boots"]


def nz(w, h, base, var, seed):
    r = np.random.RandomState(seed)
    a = np.zeros((h, w, 4), dtype=np.uint8)
    for c in range(3):
        a[..., c] = np.clip(base[c] + r.randint(-var, var + 1, (h, w)), 0, 255)
    a[..., 3] = 255
    return a


def armor_layers():
    d = os.path.join(A, "textures/models/armor")
    os.makedirs(d, exist_ok=True)
    for n in (1, 2):
        a = nz(64, 32, (74, 80, 86), 10, 30 + n)
        a[:, :, :3][::4] = a[:, :, :3][::4] * 0.8                       # horizontal seams
        for x in range(0, 64, 8):
            a[:, x, :3] = (a[:, x, :3] * 0.85).astype(np.uint8)
        # amber lantern trim
        a[10:12, 0:32, :3] = (214, 150, 58)
        if n == 1:
            a[20:24, 20:36, :3] = (214, 150, 58)                         # chest lantern plate
            a[22:23, 26:30, :3] = (255, 226, 140)
        Image.fromarray(a).save(os.path.join(d, "lantern_warden_layer_%d.png" % n))


def put(img, rows, pal):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch] + (255,))


def icons():
    d = os.path.join(A, "textures/item")
    pal = {"a": (74, 80, 86), "b": (110, 118, 126), "c": (214, 150, 58), "d": (40, 44, 50), "e": (255, 226, 140), "w": (230, 230, 225), "g": (120, 84, 50)}
    shapes = {
        "lantern_warden_helmet": ["................", "....dddddddd....", "...dbbbbbbbbd...", "..dbaaaaaaaabd..", "..dbaaaaaaaabd..", "..dbaaccccaabd..", "..dbaaceecaabd..", "..dbaaccccaabd..", "..dbaaaaaaaabd..", "..dba......abd..", "..dba......abd..", "..dd........dd.."],
        "lantern_warden_chestplate": ["..dd........dd..", ".dbbd......dbbd.", ".dabbddddddbbad.", ".daabbbbbbbbaad.", ".daaabbbbbbaaad.", "..dabbccccbbad..", "..dabbceecbbad..", "..dabbccccbbad..", "..dabbbbbbbbad..", "..daaaaaaaaaad..", "..dddddddddddd.."],
        "lantern_warden_leggings": ["..dddddddddddd..", "..dbbbbbbbbbbd..", "..dabbbccbbbad..", "..daaaaaaaaaad..", "..dabbd..dbbad..", "..dabd....dbad..", "..dabd....dbad..", "..dabd....dbad..", "..dabd....dbad..", "..dddd....dddd.."],
        "lantern_warden_boots": ["................", "................", "................", "..ddd......ddd..", "..dbd......dbd..", "..dad......dad..", "..dad......dad..", ".ddbbd....ddbbd.", ".dbaaad..dbaaad.", ".dddddd..dddddd."],
        "salt_iron_sword": ["..............dw.", ".............dww.", "............dwwd.", "...........dwwd..", "..........dwwd...", ".........dwwd....", "..d.....dwwd.....", "..dd...dwwd......", "...dd.dwwd.......", "....ddwwd........", "....cdwd.........", "...gccd..........", "..gg.cd..........", ".gg..............", "dd..............."],
        "echo_dagger": ["................", "..............dd", ".............dbd", "............dbd.", "...........dbd..", "..........dbd...", ".........dbd....", "........dbd.....", "...d...dbd......", "...dcddbd.......", "....dcdd........", "...gcd..........", "..gg............"],
        "signal_halberd": ["..........ddd...", ".........dbbbd..", ".........dbeebd.", "..........dbbd..", "..........dg....", ".........dg.....", "........dg......", ".......dg.......", "......dg........", ".....dg.........", "....dg..........", "...dg...........", "..dg............"],
    }
    for n, rows in shapes.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        put(img, rows, pal)
        img.save(os.path.join(d, n + ".png"))
        m = "item/handheld" if n in ("salt_iron_sword", "echo_dagger", "signal_halberd") else "item/generated"
        os.makedirs(os.path.join(A, "models/item"), exist_ok=True)
        json.dump({"parent": m, "textures": {"layer0": "lastsignal:item/" + n}}, open(os.path.join(A, "models/item", n + ".json"), "w"), indent=2)


def recipes():
    d = os.path.join(D, "recipes")
    I = {"item": "minecraft:iron_ingot"}
    F = {"item": "lastsignal:signal_fragment"}
    S = {"item": "minecraft:stick"}
    def shaped(name, pattern, key, out):
        json.dump({"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key, "result": {"item": "lastsignal:" + out}},
                  open(os.path.join(d, name + ".json"), "w"), indent=2)
    shaped("lantern_warden_helmet", ["IFI", "I I"], {"I": I, "F": F}, "lantern_warden_helmet")
    shaped("lantern_warden_chestplate", ["I I", "IFI", "III"], {"I": I, "F": F}, "lantern_warden_chestplate")
    shaped("lantern_warden_leggings", ["IFI", "I I", "I I"], {"I": I, "F": F}, "lantern_warden_leggings")
    shaped("lantern_warden_boots", ["F F", "I I"], {"I": I, "F": F}, "lantern_warden_boots")
    shaped("salt_iron_sword", ["P", "I", "S"], {"P": {"item": "lastsignal:salt_pouch"}, "I": I, "S": S}, "salt_iron_sword")
    shaped("echo_dagger", ["W", "I", "S"], {"W": {"item": "lastsignal:echo_whistle"}, "I": I, "S": S}, "echo_dagger")
    shaped("signal_halberd", ["FI", "FS", " S"], {"I": I, "F": F, "S": S}, "signal_halberd")


def lang():
    en = {"lantern_warden_helmet": "Lantern Warden Helm", "lantern_warden_chestplate": "Lantern Warden Plate", "lantern_warden_leggings": "Lantern Warden Greaves",
          "lantern_warden_boots": "Lantern Warden Boots", "salt_iron_sword": "Salt Iron Sword", "echo_dagger": "Echo Dagger", "signal_halberd": "Signal Halberd"}
    ar = {"lantern_warden_helmet": "خوذة حارس الفانوس", "lantern_warden_chestplate": "صدرية حارس الفانوس", "lantern_warden_leggings": "واقي ساقين حارس الفانوس",
          "lantern_warden_boots": "حذاء حارس الفانوس", "salt_iron_sword": "سيف الحديد المملّح", "echo_dagger": "خنجر الصدى", "signal_halberd": "رمح الإشارة"}
    ents = {"black_witness": "الشاهد الأسود", "tall_black": "الأسود الطويل", "borrowed_skin": "الجلد المستعار", "drowned_choir": "جوقة الغرقى",
            "hanging_man": "الرجل المعلّق", "salt_mother": "أمّ الملح", "radio_shepherd": "راعي الراديو", "glass_walker": "ماشي الزجاج",
            "candle_bearer": "حامل الشمعة", "paper_man": "رجل الورق", "stair_thing": "كائن الدرج", "moon_watcher": "مراقب القمر"}
    for code, names in (("en_us", en), ("ar_sa", ar)):
        p = os.path.join(A, "lang/%s.json" % code)
        d = json.load(open(p))
        for k, v in names.items():
            d["item.lastsignal." + k] = v
        if code == "ar_sa":
            for k, v in ents.items():
                d["entity.lastsignal." + k] = v
        json.dump(d, open(p, "w"), indent=2, ensure_ascii=False)


if __name__ == "__main__":
    armor_layers(); icons(); recipes(); lang()
    print("gear assets written")
