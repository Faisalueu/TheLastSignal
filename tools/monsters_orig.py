"""
Second-pass silhouettes for the 17 original creatures (those not added in stage 2/3). Each post-pass adds
shape-defining parts on top of the jointed base rig so no two share a silhouette:
 observer  folded mantis arms, hooked back-spine        hollow    drooping jaw, rib-cage splay, spine hooks
 mimic     second tongue + ring of chain, keyed hasp     signalman shoulder dish, back cable drum
 witness   veil skirt, stacked eye-bulbs                 crawler   side ribs plates, hooked tail barb
 lost_child oversized hood + toy-bear on a string        doorman   hinges, door-frame arch over head
 pale_herd long muzzle + bell cord, knee-long fingers    echo      offset ghost shell (two outline frames)
 burrower  drill crown, armour plates on flanks          lantern_widow web trail, veil hanging from abdomen
 facet     angled crystal wings                          archivist back-library, hanging scroll strips
 signal_core antenna crown                               buried_king chained throne-back, roots over shoulders
 unfinished scaffold outline for the missing half
"""
import math
from types import SimpleNamespace


def bc(E, X):
    hw, hh, hd = E["head"]; bw, bh, bd = E["body"]; aw, al = E["arm"]; lw, ll = E["leg"]
    hip = ll; sh = hip + bh; hy = sh + X.get("neck", 1.5); sx = bw / 2 + aw / 2 + X.get("armgap", 0.3)
    return SimpleNamespace(hip=hip, sh=sh, hy=hy, hw=hw, hh=hh, hd=hd, bw=bw, bh=bh, bd=bd, aw=aw, al=al, lw=lw, ll=ll, sx=sx)


def p_observer(R, E, X):
    c = bc(E, X)
    for sg in (-1, 1):                                  # second, thin pair of arms folded in front of the ribs
        x = sg * (c.bw / 2 + 0.6)
        R.box("chest", x - 0.4, c.hip + c.bh * 0.72, -c.bd / 2 - 5.5, 0.8, 0.8, 5.8, "bone")
        R.box("chest", x - 0.4, c.hip + c.bh * 0.42, -c.bd / 2 - 5.5, 0.8, c.bh * 0.3 + 0.8, 0.8, "bone")
        R.box("chest", x - 0.4 - sg * 0.3, c.hip + c.bh * 0.36, -c.bd / 2 - 6.2, 0.8, 1.2, 1.6, "dark")
    for i in range(5):                                  # hooked spine
        R.box("chest", -0.5, c.hip + c.bh * (0.2 + 0.15 * i), c.bd / 2 + 0.2 + i * 0.5, 1, 2, 0.8 + i * 0.4, "bone", rot=[-18, 0, 0])


def p_hollow(R, E, X):
    c = bc(E, X)
    R.box("jaw", -c.hw * 0.3, c.hy - 6, -c.hd / 2 + 0.2, c.hw * 0.6, 6, 0.8, "dark")                 # jaw hangs far down
    R.box("jaw", -c.hw * 0.3, c.hy - 6.4, -c.hd / 2 + 0.1, c.hw * 0.6, 0.6, 1.0, "bone")
    for sg in (-1, 1):
        for i in range(3):
            R.box("chest", sg * (c.bw / 2 + 0.2) - 0.4 + sg * 0.6 * i, c.hip + c.bh * (0.35 + 0.18 * i), -c.bd / 2 - 0.6, 0.8, 0.8, 3.4, "bone", rot=[0, sg * 25, 0])
    for i in range(4):
        R.box("chest", -0.6, c.hip + c.bh * (0.3 + 0.17 * i), c.bd / 2 + 1.8 + i * 0.3, 1.2, 2.4, 1.2, "bone", rot=[-30, 0, 0])


def p_mimic(R, E, X):
    R.bone("sway_tongue2", "body", (0, 7.6, -3))
    R.box("sway_tongue2", 2, 7.4, -9, 1.4, 0.8, 6, "trim")
    R.box("sway_tongue2", -3.4, 7.4, -8, 1.4, 0.8, 5, "trim")
    for sg in (-1, 1):                                  # padlock hasp and chain loops
        R.box("body", sg * 2.2 - 0.4, 2.4, -4.6, 0.8, 3.2, 0.5, "bone")
    R.box("body", -2.4, 1.6, -5.2, 4.8, 0.8, 0.8, "trim")
    R.box("body", -0.9, 0.6, -5.4, 1.8, 1.6, 0.8, "glow")
    for i in range(4):
        R.box("body", -5.2, 6 - i * 1.1, 4.1 + (i % 2) * 0.3, 10.4, 0.5, 0.5, "dark")


def p_signalman(R, E, X):
    c = bc(E, X)
    R.box("chest", c.bw / 2 - 1, c.sh - 1, -3, 1.2, 1.5, 6, "dark")                                  # dish mount
    R.box("chest", c.bw / 2 + 0.2, c.sh + 0.4, -4.4, 0.8, 7, 8.8, "bone", rot=[0, 0, 12])
    R.box("chest", c.bw / 2 + 0.6, c.sh + 3, -0.5, 0.6, 0.6, 0.6, "glow")
    R.box("chest", -c.bw / 2 + 0.5, c.hip + c.bh * 0.3, c.bd / 2, c.bw - 1, 6, 4.4, "dark")             # cable drum
    for i in range(5):
        R.box("chest", -c.bw / 2 + 0.3, c.hip + c.bh * 0.3 + i * 1.1, c.bd / 2 + 4.3, c.bw - 0.6, 0.5, 0.5, "trim")


def p_witness(R, E, X):
    c = bc(E, X)
    R.bone("sway_veil", "body", (0, c.hip + 4, 0))
    for i in range(4):
        w = c.bw + 3 + i * 1.6
        R.box("sway_veil", -w / 2, c.hip + 4 - 3 - i * 3, -c.bd / 2 - 1 - i * 0.4, w, 3, c.bd + 2 + i * 0.8, "dark" if i % 2 else "skin")
    for i in range(3):                                                                              # eye bulbs stacked above the crown
        R.box("head", -2.2 + i * 2.2 - 1, c.hy + c.hh + 0.4 + (i % 2) * 1.4, -1.4, 2, 2, 2, "glow")


def p_crawler(R, E, X):
    bw, bh, bd = E["body"]
    ls = X.get("ls", 1.0); top = max(E["arm"][1] * ls, E["leg"][1] * ls); by = max(1.0, top - bh * 0.7)
    for sg in (-1, 1):
        for i in range(4):
            R.box("chest" if i < 2 else "body", sg * (bw / 2 + 0.2) - 0.4, by + 0.6 + (i % 2) * 1.2, -bd / 2 + i * 3.2, 0.8, 0.8, 4, "bone", rot=[0, 0, sg * 28])
    R.box("body", -0.4, by + bh * 0.4, bd / 2 + 11, 0.8, 0.8, 5, "bone", rot=[35, 0, 0])


def p_lost_child(R, E, X):
    c = bc(E, X)
    R.box("head", -c.hw / 2 - 1.2, c.hy + c.hh * 0.45, -c.hd / 2 - 1.6, c.hw + 2.4, c.hh * 0.75 + 0.8, c.hd + 3, "trim")      # hood too big
    R.box("head", -c.hw / 2 - 1.5, c.hy + c.hh * 0.1, c.hd / 2 + 0.4, c.hw + 3, c.hh * 0.6, 1.2, "trim")
    R.box("chest", -c.bw / 2 - 1.1, c.hip + 0.5, -c.bd / 2 - 1.1, c.bw + 2.2, c.bh * 0.9, c.bd + 2.2, "trim")             # raincoat
    R.box("hand_l", c.sx - 1.5, c.sh - c.al - 4.5, -1.5, 3, 3.6, 2.4, "dark")                                           # teddy
    R.box("hand_l", c.sx - 1.2, c.sh - c.al - 5.6, -1.2, 0.6, 0.6, 0.3, "glow")
    R.box("hand_l", c.sx + 0.6, c.sh - c.al - 5.6, -1.2, 0.6, 0.6, 0.3, "glow")


def p_doorman(R, E, X):
    c = bc(E, X)
    for sg in (-1, 1):                                                                              # door-frame arch around the head
        R.box("head", sg * (c.hw / 2 + 1.5) - 0.9, c.hy - 6, -c.hd / 2 - 1.2, 1.8, c.hh + 9, c.hd + 2.4, "dark")
    R.box("head", -c.hw / 2 - 2.4, c.hy + c.hh + 2.2, -c.hd / 2 - 1.2, c.hw + 4.8, 2, c.hd + 2.4, "dark")
    for y in (0.15, 0.5, 0.85):
        R.box("chest", c.bw / 2 + 0.4, c.hip + c.bh * y, -1.4, 1.6, 4, 2.8, "bone")                    # hinges on the shoulder side


def p_pale_herd(R, E, X):
    c = bc(E, X)
    R.box("head", -1.4, c.hy + 0.6, -c.hd / 2 - 3.6, 2.8, 3.2, 4, "skin")                                # long muzzle
    R.box("head", -1.4, c.hy + 0.2, -c.hd / 2 - 3.8, 2.8, 0.7, 4, "bone")
    for sg in (-1, 1):
        R.box("hand_" + ("l" if sg > 0 else "r"), sg * c.sx - 0.3, c.sh - c.al - 8, -0.3, 0.6, 7, 0.6, "bone")   # fingers to the knee
    R.bone("sway_bell", "neck", (0, c.sh, -2))
    R.box("sway_bell", -0.3, c.sh - 9, -2.4, 0.6, 9, 0.6, "dark")
    R.box("sway_bell", -1.2, c.sh - 12, -3.2, 2.4, 3, 2.4, "glow")


def p_echo(R, E, X):
    c = bc(E, X)
    for off, nm in ((-3.2, "sway_ghostL"), (3.2, "sway_ghostR")):                                    # two displaced outline copies
        R.bone(nm, "root", (0, c.hip, 0))
        R.box(nm, off - c.bw / 2, c.hip, -c.bd / 2, 0.5, c.bh, 0.5, "glow")
        R.box(nm, off + c.bw / 2 - 0.5, c.hip, -c.bd / 2, 0.5, c.bh, 0.5, "glow")
        R.box(nm, off - c.bw / 2, c.hip + c.bh - 0.5, -c.bd / 2, c.bw, 0.5, 0.5, "glow")
        R.box(nm, off - c.hw / 2, c.hy + c.hh - 0.5, -c.hd / 2, c.hw, 0.5, 0.5, "glow")
        R.box(nm, off - c.hw / 2, c.hy, -c.hd / 2, 0.5, c.hh, 0.5, "glow")
        R.box(nm, off + c.hw / 2 - 0.5, c.hy, -c.hd / 2, 0.5, c.hh, 0.5, "glow")


def p_burrower(R, E, X):
    bw, bh, bd = E["body"]; hw, hh, hd = E["head"]
    ls = X.get("ls", 1.0); top = max(E["arm"][1] * ls, E["leg"][1] * ls); by = max(1.0, top - bh * 0.7)
    z0 = -bd / 2 - 2.5
    for i in range(5):                                                                              # drill crown of stacked plates
        w = hw * (1 - i * 0.17)
        R.box("head", -w / 2, by + bh * 0.5 + hh / 2 + i * 1.6, z0 - hd / 2 - 1, w, 1.5, 4 + (4 - i), "bone" if i % 2 else "dark", rot=[0, 0, 0])
    for sg in (-1, 1):
        for i in range(3):
            R.box("chest" if i < 2 else "body", sg * (bw / 2 + 0.3) - 0.5, by + 1, -bd / 2 + 2 + i * 6, 1, bh - 2, 5, "bone")


def p_lantern_widow(R, E, X):
    by = 15.0
    R.bone("sway_veil", "abdomen", (0, by - 3.5, 8))
    for i in range(5):
        R.box("sway_veil", -3 + i * 1.5 - 0.2, by - 3.5 - 6 - (i % 2) * 3, 8 + (i % 3), 0.5, 6 + (i % 2) * 3, 0.5, "trim")
    R.bone("sway_web", "body", (0, by, 4))
    R.box("sway_web", -0.2, 0.2, 10, 0.4, 0.4, 9, "bone")
    R.box("sway_web", -3, 0.2, 13, 6, 0.4, 0.4, "bone")
    R.box("sway_web", -1.6, 0.2, 16, 3.2, 0.4, 0.4, "bone")


def p_facet(R, E, X):
    c = bc(E, X)
    for sg in (-1, 1):                                                                              # angled crystal wings from the shoulder blades
        for i in range(3):
            R.box("chest", sg * (c.bw / 2 + 1.5 + i * 1.8) - 0.6, c.hip + c.bh * (0.5 + 0.12 * i), c.bd / 2 + 0.4, 1.2, 6 + 3 * (2 - i), 0.8, "glow" if i == 1 else "bone",
                  rot=[0, 0, sg * (28 + i * 14)])


def p_archivist(R, E, X):
    c = bc(E, X)
    for i in range(5):                                                                              # a library carried on the back
        R.box("chest", -c.bw / 2 + 0.5 + (i % 2) * 0.6, c.hip + c.bh * 0.4 + i * 2.1, c.bd / 2 + 4.4, c.bw - 1, 1.9, 3, "trim" if i % 2 else "dark")
    R.bone("sway_scrolls", "chest", (0, c.sh, 0))
    for i in range(4):
        R.box("sway_scrolls", -c.bw / 2 + 0.8 + i * 1.7, c.hip + 1 - (i % 2) * 2, -c.bd / 2 - 0.6, 0.9, 8 + (i % 2) * 3, 0.3, "bone")


def p_signal_core(R, E, X):
    c = bc(E, X)
    R.bone("sway_mast", "head", (0, c.hy + c.hh, 0))
    for i, (dx, h) in enumerate(((-5, 9), (0, 16), (5, 11))):
        R.box("sway_mast", dx - 0.5, c.hy + c.hh, -0.5, 1, h, 1, "dark")
        R.box("sway_mast", dx - 1.2, c.hy + c.hh + h, -1.2, 2.4, 1.6, 2.4, "glow")


def p_buried_king(R, E, X):
    c = bc(E, X)
    R.box("chest", -c.bw / 2 - 5, c.hip, c.bd / 2 + 2.6, c.bw + 10, c.bh + 12, 2.4, "dark")          # throne-back he carries
    R.box("chest", -c.bw / 2 - 5.4, c.hip + c.bh + 10, c.bd / 2 + 2.4, c.bw + 10.8, 2, 2.8, "trim")
    for sg in (-1, 1):
        R.box("chest", sg * (c.bw / 2 + 4.6) - 0.8, c.hip - 2, c.bd / 2 + 2.2, 1.6, c.bh + 16, 2.8, "trim")
    for i in range(4):
        R.box("chest", -c.bw / 2 + 1 + i * (c.bw - 2) / 3, c.hip + c.bh * 0.9, -c.bd / 2 - 1.2, 0.8, 7 + (i % 2) * 3, 0.8, "bone")   # chains across the chest


def p_unfinished(R, E, X):
    c = bc(E, X)
    x0 = -c.bw / 2 - 0.1                                                                            # the missing left half drawn as scaffolding
    for (y, h) in ((c.hip, 0.4), (c.hip + c.bh - 0.4, 0.4)):
        R.box("chest", c.bw / 2, y, -c.bd / 2, c.bw / 2 + 2, h, 0.4, "glow")
    R.box("chest", c.bw + 1.6, c.hip, -c.bd / 2, 0.4, c.bh, 0.4, "glow")
    R.box("chest", c.bw / 2, c.hip, c.bd / 2 - 0.4, c.bw / 2 + 2, 0.4, 0.4, "glow")
    for i in range(3):
        R.box("chest", c.bw / 2 + 0.4 + i * 1.3, c.hip + c.bh * 0.15, -c.bd / 2, 0.4, c.bh * 0.7, 0.3, "glow", rot=[0, 0, 24 - i * 22])
    R.box("head", c.hw / 2 - 0.4, c.hy, -c.hd / 2, 0.4, c.hh, 0.4, "glow")
    R.box("head", c.hw / 2 - 0.4, c.hy + c.hh - 0.4, -c.hd / 2, 3, 0.4, 0.4, "glow")


POST = {"observer": p_observer, "hollow": p_hollow, "mimic": p_mimic, "signalman": p_signalman, "witness": p_witness, "crawler": p_crawler,
        "lost_child": p_lost_child, "doorman": p_doorman, "pale_herd": p_pale_herd, "echo": p_echo, "burrower": p_burrower,
        "lantern_widow": p_lantern_widow, "facet": p_facet, "archivist": p_archivist, "signal_core": p_signal_core,
        "buried_king": p_buried_king, "unfinished": p_unfinished}
