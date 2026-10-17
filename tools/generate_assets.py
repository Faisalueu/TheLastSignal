#!/usr/bin/env python3
"""
THE LAST SIGNAL - asset + table generator.

Single source of truth for: entity profiles (model dimensions + texture layout),
items, blocks, sounds, language file, models, blockstates, loot tables,
dimension/biome JSON. It also emits Java tables so names never drift:
  - entity/EntityProfiles.java
  - GeneratedContent.java
  - sound/ModSounds.java
Run from the project root:  python3 tools/generate_assets.py
Requires: pillow, numpy, ffmpeg (for OGG).  Everything is procedural/original.
"""
import json, os, random, subprocess, sys, wave, math
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
JAVA = os.path.join(ROOT, "src/main/java/com/lastsignal")
MOD = "lastsignal"
ASSETS = os.path.join(RES, "assets", MOD)
DATA = os.path.join(RES, "data", MOD)


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def wtext(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


# ----------------------------------------------------------------------------
# ENTITY PROFILES
# keys: head(w,h,d) body(w,h,d) arm(w,len) leg(w,len) extra(w,h,d,ox,oy,oz)|None
#       quad(bool) hb(width,height) base dark accent eyes(style) display
# ----------------------------------------------------------------------------
ENT = [
 dict(id="observer", name="The Observer", head=(6,8,6), body=(6,18,4), arm=(2,28), leg=(3,20), extra=None, quad=False, hb=(0.6,3.4), base=0x9d9d9a, dark=0x34343a, accent=0xdff7ff, eyes="twin_pale"),
 dict(id="hollow", name="The Hollow", head=(6,7,6), body=(5,10,3), arm=(2,17), leg=(2,14), extra=None, quad=False, hb=(0.6,2.1), base=0x2b2523, dark=0x0e0b0a, accent=0xb02a1c, eyes="sockets"),
 dict(id="mimic", name="The Mimic", head=(8,8,8), body=(8,8,6), arm=(3,10), leg=(3,6), extra=None, quad=False, hb=(0.8,1.3), base=0x8a6f56, dark=0x3b2a20, accent=0xe8e0c8, eyes="grin"),
 dict(id="signalman", name="The Signalman", head=(7,8,7), body=(7,16,4), arm=(3,18), leg=(3,14), extra=(1,9,1,0,-16,0), quad=False, hb=(0.7,2.9), base=0x4b5a4a, dark=0x1a211b, accent=0x6dff9a, eyes="visor"),
 dict(id="witness", name="The Witness", head=(12,12,10), body=(5,10,3), arm=(2,19), leg=(2,10), extra=None, quad=False, hb=(0.8,2.1), base=0xcfc8c0, dark=0x5a5350, accent=0x101010, eyes="many"),
 dict(id="crawler", name="The Crawler", head=(5,5,7), body=(6,5,16), arm=(2,13), leg=(2,13), extra=None, quad=True, hb=(0.9,0.8), base=0x5b5a52, dark=0x1c1b18, accent=0xe0d0a0, eyes="slit"),
 dict(id="lost_child", name="The Lost Child", head=(6,6,6), body=(4,7,3), arm=(2,7), leg=(2,7), extra=None, quad=False, hb=(0.4,1.05), base=0x6a6a74, dark=0x15151a, accent=0xffffff, eyes="blank"),
 dict(id="doorman", name="The Doorman", head=(8,8,8), body=(12,20,4), arm=(3,24), leg=(5,6), extra=None, quad=False, hb=(1.0,3.2), base=0x5c3f26, dark=0x22160c, accent=0xc9a24a, eyes="knot"),
 dict(id="pale_herd", name="The Pale Herd", head=(5,6,5), body=(5,14,3), arm=(2,20), leg=(2,16), extra=None, quad=False, hb=(0.5,2.8), base=0xe2ddd5, dark=0x6b6660, accent=0x202020, eyes="blank"),
 dict(id="echo", name="The Echo", head=(7,8,7), body=(7,13,4), arm=(3,14), leg=(3,14), extra=None, quad=False, hb=(0.6,1.95), base=0x4a5f78, dark=0x141c28, accent=0x9fd0ff, eyes="glitch"),
 dict(id="burrower", name="The Burrower", head=(12,10,12), body=(14,10,20), arm=(4,8), leg=(4,8), extra=None, quad=True, hb=(2.4,1.8), base=0x6b4a38, dark=0x261811, accent=0xd8b080, eyes="maw"),
 dict(id="lantern_widow", name="The Lantern Widow", head=(5,5,5), body=(6,8,10), arm=(1,26), leg=(1,26), extra=(4,5,4,0,7,8), quad=True, hb=(1.3,2.8), base=0x2a2a33, dark=0x0b0b10, accent=0xffc24a, eyes="eightspots"),
 dict(id="facet", name="The Facet", head=(7,8,7), body=(8,14,4), arm=(3,15), leg=(3,15), extra=None, quad=False, hb=(0.6,1.95), base=0xaab6c4, dark=0x3d4856, accent=0xffffff, eyes="shards"),
 dict(id="archivist", name="The Archivist", head=(7,7,7), body=(8,10,6), arm=(3,12), leg=(3,10), extra=(6,7,3,0,2,5), quad=False, hb=(0.75,1.55), base=0x6e5f4c, dark=0x261f17, accent=0xf0e6b8, eyes="spectacles"),
 dict(id="signal_core", name="The Signal Core", head=(16,16,16), body=(20,24,14), arm=(6,26), leg=(8,10), extra=(8,12,8,6,-14,0), quad=False, hb=(3.0,4.6), base=0x3a1f3f, dark=0x0d060f, accent=0xff3fa4, eyes="core"),
 dict(id="buried_king", name="The Buried King", head=(14,12,14), body=(18,20,12), arm=(7,26), leg=(8,12), extra=None, quad=False, hb=(2.6,4.0), base=0x5a4a3a, dark=0x1c150f, accent=0xffd36b, eyes="crown"),
 dict(id="unfinished", name="The Unfinished", head=(7,8,7), body=(7,12,4), arm=(3,16), leg=(3,14), extra=None, quad=False, hb=(0.6,2.0), base=0xb5b0a5, dark=0x2c2a26, accent=0xff5a5a, eyes="unfinished"),
]

TEX = 256


def cube_size(w, h, d):
    return (2 * (d + w), d + h)


def layout():
    """Shelf-pack every part of every entity into its own 256x256 sheet (per-entity)."""
    for e in ENT:
        parts = {
            "head": cube_size(*e["head"]),
            "body": cube_size(*e["body"]),
            "arm": cube_size(e["arm"][0], e["arm"][1], e["arm"][0]),
            "leg": cube_size(e["leg"][0], e["leg"][1], e["leg"][0]),
        }
        if e["extra"]:
            ex = e["extra"]
            parts["extra"] = cube_size(ex[0], ex[1], ex[2])
        x = y = rowh = 0
        uv = {}
        for name, (w, h) in parts.items():
            if x + w > TEX:
                x = 0
                y += rowh
                rowh = 0
            uv[name] = (x, y)
            x += w
            rowh = max(rowh, h)
        assert y + rowh <= TEX, e["id"]
        e["uv"] = uv
        e["parts"] = parts


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def noisy(img, rect, base, dark, rng, density=0.18):
    x0, y0, w, h = rect
    px = img.load()
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            r = rng.random()
            t = rng.uniform(-14, 14)
            c = list(hexc(base))
            if r < density:
                c = [int(c[i] * 0.55 + hexc(dark)[i] * 0.45) for i in range(3)]
            c = [max(0, min(255, int(v + t))) for v in c]
            px[xx, yy] = tuple(c) + (255,)


def damage(img, rect, dark, rng, n):
    x0, y0, w, h = rect
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x = rng.randint(x0, x0 + w - 1)
        y = rng.randint(y0, y0 + h - 1)
        ln = rng.randint(2, max(3, h // 3))
        d.line([(x, y), (min(x0 + w - 1, x + rng.randint(-2, 2)), min(y0 + h - 1, y + ln))],
               fill=hexc(dark) + (255,))


def draw_face(img, rect, e, rng):
    x0, y0, w, h = rect
    d = ImageDraw.Draw(img)
    acc = hexc(e["accent"]) + (255,)
    dk = hexc(e["dark"]) + (255,)
    blk = (8, 8, 10, 255)
    st = e["eyes"]
    ey = y0 + max(1, h // 3)
    cx = x0 + w // 2
    if st == "twin_pale":
        for ex in (cx - max(1, w // 4) - 1, cx + max(1, w // 4)):
            d.rectangle([ex, ey, ex + 1, ey + 1], fill=acc)
    elif st == "sockets":
        for ex in (cx - w // 3, cx + w // 3 - 1):
            d.rectangle([ex, ey, ex + 1, ey + 2], fill=blk)
            d.point((ex, ey + 1), fill=acc)
        d.rectangle([cx - 1, y0 + h - 3, cx, y0 + h - 2], fill=blk)
    elif st == "grin":
        for ex in (cx - w // 3, cx + w // 3 - 1):
            d.rectangle([ex, ey, ex + 1, ey + 1], fill=blk)
        yy = y0 + h - 3
        d.rectangle([x0 + 1, yy, x0 + w - 2, yy + 1], fill=blk)
        for tx in range(x0 + 1, x0 + w - 1, 2):
            d.point((tx, yy), fill=acc)
    elif st == "visor":
        d.rectangle([x0 + 1, ey, x0 + w - 2, ey + 1], fill=acc)
        d.rectangle([x0, ey + 3, x0 + w - 1, ey + 3], fill=dk)
    elif st == "many":
        for _ in range(7):
            ex = rng.randint(x0 + 1, x0 + w - 3)
            ey2 = rng.randint(y0 + 1, y0 + h - 3)
            d.rectangle([ex, ey2, ex + 1, ey2 + 1], fill=blk)
            d.point((ex, ey2), fill=(240, 240, 240, 255))
    elif st == "slit":
        d.line([(x0 + 1, ey), (x0 + w - 2, ey + 1)], fill=acc)
    elif st == "blank":
        d.rectangle([cx - 1, y0 + h // 2, cx, y0 + h // 2 + 1], fill=dk)
    elif st == "knot":
        d.ellipse([cx - 2, ey - 1, cx + 2, ey + 3], fill=dk, outline=acc)
        d.line([(x0 + 1, y0 + h - 2), (x0 + w - 2, y0 + h - 2)], fill=dk)
    elif st == "glitch":
        for i in range(0, h, 3):
            off = rng.randint(-2, 2)
            d.line([(x0 + 1 + off, y0 + i), (x0 + w - 2 + off, y0 + i)], fill=acc if i % 2 else dk)
        d.rectangle([cx - 2, ey, cx - 1, ey + 1], fill=(255, 255, 255, 255))
    elif st == "maw":
        d.ellipse([x0 + 2, y0 + 2, x0 + w - 3, y0 + h - 3], fill=blk, outline=acc)
        for tx in range(x0 + 3, x0 + w - 3, 2):
            d.point((tx, y0 + 3), fill=acc)
            d.point((tx, y0 + h - 4), fill=acc)
    elif st == "eightspots":
        for i in range(8):
            ex = x0 + 1 + (i % 4) * max(1, (w - 2) // 4)
            ey2 = y0 + 1 + (i // 4) * max(2, h // 2)
            d.point((ex, ey2), fill=acc)
    elif st == "shards":
        for _ in range(6):
            ax = rng.randint(x0, x0 + w - 2)
            ay = rng.randint(y0, y0 + h - 2)
            d.line([(ax, ay), (ax + rng.randint(-3, 3), ay + rng.randint(2, 5))], fill=acc if rng.random() < .5 else dk)
        d.rectangle([cx - 2, ey, cx - 1, ey], fill=blk)
        d.rectangle([cx + 1, ey + 1, cx + 2, ey + 1], fill=blk)
    elif st == "spectacles":
        d.rectangle([cx - w // 3 - 1, ey, cx - w // 3 + 1, ey + 2], outline=acc)
        d.rectangle([cx + w // 3 - 2, ey, cx + w // 3, ey + 2], outline=acc)
        d.line([(cx - 1, ey + 1), (cx, ey + 1)], fill=acc)
    elif st == "core":
        d.ellipse([x0 + 2, y0 + 2, x0 + w - 3, y0 + h - 3], fill=blk, outline=acc)
        d.ellipse([cx - 3, y0 + h // 2 - 3, cx + 2, y0 + h // 2 + 2], fill=acc)
    elif st == "crown":
        for tx in range(x0 + 1, x0 + w - 1, 3):
            d.rectangle([tx, y0, tx + 1, y0 + 2], fill=acc)
        for ex in (cx - w // 3, cx + w // 3 - 2):
            d.rectangle([ex, ey + 1, ex + 2, ey + 2], fill=blk)
    elif st == "unfinished":
        d.rectangle([cx - w // 3, ey, cx - w // 3 + 1, ey + 1], fill=blk)
        d.line([(x0 + 1, y0 + h - 3), (cx, y0 + h - 2)], fill=acc)
        # missing half of the face
        for yy in range(y0, y0 + h):
            for xx in range(cx + 1, x0 + w):
                if (xx + yy) % 2 == 0:
                    img.putpixel((xx, yy), (0, 0, 0, 0))


def gen_entity_textures():
    rng = random.Random(1337)
    for e in ENT:
        img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        for part, (u, v) in e["uv"].items():
            if part == "arm":
                w, h, d = e["arm"][0], e["arm"][1], e["arm"][0]
            elif part == "leg":
                w, h, d = e["leg"][0], e["leg"][1], e["leg"][0]
            elif part == "extra":
                w, h, d = e["extra"][0], e["extra"][1], e["extra"][2]
            else:
                w, h, d = e[part]
            f = faces(u, v, w, h, d)
            base, dark = e["base"], e["dark"]
            if part == "extra" and e["id"] in ("lantern_widow",):
                base, dark = e["accent"], 0x8a4a10
            for name, rect in f.items():
                noisy(img, rect, base, dark, rng, 0.22 if part != "head" else 0.15)
                damage(img, rect, dark, rng, max(1, (rect[2] * rect[3]) // 40))
            if part == "head":
                draw_face(img, f["front"], e, rng)
            if part == "body":
                # sternum seam + asymmetric stain
                dr = ImageDraw.Draw(img)
                fx, fy, fw, fh = f["front"]
                dr.line([(fx + fw // 2, fy), (fx + fw // 2, fy + fh - 1)], fill=hexc(e["dark"]) + (255,))
                sx = fx + rng.randint(0, max(0, fw - 4))
                sy = fy + rng.randint(0, max(0, fh - 5))
                dr.rectangle([sx, sy, sx + 2, sy + 3], fill=hexc(e["dark"]) + (255,))
        path = os.path.join(ASSETS, "textures/entity", e["id"] + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)


# ----------------------------------------------------------------------------
# ITEMS / BLOCKS
# ----------------------------------------------------------------------------
ITEMS = [
 # id, display name, kind(for texture look), tooltip/purpose
 ("broken_radio", "Broken Radio", "radio", "Hold to listen. Sneak-use to review recordings. Tune with the Frequency Tuner."),
 ("signal_detector", "Signal Detector", "detector", "Beeps faster near Signal anomalies."),
 ("archive_key", "Archive Key", "key", "Opens Archive Terminals and sealed archive doors."),
 ("research_notebook", "Research Notebook", "book", "Your personal journal: notes and transmissions."),
 ("damaged_recording", "Damaged Recording", "tape", "Play near a Radio Receiver to unlock a transmission."),
 ("frequency_tuner", "Frequency Tuner", "dial", "Fine-tunes the Broken Radio."),
 ("protective_charm", "Protective Charm", "charm", "Weakens the pull of the Signal for a while."),
 ("emergency_flare", "Emergency Flare", "flare", "Repels the Pale Herd and Lantern Widow briefly."),
 ("strange_photograph", "Strange Photograph", "photo", "A photograph of a place that is not there."),
 ("sealed_envelope", "Sealed Envelope", "envelope", "Contains a document. Open it with use."),
 ("ancient_coin", "Ancient Coin", "coin", "Currency of the Archivist."),
 ("signal_fragment", "Signal Fragment", "crystal", "Crafting material from corrupted relays."),
 ("memory_shard", "Memory Shard", "shard", "Contains a memory that is not yours."),
 ("rusted_keycard", "Rusted Keycard", "card", "Opens research station doors."),
 ("distorted_compass", "Distorted Compass", "compass", "Points toward the nearest anomaly."),
 ("long_hall_key", "Long Hall Key", "key2", "Opens the doors of the Long Hall."),
 ("red_key", "Red Key", "key3", "Opens something in the Red Silence."),
 ("power_cell", "Power Cell", "cell", "Restores power to Archive machinery."),
 ("cassette_tape", "Cassette Tape", "tape", "A blank-looking tape."),
 ("copper_wire_spool", "Copper Wire Spool", "spool", "Crafting material."),
 ("static_dust", "Static Dust", "dust", "Fine grey dust that hums."),
 ("void_lens", "Void Lens", "lens", "Reveals false illusions made by The Facet."),
 ("lantern_oil", "Lantern Oil", "bottle", "Crafting material from the Lantern Widow."),
 ("doorframe_splinter", "Doorframe Splinter", "splinter", "A piece of the Doorman."),
 ("archivist_stamp", "Archivist Stamp", "stamp", "Proof of an exchange."),
 ("containment_badge", "Containment Badge", "badge", "Staff badge of Project Containment."),
 ("echo_whistle", "Echo Whistle", "whistle", "Calls attention. Use carefully."),
 ("signal_blade", "Signal Blade", "blade", "A weapon that hurts signal-touched creatures more."),
 ("salt_pouch", "Salt Pouch", "pouch", "Place to mark a boundary."),
 ("field_journal_page", "Field Journal Page", "page", "A torn page. Collect them all."),
 ("silent_bell", "Silent Bell", "bell", "Rings without sound. Something hears it."),
 ("buried_crown_shard", "Buried Crown Shard", "crown", "Reward from The Buried King."),
 ("unfinished_effigy", "Unfinished Effigy", "effigy", "Reward from The Unfinished."),
]

BLOCKS = [
 # id, name, light, special, base color, accent
 ("radio_receiver", "Radio Receiver", 3, "radio", 0x3f4a3f, 0x9aff9a),
 ("corrupted_relay", "Corrupted Relay", 7, "relay", 0x2a1f33, 0xff3fa4),
 ("sealed_door_block", "Sealed Door", 0, "sealed", 0x4a3b2a, 0xc9a24a),
 ("archive_terminal", "Archive Terminal", 5, "terminal", 0x26313a, 0x66d9ff),
 ("strange_pillar", "Strange Pillar", 0, "plain", 0x7a7a7a, 0x222222),
 ("signal_crystal", "Signal Crystal", 12, "plain", 0x5a2a6a, 0xff7ae0),
 ("abandoned_machinery", "Abandoned Machinery", 0, "plain", 0x5b5348, 0xa0522d),
 ("puzzle_block_a", "Puzzle Block (Circle)", 0, "puzzle", 0x6a6a6a, 0xe0e0e0),
 ("puzzle_block_b", "Puzzle Block (Triangle)", 0, "puzzle", 0x6a6a6a, 0xe0b0b0),
 ("checkpoint_beacon", "Checkpoint Beacon", 10, "checkpoint", 0x2c4a3a, 0x7affc0),
 ("quiet_grass", "Quiet Grass", 0, "plain", 0x6f7f5a, 0x3b4a2d),
 ("quiet_soil", "Quiet Soil", 0, "plain", 0x4c4038, 0x2a221d),
 ("red_stone", "Red Stone", 0, "plain", 0x6a1f1f, 0x2a0a0a),
 ("void_glass", "Void Glass", 4, "plain", 0x1a1a2a, 0x7a7aff),
 ("hall_wallpaper", "Hall Wallpaper", 0, "plain", 0x8a7a4a, 0x5a4a28),
 ("hall_tile", "Hall Tile", 0, "plain", 0xb0aa98, 0x6a6558),
 ("signal_gate", "Signal Gate", 9, "gate", 0x1a0a24, 0xff3fa4),
 ("memory_log", "Memory Log", 0, "lore", 0x4a3320, 0xd8c8a0),
 ("lore_desk", "Abandoned Desk", 0, "lore", 0x5a432e, 0xc8b890),
]

SOUNDS = [
 # name, seconds, kind
 ("static_loop", 3.0, "static"),
 ("whisper", 2.4, "whisper"),
 ("footstep_far", 0.9, "step"),
 ("knock", 1.0, "knock"),
 ("rumble", 3.0, "rumble"),
 ("distant_call", 2.5, "call"),
 ("radio_tune", 1.8, "tune"),
 ("radio_blip", 0.4, "blip"),
 ("heartbeat", 1.2, "heart"),
 ("creature_breath", 2.0, "breath"),
 ("mimic_cry", 1.6, "cry"),
 ("bell_distant", 3.0, "bell"),
 ("glitch", 0.8, "glitch"),
 ("ambient_drone", 6.0, "drone"),
 ("chase_tension", 5.0, "tension"),
 ("boss_roar", 2.4, "roar"),
 ("door_creak", 1.8, "creak"),
]


def item_icon(kind, accent):
    rng = random.Random(hash(kind) & 0xffff)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pal = [(0x55, 0x55, 0x5c), (0x8a, 0x8a, 0x92), (0xc8, 0xc8, 0xd0), (0x2a, 0x2a, 0x30)]
    cols = {
        "radio": [(70, 80, 70), (120, 130, 110), (180, 255, 180), (30, 35, 30)],
        "detector": [(60, 60, 70), (100, 100, 115), (255, 90, 60), (25, 25, 30)],
        "key": [(150, 120, 50), (200, 170, 80), (240, 215, 120), (90, 70, 25)],
        "key2": [(120, 110, 130), (170, 160, 190), (220, 210, 240), (60, 55, 70)],
        "key3": [(130, 40, 40), (180, 70, 70), (230, 110, 110), (70, 20, 20)],
        "book": [(90, 60, 40), (130, 90, 60), (220, 210, 180), (50, 30, 20)],
        "tape": [(40, 40, 45), (90, 90, 100), (200, 200, 210), (15, 15, 18)],
        "dial": [(80, 80, 90), (140, 140, 150), (255, 220, 90), (30, 30, 35)],
        "charm": [(110, 90, 60), (170, 150, 100), (120, 220, 255), (60, 45, 30)],
        "flare": [(150, 40, 30), (210, 80, 60), (255, 200, 90), (80, 20, 15)],
        "photo": [(200, 195, 180), (230, 225, 210), (80, 80, 90), (120, 115, 100)],
        "envelope": [(200, 190, 160), (225, 215, 185), (150, 40, 40), (120, 110, 90)],
        "coin": [(160, 130, 40), (215, 180, 70), (250, 235, 150), (90, 70, 20)],
        "crystal": [(90, 40, 110), (160, 80, 190), (255, 150, 235), (50, 20, 65)],
        "shard": [(40, 70, 110), (80, 130, 190), (170, 225, 255), (20, 40, 70)],
        "card": [(110, 70, 50), (160, 110, 80), (230, 220, 200), (60, 35, 25)],
        "compass": [(100, 100, 110), (160, 160, 175), (255, 60, 60), (45, 45, 55)],
        "cell": [(60, 60, 70), (100, 100, 115), (90, 255, 140), (25, 25, 30)],
        "spool": [(150, 90, 40), (200, 130, 60), (240, 180, 110), (90, 50, 20)],
        "dust": [(110, 110, 115), (160, 160, 165), (220, 220, 225), (60, 60, 65)],
        "lens": [(50, 50, 70), (90, 90, 130), (200, 200, 255), (20, 20, 35)],
        "bottle": [(120, 80, 20), (190, 130, 40), (255, 210, 110), (60, 40, 10)],
        "splinter": [(100, 70, 40), (150, 110, 65), (190, 150, 95), (55, 38, 20)],
        "stamp": [(110, 60, 60), (160, 90, 90), (230, 200, 200), (60, 30, 30)],
        "badge": [(70, 90, 110), (110, 135, 160), (230, 235, 240), (35, 45, 55)],
        "whistle": [(140, 140, 150), (190, 190, 200), (240, 240, 250), (80, 80, 90)],
        "blade": [(70, 70, 90), (130, 130, 160), (255, 100, 220), (35, 35, 50)],
        "pouch": [(180, 175, 160), (220, 215, 200), (255, 255, 250), (110, 105, 95)],
        "page": [(200, 190, 160), (230, 220, 190), (60, 50, 40), (140, 130, 105)],
        "bell": [(170, 140, 60), (220, 190, 90), (250, 235, 160), (100, 80, 30)],
        "crown": [(150, 120, 40), (210, 175, 70), (255, 230, 140), (90, 70, 20)],
        "effigy": [(180, 175, 165), (215, 210, 200), (255, 90, 90), (90, 85, 80)],
    }.get(kind, pal)
    dark, mid, light, black = cols[3], cols[0], cols[1], cols[2]
    base, hi, acc, sh = cols[0], cols[1], cols[2], cols[3]
    if kind in ("key", "key2", "key3"):
        d.ellipse([2, 2, 7, 7], fill=base, outline=sh)
        d.rectangle([6, 6, 13, 7], fill=hi)
        d.rectangle([11, 8, 12, 10], fill=hi)
        d.rectangle([13, 8, 14, 9], fill=hi)
        d.point((4, 4), fill=(0, 0, 0, 0))
    elif kind in ("book", "page", "photo", "envelope", "card", "badge"):
        d.rectangle([3, 2, 12, 13], fill=base, outline=sh)
        d.rectangle([4, 3, 11, 12], fill=hi)
        d.line([(5, 5), (10, 5)], fill=acc)
        d.line([(5, 7), (9, 7)], fill=sh)
        d.line([(5, 9), (10, 9)], fill=sh)
    elif kind in ("tape",):
        d.rectangle([2, 4, 13, 12], fill=base, outline=sh)
        d.ellipse([4, 6, 6, 8], fill=acc)
        d.ellipse([9, 6, 11, 8], fill=acc)
        d.rectangle([5, 10, 10, 11], fill=hi)
    elif kind in ("radio",):
        d.rectangle([2, 5, 13, 13], fill=base, outline=sh)
        d.line([(10, 1), (12, 5)], fill=hi)
        d.rectangle([4, 7, 8, 11], fill=sh)
        d.ellipse([10, 8, 12, 10], fill=acc)
        d.point((5, 8), fill=acc)
        d.point((6, 10), fill=acc)
    elif kind in ("detector", "cell", "dial", "compass", "lens", "coin", "bell", "whistle", "stamp"):
        d.ellipse([2, 2, 13, 13], fill=base, outline=sh)
        d.ellipse([4, 4, 11, 11], fill=hi)
        d.line([(8, 8), (11, 5)] if kind != "coin" else [(6, 8), (9, 8)], fill=acc)
        d.point((8, 8), fill=sh)
    elif kind in ("crystal", "shard"):
        d.polygon([(8, 1), (13, 8), (8, 14), (3, 8)], fill=base, outline=sh)
        d.polygon([(8, 3), (11, 8), (8, 12)], fill=hi)
        d.point((7, 5), fill=acc)
    elif kind in ("flare",):
        d.rectangle([6, 3, 9, 14], fill=base, outline=sh)
        d.rectangle([6, 1, 9, 3], fill=acc)
        d.point((7, 0), fill=(255, 255, 200, 255))
    elif kind in ("blade",):
        d.line([(3, 13), (12, 3)], fill=hi, width=2)
        d.line([(4, 12), (11, 4)], fill=acc)
        d.line([(2, 11), (6, 14)], fill=sh, width=2)
    elif kind in ("crown", "effigy"):
        d.polygon([(2, 12), (3, 5), (6, 9), (8, 3), (10, 9), (13, 5), (14, 12)], fill=base, outline=sh)
        d.point((8, 7), fill=acc)
    elif kind in ("bottle",):
        d.rectangle([5, 5, 10, 13], fill=base, outline=sh)
        d.rectangle([7, 2, 8, 5], fill=hi)
        d.rectangle([6, 8, 9, 12], fill=acc)
    elif kind in ("pouch", "dust", "spool", "splinter"):
        d.ellipse([3, 5, 12, 14], fill=base, outline=sh)
        d.rectangle([6, 3, 9, 6], fill=hi)
        d.line([(5, 9), (10, 9)], fill=acc)
    elif kind in ("charm",):
        d.line([(8, 1), (8, 4)], fill=hi)
        d.polygon([(8, 4), (13, 8), (8, 14), (3, 8)], fill=base, outline=sh)
        d.ellipse([6, 7, 9, 10], fill=acc)
    else:
        d.rectangle([3, 3, 12, 12], fill=base, outline=sh)
    # grime
    for _ in range(6):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        px = img.getpixel((x, y))
        if px[3] > 0:
            img.putpixel((x, y), tuple(int(c * 0.7) for c in px[:3]) + (255,))
    return img


def block_tex(base, accent, special, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    noisy(img, (0, 0, 16, 16), base, 0x101010, rng, 0.25)
    d = ImageDraw.Draw(img)
    a = hexc(accent) + (255,)
    if special in ("radio",):
        d.rectangle([2, 3, 13, 12], outline=a)
        for i in range(4):
            d.line([(3, 5 + i * 2), (8, 5 + i * 2)], fill=a)
        d.ellipse([10, 6, 12, 8], fill=a)
    elif special == "relay":
        d.line([(0, 8), (5, 3), (9, 12), (15, 7)], fill=a)
        d.point((8, 8), fill=(255, 255, 255, 255))
    elif special == "sealed":
        d.rectangle([1, 1, 14, 14], outline=a)
        d.line([(1, 1), (14, 14)], fill=a)
        d.line([(14, 1), (1, 14)], fill=a)
    elif special == "terminal":
        d.rectangle([2, 2, 13, 9], outline=a, fill=(10, 30, 40, 255))
        for i in range(3):
            d.line([(3, 4 + i * 2), (3 + 3 + i * 2, 4 + i * 2)], fill=a)
        d.rectangle([3, 11, 12, 13], fill=(30, 40, 50, 255))
    elif special == "puzzle":
        if seed % 2:
            d.ellipse([3, 3, 12, 12], outline=a)
        else:
            d.polygon([(8, 3), (13, 12), (3, 12)], outline=a)
    elif special == "checkpoint":
        d.rectangle([3, 3, 12, 12], outline=a)
        d.line([(8, 4), (8, 11)], fill=a)
        d.line([(4, 8), (11, 8)], fill=a)
    elif special == "gate":
        d.rectangle([1, 1, 14, 14], outline=a)
        d.ellipse([4, 4, 11, 11], outline=a)
        d.point((8, 8), fill=(255, 255, 255, 255))
    elif special == "lore":
        d.rectangle([2, 3, 13, 12], fill=a)
        for i in range(4):
            d.line([(3, 5 + i * 2), (12, 5 + i * 2)], fill=hexc(base) + (255,))
    else:
        for _ in range(5):
            x = rng.randint(0, 15)
            d.line([(x, 0), (x + rng.randint(-1, 1), 15)], fill=a if rng.random() < 0.2 else hexc(base) + (255,))
    return img


def gen_items_blocks_assets():
    # items
    for iid, name, kind, tip in ITEMS:
        img = item_icon(kind, 0)
        p = os.path.join(ASSETS, "textures/item", iid + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        wjson(os.path.join(ASSETS, "models/item", iid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{iid}"}})
    # blocks
    for i, (bid, name, light, special, base, accent) in enumerate(BLOCKS):
        img = block_tex(base, accent, special, i + 3)
        p = os.path.join(ASSETS, "textures/block", bid + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        wjson(os.path.join(ASSETS, "models/block", bid + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{bid}"}})
        if special == "gate":
            wjson(os.path.join(ASSETS, "blockstates", bid + ".json"),
                  {"variants": {f"target={i}": {"model": f"{MOD}:block/{bid}"} for i in range(7)}})
        else:
            wjson(os.path.join(ASSETS, "blockstates", bid + ".json"),
                  {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
        wjson(os.path.join(ASSETS, "models/item", bid + ".json"), {"parent": f"{MOD}:block/{bid}"})
        wjson(os.path.join(DATA, "loot_tables/blocks", bid + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # block tags for pickaxe (mineable) - drop with any tool for simplicity: no tag needed.


def gen_loot():
    tiers = {
        "common": [("ancient_coin", 4, 1, 3), ("static_dust", 5, 1, 4), ("copper_wire_spool", 4, 1, 3), ("field_journal_page", 5, 1, 1),
                   ("emergency_flare", 3, 1, 2), ("salt_pouch", 3, 1, 2), ("sealed_envelope", 3, 1, 1), ("cassette_tape", 2, 1, 1),
                   ("strange_photograph", 2, 1, 1), ("lantern_oil", 2, 1, 1), ("rusted_keycard", 2, 1, 1), ("damaged_recording", 3, 1, 1)],
        "archive": [("archive_key", 3, 1, 1), ("power_cell", 4, 1, 1), ("research_notebook", 3, 1, 1), ("frequency_tuner", 3, 1, 1),
                    ("memory_shard", 4, 1, 2), ("signal_fragment", 5, 1, 3), ("void_lens", 2, 1, 1), ("containment_badge", 3, 1, 1),
                    ("sealed_envelope", 5, 1, 2), ("protective_charm", 3, 1, 1), ("signal_detector", 2, 1, 1), ("broken_radio", 2, 1, 1)],
        "endgame": [("signal_blade", 2, 1, 1), ("distorted_compass", 3, 1, 1), ("silent_bell", 3, 1, 1), ("memory_shard", 5, 2, 4),
                    ("signal_fragment", 5, 2, 5), ("protective_charm", 4, 1, 2), ("echo_whistle", 2, 1, 1), ("archivist_stamp", 3, 1, 2)],
    }
    for tier, entries in tiers.items():
        pool_entries = []
        for name, w, lo, hi in entries:
            e = {"type": "minecraft:item", "name": f"{MOD}:{name}", "weight": w}
            if hi > 1:
                e["functions"] = [{"function": "minecraft:set_count", "count": {"min": lo, "max": hi}}]
            pool_entries.append(e)
        wjson(os.path.join(DATA, "loot_tables/chests", tier + ".json"),
              {"type": "minecraft:chest", "pools": [{"rolls": {"min": 3, "max": 6} if tier != "endgame" else {"min": 4, "max": 7}, "entries": pool_entries}]})


def gen_recipes():
    R = os.path.join(DATA, "recipes")
    def shaped(name, pattern, key, result, count=1):
        wjson(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
              "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": f"{MOD}:{result}", "count": count}})
    def shapeless(name, ings, result, count=1):
        wjson(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
              "ingredients": [{"item": i} for i in ings], "result": {"item": f"{MOD}:{result}", "count": count}})
    shaped("broken_radio", [" I ", "ICI", "IRI"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, "broken_radio")
    shapeless("research_notebook", ["minecraft:book", "minecraft:ink_sac"], "research_notebook")
    shaped("frequency_tuner", [" C ", "RIR", " C "], {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "I": "minecraft:iron_nugget"}, "frequency_tuner")
    shaped("signal_detector", [" R ", "ICI", "IRI"], {"R": "minecraft:redstone", "I": "minecraft:iron_ingot", "C": "minecraft:compass"}, "signal_detector")
    shapeless("salt_pouch", ["minecraft:bone_meal", "minecraft:bone_meal", "minecraft:string"], "salt_pouch")
    shapeless("protective_charm", [f"{MOD}:salt_pouch", "minecraft:gold_nugget", f"{MOD}:static_dust"], "protective_charm")
    shapeless("emergency_flare", ["minecraft:gunpowder", "minecraft:red_dye", "minecraft:paper"], "emergency_flare", 2)
    shapeless("power_cell", ["minecraft:redstone", "minecraft:copper_ingot", "minecraft:iron_ingot", f"{MOD}:signal_fragment"], "power_cell")
    shapeless("void_lens", ["minecraft:glass_pane", f"{MOD}:signal_fragment", f"{MOD}:memory_shard"], "void_lens")
    shaped("signal_blade", [" F ", " F ", " S "], {"F": f"{MOD}:signal_fragment", "S": "minecraft:stick"}, "signal_blade")
    shaped("distorted_compass", [" I ", "IFI", " I "], {"I": "minecraft:iron_ingot", "F": f"{MOD}:signal_fragment"}, "distorted_compass")
    shaped("copper_wire_spool", ["CCC", "CSC", "CCC"], {"C": "minecraft:copper_ingot", "S": "minecraft:stick"}, "copper_wire_spool")


def gen_particles_and_icon():
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 5, 5], fill=(200, 200, 210, 200))
    d.point((3, 3), fill=(255, 255, 255, 255))
    p = os.path.join(ASSETS, "textures/particle/static.png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    wjson(os.path.join(ASSETS, "particles/static.json"), {"textures": [f"{MOD}:static"]})
    # logo / pack icon
    rng = random.Random(7)
    logo = Image.new("RGBA", (128, 128), (10, 10, 14, 255))
    d = ImageDraw.Draw(logo)
    for y in range(0, 128, 2):
        d.line([(0, y), (127, y)], fill=(18 + rng.randint(0, 10),) * 3 + (255,))
    for r in (14, 26, 38, 50):
        d.arc([64 - r, 64 - r, 64 + r, 64 + r], -60, 60, fill=(0x7a, 0xff, 0xc0, 255), width=3)
    d.ellipse([58, 58, 70, 70], fill=(0xff, 0x3f, 0xa4, 255))
    logo.save(os.path.join(RES, "logo.png"))
    logo.save(os.path.join(RES, "pack.png"))


# ----------------------------------------------------------------------------
# SOUNDS (procedural -> WAV -> OGG via ffmpeg)
# ----------------------------------------------------------------------------
def synth(kind, secs, sr=22050):
    import numpy as np
    n = int(secs * sr)
    t = np.linspace(0, secs, n, endpoint=False)
    rng = np.random.default_rng(abs(hash(kind)) % (2 ** 32))
    noise = rng.uniform(-1, 1, n)
    env_fade = np.minimum(1, np.minimum(t / 0.05, (secs - t) / 0.1))

    def lp(x, k):
        return np.convolve(x, np.ones(k) / k, mode="same")

    if kind == "static":
        s = noise * 0.35 * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t))
    elif kind == "whisper":
        s = lp(noise, 6) * 1.4 * (np.sin(np.pi * t / secs) ** 2) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t))
    elif kind == "step":
        s = lp(noise, 40) * np.exp(-t * 18) * 1.5
    elif kind == "knock":
        s = np.sin(2 * np.pi * 140 * t) * np.exp(-t * 25) * (1 + (t > 0.35) * 0)
        s += np.roll(s, int(0.3 * sr)) * 0.8
    elif kind == "rumble":
        s = lp(noise, 300) * 6 + np.sin(2 * np.pi * 38 * t) * 0.5
        s *= np.sin(np.pi * t / secs) ** 0.7
    elif kind == "call":
        f = 380 + 120 * np.sin(2 * np.pi * 0.7 * t)
        s = np.sin(2 * np.pi * np.cumsum(f) / sr) * 0.4 * np.sin(np.pi * t / secs) ** 2
        s += lp(noise, 8) * 0.1
    elif kind == "tune":
        f = 200 + 1200 * (t / secs) ** 2
        s = np.sin(2 * np.pi * np.cumsum(f) / sr) * 0.25 + noise * 0.15
    elif kind == "blip":
        s = np.sin(2 * np.pi * 880 * t) * np.exp(-t * 9) * 0.5
    elif kind == "heart":
        def thump(t0):
            tt = np.clip(t - t0, 0, None)
            return np.sin(2 * np.pi * 55 * tt) * np.exp(-tt * 14) * (t >= t0)
        s = thump(0.0) + 0.7 * thump(0.28)
    elif kind == "breath":
        s = lp(noise, 10) * 1.2 * (0.5 - 0.5 * np.cos(2 * np.pi * t / secs * 2))
    elif kind == "cry":
        f = 700 - 400 * (t / secs) + 60 * np.sin(2 * np.pi * 9 * t)
        s = np.sign(np.sin(2 * np.pi * np.cumsum(f) / sr)) * 0.2 + lp(noise, 4) * 0.2
        s *= np.sin(np.pi * t / secs) ** 0.5
    elif kind == "bell":
        s = (np.sin(2 * np.pi * 523 * t) + 0.5 * np.sin(2 * np.pi * 1318 * t) + 0.3 * np.sin(2 * np.pi * 1760 * t)) * np.exp(-t * 1.6) * 0.3
    elif kind == "glitch":
        s = np.where(np.sin(2 * np.pi * 30 * t) > 0, noise, np.sin(2 * np.pi * 220 * t)) * 0.4 * np.exp(-t * 2)
    elif kind == "drone":
        s = (np.sin(2 * np.pi * 55 * t) * 0.3 + np.sin(2 * np.pi * 82.5 * t + 0.3) * 0.2 + np.sin(2 * np.pi * 110.7 * t) * 0.15)
        s *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.25 * t)
    elif kind == "tension":
        f = 90 + 40 * (t / secs)
        s = np.sin(2 * np.pi * np.cumsum(f) / sr) * 0.3 + np.sin(2 * np.pi * np.cumsum(f * 1.06) / sr) * 0.25
        s *= 0.4 + 0.6 * (t / secs)
    elif kind == "roar":
        s = lp(noise, 5) * 2 + np.sin(2 * np.pi * np.cumsum(70 + 30 * np.sin(2 * np.pi * 5 * t)) / sr) * 0.5
        s *= np.sin(np.pi * t / secs) ** 0.4
    elif kind == "creak":
        f = 160 + 90 * np.sin(2 * np.pi * 2.2 * t) * (t / secs)
        s = np.sign(np.sin(2 * np.pi * np.cumsum(f) / sr)) * 0.12 + lp(noise, 20) * 0.5
        s *= np.sin(np.pi * t / secs) ** 0.8
    else:
        s = noise * 0.1
    s = s * env_fade
    m = np.max(np.abs(s)) or 1
    s = s / m * 0.8
    return (s * 32767).astype(np.int16), sr


def gen_sounds():
    outdir = os.path.join(ASSETS, "sounds")
    os.makedirs(outdir, exist_ok=True)
    have_ffmpeg = subprocess.call(["ffmpeg", "-version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0
    if not have_ffmpeg:
        print("WARNING: ffmpeg missing - OGG sounds NOT generated.")
        return False
    for name, secs, kind in SOUNDS:
        data, sr = synth(kind, secs)
        wav = os.path.join(outdir, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(sr)
            w.writeframes(data.tobytes())
        ogg = os.path.join(outdir, name + ".ogg")
        r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", ogg])
        os.remove(wav)
        if r.returncode != 0:
            print("WARNING: failed to encode", name)
            return False
    sj = {n: {"sounds": [f"{MOD}:{n}"], "subtitle": f"subtitles.{MOD}.{n}"} for n, _, _ in SOUNDS}
    wjson(os.path.join(ASSETS, "sounds.json"), sj)
    return True


# ----------------------------------------------------------------------------
# LANG, DIMENSIONS, JAVA TABLES
# ----------------------------------------------------------------------------
DIMS = [
 # id, name, generator ("overworld"|"nether"|"islands"|"flat"), sky, fog, downfall, vegetation features
 ("quiet_fields", "The Quiet Fields", "overworld", 0x9aa7b3, 0xb8b8b0, 0.6, ["minecraft:trees_plains", "minecraft:patch_grass_plain"]),
 ("long_hall", "The Long Hall", "flat", 0x1a1814, 0x2a2418, 0.0, []),
 ("archive_below", "The Archive Below", "flat", 0x0e1418, 0x16222a, 0.0, []),
 ("red_silence", "The Red Silence", "nether", 0x7a1414, 0x4a0a0a, 0.0, []),
 ("memory_woods", "The Memory Woods", "overworld", 0x6a7a8a, 0x8a9aa0, 0.8, ["minecraft:dark_forest_vegetation", "minecraft:patch_grass_forest"]),
 ("signal_void", "The Signal Void", "islands", 0x12061a, 0x1a0a24, 0.0, []),
]

SETTINGS = {"overworld": "minecraft:overworld", "nether": "minecraft:nether", "islands": "minecraft:floating_islands"}


def gen_dimensions():
    for did, name, gen, sky, fog, down, veg in DIMS:
        features = [[] for _ in range(11)]
        features[9] = veg
        biome = {
            "has_precipitation": did in ("quiet_fields", "memory_woods"),
            "temperature": 0.5, "downfall": down,
            "effects": {"sky_color": sky, "fog_color": fog, "water_color": 0x3a4a52, "water_fog_color": 0x1a2428,
                        "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}},
            "spawners": {"monster": [], "creature": [], "ambient": [], "axolotls": [], "underground_water_creature": [],
                         "water_creature": [], "water_ambient": [], "misc": []},
            "spawn_costs": {}, "carvers": {}, "features": features,
        }
        wjson(os.path.join(DATA, "worldgen/biome", did + ".json"), biome)
        natural = did in ("quiet_fields", "memory_woods")
        dt = {
            "ultrawarm": False, "natural": natural,
            "coordinate_scale": 1.0, "has_skylight": natural,
            "has_ceiling": did in ("long_hall", "archive_below", "red_silence"),
            "ambient_light": 0.05 if did == "quiet_fields" else 0.0,
            "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False,
            "has_raids": False, "logical_height": 384, "min_y": -64, "height": 384,
            "infiniburn": "#minecraft:infiniburn_overworld",
            "effects": "minecraft:the_end" if did == "signal_void" else ("minecraft:the_nether" if did == "red_silence" else "minecraft:overworld"),
            "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        }
        ft = {"quiet_fields": 13000, "memory_woods": 18000, "red_silence": 15000, "signal_void": 6000}.get(did)
        if ft is not None:
            dt["fixed_time"] = ft
        wjson(os.path.join(DATA, "dimension_type", did + ".json"), dt)
        if gen == "flat":
            generator = {"type": "minecraft:flat", "settings": {
                "biome": f"{MOD}:{did}", "features": False, "lakes": False, "structure_overrides": [],
                "layers": [{"block": "minecraft:bedrock", "height": 1}]}}
        else:
            generator = {"type": "minecraft:noise", "settings": SETTINGS[gen],
                         "biome_source": {"type": "minecraft:fixed", "biome": f"{MOD}:{did}"}}
        wjson(os.path.join(DATA, "dimension", did + ".json"), {"type": f"{MOD}:{did}", "generator": generator})


def gen_lang():
    lang = {
        "itemGroup.lastsignal": "The Last Signal",
        "lastsignal.msg.nothing": "Only static.",
        "lastsignal.msg.recorded": "Recording saved to your notebook.",
        "lastsignal.msg.locked": "It does not open.",
    }
    for e in ENT:
        wjson(os.path.join(ASSETS, "models/item", e["id"] + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        lang[f"entity.{MOD}.{e['id']}"] = e["name"]
        lang[f"item.{MOD}.{e['id']}_spawn_egg"] = e["name"] + " Spawn Egg"
    for iid, name, kind, tip in ITEMS:
        lang[f"item.{MOD}.{iid}"] = name
        lang[f"item.{MOD}.{iid}.tooltip"] = tip
    for bid, name, *_ in BLOCKS:
        lang[f"block.{MOD}.{bid}"] = name
    for did, name, *_ in DIMS:
        lang[f"lastsignal.dimension.{did}"] = name
    for n, _, _ in SOUNDS:
        lang[f"subtitles.{MOD}.{n}"] = n.replace("_", " ").capitalize()
    wjson(os.path.join(ASSETS, "lang/en_us.json"), lang)


def gen_java_tables():
    # EntityProfiles
    L = []
    L.append("package com.lastsignal.entity;\n")
    L.append("// GENERATED by tools/generate_assets.py - do not edit by hand.")
    L.append("public final class EntityProfiles {")
    L.append("    public record Profile(String id, String name, int[] head, int[] body, int[] arm, int[] leg, int[] extra,")
    L.append("                          boolean quad, float hbW, float hbH, int[] uvHead, int[] uvBody, int[] uvArm, int[] uvLeg, int[] uvExtra,")
    L.append("                          int base, int accent) {}")
    L.append("    public static final Profile[] ALL = new Profile[] {")
    for e in ENT:
        def arr(t):
            return "new int[]{" + ",".join(str(x) for x in t) + "}"
        ex = arr(e["extra"]) if e["extra"] else "null"
        uvex = arr(e["uv"]["extra"]) if e["extra"] else "null"
        L.append(f'        new Profile("{e["id"]}", "{e["name"]}", {arr(e["head"])}, {arr(e["body"])}, {arr(e["arm"])}, {arr(e["leg"])}, {ex},')
        L.append(f'            {"true" if e["quad"] else "false"}, {e["hb"][0]}f, {e["hb"][1]}f, {arr(e["uv"]["head"])}, {arr(e["uv"]["body"])}, {arr(e["uv"]["arm"])}, {arr(e["uv"]["leg"])}, {uvex},')
        L.append(f'            0x{e["base"]:06X}, 0x{e["accent"]:06X}),')
    L.append("    };")
    L.append("    public static Profile get(String id) { for (Profile p : ALL) if (p.id().equals(id)) return p; throw new IllegalArgumentException(id); }")
    L.append("    private EntityProfiles() {}")
    L.append("}")
    wtext(os.path.join(JAVA, "entity/EntityProfiles.java"), "\n".join(L) + "\n")

    # GeneratedContent
    G = ["package com.lastsignal;", "", "// GENERATED by tools/generate_assets.py - do not edit by hand.",
         "public final class GeneratedContent {"]
    G.append("    public static final String[] ITEMS = {" + ", ".join(f'"{i[0]}"' for i in ITEMS) + "};")
    G.append("    /** id, light level, special behaviour tag */")
    G.append("    public static final String[][] BLOCKS = {")
    for b in BLOCKS:
        G.append(f'        {{"{b[0]}", "{b[2]}", "{b[3]}"}},')
    G.append("    };")
    G.append("    public static final String[] DIMENSIONS = {" + ", ".join(f'"{d[0]}"' for d in DIMS) + "};")
    G.append("    private GeneratedContent() {}")
    G.append("}")
    wtext(os.path.join(JAVA, "GeneratedContent.java"), "\n".join(G) + "\n")

    # ModSounds
    S = ["package com.lastsignal.sound;", "",
         "import com.lastsignal.LastSignalMod;",
         "import net.minecraft.resources.ResourceLocation;",
         "import net.minecraft.sounds.SoundEvent;",
         "import net.minecraftforge.registries.DeferredRegister;",
         "import net.minecraftforge.registries.ForgeRegistries;",
         "import net.minecraftforge.registries.RegistryObject;", "",
         "// GENERATED by tools/generate_assets.py - do not edit by hand.",
         "public final class ModSounds {",
         "    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LastSignalMod.MODID);"]
    for n, _, _ in SOUNDS:
        S.append(f'    public static final RegistryObject<SoundEvent> {n.upper()} = SOUNDS.register("{n}", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LastSignalMod.MODID, "{n}")));')
    S.append("    private ModSounds() {}")
    S.append("}")
    wtext(os.path.join(JAVA, "sound/ModSounds.java"), "\n".join(S) + "\n")


def main():
    layout()
    gen_entity_textures()
    gen_items_blocks_assets()
    gen_particles_and_icon()
    gen_loot()
    gen_recipes()
    ok = gen_sounds()
    gen_dimensions()
    gen_lang()
    gen_java_tables()
    print("entities:", len(ENT), "items:", len(ITEMS), "blocks:", len(BLOCKS), "sounds:", len(SOUNDS), "ogg_ok:", ok)


if __name__ == "__main__":
    main()
