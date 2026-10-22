#!/usr/bin/env python3
"""
THE LAST SIGNAL - GeckoLib monster generator.

Builds, for each of the 17 creatures in generate_assets.ENT:
  assets/lastsignal/geo/<id>.geo.json                (jointed model, many bones, per-face UVs)
  assets/lastsignal/animations/<id>.animation.json   (idle/walk/run/watch/stalk/search/attack/special/retreat/vanish/death)
  assets/lastsignal/textures/entity/<id>.png         (128x192 palette atlas: skin/dark/trim/face/glow/bone regions)
  assets/lastsignal/textures/entity/<id>_glowmask.png (emissive layer for GeckoLib AutoGlowingGeoLayer)

Conventions (Java-model parity): +X rotation = bottoms swing backward / fronts tip down (nod, lean forward, knee bend);
negative X raises an arm forward. Model front is -Z. Bones named sway_* wobble, spinN_* orbit (N-fold symmetric).
"""
import json, math, os, random, sys, zlib
from types import SimpleNamespace
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_assets as GA
import monsters_orig as MO
from PIL import Image, ImageDraw

MOD = GA.MOD
GEO_DIR = os.path.join(GA.ASSETS, "geo")
ANIM_DIR = os.path.join(GA.ASSETS, "animations")
TEX_DIR = os.path.join(GA.ASSETS, "textures/entity")
TW, TH = 128, 192
REG = {"skin": (0, 0), "dark": (64, 0), "trim": (0, 64), "face": (64, 64), "glow": (0, 128), "bone": (64, 128)}


# ------------------------------------------------------------------------------------------------ rig
class Rig:
    def __init__(self, eid, sc=1.0):
        self.eid, self.sc = eid, sc
        self.bones, self.cubes, self.face = [], {}, None
        self.rng = random.Random(zlib.crc32(eid.encode()) & 0xFFFF)
        self.maxy = 0.0

    def bone(self, name, parent, pivot):
        s = self.sc
        b = {"name": name, "pivot": [round(v * s, 3) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        self.cubes[name] = []
        return b

    def _uv(self, mat, fw, fh, face=False):
        rx, ry = REG[mat]
        fw, fh = max(0.5, min(fw, 60.0)), max(0.5, min(fh, 60.0))
        if face:
            u = rx + int((64 - fw) / 2); v = ry + int((64 - fh) / 2)
            self.face = (u, v, max(4, int(round(fw))), max(4, int(round(fh))))
        else:
            u = rx + self.rng.randrange(0, max(1, int(64 - math.ceil(fw)) + 1))
            v = ry + self.rng.randrange(0, max(1, int(64 - math.ceil(fh)) + 1))
        return {"uv": [u, v], "uv_size": [round(fw, 3), round(fh, 3)]}

    def box(self, bone, x, y, z, w, h, d, mat="skin", rot=None, pivot=None, face=False):
        s = self.sc
        uv = {"north": self._uv(mat, w, h, face), "south": self._uv(mat, w, h),
              "east": self._uv(mat, d, h), "west": self._uv(mat, d, h),
              "up": self._uv(mat, w, d), "down": self._uv(mat, w, d)}
        c = {"origin": [round(x * s, 3), round(y * s, 3), round(z * s, 3)],
             "size": [round(w * s, 3), round(h * s, 3), round(d * s, 3)], "uv": uv}
        if rot:
            c["rotation"] = list(rot)
            c["pivot"] = [round(v * s, 3) for v in (pivot or (x + w / 2, y + h / 2, z + d / 2))]
        self.cubes[bone].append(c)
        self.maxy = max(self.maxy, y + h)

    def geo(self, hbW, hbH):
        out = []
        for b in self.bones:
            bb = dict(b)
            if self.cubes[b["name"]]:
                bb["cubes"] = self.cubes[b["name"]]
            out.append(bb)
        big = max(hbW, hbH / 1.5) * 2 + 2
        return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
            "identifier": f"geometry.{MOD}.{self.eid}", "texture_width": TW, "texture_height": TH,
            "visible_bounds_width": round(big, 1), "visible_bounds_height": round(hbH * 1.6 + 1, 1),
            "visible_bounds_offset": [0, round(hbH / 2, 2), 0]}, "bones": out}]}


def limb(R, pfx_up, pfx_lo, pfx_end, parent, x, y, z, w, length, up=0.5, endh=1.5, claw=2.0, mat="skin", endmat="dark", nclaw=3, dz=0.0):
    """Two-segment limb hanging from pivot (x,y,z): upper bone, lower bone, foot/hand bone with claws."""
    ul = length * up
    R.bone(pfx_up, parent, (x, y, z))
    R.box(pfx_up, x - w / 2, y - ul, z - w / 2, w, ul, w, mat)
    ly = y - ul
    R.bone(pfx_lo, pfx_up, (x, ly, z))
    ll = max(0.8, length - ul - endh)
    R.box(pfx_lo, x - w * 0.42, ly - ll, z - w * 0.42, w * 0.84, ll, w * 0.84, mat)
    ey = ly - ll
    R.bone(pfx_end, pfx_lo, (x, ey, z))
    R.box(pfx_end, x - w / 2, ey - endh, z - w / 2 + dz * 0, w, endh, w, endmat)
    for i in range(nclaw):
        cx = x - w / 2 + (i + 0.5) * w / nclaw
        R.box(pfx_end, cx - 0.25, ey - endh - claw, z - w / 2 - 0.2, 0.5, claw, 0.5, "bone")


def head_parts(R, hy, hw, hh, hd, z0=0.0, crh=0.7, teeth=True, parent="neck", forward=False):
    """Head bone (cranium + face) and jaw bone. hy = bottom of head; forward: head extends toward -Z from z0."""
    cr = hh * crh
    R.bone("head", parent, (0, hy, z0))
    if forward:
        R.box("head", -hw / 2, hy + hh - cr, z0 - hd, hw, cr, hd, "skin", face=True)
        R.bone("jaw", "head", (0, hy + hh - cr, z0 - 0.5))
        R.box("jaw", -hw * 0.45, hy, z0 - hd + 0.3, hw * 0.9, hh - cr, hd - 0.8, "dark")
        zf = z0 - hd + 0.6
        wid = hw * 0.8
    else:
        R.box("head", -hw / 2, hy + hh - cr, z0 - hd / 2, hw, cr, hd, "skin", face=True)
        R.bone("jaw", "head", (0, hy + hh - cr, z0 + hd / 2 - 0.5))
        R.box("jaw", -hw * 0.45, hy, z0 - hd / 2 + 0.2, hw * 0.9, hh - cr, hd * 0.9, "dark")
        zf = z0 - hd / 2 + 0.5
        wid = hw * 0.8
    if teeth:
        n = max(3, int(wid / 1.4))
        for i in range(n):
            tx = -wid / 2 + (i + 0.5) * wid / n
            R.box("jaw", tx - 0.3, hy + (hh - cr), zf, 0.6, 0.9, 0.5, "bone")
    return cr


# ------------------------------------------------------------------------------------------------ biped
def biped(R, E, X):
    hw, hh, hd = E["head"]; bw, bh, bd = E["body"]; aw, al = E["arm"]; lw, ll = E["leg"]
    R.bone("root", None, (0, 0, 0))
    hip = ll
    lx = max(lw / 2 + 0.3, bw / 4)
    for side, sg in (("r", -1), ("l", 1)):
        x = sg * lx
        th = ll * 0.5
        R.bone("leg_" + side, "root", (x, hip, 0))
        R.box("leg_" + side, x - lw / 2, hip - th, -lw / 2, lw, th, lw, "dark" if X.get("darklegs") else "skin")
        R.bone("shin_" + side, "leg_" + side, (x, hip - th, 0))
        R.box("shin_" + side, x - lw * 0.42, 1.5, -lw * 0.42, lw * 0.84, max(0.5, hip - th - 1.5), lw * 0.84, "dark" if X.get("darklegs") else "skin")
        R.bone("foot_" + side, "shin_" + side, (x, 1.5, 0))
        fz = X.get("foot", 2)
        R.box("foot_" + side, x - lw * 0.45, 0, -lw * 0.45 - fz, lw * 0.9, 1.5, lw * 0.9 + fz, "dark")
    R.bone("body", "root", (0, hip, 0))
    pb = bh * 0.4
    R.box("body", -bw * 0.46, hip, -bd * 0.475, bw * 0.92, pb, bd * 0.95, "dark" if X.get("darkpelvis") else "skin")
    R.bone("chest", "body", (0, hip + pb, 0))
    R.box("chest", -bw / 2, hip + pb, -bd / 2, bw, bh - pb, bd, "skin")
    sh = hip + bh
    sx = bw / 2 + aw / 2 + X.get("armgap", 0.3)
    wy = sh - 1 - al
    for side, sg in (("r", -1), ("l", 1)):
        x = sg * sx
        if X.get("stump_l") and side == "l":
            R.bone("arm_l", "chest", (x, sh - 1, 0))
            R.box("arm_l", x - aw / 2, sh - 1 - al * 0.4, -aw / 2, aw, al * 0.4, aw, "skin")
            R.bone("forearm_l", "arm_l", (x, sh - 1 - al * 0.4, 0))
            R.bone("hand_l", "forearm_l", (x, sh - 1 - al * 0.4, 0))
            R.bone("sway_thread", "forearm_l", (x, sh - 1 - al * 0.4, 0))
            R.box("sway_thread", x - 0.3, sh - 1 - al * 0.4 - 9, -0.3, 0.6, 9, 0.6, "glow")
            continue
        limb(R, "arm_" + side, "forearm_" + side, "hand_" + side, "chest", x, sh - 1, 0, aw, al - 0, up=0.5, endh=2,
             claw=X.get("claw", 3), nclaw=X.get("nclaw", 3))
    nh = X.get("neck", 1.5)
    R.bone("neck", "chest", (0, sh, 0))
    R.box("neck", -1, sh, -1, 2, nh, 2, "dark")
    hy = sh + nh
    if X.get("halfhead"):
        R.bone("head", "neck", (0, hy, 0))
        cr = hh * 0.7
        R.box("head", 0, hy + hh - cr, -hd / 2, hw / 2, cr, hd, "skin", face=True)
        R.box("head", -0.4, hy + hh - cr, -hd / 2, 0.4, cr, hd, "dark")
        for (x0, y0, z0, w0, h0, d0) in ((-hw / 2, hy + hh - cr, -hd / 2, hw / 2, 0.5, 0.5), (-hw / 2, hy + hh - 0.5, -hd / 2, hw / 2, 0.5, 0.5),
                                         (-hw / 2, hy + hh - cr, -hd / 2, 0.5, cr, 0.5), (-hw / 2, hy + hh - cr, hd / 2 - 0.5, hw / 2, 0.5, 0.5)):
            R.box("head", x0, y0, z0, w0, h0, d0, "glow")
        R.bone("jaw", "head", (0, hy + hh - cr, hd / 2 - 0.5))
        R.box("jaw", 0, hy, -hd / 2 + 0.2, hw * 0.45, hh - cr, hd * 0.9, "dark")
        cr_ = cr
    else:
        cr_ = head_parts(R, hy, hw, hh, hd, 0.0, 0.7, X.get("teeth", True))
    c = dict(hip=hip, sh=sh, hy=hy, hw=hw, hh=hh, hd=hd, bw=bw, bh=bh, bd=bd, aw=aw, al=al, lw=lw, ll=ll, sx=sx, lx=lx, wy=wy, pb=pb)
    if X.get("deco"):
        X["deco"](R, SimpleNamespace(**c))


# ------------------------------------------------------------------------------------------------ quad
def quad(R, E, X):
    hw, hh, hd = E["head"]; bw, bh, bd = E["body"]; aw, al = E["arm"]; lw, ll = E["leg"]
    ls = X.get("ls", 1.0)
    al *= ls; ll *= ls
    top = max(al, ll)
    by = max(1.0, top - bh * 0.7)
    cy = by + bh / 2
    R.bone("root", None, (0, 0, 0))
    R.bone("body", "root", (0, cy, 0))
    R.box("body", -bw / 2, by, 0, bw, bh, bd / 2, "skin")
    R.bone("chest", "body", (0, cy, 0))
    R.box("chest", -bw / 2, by, -bd / 2, bw, bh, bd / 2, "skin")
    py = by + bh * 0.7
    gx = bw / 2 + aw / 2 - 0.5
    for side, sg in (("r", -1), ("l", 1)):
        limb(R, "arm_" + side, "forearm_" + side, "hand_" + side, "chest", sg * gx, py, -bd / 2 + aw, aw, al, endh=1.2, claw=X.get("claw", 2.5))
        limb(R, "leg_" + side, "shin_" + side, "foot_" + side, "body", sg * gx, py, bd / 2 - lw, lw, ll, endh=1.2, claw=X.get("claw", 2.5))
    z0 = -bd / 2 - 2.5
    R.bone("neck", "chest", (0, by + bh * 0.6, -bd / 2))
    R.box("neck", -hw * 0.3, by + bh * 0.25, z0, hw * 0.6, bh * 0.6, 2.8, "dark")
    yc = by + bh * 0.5
    head_parts(R, yc - hh / 2, hw, hh, hd, z0, 0.62, True, parent="neck", forward=True)
    # tail chain
    n = X.get("tail", 0)
    ty = by + bh / 2
    z = bd / 2
    par = "body"
    for i in range(n):
        s = max(0.8, bw * 0.3 * (1 - i / (n + 1)))
        nm = f"sway_tail{i + 1}"
        R.bone(nm, par, (0, ty, z))
        R.box(nm, -s / 2, ty - s / 2, z, s, s, 4.0, "dark" if i % 2 else "skin")
        par, z = nm, z + 4.0
    c = dict(by=by, bh=bh, bw=bw, bd=bd, hw=hw, hh=hh, hd=hd, z0=z0, yc=yc, py=py, gx=gx, top=top)
    if X.get("deco"):
        X["deco"](R, SimpleNamespace(**c))


# ------------------------------------------------------------------------------------------------ spider (lantern widow)
def spider(R, E, X):
    hw, hh, hd = E["head"]; bw, bh, bd = E["body"]
    R.bone("root", None, (0, 0, 0))
    by = 15.0
    R.bone("body", "root", (0, by, 0))
    R.box("body", -3, by - 2.5, -4, 6, 5, 8, "dark")          # cephalothorax
    R.box("body", -2, by + 2.5, -3, 4, 1, 5, "trim")
    # legs
    for i, z in enumerate((-3.2, -1.1, 1.0, 3.1)):
        for side, sg in (("r", -1), ("l", 1)):
            fx = sg * 3
            R.bone(f"L{i}_{side}", "body", (fx, by, z))
            R.box(f"L{i}_{side}", fx if sg > 0 else fx - 7, by - 0.5, z - 0.5, 7, 1, 1, "dark")
            kx = sg * 10
            R.bone(f"T{i}_{side}", f"L{i}_{side}", (kx, by, z))
            R.box(f"T{i}_{side}", kx - 0.5, by - 8, z - 0.5, 1, 8.5, 1, "dark")
            R.box(f"T{i}_{side}", kx - 0.5 + sg * 0.5 - 0.5, 0, z - 0.5, 1, by - 8, 1, "skin")
            R.box(f"T{i}_{side}", kx + sg * 0.5 - 1.0, 0, z - 0.5, 2, 0.8, 1, "bone")
    # head
    R.bone("neck", "body", (0, by, -4))
    z0 = -4
    R.bone("head", "neck", (0, by - 2.5, z0))
    R.box("head", -hw / 2, by - 2.5, z0 - hd, hw, hh, hd, "skin", face=True)
    R.bone("jaw", "head", (0, by - 2.5, z0 - 0.5))
    R.box("jaw", -1.2, by - 4.2, z0 - hd + 0.5, 0.8, 2.2, 0.8, "bone")
    R.box("jaw", 0.4, by - 4.2, z0 - hd + 0.5, 0.8, 2.2, 0.8, "bone")
    # abdomen
    R.bone("abdomen", "body", (0, by, 3))
    R.box("abdomen", -bw / 2, by - 3.5, 3, bw, bh, bd, "skin")
    R.box("abdomen", -bw / 2 - 0.3, by - 1, 6, bw + 0.6, 1.2, 4, "trim")
    R.box("abdomen", -1, by + 0.5, 3 + bd - 0.2, 2, 2, 0.4, "glow")
    # lure stalk + lantern
    R.bone("sway_stalk", "head", (0, by + 2.5, z0 - 1))
    R.box("sway_stalk", -0.4, by + 2.5, z0 - 1.5, 0.8, 9, 0.8, "dark")
    R.box("sway_stalk", -0.4, by + 10.7, z0 - 8.5, 0.8, 0.8, 7.8, "dark")
    R.bone("sway_lantern", "sway_stalk", (0, by + 10.7, z0 - 8.5))
    R.box("sway_lantern", -0.2, by + 6.5, z0 - 8.2, 0.4, 4.4, 0.4, "dark")
    R.box("sway_lantern", -2, by + 1.5, z0 - 10.2, 4, 5, 4, "glow")
    for (x, z) in ((-2.2, -10.4), (1.7, -10.4), (-2.2, -6.5), (1.7, -6.5)):
        R.box("sway_lantern", x, by + 1.2, z, 0.5, 5.6, 0.5, "dark")
    R.box("sway_lantern", -2.3, by + 6.5, z0 - 10.5, 4.6, 0.6, 4.6, "trim")
    R.box("sway_lantern", -2.3, by + 1.0, z0 - 10.5, 4.6, 0.6, 4.6, "trim")


# ------------------------------------------------------------------------------------------------ mimic (chest creature)
def mimic(R, E, X):
    R.bone("root", None, (0, 0, 0))
    R.bone("body", "root", (0, 3, 0))
    R.box("body", -5, 3, -4, 10, 5, 8, "skin")
    for (x, z) in ((-5.2, -4.2), (3.7, -4.2), (-5.2, 2.7), (3.7, 2.7)):
        R.box("body", x, 2.8, z, 1.5, 5.4, 1.5, "trim")
    R.box("body", -1.5, 4.5, -4.35, 3, 3, 0.4, "trim")
    R.box("body", -0.4, 5.3, -4.5, 0.8, 1.4, 0.2, "glow")
    for i in range(8):
        x = -4.2 + i * 1.18
        R.box("body", x, 8, -3.7, 0.8, 1.7, 0.7, "bone")
        R.box("body", x, 8, 3.0, 0.8, 1.7, 0.7, "bone")
    for i in range(4):
        z = -2.5 + i * 1.6
        R.box("body", -4.5, 8, z, 0.7, 1.7, 0.8, "bone")
        R.box("body", 3.8, 8, z, 0.7, 1.7, 0.8, "bone")
    R.box("body", -4.2, 7.7, -3.2, 8.4, 0.5, 6.4, "dark")           # throat
    R.bone("head", "body", (0, 8, 4))                                 # the lid
    R.box("head", -5, 8, -4, 10, 3, 8, "skin")
    R.box("head", -5.3, 10.6, -4.3, 10.6, 0.8, 8.6, "trim")
    R.box("head", -3.6, 8.8, -4.3, 2.2, 1.6, 0.4, "glow")
    R.box("head", 1.4, 8.8, -4.3, 2.2, 1.6, 0.4, "glow")
    R.box("head", -3.2, 9.1, -4.45, 0.9, 1.0, 0.2, "dark")
    R.box("head", 1.8, 9.1, -4.45, 0.9, 1.0, 0.2, "dark")
    for i in range(8):
        x = -4.2 + i * 1.18
        R.box("head", x, 6.6, -3.7, 0.8, 1.5, 0.7, "bone")
        R.box("head", x, 6.6, 3.0, 0.8, 1.5, 0.7, "bone")
    R.bone("jaw", "body", (0, 8, -3))
    R.bone("sway_tongue", "body", (0, 7.8, -3))
    R.box("sway_tongue", -1, 7.5, -10, 2, 0.8, 7, "dark")
    R.box("sway_tongue", -0.5, 7.5, -12, 1, 0.8, 2.2, "trim")
    for side, sg in (("r", -1), ("l", 1)):
        limb(R, "arm_" + side, "forearm_" + side, "hand_" + side, "body", sg * 4.4, 3, -2.8, 3, 3, endh=0.8, claw=1.5)
        limb(R, "leg_" + side, "shin_" + side, "foot_" + side, "body", sg * 4.4, 3, 2.8, 3, 3, endh=0.8, claw=1.5)


# ------------------------------------------------------------------------------------------------ per-creature decorations
def d_observer(R, c):
    # floating halo (4-fold symmetric -> spin4 loops seamlessly)
    R.bone("spin4_halo", "head", (0, c.hy + c.hh + 3.5, 0))
    r = c.hw * 0.95
    y = c.hy + c.hh + 3.2
    R.box("spin4_halo", -r, y, -r, 2 * r, 0.5, 0.5, "glow")
    R.box("spin4_halo", -r, y, r - 0.5, 2 * r, 0.5, 0.5, "glow")
    R.box("spin4_halo", -r, y, -r, 0.5, 0.5, 2 * r, "glow")
    R.box("spin4_halo", r - 0.5, y, -r, 0.5, 0.5, 2 * r, "glow")
    for i in range(6):
        R.box("chest", -0.5, c.hip + c.bh * (0.1 + 0.15 * i), c.bd / 2, 1, 1.4, 1.8, "bone")
    R.box("chest", -c.bw / 2 - 0.3, c.hip + c.bh * 0.55, -c.bd / 2 - 0.3, c.bw + 0.6, 0.8, c.bd + 0.6, "dark")   # ribbon
    R.box("neck", -1.6, c.sh + 0.2, -1.6, 3.2, 0.8, 3.2, "bone")


def d_hollow(R, c):
    R.box("chest", -c.bw * 0.32, c.hip + c.pb * 0.9, -c.bd / 2 - 0.15, c.bw * 0.64, c.bh * 0.5, 0.4, "dark")
    R.box("chest", -0.5, c.hip + c.bh * 0.55, -c.bd / 2 - 0.35, 1, 1, 0.2, "glow")
    R.box("chest", -0.5, c.hip + c.bh * 0.35, -c.bd / 2 - 0.35, 1, 0.6, 0.2, "glow")
    for i in range(3):
        R.box("chest", c.bw / 2 - 0.2, c.hip + c.bh * (0.3 + 0.17 * i), -1, 2.4, 0.6, 0.6, "bone")
        R.box("chest", -c.bw / 2 - 2.2, c.hip + c.bh * (0.3 + 0.17 * i), 0.4, 2.4, 0.6, 0.6, "bone")
    R.box("chest", -c.bw * 0.45, c.hip + c.bh * 0.55, c.bd / 2 - 0.2, c.bw * 0.9, c.bh * 0.4, 2.6, "dark")     # hunched back
    R.box("chest", -c.bw * 0.3, c.hip + c.bh * 0.85, c.bd / 2 + 1.5, c.bw * 0.6, 1.5, 1.6, "bone")
    R.box("hand_r", -c.sx - 0.3, c.wy - 2, -0.3, 0.6, 5, 0.6, "bone")                                          # overlong finger
    R.bone("sway_rags", "body", (0, c.hip + 1, 0))
    R.box("sway_rags", -c.bw / 2, c.hip - 5, -c.bd / 2 - 0.2, c.bw, 5, 0.6, "dark")
    R.box("sway_rags", -c.bw / 2, c.hip - 4, c.bd / 2 - 0.4, c.bw * 0.7, 4, 0.6, "dark")


def d_signalman(R, c):
    R.bone("sway_antenna", "chest", (0, c.sh - 2, c.bd / 2 + 1))
    R.box("sway_antenna", -0.4, c.sh - 2, c.bd / 2 + 0.6, 0.8, 19, 0.8, "dark")
    R.box("sway_antenna", -3, c.sh + 7, c.bd / 2 + 0.7, 6, 0.6, 0.6, "dark")
    R.box("sway_antenna", -2, c.sh + 12, c.bd / 2 + 0.7, 4, 0.6, 0.6, "dark")
    R.box("sway_antenna", -0.8, c.sh + 17, c.bd / 2 + 0.4, 1.6, 1.6, 1.6, "glow")
    R.box("chest", -c.bw / 2 + 0.5, c.hip + c.bh * 0.3, -c.bd / 2 - 2.6, c.bw - 1, 4.2, 2.6, "dark")
    R.box("chest", -c.bw / 2 + 1.2, c.hip + c.bh * 0.3 + 2.2, -c.bd / 2 - 2.8, 2.2, 1.4, 0.3, "glow")
    R.box("chest", 0.6, c.hip + c.bh * 0.3 + 2.4, -c.bd / 2 - 2.8, 0.8, 0.8, 0.3, "trim")
    R.box("chest", 0.6, c.hip + c.bh * 0.3 + 0.8, -c.bd / 2 - 2.8, 0.8, 0.8, 0.3, "trim")
    R.box("head", -c.hw / 2 - 0.5, c.hy + c.hh * 0.7, -c.hd / 2 - 0.5, c.hw + 1, 2.6, c.hd + 1.4, "dark")      # hood
    R.box("head", -c.hw / 2 - 0.5, c.hy + c.hh * 0.2, c.hd / 2 + 0.2, c.hw + 1, c.hh * 0.6, 1.2, "dark")
    R.bone("sway_cable", "chest", (c.bw / 2, c.hip + c.bh * 0.5, -c.bd / 2))
    R.box("sway_cable", c.bw / 2, c.hip + c.bh * 0.5 - 7, -c.bd / 2 - 1, 0.6, 7, 0.6, "dark")


def d_witness(R, c):
    for i in range(5):
        x = -c.hw / 2 + 1 + i * (c.hw - 2) / 4
        nm = f"sway_tendril{i}"
        R.bone(nm, "head", (x, c.hy + 1, c.hd / 2 - 2))
        R.box(nm, x - 0.5, c.hy + 1 - 8 - (i % 2) * 2, c.hd / 2 - 2.5, 1, 8 + (i % 2) * 2, 1, "skin" if i % 2 else "dark")
    R.box("head", -c.hw / 2 - 0.4, c.hy + c.hh - 1, -c.hd / 2 - 0.4, c.hw + 0.8, 1.2, c.hd + 0.8, "dark")        # brow ridge
    R.box("chest", -c.bw / 2 - 0.4, c.hip + c.bh * 0.55, -c.bd / 2 - 0.4, c.bw + 0.8, 1.2, c.bd + 0.8, "dark")


def d_lost_child(R, c):
    R.box("body", -c.bw / 2 - 0.9, c.hip - 1.5, -c.bd / 2 - 0.9, c.bw + 1.8, c.bh * 0.65 + 1.5, c.bd + 1.8, "trim")  # nightshirt
    R.box("head", -c.hw / 2 - 0.4, c.hy + c.hh - 1.2, -c.hd / 2 - 0.4, c.hw + 0.8, 1.6, c.hd + 0.8, "dark")        # hair
    R.box("head", -c.hw / 2 - 0.4, c.hy + 1, c.hd / 2 - 0.4, c.hw + 0.8, c.hh - 1.5, 1.0, "dark")
    R.bone("sway_string", "hand_r", (-c.sx, c.wy - 2, 0))
    R.box("sway_string", -c.sx - 0.2, c.wy - 9, -0.2, 0.4, 7, 0.4, "bone")
    R.box("sway_string", -c.sx - 1.2, c.wy - 12, -1.2, 2.4, 3, 2.4, "glow")      # small lantern/bell it drags


def d_doorman(R, c):
    R.box("chest", -c.bw / 2 - 1, c.hip - 2, c.bd / 2, c.bw + 2, c.bh + 8, 2.4, "skin")      # the door on his back
    for y in (0.2, 0.55, 0.85):
        R.box("chest", -c.bw / 2 - 1.3, c.hip + (c.bh + 6) * y, c.bd / 2 + 0.2, c.bw + 2.6, 1.2, 2.8, "trim")
    R.box("chest", -c.bw / 2 + 1, c.hip + 3, -c.bd / 2 - 1.2, c.bw - 2, c.bh - 6, 1.2, "bone")
    R.box("chest", -0.9, c.hip + c.bh * 0.5, -c.bd / 2 - 1.5, 1.8, 2.8, 0.4, "glow")      # keyhole
    for sg in (-1, 1):
        R.box("chest", sg * (c.bw / 2 + 0.2) - 2.5, c.sh - 3, -c.bd / 2 - 0.8, 5, 4, c.bd + 1.6, "trim")       # shoulder plates
    R.box("head", -c.hw / 2 - 0.5, c.hy + c.hh - 1.5, -c.hd / 2 - 0.5, c.hw + 1, 1.6, c.hd + 1, "trim")       # frame
    R.bone("sway_keys", "body", (c.bw / 2, c.hip + 2, 0))
    for i in range(3):
        R.box("sway_keys", c.bw / 2 + 0.2 + i * 0.9, c.hip - 5 - i, -0.4, 0.7, 5 + i, 0.8, "glow")


def d_pale_herd(R, c):
    for sg, nm in ((-1, "r"), (1, "l")):
        R.bone("sway_antler_" + nm, "head", (sg * c.hw * 0.3, c.hy + c.hh, 0))
        R.box("sway_antler_" + nm, sg * c.hw * 0.3 - 0.4, c.hy + c.hh, -0.4, 0.8, 5, 0.8, "bone")
        R.box("sway_antler_" + nm, sg * c.hw * 0.3 - 0.4 + sg * 0.8, c.hy + c.hh + 3.5, -0.4, 0.8, 4, 0.8, "bone")
        R.box("sway_antler_" + nm, sg * c.hw * 0.3 - 0.4 + sg * 1.6, c.hy + c.hh + 6.5, -0.4, 0.8, 3, 0.8, "bone")
        R.box("sway_antler_" + nm, sg * c.hw * 0.3 - 0.4, c.hy + c.hh + 3, 0.6, 0.7, 3.5, 0.7, "bone")
    for i in range(7):
        R.box("chest", -c.bw / 2 + 0.3, c.hip + c.bh * (0.15 + 0.1 * i), -c.bd / 2 - 0.2, c.bw - 0.6, 0.35, 0.3, "dark")
    R.bone("sway_hair", "head", (0, c.hy + c.hh - 1, c.hd / 2))
    R.box("sway_hair", -c.hw / 2, c.hy - 9, c.hd / 2 - 0.2, c.hw, 10, 0.6, "dark")


def d_echo(R, c):
    R.bone("spin4_frag", "chest", (0, c.hip + c.bh * 0.5, 0))
    for i in range(4):
        a = i * math.pi / 2
        x, z = math.cos(a) * 9, math.sin(a) * 9
        R.box("spin4_frag", x - 0.8, c.hip + c.bh * 0.5 + (i % 2) * 3 - 1, z - 0.8, 1.6, 2.2, 1.6, "glow")
    R.box("head", -c.hw / 2 - 0.6, c.hy + 1, -c.hd / 2 - 0.6, 0.6, c.hh - 2, c.hd + 1.2, "glow")     # offset ghost edge
    R.box("chest", c.bw / 2, c.hip + c.pb, -c.bd / 2 - 0.4, 0.6, c.bh - c.pb, c.bd + 0.8, "glow")


def d_facet(R, c):
    R.bone("spin6_shards", "root", (0, c.hip + c.bh * 0.5, 0))
    for i in range(6):
        a = i * math.pi / 3
        x, z = math.cos(a) * 10, math.sin(a) * 10
        h = 6 + (i % 3) * 2
        R.box("spin6_shards", x - 0.9, c.hip + c.bh * 0.5 - h / 2 + (i % 2) * 2, z - 0.9, 1.8, h, 1.8, "bone" if i % 2 else "glow")
    for i in range(5):
        R.box("head", -c.hw / 2 + 0.5 + i * (c.hw - 1.5) / 4, c.hy + c.hh, -0.5, 1, 2 + (i % 3) * 1.5, 1, "bone")
    for (x, y, h) in ((-c.bw / 2 - 1.3, 0.4, 5), (c.bw / 2 + 0.3, 0.55, 4), (-c.bw / 2 - 0.9, 0.7, 3.5), (c.bw / 2 + 0.2, 0.2, 3)):
        R.box("chest", x, c.hip + c.bh * y, -1, 1.2, h, 1.2, "bone")
    for sg, s in ((-1, "r"), (1, "l")):
        R.box("forearm_" + s, sg * c.sx - 0.3, c.sh - 1 - c.al * 0.75, -c.aw / 2 - 0.2, 0.6, c.al * 0.35, 0.6, "glow")


def d_archivist(R, c):
    R.box("body", -(c.bw + 3) / 2, 1, -(c.bd + 3) / 2, c.bw + 3, c.hip + c.bh * 0.5 - 1, c.bd + 3, "dark")   # long robe hides legs
    R.box("chest", -(c.bw + 1.5) / 2, c.hip + c.bh * 0.5, -(c.bd + 1.5) / 2, c.bw + 1.5, c.bh * 0.5, c.bd + 1.5, "trim")
    R.box("head", -c.hw / 2 - 0.8, c.hy + c.hh * 0.4, -c.hd / 2 - 0.8, c.hw + 1.6, c.hh * 0.7, c.hd + 1.8, "dark")   # deep hood
    R.box("chest", -3.2, c.hip + c.bh * 0.35, -c.bd / 2 - 3.8, 6.4, 5, 3.6, "trim")                              # tome
    R.box("chest", -3.0, c.hip + c.bh * 0.35 + 0.4, -c.bd / 2 - 4.0, 0.5, 4.2, 3.4, "dark")
    R.box("chest", -2.4, c.hip + c.bh * 0.35 + 0.2, -c.bd / 2 - 4.1, 5.2, 0.5, 3.4, "glow")
    R.bone("sway_chain", "body", (c.bw / 2, c.hip + 3, 0))
    R.box("sway_chain", c.bw / 2 + 1, c.hip - 4, -0.4, 0.6, 7, 0.6, "bone")
    R.box("sway_chain", c.bw / 2 + 0.5, c.hip - 6.5, -0.9, 1.6, 2.2, 1.6, "glow")
    R.box("chest", -c.bw / 2 - 0.3, c.hip + c.bh * 0.5, c.bd / 2 + 0.2, c.bw + 0.6, c.bh * 0.5, 4.2, "skin")    # satchel/book on back


def d_signal_core(R, c):
    R.bone("spin4_ring1", "chest", (0, c.hip + c.bh * 0.5, 0))
    r = 19
    y = c.hip + c.bh * 0.5
    for (x, z, w, d) in ((-r, -r, 2 * r, 1.4), (-r, r - 1.4, 2 * r, 1.4), (-r, -r, 1.4, 2 * r), (r - 1.4, -r, 1.4, 2 * r)):
        R.box("spin4_ring1", x, y, z, w, 1.4, d, "glow")
        R.box("spin4_ring1", x, y + 1.4, z, w, 0.8, d, "trim")
    R.bone("spin3_pylons", "root", (0, 0, 0))
    for i in range(3):
        a = i * 2 * math.pi / 3
        x, z = math.cos(a) * 26, math.sin(a) * 26
        R.box("spin3_pylons", x - 2, 6, z - 2, 4, 26, 4, "dark")
        R.box("spin3_pylons", x - 2.4, 30, z - 2.4, 4.8, 5, 4.8, "glow")
        R.box("spin3_pylons", x - 1, 3, z - 1, 2, 4, 2, "trim")
    for i in range(5):
        R.box("chest", -c.bw / 2 + 2 + i * 3.4, c.hip + c.bh * 0.3, -c.bd / 2 - 0.4, 1.2, c.bh * 0.5, 0.5, "glow")
    R.box("chest", -c.bw / 2 - 2, c.hip + c.bh * 0.6, -c.bd / 2 - 2, c.bw + 4, 3, c.bd + 4, "trim")
    R.bone("sway_dish", "chest", (0, c.sh - 2, c.bd / 2 + 2))
    R.box("sway_dish", -6, c.sh - 2, c.bd / 2 + 2, 12, 9, 1.5, "bone")
    R.box("sway_dish", -0.8, c.sh + 2, c.bd / 2 - 1, 1.6, 1.6, 4, "glow")
    for i in range(6):
        a = i * 2 * math.pi / 6
        R.box("head", math.cos(a) * 9.4 - 0.8, c.hy + c.hh * 0.5 - 0.8, math.sin(a) * 9.4 - 0.8, 1.6, 1.6, 1.6, "glow")


def d_buried_king(R, c):
    R.box("head", -c.hw / 2 - 0.5, c.hy + c.hh - 0.5, -c.hd / 2 - 0.5, c.hw + 1, 2, c.hd + 1, "trim")      # crown band
    for i in range(6):
        a = i * math.pi / 3
        R.box("head", math.cos(a) * (c.hw / 2 - 0.5) - 0.9, c.hy + c.hh + 1.5, math.sin(a) * (c.hd / 2 - 0.5) - 0.9, 1.8, 3.5 + (i % 2) * 1.5, 1.8, "trim")
    R.box("head", -0.8, c.hy + c.hh + 1.5, -c.hd / 2 - 0.4, 1.6, 1.6, 0.4, "glow")
    R.bone("sway_cape", "chest", (0, c.sh - 1, c.bd / 2))
    R.box("sway_cape", -c.bw / 2 - 2.5, c.hip - 8, c.bd / 2 + 0.3, c.bw + 5, c.bh + 7, 1.2, "dark")
    R.box("sway_cape", -c.bw / 2 - 2.5, c.hip - 8, c.bd / 2 + 1.4, c.bw + 5, 2, 0.6, "trim")
    for i in range(5):
        R.box("chest", -c.bw / 2 + 1.5, c.hip + c.bh * (0.2 + 0.15 * i), -c.bd / 2 - 0.5, c.bw - 3, 0.8, 0.8, "bone")
    R.box("chest", -0.5, c.hip + c.bh * 0.2, -c.bd / 2 - 0.5, 1, c.bh * 0.65, 0.8, "bone")
    for sg in (-1, 1):
        R.box("chest", sg * (c.bw / 2 + 0.3) - 3.5, c.sh - 4, -c.bd / 2, 7, 5.5, c.bd, "trim")                    # pauldrons
        R.box("chest", sg * (c.bw / 2 + 0.3) - 1.2, c.sh + 0.8, -1, 2.4, 3, 2.4, "bone")
    R.box("hand_r", -c.sx - 0.7, c.wy - 34, -0.7, 1.4, 34, 1.4, "dark")                                           # scepter
    R.box("hand_r", -c.sx - 2, c.wy - 4, -2, 4, 4, 4, "glow")
    R.box("hand_r", -c.sx - 1, c.wy - 36, -1, 2, 2, 2, "trim")
    R.bone("sway_roots", "chest", (c.bw / 2, c.sh, 0))
    for i in range(4):
        R.box("sway_roots", -c.bw / 2 + i * (c.bw / 3), c.sh - 1 - (i % 2) * 4, c.bd / 2 - 0.5, 0.8, 8 + (i % 2) * 4, 0.8, "bone")


def d_unfinished(R, c):
    for i in range(4):
        R.box("chest", -c.bw / 2 + 0.5, c.hip + c.bh * (0.25 + 0.17 * i), -c.bd / 2 - 0.3, c.bw - 1, 0.7, 0.5, "bone")
    R.box("chest", -0.4, c.hip + c.bh * 0.2, -c.bd / 2 - 0.3, 0.8, c.bh * 0.65, 0.5, "bone")
    R.box("chest", c.bw / 2 - 0.1, c.hip + c.pb, -c.bd / 2, 0.2, c.bh - c.pb, c.bd, "glow")        # unfinished wireframe edge
    R.box("chest", c.bw / 2 - 0.1, c.hip + c.pb, -c.bd / 2, 0.2, 0.4, c.bd, "glow")
    R.bone("sway_float", "chest", (c.sx, c.sh - 5, 0))
    R.box("sway_float", c.sx - 1, c.sh - 14, -1, 2, 6, 2, "bone")
    R.box("sway_float", c.sx - 1.4, c.sh - 16, -1.4, 2.8, 2, 2.8, "dark")
    R.box("leg_l", c.lx - c.lw / 2 - 0.2, 0.0, -c.lw / 2 - 0.2, 0.2, c.ll * 0.4, c.lw + 0.4, "glow")


def d_crawler(R, c):
    for i in range(6):
        R.box("body", -0.5, c.by + c.bh, 0.6 + i * 1.3, 1, 1.2 + (i % 2), 0.9, "bone")
        R.box("chest", -0.5, c.by + c.bh, -0.6 - i * 1.3 - 0.9, 1, 1.2 + (i % 2), 0.9, "bone")
    R.box("chest", -c.bw / 2 - 0.2, c.by + 0.8, -c.bd / 4, c.bw + 0.4, 0.7, 0.7, "dark")
    R.box("body", -c.bw / 2 - 0.2, c.by + 1.6, c.bd / 4, c.bw + 0.4, 0.7, 0.7, "dark")


def d_burrower(R, c):
    for i, (w, h, l) in enumerate(((c.bw * 0.82, c.bh * 0.85, 7), (c.bw * 0.62, c.bh * 0.7, 6), (c.bw * 0.4, c.bh * 0.5, 5))):
        pass
    # segmented rear (body already occupies z 0..bd/2)
    z = c.bd / 2
    par = "body"
    for i, (w, h, l) in enumerate(((c.bw * 0.82, c.bh * 0.85, 7), (c.bw * 0.62, c.bh * 0.7, 6), (c.bw * 0.4, c.bh * 0.5, 5))):
        nm = f"sway_seg{i + 1}"
        R.bone(nm, par, (0, c.by + c.bh / 2, z))
        R.box(nm, -w / 2, c.by + c.bh / 2 - h / 2, z, w, h, l, "dark" if i % 2 else "skin")
        R.box(nm, -w / 2 - 0.4, c.by + c.bh / 2 + h / 2 - 0.4, z + 0.5, w + 0.8, 0.8, l - 1, "bone")
        par, z = nm, z + l
    for sg, nm in ((-1, "r"), (1, "l")):
        R.bone("mand_" + nm, "head", (sg * c.hw * 0.42, c.yc, c.z0 - c.hd + 1))
        R.box("mand_" + nm, sg * c.hw * 0.42 - 0.9, c.yc - 1.5, c.z0 - c.hd - 5, 1.8, 3, 6.5, "bone")
        R.box("mand_" + nm, sg * c.hw * 0.42 - 0.6 - sg * 1.2, c.yc - 1.2, c.z0 - c.hd - 8.5, 1.2, 2.4, 3.5, "bone")
    for i in range(6):
        R.box("chest", -c.bw / 2 + 1 + i * (c.bw - 2) / 5 - 0.5, c.by + c.bh, -c.bd / 2 + 2 + (i % 3) * 3, 1, 2.2 + (i % 2), 1, "bone")
    R.box("chest", -c.bw / 2 - 0.4, c.by + 2, -c.bd / 2, 0.8, c.bh - 3, c.bd / 2, "dark")
    R.box("chest", c.bw / 2 - 0.4, c.by + 2, -c.bd / 2, 0.8, c.bh - 3, c.bd / 2, "dark")


# ------------------------------------------------------------------------------------------------ stage 3 roster
def d_black_witness(R, c):
    d_witness(R, c)
    R.bone("sway_cloak", "chest", (0, c.sh, c.bd / 2))
    R.box("sway_cloak", -c.bw / 2 - 1, c.hip - 6, c.bd / 2, c.bw + 2, c.bh + 6, 0.8, "dark")
    for i in range(3):
        R.box("head", -c.hw / 2 - 0.6 + i * (c.hw / 2 + 0.6), c.hy + c.hh, -c.hd / 2 + 1, 1.2, 4 + i % 2 * 2, 1.2, "glow")   # pale lashes of light


def d_tall_black(R, c):
    for i in range(7):                                    # spine ridge, bent forward like something folded to fit under the sky
        R.box("chest", -0.6, c.hip + c.bh * (0.15 + 0.12 * i), c.bd / 2, 1.2, 2.2 + (i % 2), 1.6 + 0.2 * i, "dark")
    R.box("head", -c.hw / 2 + 0.4, c.hy - 4, -c.hd / 2 - 1.2, c.hw - 0.8, 4.5, 1.4, "dark")                           # stretched jaw
    R.bone("sway_rag", "chest", (0, c.sh - 2, c.bd / 2))
    R.box("sway_rag", -c.bw / 2, c.hip - 8, c.bd / 2 - 0.2, c.bw, c.bh * 0.6 + 8, 0.6, "dark")
    R.box("hand_r", -c.sx - 0.3, c.wy - 3, -0.3, 0.6, 7, 0.6, "bone")
    R.box("hand_l", c.sx - 0.3, c.wy - 3, -0.3, 0.6, 7, 0.6, "bone")


def d_borrowed_skin(R, c):
    R.bone("sway_flap_l", "arm_l", (c.sx, c.wy + c.al - 2, 0))
    R.box("sway_flap_l", c.sx - 1.5, c.wy - 4, -c.aw / 2 - 0.5, 3, c.al * 0.8, 0.6, "trim")
    R.bone("sway_flap_r", "arm_r", (-c.sx, c.wy + c.al - 2, 0))
    R.box("sway_flap_r", -c.sx - 1.5, c.wy - 4, -c.aw / 2 - 0.5, 3, c.al * 0.8, 0.6, "trim")
    R.box("chest", -0.3, c.hip + c.pb, -c.bd / 2 - 0.3, 0.6, c.bh - c.pb, 0.4, "dark")                                # a long seam down the front
    for i in range(5):
        R.box("chest", -1.4, c.hip + c.pb + i * 1.6, -c.bd / 2 - 0.35, 2.8, 0.3, 0.3, "dark")
    R.box("head", -c.hw / 2 - 0.3, c.hy + c.hh - 1.5, -c.hd / 2 - 0.3, c.hw + 0.6, 1.8, c.hd + 0.6, "dark")          # hair of someone else


def d_drowned_choir(R, c):
    for i in range(4):
        nm = f"sway_weed{i}"
        R.bone(nm, "head", (-c.hw / 2 + 1 + i * (c.hw - 2) / 3, c.hy + c.hh - 1, 0))
        R.box(nm, -c.hw / 2 + 0.5 + i * (c.hw - 2) / 3, c.hy - 10 - (i % 2) * 3, -0.5, 1, 10 + (i % 2) * 3 + c.hh, 1, "dark")
    R.box("chest", -c.bw / 2 - 0.4, c.hip + c.pb, -c.bd / 2 - 0.4, c.bw + 0.8, c.bh - c.pb, 0.5, "bone")           # ribs showing through
    R.box("chest", -c.bw / 2 - 0.4, c.hip + c.bh * 0.6, -c.bd / 2 - 0.4, c.bw + 0.8, 0.6, c.bd + 0.8, "glow")


def d_hanging_man(R, c):
    R.bone("sway_rope", "head", (0, c.hy + c.hh, 0))
    R.box("sway_rope", -0.5, c.hy + c.hh, -0.5, 1, 26, 1, "bone")                                                     # the rope goes up out of sight
    R.box("head", -c.hw / 2 - 0.5, c.hy + c.hh - 4, -c.hd / 2 - 0.5, c.hw + 1, 1, c.hd + 1, "bone")                  # noose
    R.box("neck", -1.6, c.sh, -1.6, 3.2, 2, 3.2, "bone")
    for sg in (-1, 1):
        R.box("leg_r" if sg < 0 else "leg_l", sg * c.lx - c.lw / 2 - 0.3, 0.0, -c.lw / 2 - 0.3, c.lw + 0.6, 2, c.lw + 0.6, "dark")


def d_salt_mother(R, c):
    R.box("body", -c.bw / 2 - 2, c.hip - 6, -c.bd / 2 - 2, c.bw + 4, c.pb + 6, c.bd + 4, "trim")                      # wide skirt of crust
    for i in range(6):
        R.box("chest", -c.bw / 2 - 1 + i * 2.4, c.sh - 1.5, -2 + (i % 3), 1.6, 3 + i % 3, 1.6, "bone")                # salt crystals along the shoulders
    R.box("head", -c.hw / 2 - 0.6, c.hy + c.hh - 1, -c.hd / 2 - 0.6, c.hw + 1.2, 2.5, c.hd + 1.2, "trim")            # veil
    R.bone("sway_veil", "head", (0, c.hy + c.hh, c.hd / 2))
    R.box("sway_veil", -c.hw / 2, c.hy - 6, c.hd / 2 - 0.2, c.hw, c.hh + 6, 0.5, "trim")


def d_radio_shepherd(R, c):
    R.box("hand_r", -c.sx - 0.4, c.wy - 4, -0.4, 0.8, 30, 0.8, "dark")                                                # a crook made of antenna
    R.box("hand_r", -c.sx - 3, c.wy + 24, -0.4, 4, 0.8, 0.8, "glow")
    R.box("chest", -c.bw / 2 - 0.6, c.hip + c.pb, c.bd / 2, c.bw + 1.2, c.bh - c.pb, 3.5, "dark")                     # radio pack
    R.box("chest", -c.bw / 2 + 0.6, c.hip + c.bh - 3, c.bd / 2 + 3.4, c.bw - 1.2, 1.2, 0.6, "glow")
    R.bone("sway_wire", "chest", (0, c.sh - 2, c.bd / 2 + 3))
    R.box("sway_wire", -0.3, c.sh - 14, c.bd / 2 + 3, 0.6, 12, 0.6, "glow")


def d_glass_walker(R, c):
    for i in range(8):
        R.box("chest", -c.bw / 2 + (i % 4) * (c.bw / 3.2), c.hip + c.bh * (0.25 + 0.1 * i), c.bd / 2, 1.2, 3 + i % 3, 1.2, "glow")
    for sg in (-1, 1):
        R.box("arm_r" if sg < 0 else "arm_l", sg * c.sx - 0.3, c.wy + c.al * 0.4, -c.aw / 2 - 2.5, 0.6, 2.2, 3, "glow")
    R.box("head", -c.hw / 2 - 0.2, c.hy + c.hh - 0.5, -0.5, 0.6, 5, 1.4, "glow")


def d_candle_bearer(R, c):
    R.box("hand_r", -c.sx - 1.2, c.wy - 4, -1.8, 2.4, 6, 2.4, "bone")                                                # a candle it carries before it
    R.box("hand_r", -c.sx - 0.2, c.wy + 2, -0.6, 0.4, 1.2, 0.4, "glow")
    R.bone("sway_wax", "chest", (0, c.sh - 2, -c.bd / 2))
    R.box("sway_wax", -c.bw / 2, c.hip - 2, -c.bd / 2 - 0.5, c.bw, c.bh + 2, 0.8, "bone")                             # dripped wax down the front
    R.box("head", -c.hw / 2 - 0.4, c.hy + c.hh, -c.hd / 2 - 0.4, c.hw + 0.8, 1.2, c.hd + 0.8, "bone")


def d_paper_man(R, c):
    R.box("chest", -c.bw / 2 - 0.2, c.hip + c.pb, -c.bd / 2 - 0.2, c.bw + 0.4, 0.4, c.bd + 0.4, "dark")               # fold lines
    R.box("chest", -c.bw / 2 - 0.2, c.hip + c.bh * 0.65, -c.bd / 2 - 0.2, c.bw + 0.4, 0.4, c.bd + 0.4, "dark")
    R.box("chest", -0.2, c.hip + c.pb, -c.bd / 2 - 0.3, 0.4, c.bh - c.pb, 0.4, "dark")
    R.bone("sway_edge", "chest", (c.bw / 2, c.sh - 2, 0))
    R.box("sway_edge", c.bw / 2, c.hip + 1, -0.8, 1.2, c.bh - 2, 1.6, "bone")                                        # a curling edge that never lies flat
    R.box("head", -c.hw / 2 - 0.2, c.hy + c.hh - 1.5, -c.hd / 2 - 0.3, c.hw + 0.4, 0.5, c.hd + 0.6, "dark")


def d_stair_thing(R, c):
    for i in range(8):
        R.box("body", -c.bw / 2 - 0.3, c.by + c.bh + i * 0.0, 0.4 + i * 1.6, c.bw + 0.6, 0.8 + (i % 4) * 0.8, 1.2, "bone")   # steps up its back
    R.box("chest", -c.bw / 2 - 0.2, c.by + 0.5, -c.bd / 4, c.bw + 0.4, 0.8, 0.8, "dark")
    R.box("body", -c.bw / 2 - 0.2, c.by + 1.4, c.bd / 4, c.bw + 0.4, 0.8, 0.8, "dark")


def d_moon_watcher(R, c):
    R.box("head", -c.hw / 2 - 1, c.hy - 0.5, -c.hd / 2 - 0.4, c.hw + 2, 1, c.hd + 0.8, "bone")                        # pale disc rim
    R.box("head", -c.hw / 2 - 0.4, c.hy + c.hh - 0.5, -c.hd / 2 - 0.4, c.hw + 0.8, 1, c.hd + 0.8, "bone")
    for i in range(3):
        R.box("head", -c.hw / 2 + 2 + i * 4, c.hy + 4 + (i % 2) * 5, -c.hd / 2 - 0.5, 2.6, 2.6, 0.5, "dark")        # craters
    R.bone("spin4_halo", "head", (0, c.hy + c.hh + 4, 0))
    r = c.hw * 0.55
    y = c.hy + c.hh + 3.5
    R.box("spin4_halo", -r, y, -r, 2 * r, 0.5, 0.5, "glow")
    R.box("spin4_halo", -r, y, r - 0.5, 2 * r, 0.5, 0.5, "glow")
    R.box("spin4_halo", -r, y, -r, 0.5, 0.5, 2 * r, "glow")
    R.box("spin4_halo", r - 0.5, y, -r, 0.5, 0.5, 2 * r, "glow")


# id -> (builder, params)
BUILD = {
    "observer": (biped, dict(claw=6, nclaw=3, neck=3.5, deco=d_observer, foot=2)),
    "hollow": (biped, dict(claw=3.5, darklegs=True, deco=d_hollow, neck=1.0, head_z=18)),
    "mimic": (mimic, dict(lid=True)),
    "signalman": (biped, dict(claw=2, deco=d_signalman)),
    "witness": (biped, dict(neck=1.0, deco=d_witness, claw=3)),
    "crawler": (quad, dict(ls=0.62, tail=3, deco=d_crawler, head_z=40, claw=3)),
    "lost_child": (biped, dict(claw=1.5, neck=1.0, deco=d_lost_child, foot=1.2)),
    "doorman": (biped, dict(deco=d_doorman, claw=2.5, armgap=1.5, foot=3, teeth=False)),
    "pale_herd": (biped, dict(claw=4, neck=3.5, deco=d_pale_herd, teeth=False)),
    "echo": (biped, dict(deco=d_echo, claw=2.5)),
    "burrower": (quad, dict(ls=1.0, tail=0, deco=d_burrower, claw=3)),
    "lantern_widow": (spider, dict()),
    "facet": (biped, dict(deco=d_facet, claw=5, nclaw=2)),
    "archivist": (biped, dict(deco=d_archivist, claw=2.5, neck=1.0, teeth=False)),
    "signal_core": (biped, dict(deco=d_signal_core, claw=6, armgap=2, foot=3)),
    "buried_king": (biped, dict(deco=d_buried_king, claw=3.5, armgap=1.5, foot=3)),
    "unfinished": (biped, dict(deco=d_unfinished, claw=3, halfhead=True, stump_l=True, head_z=12)),
    "black_witness": (biped, dict(neck=1.0, deco=d_black_witness, claw=3)),
    "tall_black": (biped, dict(neck=2.5, deco=d_tall_black, claw=7, nclaw=4, darklegs=True)),
    "borrowed_skin": (biped, dict(deco=d_borrowed_skin, claw=1.5, teeth=False)),
    "drowned_choir": (biped, dict(deco=d_drowned_choir, claw=2.5)),
    "hanging_man": (biped, dict(deco=d_hanging_man, claw=2, neck=2.5, teeth=False)),
    "salt_mother": (biped, dict(deco=d_salt_mother, claw=2.5, armgap=1.5, foot=3, teeth=False)),
    "radio_shepherd": (biped, dict(deco=d_radio_shepherd, claw=2)),
    "glass_walker": (biped, dict(deco=d_glass_walker, claw=5, nclaw=2)),
    "candle_bearer": (biped, dict(deco=d_candle_bearer, claw=2, teeth=False)),
    "paper_man": (biped, dict(deco=d_paper_man, claw=3, teeth=False, neck=1.0)),
    "stair_thing": (quad, dict(ls=0.8, tail=0, deco=d_stair_thing, head_z=30, claw=3)),
    "moon_watcher": (biped, dict(deco=d_moon_watcher, claw=3, neck=1.0, teeth=False)),
}


# Wonderland-inspired body language (from the wiki's textual descriptions: tall stickman builds, thin limbs, hunched posture, elongated heads, matte black bodies).
# (width, leg length, arm length, body height, head width, head height, hunch degrees). Original proportions otherwise; hitboxes are untouched.
# Measured from the reference geometry (the_entity, messenger, no_one_twisted, silhouette, woodseeker): a normal-sized HEAD (6-8 px, not stretched)
# on a 22-24 px torso, with legs and arms 1.5-2x longer than a normal biped (arms hang to the knees), limbs 3 px wide, little or no hunch except stalkers.
TALL = (0.95, 1.7, 1.9, 1.12, 0.95, 1.0, 8)       # long-limbed standing figures
POLE = (0.62, 1.8, 2.0, 1.2, 0.9, 1.0, 12)        # pole-thin stalkers
LEAN = (0.95, 1.4, 1.55, 1.05, 0.95, 1.0, 8)
BULKY = (0.95, 1.05, 1.4, 1.0, 0.95, 1.0, 6)
SMALL = (0.95, 1.25, 1.35, 1.0, 1.0, 1.0, 8)
STYLE = {
    "observer": TALL, "tall_black": POLE, "pale_herd": TALL, "hanging_man": POLE, "radio_shepherd": TALL, "signalman": LEAN, "hollow": POLE,
    "echo": LEAN, "facet": LEAN, "unfinished": LEAN, "drowned_choir": LEAN, "glass_walker": TALL, "candle_bearer": LEAN, "archivist": LEAN,
    "witness": (0.95, 1.5, 1.7, 1.1, 1.0, 1.0, 10), "black_witness": (0.95, 1.5, 1.7, 1.1, 1.0, 1.0, 10), "moon_watcher": (0.95, 1.5, 1.7, 1.1, 1.0, 1.0, 8),
    "doorman": BULKY, "salt_mother": BULKY, "signal_core": BULKY, "buried_king": BULKY,
    "lost_child": SMALL, "borrowed_skin": LEAN, "paper_man": (1.0, 1.3, 1.4, 1.05, 1.0, 1.0, 6),
    "crawler": (0.9, 1.35, 1.35, 1.0, 0.95, 1.0, 0),
}


def styled(E):
    st = STYLE.get(E["id"])
    if not st or E.get("quad") and E["id"] not in ("crawler",):
        return E
    w, ll, al, bh, hw, hh, _ = st
    e = dict(E)
    e["head"] = (E["head"][0] * hw, E["head"][1] * hh, E["head"][2])
    e["body"] = (E["body"][0] * w, E["body"][1] * bh, max(E["body"][2] * (w if E["body"][2] > 1 else 1), 1))
    e["arm"] = (min(3.0, max(1.2, E["arm"][0] * w)), E["arm"][1] * al)
    e["leg"] = (min(3.2, max(1.2, E["leg"][0] * w)), E["leg"][1] * ll)
    return e


def hunch(R, E):
    st = STYLE.get(E["id"])
    if not st or st[6] <= 0 or E.get("quad"): return
    a = st[6]
    for b in R.bones:
        if b["name"] == "chest": b["rotation"] = [a, 0, 0]
        elif b["name"] == "neck": b["rotation"] = [a * 0.7, 0, 0]
        elif b["name"] == "head": b["rotation"] = [-a * 0.35, 0, 0]


SIMPLE = True   # Wonderland-style: few plain cubes, thin limbs, no claws/teeth/decorations


def simplify(E, X):
    X = dict(X)
    if SIMPLE:
        X.pop("deco", None)
        X["nclaw"] = 0; X["claw"] = 0; X["teeth"] = False; X["foot"] = 1
    return X


def build_rig(E):
    E = styled(E)
    fn, X = BUILD[E["id"]]
    X = simplify(E, X)
    R = Rig(E["id"], 1.0)
    fn(R, E, X)
    if E["id"] in MO.POST and not SIMPLE: MO.POST[E["id"]](R, E, X)
    target = E["hb"][1] * 16 * 0.97
    sc = max(0.5, min(1.35, target / max(R.maxy, 1.0)))
    if E["id"] == "burrower":
        sc = max(sc, 1.0)
    R = Rig(E["id"], sc)
    fn(R, E, X)
    if E["id"] in MO.POST and not SIMPLE: MO.POST[E["id"]](R, E, X)
    hunch(R, E)
    return R, X, fn


# ------------------------------------------------------------------------------------------------ animations
S = lambda ph, k=1, o=0.0: math.sin(2 * math.pi * (ph * k + o))
SM = lambda x: x * x * (3 - 2 * x)


def bone_names(R):
    return [b["name"] for b in R.bones]


def make_animations(eid, B, kind, X):
    has = set(B)
    quadlike = kind in ("quad", "chest")
    base = {}
    if "head_z" in X:
        base["head"] = (0, 0, X["head_z"])
    lid = X.get("lid")
    jopen = -1 if lid else 1          # opening direction for the jaw/lid bone ("head" is the lid on the mimic)

    def gait(P, R_, ph, amp, elbow, kneebend=1.0):
        s, c = S(ph), S(ph, 1, 0.25)
        if quadlike:
            R_["arm_r"] = (amp * s, 0, 0); R_["arm_l"] = (-amp * s, 0, 0)
            R_["leg_r"] = (-amp * s, 0, 0); R_["leg_l"] = (amp * s, 0, 0)
            R_["forearm_r"] = (kneebend * amp * 0.7 * max(0, -s) + elbow, 0, 0); R_["forearm_l"] = (kneebend * amp * 0.7 * max(0, s) + elbow, 0, 0)
            R_["shin_r"] = (kneebend * amp * 0.9 * max(0, s), 0, 0); R_["shin_l"] = (kneebend * amp * 0.9 * max(0, -s), 0, 0)
        else:
            R_["leg_r"] = (amp * s, 0, 0); R_["leg_l"] = (-amp * s, 0, 0)
            R_["shin_r"] = (kneebend * amp * 1.1 * max(0, -S(ph, 1, 0.25)), 0, 0); R_["shin_l"] = (kneebend * amp * 1.1 * max(0, S(ph, 1, 0.25)), 0, 0)
            R_["arm_r"] = (-amp * 0.8 * s, 0, 2); R_["arm_l"] = (amp * 0.8 * s, 0, -2)
            R_["forearm_r"] = (elbow - max(0, -s) * amp * 0.5, 0, 0); R_["forearm_l"] = (elbow - max(0, s) * amp * 0.5, 0, 0)
            R_["foot_r"] = (-max(0, s) * 12, 0, 0); R_["foot_l"] = (-max(0, -s) * 12, 0, 0)
        P["body"] = (0, 0.5 * abs(c) * (amp / 35.0), 0)

    def idle(ph):
        R_, P = {}, {}
        R_["chest"] = (1.5 * S(ph), 0, 0)
        R_["head"] = (2 * S(ph, 1, 0.25), 6 * S(ph), 1.5 * S(ph, 2))
        R_["arm_r"] = (0, 0, 2.5 + 1.5 * S(ph)); R_["arm_l"] = (0, 0, -2.5 - 1.5 * S(ph))
        R_["forearm_r"] = (-8 - 3 * S(ph, 1, 0.5), 0, 0); R_["forearm_l"] = (-8 - 3 * S(ph, 1, 0.25), 0, 0)
        R_["jaw"] = (3 + 2 * S(ph), 0, 0)
        R_["hand_r"] = (-4 * S(ph, 2), 0, 0); R_["hand_l"] = (-4 * S(ph, 2, 0.3), 0, 0)
        if lid:
            R_["head"] = (-3 - 3 * S(ph), 0, 0); P["body"] = (0, 0.3 * S(ph, 2), 0)
        return R_, P

    def walk(ph):
        R_, P = idle(ph)
        gait(P, R_, ph, 32, -14)
        R_["chest"] = (4, 2.5 * S(ph), 0)
        R_["head"] = (-2, -3 * S(ph), 0)
        if lid:
            R_["head"] = (-8 - 8 * max(0, S(ph, 2)), 0, 0); P["body"] = (0, 1.2 * abs(S(ph)), 0)
        return R_, P

    def run(ph):
        R_, P = idle(ph)
        gait(P, R_, ph, 52, -60)
        R_["chest"] = (16, 4 * S(ph), 0)
        R_["head"] = (-6, -3 * S(ph), 0)
        R_["jaw"] = (12 + 6 * S(ph, 2), 0, 0)
        if lid:
            R_["head"] = (-25 - 18 * max(0, S(ph, 2)), 0, 0); P["body"] = (0, 2.0 * abs(S(ph)), 0)
        return R_, P

    def watch(ph):
        R_, P = {}, {}
        R_["head"] = (0.5 * S(ph, 3), 0.8 * S(ph, 5), 0.6 * S(ph, 7))
        R_["arm_r"] = (0, 0, 1.5); R_["arm_l"] = (0, 0, -1.5)
        R_["forearm_r"] = (-3, 0, 0); R_["forearm_l"] = (-3, 0, 0)
        R_["chest"] = (0.5 * S(ph), 0, 0)
        R_["jaw"] = (1.5, 0, 0)
        if lid:
            R_["head"] = (-1.5, 0, 0)
        return R_, P

    def stalk(ph):
        R_, P = idle(ph)
        gait(P, R_, ph, 24, -35, 1.4)
        R_["chest"] = ((-6 if quadlike else 24), 0, 0)
        R_["head"] = ((-26 if not quadlike else 12), 0, 0)
        R_["arm_r"] = (R_["arm_r"][0] - (0 if quadlike else 18), 0, 3); R_["arm_l"] = (R_["arm_l"][0] - (0 if quadlike else 18), 0, -3)
        R_["jaw"] = (10 + 4 * S(ph, 2), 0, 0)
        if lid:
            R_["head"] = (-14 - 6 * S(ph, 2), 0, 0)
        return R_, P

    def search(ph):
        R_, P = idle(ph)
        R_["head"] = (4, 55 * S(ph), 0)
        R_["chest"] = (6, 14 * S(ph, 1, 0.1), 0)
        R_["arm_r"] = (-14, 0, 6); R_["arm_l"] = (-14, 0, -6)
        R_["forearm_r"] = (-22, 0, 0); R_["forearm_l"] = (-22, 0, 0)
        if lid:
            R_["head"] = (-20 - 10 * S(ph), 0, 0)
        return R_, P

    def attack(ph):
        R_, P = {}, {}
        w = SM(min(1, ph / 0.35)) if ph < 0.5 else SM(max(0, 1 - (ph - 0.5) / 0.5))
        strike = max(0.0, S(ph, 1, -0.05))
        R_["chest"] = ((-30 * w + 10) if quadlike else (28 * strike - 8 * w), 0, 0)
        R_["head"] = (-8 + 24 * strike, 0, 0)
        R_["jaw"] = (10 + 38 * w, 0, 0)
        R_["arm_r"] = (-120 * w + 70 * strike, 0, 6); R_["arm_l"] = (-120 * w + 70 * strike, 0, -6)
        R_["forearm_r"] = (-10 - 25 * strike, 0, 0); R_["forearm_l"] = (-10 - 25 * strike, 0, 0)
        R_["leg_r"] = (14 * w, 0, 0); R_["leg_l"] = (-10 * w, 0, 0)
        R_["mand_r"] = (0, 38 * w, 0); R_["mand_l"] = (0, -38 * w, 0)
        if lid:
            R_["head"] = (-70 * w, 0, 0); P["body"] = (0, 3 * strike, 0)
        return R_, P

    def special(ph):
        R_, P = {}, {}
        R_["arm_r"] = (-10, 0, 80 + 6 * S(ph, 3)); R_["arm_l"] = (-10, 0, -80 - 6 * S(ph, 3))
        R_["forearm_r"] = (-12, 0, 0); R_["forearm_l"] = (-12, 0, 0)
        R_["head"] = (-24 + 3 * S(ph, 4), 0, 0)
        R_["chest"] = (-6, 0, 0)
        R_["jaw"] = (28 + 6 * S(ph, 5), 0, 0)
        R_["leg_r"] = (0, 0, 4); R_["leg_l"] = (0, 0, -4)
        P["body"] = (0, 1.0 + 1.0 * S(ph), 0)
        if quadlike:
            R_["chest"] = (-26, 0, 0); R_["arm_r"] = (-70, 0, 20); R_["arm_l"] = (-70, 0, -20)
        if lid:
            R_["head"] = (-62 + 5 * S(ph, 4), 0, 0)
        R_["mand_r"] = (0, 30, 0); R_["mand_l"] = (0, -30, 0)
        return R_, P

    def retreat(ph):
        R_, P = idle(ph)
        gait(P, R_, ph, 26, -30)
        R_["chest"] = (-10, 0, 0)
        R_["head"] = (-6, 18 * S(ph, 2), 0)
        R_["arm_r"] = (-34, 0, 22); R_["arm_l"] = (-34, 0, -22)
        if lid:
            R_["head"] = (-30, 0, 0)
        return R_, P

    def vanish(ph):
        R_, P = idle(ph)
        R_["head"] = (6 * S(ph, 5), 30 * S(ph, 4), 20 * S(ph, 6))
        R_["chest"] = (3 * S(ph, 7), 8 * S(ph, 5), 0)
        R_["arm_r"] = (-20 + 15 * S(ph, 6), 0, 10); R_["arm_l"] = (-20 - 15 * S(ph, 6), 0, -10)
        P["body"] = (1.2 * S(ph, 9), 0.6 * S(ph, 7), 0)
        P["root"] = (0.8 * S(ph, 11), 0, 0)
        return R_, P

    def death(ph):
        e = SM(min(1, ph / 0.7))
        R_, P = {}, {}
        R_["root"] = (0, 0, 88 * e)
        R_["chest"] = (30 * e, 0, 0)
        R_["head"] = (35 * e, 0, 20 * e)
        R_["jaw"] = (40 * e, 0, 0)
        R_["arm_r"] = (-30 * e, 0, 40 * e); R_["arm_l"] = (20 * e, 0, -30 * e)
        R_["forearm_r"] = (-40 * e, 0, 0); R_["forearm_l"] = (-20 * e, 0, 0)
        R_["leg_r"] = (-25 * e, 0, 0); R_["leg_l"] = (15 * e, 0, 0)
        R_["shin_r"] = (50 * e, 0, 0); R_["shin_l"] = (30 * e, 0, 0)
        if lid:
            R_["head"] = (-8 * e, 0, 0)
        return R_, P

    # --- spider variants
    def sp_leg(ph, amp, lift, fold=0):
        R_, P = {}, {}
        for i in range(4):
            for side, sg in (("r", -1), ("l", 1)):
                o = ((i + (0 if side == "r" else 1)) % 2) * 0.5
                R_[f"L{i}_{side}"] = (0, sg * amp * S(ph, 1, o + i * 0.04), sg * (lift * max(0, S(ph, 1, o + 0.25)) + fold))
                R_[f"T{i}_{side}"] = (0, 0, sg * (lift * 0.5 * max(0, S(ph, 1, o + 0.25)) + fold * 0.6))
        return R_, P

    def sp_idle(ph):
        R_, P = sp_leg(ph, 2, 1.5)
        R_["abdomen"] = (3 * S(ph), 2 * S(ph, 1, 0.25), 0)
        R_["head"] = (2 * S(ph, 1, 0.3), 4 * S(ph), 0)
        R_["jaw"] = (4 + 3 * S(ph, 2), 0, 0)
        P["body"] = (0, 0.25 * S(ph, 2), 0)
        return R_, P

    def sp_walk(ph, a=22, lift=18):
        R_, P = sp_leg(ph, a, lift)
        R_["abdomen"] = (4 * S(ph, 2), 5 * S(ph), 0)
        R_["head"] = (0, -5 * S(ph), 0)
        P["body"] = (0, 0.6 * abs(S(ph)), 0)
        return R_, P

    def sp_attack(ph):
        w = SM(min(1, ph / 0.35)) if ph < 0.5 else SM(max(0, 1 - (ph - 0.5) / 0.5))
        R_, P = sp_leg(ph, 4, 0)
        for side, sg in (("r", -1), ("l", 1)):
            R_[f"L0_{side}"] = (-60 * w, sg * 25 * w, sg * 30 * w)
            R_[f"L1_{side}"] = (-30 * w, sg * 15 * w, sg * 20 * w)
        R_["body"] = (-18 * w, 0, 0)
        R_["head"] = (14 * w, 0, 0)
        R_["jaw"] = (40 * w, 0, 0)
        R_["abdomen"] = (14 * w, 0, 0)
        return R_, P

    def sp_special(ph):
        R_, P = sp_leg(ph, 3, 28 + 6 * S(ph, 3))
        R_["head"] = (-14, 0, 0); R_["abdomen"] = (-12 + 4 * S(ph, 3), 0, 0)
        R_["jaw"] = (30, 0, 0)
        P["body"] = (0, 2 + S(ph), 0)
        return R_, P

    def sp_death(ph):
        e = SM(min(1, ph / 0.7))
        R_, P = sp_leg(ph, 0, 0, 55 * e)
        R_["abdomen"] = (-10 * e, 0, 0)
        R_["jaw"] = (40 * e, 0, 0)
        P["body"] = (0, -9 * e, 0)
        return R_, P

    if kind == "spider":
        defs = [("idle", 4, True, sp_idle), ("walk", 1.0, True, sp_walk), ("run", 0.5, True, lambda p: sp_walk(p, 30, 26)),
                ("watch", 3, True, lambda p: (lambda r: (r.__setitem__("head", (0, 1.5 * S(p, 3), 0)) or r, {}))(sp_leg(p, 0.5, 0.5)[0])),
                ("stalk", 1.8, True, lambda p: sp_walk(p, 12, 10)), ("search", 3, True, lambda p: (lambda rp: (rp[0].__setitem__("head", (4, 50 * S(p), 0)) or rp[0], rp[1]))(sp_idle(p))),
                ("attack", 0.8, True, sp_attack), ("special", 2.4, True, sp_special), ("retreat", 1.2, True, lambda p: sp_walk(p, 18, 14)),
                ("vanish", 0.8, True, lambda p: (lambda rp: (rp[0].__setitem__("body", (6 * S(p, 4), 0, 12 * S(p, 6))) or rp[0], rp[1]))(sp_idle(p))), ("death", 1.4, False, sp_death)]
    else:
        defs = [("idle", 4, True, idle), ("walk", 1.0, True, walk), ("run", 0.6, True, run), ("watch", 3, True, watch),
                ("stalk", 1.6, True, stalk), ("search", 3, True, search), ("attack", 0.8, True, attack),
                ("special", 2.4, True, special), ("retreat", 1.2, True, retreat), ("vanish", 0.8, True, vanish), ("death", 1.4, False, death)]

    out = {}
    for name, L, loop, fn in defs:
        n = 16 if name in ("attack", "vanish", "death") else 12
        rot, pos = {}, {}
        for i in range(n + 1):
            ph = i / n
            t = round(ph * L, 4)
            R_, P = fn(ph)
            if "body" in has or "root" in has:
                pass
            for b, v in R_.items():
                if b in has:
                    add = base.get("head", (0, 0, 0)) if b == "head" else (0, 0, 0)
                    rot.setdefault(b, {})[str(t)] = [round(v[0] + add[0], 3), round(v[1] + add[1], 3), round(v[2] + add[2], 3)]
            for b, v in P.items():
                if b in has:
                    pos.setdefault(b, {})[str(t)] = [round(v[0], 3), round(v[1], 3), round(v[2], 3)]
            # sway_*/spin* bones
            amp = {"idle": 6, "watch": 3, "walk": 11, "run": 16, "stalk": 7, "search": 8, "attack": 20, "special": 14, "retreat": 12, "vanish": 25, "death": 4}[name]
            for b in B:
                if b.startswith("sway_"):
                    seed = (sum(ord(ch) for ch in b) % 100) / 100.0
                    r = rot.setdefault(b, {})
                    cur = r.get(str(t), [0, 0, 0])
                    d_ = (len(b) % 3) * 0.12
                    r[str(t)] = [round(cur[0] + amp * S(ph, 1, seed + d_), 3), round(cur[1], 3), round(cur[2] + amp * 0.6 * S(ph, 1, seed + 0.25), 3)]
                elif b.startswith("spin") and name != "death":
                    nfold = int("".join(ch for ch in b.split("_")[0] if ch.isdigit()) or "4")
                    rot.setdefault(b, {})[str(t)] = [0, round(360.0 / nfold * ph, 3), 0]
        bones = {}
        for b in set(rot) | set(pos):
            bones[b] = {}
            if b in rot:
                bones[b]["rotation"] = rot[b]
            if b in pos:
                bones[b]["position"] = pos[b]
        out[f"animation.{eid}.{name}"] = {"loop": loop, "animation_length": L, "bones": bones}
    return {"format_version": "1.8.0", "animations": out}


# ------------------------------------------------------------------------------------------------ textures
def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def gen_texture(E, R):
    rng = random.Random(4242 + sum(map(ord, E["id"])))
    base, dark, acc = GA.hexc(E["base"]), GA.hexc(E["dark"]), GA.hexc(E["accent"])
    bone = mix(mix(base, (222, 214, 190), 0.65), base, 0.2)
    trim = mix(dark, acc, 0.35)
    ghost = E["id"] in ("echo",)
    img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
    glow = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))

    def fill(name, bc, dc, dens, alpha=255):
        x, y = REG[name]
        GA.noisy(img, (x, y, 64, 64), (bc[0] << 16) | (bc[1] << 8) | bc[2], (dc[0] << 16) | (dc[1] << 8) | dc[2], rng, dens)
        px = img.load()
        for yy in range(y, y + 64):
            for xx in range(x, x + 64):
                r, g, b, _ = px[xx, yy]
                px[xx, yy] = (r, g, b, alpha)

    fill("skin", base, dark, 0.04 if SIMPLE else 0.20, 165 if ghost else 255)
    fill("dark", dark, mix(dark, (0, 0, 0), 0.5), 0.05 if SIMPLE else 0.25, 175 if ghost else 255)
    fill("trim", trim, dark, 0.2, 190 if ghost else 255)
    fill("bone", bone, mix(bone, dark, 0.5), 0.18, 200 if ghost else 255)
    fill("face", base, dark, 0.15, 165 if ghost else 255)
    # glow region: bright accent, a little noise
    gx, gy = REG["glow"]
    gc = mix(acc, (255, 255, 255), 0.25)
    d = ImageDraw.Draw(img)
    for yy in range(gy, gy + 64):
        for xx in range(gx, gx + 64):
            t = rng.uniform(-18, 18)
            c = tuple(max(0, min(255, int(v + t))) for v in gc)
            img.putpixel((xx, yy), c + (255,))
            glow.putpixel((xx, yy), c + (255,))
    # damage/scars on skin + dark
    for nm, n in ((("skin", 3), ("dark", 2), ("bone", 2)) if SIMPLE else (("skin", 26), ("dark", 14), ("bone", 10))):
        x, y = REG[nm]
        GA.damage(img, (x, y, 64, 64), E["dark"], rng, n)
    # seams on skin: horizontal stitch lines
    sx, sy = REG["skin"]
    for _ in range(0 if SIMPLE else 5):
        yy = sy + rng.randrange(4, 60)
        d.line([(sx, yy), (sx + 63, yy)], fill=mix(base, dark, 0.45) + (255,))
    # face
    if R.face:
        u, v, w, h = R.face
        fl = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
        GA.draw_face(fl, (u, v, w, h), E, rng)
        face_px, fp, gp = img.load(), fl.load(), glow.load()
        for yy in range(v, v + h):
            for xx in range(u, u + w):
                r, g, b, a = fp[xx, yy]
                if a > 0:
                    face_px[xx, yy] = (r, g, b, 255)
                    if max(r, g, b) > 110:
                        gp[xx, yy] = (r, g, b, 255)
    os.makedirs(TEX_DIR, exist_ok=True)
    img.save(os.path.join(TEX_DIR, E["id"] + ".png"))
    glow.save(os.path.join(TEX_DIR, E["id"] + "_glowmask.png"))


def main():
    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    total_bones = total_cubes = 0
    for E in GA.ENT:
        R, X, fn = build_rig(E)
        kind = {"biped": "biped", "quad": "quad", "spider": "spider", "mimic": "chest"}[fn.__name__]
        B = bone_names(R)
        GA.wjson(os.path.join(GEO_DIR, E["id"] + ".geo.json"), R.geo(E["hb"][0], E["hb"][1]))
        GA.wjson(os.path.join(ANIM_DIR, E["id"] + ".animation.json"), make_animations(E["id"], B, kind, X))
        gen_texture(E, R)
        nc = sum(len(v) for v in R.cubes.values())
        total_bones += len(B); total_cubes += nc
        print(f"{E['id']:14s} bones={len(B):3d} cubes={nc:4d} scale={R.sc:.2f} height={R.maxy * R.sc / 16:.2f}b (hitbox {E['hb'][1]})")
    print("monsters:", len(GA.ENT), "bones:", total_bones, "cubes:", total_cubes)


if __name__ == "__main__":
    main()
