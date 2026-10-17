"""48 hand-built marking glyphs, 11x11 bitmaps. One drawing function per dimension; the SAME bitmap is used for
the marking block texture and for the in-world stamps (floors/murals), so the marking in the world matches the block."""
import random, zlib
from PIL import Image, ImageDraw

N = 11
DIG = {"0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
       "3": ["111", "001", "111", "001", "111"], "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
       "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"], "8": ["111", "101", "111", "101", "111"],
       "9": ["111", "101", "111", "001", "111"]}


def digits(d, s, x, y):
    for i, ch in enumerate(s):
        for r, row in enumerate(DIG[ch]):
            for c, v in enumerate(row):
                if v == "1":
                    d.point((x + i * 4 + c, y + r), 255)


def poly(d, pts):
    d.line(pts, 255)


def g_eye_cluster(d, r, v):
    d.ellipse([0, 3, 10, 7], outline=255); d.ellipse([4, 4, 6, 6], fill=255)
    d.ellipse([0, 8, 2, 9], outline=255); d.ellipse([8, 8, 10, 9], outline=255); d.point((5, 9), 255)
    for a, b in (((0, 5), (0, 1)), ((10, 5), (10, 1)), ((5, 3), (5, 0)), ((3, 3), (2, 1)), ((7, 3), (8, 1))): d.line([a, b], 255)


def g_scratches(d, r, v):
    for x in (2, 4, 6, 8): d.line([(x, 1), (x, 9)], 255)
    d.line([(1, 8), (9, 2)], 255)
    for _ in range(14): d.point((r.randint(1, 9), r.randint(1, 9)), 0)


def g_rose(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255); d.ellipse([3, 3, 7, 7], outline=255)
    for a in ((5, 0), (5, 10), (0, 5), (10, 5), (1, 1), (9, 9), (1, 9), (9, 1)): d.line([(5, 5), a], 255)


def g_index_grid(d, r, v):
    for x in (1, 5, 9):
        for y in (1, 5, 9): d.point((x, y), 255)
    d.line([(1, 1), (9, 1)], 255); d.line([(1, 5), (1, 9)], 255); d.rectangle([3, 3, 7, 7], outline=255)
    d.line([(5, 1), (5, 3)], 255)


def g_room_numbers(d, r, v):
    digits(d, "304", 0, 1); digits(d, "17", 0, 7) if False else None
    d.line([(0, 8), (10, 8)], 255)
    for x in range(0, 11, 2): d.point((x, 10), 255)


def g_clock(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255)
    for p in ((5, 1), (9, 5), (5, 9), (1, 5)): d.point(p, 255)
    d.line([(5, 5), (5, 2)], 255); d.line([(5, 5), (8, 6)], 255)


def g_black_star(d, r, v):
    d.line([(5, 0), (5, 10)], 255); d.line([(0, 5), (10, 5)], 255); d.line([(1, 1), (9, 9)], 255); d.line([(1, 9), (9, 1)], 255)


def g_iris(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255); d.ellipse([2, 2, 8, 8], outline=255); d.ellipse([4, 4, 6, 6], fill=255)
    for p in ((5, 0), (5, 10), (0, 5), (10, 5)): d.point(p, 0)


def g_impossible_cube(d, r, v):
    poly(d, [(5, 0), (10, 3), (10, 7), (5, 10), (0, 7), (0, 3), (5, 0)])
    d.line([(5, 0), (5, 5)], 255); d.line([(5, 5), (10, 7)], 255); d.line([(5, 5), (0, 7)], 255); d.line([(10, 3), (5, 6)], 255)


def g_angular(d, r, v):
    poly(d, [(0, 8), (3, 2), (5, 8), (7, 2), (10, 8)]); d.line([(0, 10), (10, 10)], 255); d.line([(5, 0), (5, 4)], 255)


def g_moon(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255)
    for b in ([2, 2, 4, 4], [6, 5, 8, 7], [3, 7, 5, 9]): d.ellipse(b, outline=255)


def g_fracture(d, r, v):
    for a, b in (((5, 0), (4, 4)), ((4, 4), (0, 6)), ((4, 4), (6, 6)), ((6, 6), (10, 5)), ((6, 6), (7, 10)), ((4, 4), (3, 10)), ((5, 0), (9, 1))): d.line([a, b], 255)


def g_veins(d, r, v):
    d.ellipse([4, 4, 6, 6], outline=255)
    for a, b in (((0, 0), (3, 3)), ((10, 1), (7, 4)), ((0, 9), (3, 7)), ((10, 10), (7, 7)), ((5, 0), (5, 3)), ((5, 10), (5, 7)), ((0, 5), (3, 5)), ((10, 6), (7, 5))): d.line([a, b], 255)
    d.line([(1, 1), (1, 3)], 255); d.line([(9, 9), (7, 9)], 255)


def g_fungal_ring(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255)
    for p in ((5, 2), (8, 5), (5, 8), (2, 5), (7, 3), (3, 7), (7, 7), (3, 3), (5, 5)): d.point(p, 255)


def g_knots(d, r, v):
    d.ellipse([0, 2, 5, 8], outline=255); d.ellipse([3, 2, 8, 8], outline=255); d.ellipse([5, 3, 10, 9], outline=255)
    d.line([(1, 0), (1, 2)], 255); d.line([(9, 9), (9, 10)], 255)


def g_ovals(d, r, v):
    for b in ([0, 0, 3, 4], [4, 1, 7, 5], [7, 0, 10, 3], [1, 6, 4, 10], [5, 7, 9, 10]): d.ellipse(b, outline=255)


def g_ripples(d, r, v):
    d.arc([0, 0, 10, 10], 20, 250, 255); d.arc([2, 2, 8, 8], 90, 340, 255); d.point((5, 5), 255)


def g_growth_rings(d, r, v):
    d.arc([0, 0, 10, 10], 10, 330, 255); d.arc([2, 1, 9, 8], 40, 350, 255); d.arc([3, 3, 7, 7], 0, 300, 255); d.point((5, 5), 255); d.line([(5, 5), (10, 4)], 255)


def g_gear(d, r, v):
    d.ellipse([2, 2, 8, 8], outline=255); d.ellipse([4, 4, 6, 6], outline=255)
    for p in ((5, 0), (5, 10), (0, 5), (10, 5), (1, 1), (9, 9), (1, 9), (9, 1)): d.point(p, 255)


def g_experiment(d, r, v):
    d.rectangle([0, 0, 10, 10], outline=255); digits(d, "07", 1, 1); d.line([(1, 7), (9, 9)], 255); d.line([(9, 7), (6, 9)], 255)


def g_circuit(d, r, v):
    for a, b in (((0, 2), (4, 2)), ((4, 2), (4, 8)), ((4, 8), (10, 8)), ((4, 5), (10, 5)), ((10, 5), (10, 2)), ((2, 10), (2, 5))): d.line([a, b], 255)
    for p in ((4, 5), (10, 5), (4, 2)): d.rectangle([p[0] - 1 if p[0] > 0 else 0, p[1] - 1 if p[1] > 0 else 0, min(10, p[0] + 1), min(10, p[1] + 1)], outline=255)


def g_warning(d, r, v):
    poly(d, [(5, 0), (10, 9), (0, 9), (5, 0)]); d.line([(5, 3), (5, 6)], 255); d.point((5, 8), 255); d.line([(0, 10), (10, 10)], 255)


def g_pixel_grid(d, r, v):
    for x in range(N):
        for y in range(N):
            if ((x * 3 + y * 5 + v) % 4 == 0) and (x % 2 == 0 or y % 3 == 0): d.point((x, y), 255)
    for y in (3, 7):
        d.line([(0, y), (r.randint(3, 10), y)], 255)


def g_tool_marks(d, r, v):
    for y in range(1, 10, 2): d.line([(0, y + 1), (r.randint(3, 5), y - 1)], 255)


def g_misaligned_squares(d, r, v):
    d.rectangle([0, 0, 10, 10], outline=255); d.rectangle([1, 1, 9, 9], outline=255); d.rectangle([3, 2, 7, 7], outline=255)
    d.point((1, 1), 0); d.point((9, 9), 0)


def g_clock_broken(d, r, v):
    d.arc([0, 0, 10, 10], 30, 340, 255); d.line([(5, 5), (8, 1)], 255); d.line([(5, 5), (1, 7)], 255); d.line([(7, 0), (4, 3)], 255)
    for p in ((5, 1), (9, 5), (1, 5)): d.point(p, 255)


def g_reflect(d, r, v):
    for k in (2, 5, 8): d.line([(0, k), (k, 0)], 255)
    for k in (3, 6): d.line([(10, k + 2), (k + 2, 10)], 255)
    d.line([(0, 10), (10, 0)], 255)


def g_childlike(d, r, v):
    d.ellipse([0, 0, 3, 3], outline=255)
    for p in ((5, 0), (5, 2), (2, 5), (0, 4)): d.point(p, 255)
    poly(d, [(5, 10), (5, 7), (8, 5), (10, 7), (10, 10)]); d.line([(7, 10), (7, 8)], 255)
    d.point((1, 8), 255); d.line([(1, 9), (1, 10)], 255); d.line([(0, 9), (2, 9)], 255)


def g_mask(d, r, v):
    d.ellipse([1, 0, 9, 10], outline=255); d.line([(2, 3), (4, 3)], 255); d.line([(6, 3), (8, 3)], 255); d.line([(3, 7), (7, 7)], 255)


def g_steps(d, r, v):
    poly(d, [(0, 10), (0, 8), (2, 8), (2, 6), (4, 6), (4, 4), (6, 4), (6, 2), (8, 2), (8, 0), (10, 0)]); digits(d, "9", 6, 6)


def g_big_circles(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255); d.ellipse([2, 2, 8, 8], outline=255); d.point((5, 5), 255)


def g_face_fracture(d, r, v):
    d.ellipse([1, 0, 9, 10], outline=255); d.line([(3, 3), (4, 4)], 255); d.line([(7, 3), (6, 5)], 255)
    poly(d, [(3, 8), (4, 7), (5, 8), (6, 7), (7, 8)]); d.line([(5, 0), (4, 3)], 255); d.line([(9, 5), (6, 6)], 255)


def g_sun(d, r, v):
    d.ellipse([3, 3, 7, 7], fill=255)
    for a, b in (((5, 0), (5, 2)), ((5, 10), (5, 8)), ((0, 5), (2, 5)), ((10, 5), (8, 5)), ((1, 1), (2, 2)), ((9, 9), (8, 8)), ((1, 9), (2, 8)), ((9, 1), (8, 2))): d.line([a, b], 255)


def g_spiral(d, r, v):
    poly(d, [(5, 5), (6, 5), (6, 6), (4, 6), (4, 4), (7, 4), (7, 7), (3, 7), (3, 3), (8, 3), (8, 8), (2, 8), (2, 2)])


def g_lightning(d, r, v):
    for a, b in (((5, 0), (4, 3)), ((4, 3), (6, 5)), ((6, 5), (5, 7)), ((5, 7), (7, 10)), ((4, 3), (2, 5)), ((6, 5), (9, 6))): d.line([a, b], 255)


def g_tree_rings(d, r, v):
    d.arc([0, 1, 10, 10], 0, 320, 255); d.arc([2, 3, 8, 8], 30, 360, 255); d.point((5, 5), 255); d.line([(5, 5), (0, 3)], 255)


def g_ritual(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255); d.ellipse([3, 3, 7, 7], outline=255)
    for a, b in (((5, 0), (5, 3)), ((0, 5), (3, 5)), ((10, 5), (7, 5)), ((2, 8), (4, 6)), ((8, 8), (6, 6)), ((5, 10), (5, 7))): d.line([a, b], 255)
    d.point((5, 5), 255)


def g_blank_grave(d, r, v):
    d.line([(1, 10), (1, 3)], 255); d.line([(9, 10), (9, 3)], 255); d.arc([1, 0, 9, 6], 180, 360, 255); d.line([(1, 10), (9, 10)], 255)


def g_seal(d, r, v):
    d.ellipse([0, 0, 10, 10], outline=255); d.line([(5, 2), (5, 8)], 255); d.line([(2, 5), (5, 5)], 255); d.line([(5, 2), (7, 4)], 255)


def g_broken_circle(d, r, v):
    d.arc([0, 0, 10, 10], 50, 310, 255)
    for p in ((5, 3), (7, 5), (5, 7), (3, 5), (4, 4)): d.point(p, 255)


def g_vow_rect(d, r, v):
    d.rectangle([0, 2, 2, 8], outline=255); d.rectangle([4, 1, 6, 9], outline=255); d.rectangle([8, 2, 10, 8], outline=255)
    d.point((5, 5), 0); d.line([(9, 3), (9, 6)], 0)


def g_grave_aligned(d, r, v):
    for x, y in ((1, 7), (5, 8), (9, 7)):
        d.rectangle([x - 1, y - 2, x + 1, y + 1], outline=255)
        d.line([(x, y - 3), (5, 0)], 255)


def g_corrupt_grid(d, r, v):
    for y in range(N):
        off = r.choice([0, 0, 1, 2, 3])
        for x in range(N):
            if ((x + off + y * 2 + v) % 3 == 0): d.point((x, y), 255)
    d.line([(0, 5), (10, 5)], 255) if v % 2 else None


def g_blank_squares(d, r, v):
    d.rectangle([0, 0, 4, 4], outline=255); d.rectangle([6, 0, 10, 4], outline=255); d.rectangle([0, 6, 4, 10], outline=255)
    d.line([(6, 6), (10, 6)], 255); d.line([(6, 6), (6, 10)], 255)


def g_star_chart(d, r, v):
    pts = [(1, 2), (4, 1), (7, 3), (9, 1), (3, 6), (6, 7), (9, 8)]
    for p in pts: d.point(p, 255)
    for a, b in ((0, 1), (1, 2), (2, 3), (4, 5), (5, 6), (1, 4)): d.line([pts[a], pts[b]], 255)
    d.arc([0, 0, 10, 10], 0, 360, 255)
    d.point((9, 1), 0); d.point((8, 1), 0)


def g_index_empty(d, r, v):
    d.rectangle([0, 0, 4, 3], outline=255); d.rectangle([6, 0, 10, 3], outline=255); d.rectangle([0, 5, 4, 8], outline=255)
    d.line([(6, 5), (10, 5)], 255); d.line([(6, 5), (6, 8)], 255); d.line([(0, 10), (10, 10)], 255)


def g_core_network(d, r, v):
    for a in ((0, 0), (10, 0), (0, 10), (10, 10), (5, 0), (5, 10), (0, 5), (10, 5)): d.line([a, (5, 5)], 255)
    d.rectangle([4, 4, 6, 6], fill=0); d.rectangle([4, 4, 6, 6], outline=255); d.point((5, 5), 255)


def g_final(d, r, v):
    rr = random.Random(900 + v)
    d.ellipse([0, 0, 10, 10], outline=255)
    d.line([(5, 1), (5, 9)], 255); d.line([(1, 5), (9, 5)], 255)
    for _ in range(3 + v): d.line([(rr.randint(1, 9), rr.randint(1, 9)), (rr.randint(1, 9), rr.randint(1, 9))], 255)
    for _ in range(3 + v): d.point((rr.randint(0, 10), rr.randint(0, 10)), 0)
    d.point((5, 5), 0 if v % 2 else 255)


GLYPHS = {k[2:]: v for k, v in globals().items() if k.startswith("g_")}


def bitmap(kind, variant=0):
    img = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(img)
    GLYPHS[kind](d, random.Random(zlib.crc32(kind.encode()) & 0xFFFF), variant)
    return ["".join("#" if img.getpixel((x, y)) > 0 else "." for x in range(N)) for y in range(N)]
