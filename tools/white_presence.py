#!/usr/bin/env python3
"""The White Presence (peaceful Sky creature): GeckoLib geo model, animations, an all-white texture and original synthesized sounds.
Run from the repo root:  python3 tools/white_presence.py     (idempotent; overwrites its own outputs only)"""
import json, os, subprocess, wave, sys
import numpy as np

RES = "src/main/resources/assets/lastsignal"
ID = "white_presence"

# ------------------------------------------------------------------------------------------------ model
# bone: (parent, pivot, [(origin, size)])   - pixel units, y up, feet at y=0. Box UV (ints only).
BONES = {
    "root": (None, (0, 0, 0), []),
    "body": ("root", (0, 13, 0), [((-3.5, 13, -2), (7, 11, 4))]),
    "skirt": ("body", (0, 13, 0), [((-4.5, 7, -3), (9, 6, 6))]),
    "head": ("body", (0, 24, 0), [((-3.5, 24, -3.5), (7, 8, 7))]),
    "halo": ("head", (0, 34, 0), [((-5, 34, -5), (10, 1, 1)), ((-5, 34, 4), (10, 1, 1)), ((-5, 34, -4), (1, 1, 8)), ((4, 34, -4), (1, 1, 8))]),
    "arm_l": ("body", (4.5, 23, 0), [((4.5, 11, -1.5), (2, 12, 3))]),
    "arm_r": ("body", (-4.5, 23, 0), [((-6.5, 11, -1.5), (2, 12, 3))]),
    "leg_l": ("root", (1.8, 13, 0), [((0.3, 0, -1.5), (3, 13, 3))]),
    "leg_r": ("root", (-1.8, 13, 0), [((-3.3, 0, -1.5), (3, 13, 3))]),
    "wing_l": ("body", (3, 22, 2.5), [((3, 14, 2), (11, 8, 1))]),
    "wing_l_tip": ("wing_l", (14, 18, 2.5), [((14, 12, 2), (9, 10, 1)), ((14, 9, 2), (7, 3, 1))]),
    "wing_r": ("body", (-3, 22, 2.5), [((-14, 14, 2), (11, 8, 1))]),
    "wing_r_tip": ("wing_r", (-14, 18, 2.5), [((-23, 12, 2), (9, 10, 1)), ((-21, 9, 2), (7, 3, 1))]),
    "tail": ("body", (0, 12, 2.5), [((-1.5, 3, 2), (3, 10, 1))]),
}
TW = TH = 128


def pack():
    """row packer: returns {(bone, idx): (u, v)} and the box size of each cube."""
    pos, x, y, rowh = {}, 0, 0, 0
    for b, (_, _, cubes) in BONES.items():
        for i, (_, (sx, sy, sz)) in enumerate(cubes):
            w, h = 2 * (sx + sz), sy + sz
            if x + w > TW:
                x, y, rowh = 0, y + rowh, 0
            pos[(b, i)] = (x, y)
            x += w
            rowh = max(rowh, h)
    assert y + rowh <= TH, "texture atlas overflow"
    return pos


def write_geo():
    pos = pack()
    bones = []
    for name, (parent, pivot, cubes) in BONES.items():
        d = {"name": name, "pivot": list(pivot)}
        if parent:
            d["parent"] = parent
        if cubes:
            d["cubes"] = [{"origin": list(o), "size": list(s), "uv": list(pos[(name, i)])} for i, (o, s) in enumerate(cubes)]
        bones.append(d)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.lastsignal." + ID, "texture_width": TW, "texture_height": TH,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}
    os.makedirs(RES + "/geo", exist_ok=True)
    json.dump(geo, open(RES + "/geo/%s.geo.json" % ID, "w"), indent=1)


def write_texture():
    from PIL import Image
    rng = np.random.default_rng(5150)
    img = np.zeros((TH, TW, 4), dtype=np.uint8)
    pos = pack()

    def paint(x0, y0, w, h, base):
        for yy in range(h):
            for xx in range(w):
                v = base - int(10 * yy / max(1, h)) + int(rng.integers(-2, 3))      # soft shading only, never darker than light grey
                v = max(212, min(255, v))
                img[y0 + yy, x0 + xx] = (v, v, v, 255)               # R=G=B: a pure white/grey ramp, no coloured detail anywhere
    for (b, i), (u, v) in pos.items():
        sx, sy, sz = BONES[b][2][i][1]
        paint(u + sz, v, sx, sz, 255)                       # top
        paint(u + sz + sx, v, sx, sz, 226)                  # bottom
        paint(u, v + sz, sz, sy, 240)                       # side
        paint(u + sz, v + sz, sx, sy, 250)                  # front
        paint(u + sz + sx, v + sz, sz, sy, 238)             # side
        paint(u + 2 * sz + sx, v + sz, sx, sy, 232)         # back
    os.makedirs(RES + "/textures/entity", exist_ok=True)
    Image.fromarray(img, "RGBA").save(RES + "/textures/entity/%s.png" % ID)


# ------------------------------------------------------------------------------------------------ animations
def kf(pts):
    return {("%g" % t): list(v) for t, v in pts}


def anim(length, loop, bones):
    out = {}
    for b, chans in bones.items():
        out[b] = {c: kf(p) for c, p in chans.items()}
    return {"loop": loop, "animation_length": length, "bones": out}


FOLD = 75


def wings(zl, zr_sign=-1, tip=None):
    """zl: [(t, z)] for the left wing; the right wing mirrors it (opposite Z sign). tip: [(t, z)] for the tips."""
    d = {"wing_l": {"rotation": [(t, (0, 0, z)) for t, z in zl]}, "wing_r": {"rotation": [(t, (0, 0, zr_sign * z)) for t, z in zl]}}
    if tip:
        d["wing_l_tip"] = {"rotation": [(t, (0, 0, z)) for t, z in tip]}
        d["wing_r_tip"] = {"rotation": [(t, (0, 0, zr_sign * z)) for t, z in tip]}
    return d


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def write_anim():
    A = {}
    A["idle"] = anim(6, True, merge(
        {"body": {"position": [(0, (0, 0, 0)), (3, (0, .3, 0)), (6, (0, 0, 0))], "rotation": [(0, (0, 0, 0)), (3, (1, 0, 0)), (6, (0, 0, 0))]},
         "head": {"rotation": [(0, (0, 0, 0)), (1.5, (1.5, 6, 0)), (3, (0, 0, 0)), (4.5, (1.5, -6, 0)), (6, (0, 0, 0))]},
         "halo": {"rotation": [(0, (0, 0, 0)), (3, (0, 180, 0)), (6, (0, 360, 0))], "position": [(0, (0, 0, 0)), (3, (0, .6, 0)), (6, (0, 0, 0))]},
         "arm_l": {"rotation": [(0, (3, 0, 2)), (3, (-3, 0, 2)), (6, (3, 0, 2))]}, "arm_r": {"rotation": [(0, (-3, 0, -2)), (3, (3, 0, -2)), (6, (-3, 0, -2))]},
         "tail": {"rotation": [(0, (5, 0, 0)), (3, (-5, 0, 0)), (6, (5, 0, 0))]}},
        wings([(0, -FOLD), (3, -FOLD + 4), (6, -FOLD)], tip=[(0, -8), (3, -3), (6, -8)])))
    A["walk"] = anim(1.0, True, merge(
        {"leg_l": {"rotation": [(0, (0, 0, 0)), (.25, (25, 0, 0)), (.5, (0, 0, 0)), (.75, (-25, 0, 0)), (1, (0, 0, 0))]},
         "leg_r": {"rotation": [(0, (0, 0, 0)), (.25, (-25, 0, 0)), (.5, (0, 0, 0)), (.75, (25, 0, 0)), (1, (0, 0, 0))]},
         "arm_l": {"rotation": [(0, (0, 0, 2)), (.25, (-15, 0, 2)), (.5, (0, 0, 2)), (.75, (15, 0, 2)), (1, (0, 0, 2))]},
         "arm_r": {"rotation": [(0, (0, 0, -2)), (.25, (15, 0, -2)), (.5, (0, 0, -2)), (.75, (-15, 0, -2)), (1, (0, 0, -2))]},
         "body": {"position": [(0, (0, 0, 0)), (.25, (0, -.5, 0)), (.5, (0, 0, 0)), (.75, (0, -.5, 0)), (1, (0, 0, 0))], "rotation": [(0, (0, 2, 0)), (.5, (0, -2, 0)), (1, (0, 2, 0))]},
         "tail": {"rotation": [(0, (10, 0, 0)), (.5, (-8, 0, 0)), (1, (10, 0, 0))]},
         "halo": {"position": [(0, (0, 0, 0)), (.5, (0, .5, 0)), (1, (0, 0, 0))], "rotation": [(0, (0, 0, 0)), (1, (0, 120, 0))]}},
        wings([(0, -FOLD), (.5, -FOLD + 5), (1, -FOLD)], tip=[(0, -8), (.5, -2), (1, -8)])))
    A["turn"] = anim(0.8, True, merge(
        {"head": {"rotation": [(0, (0, 0, 0)), (.3, (0, 25, 0)), (.8, (0, 0, 0))]}, "body": {"rotation": [(0, (0, 0, 0)), (.3, (0, -8, 0)), (.8, (0, 0, 0))]},
         "leg_l": {"rotation": [(0, (0, 0, 0)), (.4, (10, 0, 0)), (.8, (0, 0, 0))]}, "leg_r": {"rotation": [(0, (0, 0, 0)), (.4, (-10, 0, 0)), (.8, (0, 0, 0))]}},
        wings([(0, -FOLD), (.3, -FOLD + 14), (.8, -FOLD)], tip=[(0, -8), (.3, 6), (.8, -8)])))
    A["takeoff"] = anim(1.0, False, merge(
        {"body": {"position": [(0, (0, 0, 0)), (.25, (0, -2, 0)), (.5, (0, 0, 0)), (1, (0, 1.5, 0))], "rotation": [(0, (0, 0, 0)), (.25, (12, 0, 0)), (1, (8, 0, 0))]},
         "leg_l": {"rotation": [(0, (0, 0, 0)), (.25, (22, 0, 0)), (.6, (-10, 0, 0)), (1, (-18, 0, 0))]}, "leg_r": {"rotation": [(0, (0, 0, 0)), (.25, (22, 0, 0)), (.6, (-10, 0, 0)), (1, (-18, 0, 0))]},
         "arm_l": {"rotation": [(0, (0, 0, 2)), (.25, (-30, 0, 2)), (1, (20, 0, 2))]}, "arm_r": {"rotation": [(0, (0, 0, -2)), (.25, (-30, 0, -2)), (1, (20, 0, -2))]},
         "tail": {"rotation": [(0, (5, 0, 0)), (1, (22, 0, 0))]}},
        wings([(0, -FOLD), (.2, -FOLD - 8), (.45, 40), (.7, -40), (1, 30)], tip=[(0, -8), (.3, 10), (.55, -30), (.8, 25), (1, 15)])))
    A["fly"] = anim(0.8, True, merge(
        {"body": {"position": [(0, (0, 0, 0)), (.2, (0, .8, 0)), (.6, (0, -.5, 0)), (.8, (0, 0, 0))], "rotation": [(0, (10, 0, 0)), (.8, (10, 0, 0))]},
         "leg_l": {"rotation": [(0, (-20, 0, 0)), (.4, (-24, 0, 0)), (.8, (-20, 0, 0))]}, "leg_r": {"rotation": [(0, (-22, 0, 0)), (.4, (-18, 0, 0)), (.8, (-22, 0, 0))]},
         "arm_l": {"rotation": [(0, (25, 0, 8)), (.4, (30, 0, 8)), (.8, (25, 0, 8))]}, "arm_r": {"rotation": [(0, (25, 0, -8)), (.4, (30, 0, -8)), (.8, (25, 0, -8))]},
         "tail": {"rotation": [(0, (25, 0, 0)), (.4, (18, 0, 0)), (.8, (25, 0, 0))]},
         "halo": {"rotation": [(0, (0, 0, 0)), (.8, (0, 90, 0))]}},
        wings([(0, 30), (.4, -35), (.8, 30)], tip=[(0, 15), (.2, 35), (.6, -30), (.8, 15)])))
    A["hover"] = anim(1.6, True, merge(
        {"body": {"position": [(0, (0, 0, 0)), (.8, (0, 1, 0)), (1.6, (0, 0, 0))], "rotation": [(0, (0, 0, 0)), (1.6, (0, 0, 0))]},
         "leg_l": {"rotation": [(0, (5, 0, 0)), (.8, (-4, 0, 0)), (1.6, (5, 0, 0))]}, "leg_r": {"rotation": [(0, (-4, 0, 0)), (.8, (5, 0, 0)), (1.6, (-4, 0, 0))]},
         "arm_l": {"rotation": [(0, (10, 0, 10)), (.8, (4, 0, 10)), (1.6, (10, 0, 10))]}, "arm_r": {"rotation": [(0, (10, 0, -10)), (.8, (4, 0, -10)), (1.6, (10, 0, -10))]},
         "head": {"rotation": [(0, (0, 0, 0)), (.8, (-2, 4, 0)), (1.6, (0, 0, 0))]},
         "halo": {"rotation": [(0, (0, 0, 0)), (1.6, (0, 180, 0))], "position": [(0, (0, 0, 0)), (.8, (0, .8, 0)), (1.6, (0, 0, 0))]},
         "tail": {"rotation": [(0, (8, 0, 0)), (.8, (-4, 0, 0)), (1.6, (8, 0, 0))]}},
        wings([(0, 20), (.8, -22), (1.6, 20)], tip=[(0, 10), (.4, 24), (1.2, -18), (1.6, 10)])))
    A["land"] = anim(0.8, False, merge(
        {"body": {"position": [(0, (0, 0, 0)), (.5, (0, -1, 0)), (.8, (0, 0, 0))], "rotation": [(0, (6, 0, 0)), (.8, (0, 0, 0))]},
         "leg_l": {"rotation": [(0, (-20, 0, 0)), (.4, (25, 0, 0)), (.8, (0, 0, 0))]}, "leg_r": {"rotation": [(0, (-20, 0, 0)), (.4, (25, 0, 0)), (.8, (0, 0, 0))]},
         "arm_l": {"rotation": [(0, (20, 0, 8)), (.4, (-10, 0, 8)), (.8, (0, 0, 2))]}, "arm_r": {"rotation": [(0, (20, 0, -8)), (.4, (-10, 0, -8)), (.8, (0, 0, -2))]},
         "tail": {"rotation": [(0, (22, 0, 0)), (.8, (5, 0, 0))]}},
        wings([(0, 30), (.2, -20), (.4, 30), (.6, -50), (.8, -FOLD)], tip=[(0, 15), (.3, 20), (.6, 0), (.8, -8)])))
    A["death"] = anim(1.2, False, merge(
        {"body": {"rotation": [(0, (0, 0, 0)), (1.2, (-85, 0, 0))], "position": [(0, (0, 0, 0)), (1.2, (0, -9, 4))]},
         "head": {"rotation": [(0, (0, 0, 0)), (1.2, (25, 0, 0))]},
         "halo": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 90, 0))], "position": [(0, (0, 0, 0)), (1.2, (0, -3, 0))]},
         "leg_l": {"rotation": [(0, (0, 0, 0)), (1.2, (10, 0, 0))]}, "leg_r": {"rotation": [(0, (0, 0, 0)), (1.2, (10, 0, 0))]}},
        wings([(0, 20), (.6, -10), (1.2, -FOLD + 10)], tip=[(0, 10), (1.2, -20)])))
    doc = {"format_version": "1.8.0", "animations": {"animation.%s.%s" % (ID, k): v for k, v in A.items()}}
    os.makedirs(RES + "/animations", exist_ok=True)
    json.dump(doc, open(RES + "/animations/%s.animation.json" % ID, "w"), indent=1)


# ------------------------------------------------------------------------------------------------ sounds
SR = 22050


def T(s):
    return np.arange(int(s * SR)) / SR


def bp(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(X, len(x))


def env_sin(n):
    return np.sin(np.linspace(0, np.pi, n)) ** 2


def save_ogg(name, x, peak=0.5):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    d = RES + "/sounds/entity/" + ID
    os.makedirs(d, exist_ok=True)
    wav = d + "/_tmp_%s.wav" % name
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", d + "/%s.ogg" % name], check=True)
    os.remove(wav)


def write_sounds():
    r = np.random.default_rng(777)
    for i, f in enumerate((392.0, 466.2, 523.3), 1):                       # ambient: a soft airy chord with a breath of noise
        t = T(2.8)
        e = env_sin(len(t))
        x = (np.sin(2 * np.pi * f * t + .3 * np.sin(2 * np.pi * 4.7 * t)) + .5 * np.sin(2 * np.pi * f * 1.5 * t) + .25 * np.sin(2 * np.pi * f * 2 * t + 1)) * e
        x += bp(r.standard_normal(len(t)), 900, 3500) * .12 * e
        save_ogg("ambient%d" % i, x, .3)
    t = T(.55)                                                              # hurt: a falling airy note
    x = np.sin(2 * np.pi * np.cumsum(620 - 300 * t / .55) / SR) * np.exp(-t * 5) + bp(r.standard_normal(len(t)), 800, 3000) * .2 * np.exp(-t * 8)
    save_ogg("hurt", x, .4)
    t = T(2.4)                                                              # death: three descending notes dissolving into breath
    x = sum(np.sin(2 * np.pi * np.cumsum(f0 * (1 - .45 * t / 2.4)) / SR) * a for f0, a in ((520, 1), (660, .6), (780, .4))) * np.exp(-t * 1.3)
    x += bp(r.standard_normal(len(t)), 600, 2500) * .15 * np.exp(-t * 1.5)
    save_ogg("death", x, .4)
    for i in (1, 2):                                                        # wings: a soft filtered-air flutter
        t = T(.5)
        n = bp(r.standard_normal(len(t)), 250, 1500)
        x = n * np.minimum(1, t / .08) * np.exp(-t * 6) * (.7 + .3 * np.sin(2 * np.pi * 14 * t))
        save_ogg("wings%d" % i, x, .3)
    t = T(.14)                                                              # footstep: a muffled tap
    save_ogg("step", bp(r.standard_normal(len(t)), 80, 450) * np.exp(-t * 45), .25)
    t = T(1.1)                                                              # takeoff: rising air
    x = np.zeros(len(t))
    for k, (lo, hi) in enumerate(((200, 600), (500, 1200), (1000, 2400), (1800, 3500))):
        n = bp(r.standard_normal(len(t)), lo, hi)
        e = np.exp(-((t - .2 - .22 * k) ** 2) / (2 * .12 ** 2))
        x += n * e
    save_ogg("takeoff", x, .35)


def write_json_and_lang():
    sj = RES + "/sounds.json"
    s = json.load(open(sj))
    groups = {"ambient": ["ambient1", "ambient2", "ambient3"], "hurt": ["hurt"], "death": ["death"], "wings": ["wings1", "wings2"], "step": ["step"], "takeoff": ["takeoff"]}
    for g, files in groups.items():
        s["entity.%s.%s" % (ID, g)] = {"sounds": ["lastsignal:entity/%s/%s" % (ID, f) for f in files], "subtitle": "subtitles.lastsignal.%s.%s" % (ID, g)}
    json.dump(s, open(sj, "w"), indent=2)
    for lang in ("en_us", "ar_sa"):
        p = RES + "/lang/%s.json" % lang
        if not os.path.exists(p):
            continue
        d = json.load(open(p))
        en = lang == "en_us"
        d["entity.lastsignal." + ID] = "The White Presence" if en else "الحضور الأبيض"
        d["item.lastsignal.%s_spawn_egg" % ID] = "The White Presence Spawn Egg" if en else "بيضة استدعاء الحضور الأبيض"
        names = {"ambient": ("A soft, airy note", "نغمة هوائية هادئة"), "hurt": ("The White Presence cries softly", "الحضور الأبيض يئن بهدوء"),
                 "death": ("The White Presence fades", "الحضور الأبيض يتلاشى"), "wings": ("Wings flutter", "رفرفة أجنحة"),
                 "step": ("Soft footsteps", "خطوات خفيفة"), "takeoff": ("Rising air", "هواء يتصاعد")}
        for g, (e_, a_) in names.items():
            d["subtitles.lastsignal.%s.%s" % (ID, g)] = e_ if en else a_
        json.dump(d, open(p, "w"), indent=2, ensure_ascii=False)
    m = RES + "/models/item/%s_spawn_egg.json" % ID
    json.dump({"parent": "minecraft:item/template_spawn_egg"}, open(m, "w"), indent=2)


if __name__ == "__main__":
    write_geo(); write_texture(); write_anim(); write_sounds(); write_json_and_lang()
    print("white_presence assets written")
