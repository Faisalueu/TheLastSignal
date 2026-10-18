package com.lastsignal.world.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Big implicit structures (towers, monoliths, domes, ziggurats, clocks, villages...). Every landmark is a pure function of
 * (theme, supercell) and is evaluated per column, so a landmark that spans many chunks is generated consistently by each of them
 * without any cross-chunk writes. Landmarks are placed before small scatter features, which then avoid their zone.
 */
final class Landmarks {
    static final int SC = 128;                 // supercell size in blocks (8 chunks)
    static final int CENTER_X = 72, CENTER_Z = 8;

    static final class LM { String type; int wx, wz, rad; long seed; }

    static int radius(String type) {
        switch (type) {
            case "tower": case "lighthouse": return 11;
            case "mast": return 7;
            case "monolith": return 14;
            case "dome": return 25;
            case "zig": return 24;
            case "arch": return 18;
            case "ring": return 27;
            case "clock": return 27;
            case "trunk": return 15;
            case "village": return 46;
            case "chapel": return 30;
            case "monastery": return 22;
            case "greenhouse": return 15;
            case "core": return 31;
            case "slab": return 16;
            case "geo": return 13;
            case "spires": return 21;
            default: return 10;
        }
    }

    static LM make(String type, int wx, int wz, long seed) {
        LM l = new LM(); l.type = type; l.wx = wx; l.wz = wz; l.rad = radius(type); l.seed = seed; return l;
    }

    static LM center(Theme t, Profile p) {
        return p.center.isEmpty() ? null : make(p.center, CENTER_X, CENTER_Z, t.seed * 7 + 3);
    }

    static LM cellLM(Theme t, Profile p, int sx, int sz) {
        if (p.lm.length == 0 || p.lmDens <= 0) return null;
        int h = Noise.hash(t.seed + 501, sx, sz);
        if ((h % 1000) / 1000.0 >= p.lmDens) return null;
        int wx = sx * SC + 48 + ((h >> 8) & 0xff) % 32, wz = sz * SC + 48 + ((h >> 16) & 0xff) % 32;
        if (Math.hypot(wx - 8, wz - 8) < 56) return null;
        LM c = center(t, p);
        String type = p.lm[((h >> 4) & 0xffff) % p.lm.length];
        LM l = make(type, wx, wz, h * 131L + t.seed);
        if (c != null && Math.hypot(c.wx - wx, c.wz - wz) < c.rad + l.rad + 6) return null;
        return l;
    }

    static boolean inZone(Gen g, int wx, int wz) {
        LM c = center(g.t, g.p);
        if (c != null && Math.hypot(wx - c.wx, wz - c.wz) < c.rad + 3) return true;
        int sx0 = Math.floorDiv(wx, SC);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            LM l = cellLM(g.t, g.p, sx0 + a, Math.floorDiv(wz, SC) + b);
            if (l != null && Math.hypot(wx - l.wx, wz - l.wz) < l.rad + 3) return true;
        }
        return false;
    }

    static void apply(Gen g) {
        if (g.p.lm.length == 0 && g.p.center.isEmpty()) return;
        LM c = center(g.t, g.p);
        if (c != null) build(g, c);
        int sx = Math.floorDiv(g.bx, SC), sz = Math.floorDiv(g.bz, SC);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            LM l = cellLM(g.t, g.p, sx + a, sz + b);
            if (l != null) build(g, l);
        }
    }

    private static void build(Gen g, LM l) {
        int lx = l.wx - g.bx, lz = l.wz - g.bz;
        if (lx < -l.rad - 1 || lx > 16 + l.rad || lz < -l.rad - 1 || lz > 16 + l.rad) return;
        Random r = new Random(l.seed);
        int yb = baseY(g, l.wx, l.wz);
        Random saved = g.r;
        g.r = r;
        switch (l.type) {
            case "tower" -> tower(g, lx, lz, yb, r, 5 + r.nextInt(4), 30 + r.nextInt(30), false);
            case "lighthouse" -> tower(g, lx, lz, yb, r, 5 + r.nextInt(2), Math.max(36, ((int) g.t.p[Theme.P_SEA] - yb) + 26), true);
            case "mast" -> mast(g, lx, lz, yb, r);
            case "monolith" -> monolith(g, lx, lz, yb, r);
            case "dome" -> dome(g, lx, lz, yb, r, g.p.center.equals("dome") ? 21 : 14 + r.nextInt(7));
            case "zig" -> zig(g, lx, lz, yb, r);
            case "arch" -> arch(g, lx, lz, yb, r);
            case "ring" -> ring(g, lx, lz, yb, r);
            case "clock" -> clock(g, lx, lz, yb, r);
            case "trunk" -> trunk(g, lx, lz, yb, r);
            case "village" -> village(g, l, yb, r);
            case "chapel" -> chapel(g, lx, lz, yb, r);
            case "monastery" -> monastery(g, lx, lz, yb, r);
            case "greenhouse" -> greenhouse(g, lx, lz, yb, r);
            case "core" -> core(g, lx, lz, yb, r);
            case "slab" -> slab(g, lx, lz, yb, r);
            case "geo" -> geo(g, lx, lz, yb, r);
            case "spires" -> spires(g, lx, lz, yb, r);
            default -> { }
        }
        g.r = saved;
    }

    static int baseY(Gen g, int wx, int wz) {
        switch (g.t.kind) {
            case "open": return Math.max(Gen.openHeight(g.t, wx, wz), (int) g.t.p[Theme.P_SEA] > 0 ? Gen.openHeight(g.t, wx, wz) : 0);
            case "deep": return Gen.deepFloor(g.t, wx, wz);
            case "sea": return Gen.seaFloor(g.t, wx, wz);
            default: return (int) g.t.p[Theme.P_BASE] + 10;
        }
    }

    private static boolean grounded(Gen g) { return g.t.kind.equals("open") || g.t.kind.equals("deep") || g.t.kind.equals("sea"); }

    /** level a circular footprint to surface height yb (solid at yb, air above up to 'clear' blocks unless that is water). */
    private static void level(Gen g, int x, int z, int yb, int clear) {
        if (!grounded(g)) return;
        int gl = g.groundAt(x, z);
        BlockState sub = g.S(Theme.SUB), top = g.S(Theme.GROUND);
        for (int y = Math.max(1, gl - 3); y < yb; y++) if (g.get(x, y, z).isAir() || !g.get(x, y, z).getFluidState().isEmpty()) g.set(x, y, z, sub);
        g.set(x, yb, z, top);
        int sea = (int) g.t.p[Theme.P_SEA];
        for (int y = yb + 1; y <= Math.min(250, Math.max(gl, yb) + clear); y++) g.set(x, y, z, (g.t.kind.equals("sea") || (sea > 0 && y <= sea)) ? Gen.WATER : Gen.AIR);
    }

    private static BlockState light(Gen g, BlockState alt) {
        BlockState l = g.S(Theme.LIGHT);
        return (l.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) ? alt : l;
    }

    // ------------------------------------------------------------------ tower / lighthouse
    private static void tower(Gen g, int lx, int lz, int yb, Random r, int R, int Ht, boolean beacon) {
        BlockState wall = g.S(Theme.WALL), acc = g.S(Theme.ACCENT), floor = g.S(Theme.FLOOR), ceil = g.S(Theme.CEIL), mark = g.S(Theme.MARK);
        int N = (int) Math.round(2 * Math.PI * (R - 2));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - lx, dz = z - lz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 2.2) continue;
            level(g, x, z, yb, Ht + 8);
            double ang = Math.atan2(dz, dx);
            if (d <= R + .5 && d >= R - 1.0) {
                for (int y = yb + 1; y <= yb + Ht; y++) {
                    int h = y - yb;
                    boolean win = h > 4 && h % 7 == 4 && (int) Math.floor((ang + Math.PI) * 3 / Math.PI) % 2 == 0;
                    boolean door = dx > 0 && Math.abs(dz) <= 1 && h <= 3;
                    if (win || door) { g.set(x, y, z, Gen.AIR); continue; }
                    g.set(x, y, z, h % 12 == 0 ? acc : (h % 17 == 3 && ((int) Math.floor((ang + Math.PI) * 6 / Math.PI)) % 3 == 0 ? mark : wall));
                }
            } else if (d < R - 1.0) {
                for (int y = yb + 1; y <= yb + Ht; y++) g.set(x, y, z, Gen.AIR);
                if (d >= R - 3.2) {                                        // climbable spiral ramp, 2 wide
                    int a = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * N) % N;
                    for (int rev = 0; ; rev++) {
                        int y = yb + 1 + a + rev * N;
                        if (y > yb + Ht - 4) break;
                        g.set(x, y, z, rev % 2 == 0 ? floor : acc);
                    }
                }
            }
            if (d <= R + .5) g.set(x, yb + Ht, z, d >= R - 3.2 ? g.get(x, yb + Ht, z) : ceil);
            if (d <= R - 1 && d >= R - 3.2) {}
            if (d >= R - 1.0 && d <= R + 1.5) g.set(x, yb + Ht + 1, z, ((int) (ang * 8) & 1) == 0 ? wall : Gen.AIR);
            if (d < 1.2) {
                for (int y = yb + Ht + 1; y <= yb + Ht + 5; y++) g.set(x, y, z, acc);
                g.set(x, yb + Ht + 6, z, light(g, acc));
                if (beacon) g.set(x, yb + Ht + 7, z, light(g, acc));
            }
        }
        if (lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14 && g.chance(.6 * g.p.chest)) { g.set(lx + 2, yb + Ht, lz, Gen.AIR); g.cache(lx + 2, yb + Ht + 1, lz); }
    }

    // ------------------------------------------------------------------ lattice mast
    private static void mast(Gen g, int lx, int lz, int yb, Random r) {
        int Ht = 38 + r.nextInt(34);
        BlockState stone = g.S(Theme.STONE), acc = g.S(Theme.ACCENT);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;
            if (Math.abs(dx) > 4 || Math.abs(dz) > 4) continue;
            level(g, x, z, yb, Ht + 4);
            boolean leg = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            for (int y = yb + 1; y <= yb + Ht; y++) {
                int h = y - yb;
                int taper = Math.max(0, 2 - h / 20);
                boolean legT = Math.abs(Math.abs(dx) - (2 - (h > Ht * 2 / 3 ? 1 : 0))) == 0 && Math.abs(Math.abs(dz) - (2 - (h > Ht * 2 / 3 ? 1 : 0))) == 0;
                if (leg || legT) g.set(x, y, z, stone);
                else if (h % 8 == 0 && Math.max(Math.abs(dx), Math.abs(dz)) == 2) g.set(x, y, z, acc);
                else if (h % 16 == 8 && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) g.set(x, y, z, stone);   // platforms
            }
            if (dx == 0 && dz == 0) { for (int y = yb + 1; y <= yb + Ht + 6; y++) g.set(x, y, z, stone); g.set(x, yb + Ht + 7, z, light(g, acc)); }
        }
        if (lx >= 3 && lx <= 12 && lz >= 3 && lz <= 12 && g.chance(.5 * g.p.chest)) g.cache(lx + 3, yb + 1, lz);
    }

    // ------------------------------------------------------------------ monolith
    private static void monolith(Gen g, int lx, int lz, int yb, Random r) {
        int w = 5 + r.nextInt(7), dd = 4 + r.nextInt(7), Ht = 28 + r.nextInt(55), tiers = 4 + r.nextInt(4), lean = r.nextInt(3) - 1;
        BlockState st = g.S(Theme.STONE), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK), wall = g.S(Theme.WALL);
        String[] gl = g.t.glyph[0];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx0 = x - lx, dz0 = z - lz;
            if (Math.abs(dx0) > w + 6 || Math.abs(dz0) > dd + 6) continue;
            level(g, x, z, yb, Ht + 4);
            for (int y = yb + 1; y <= yb + Ht; y++) {
                int h = y - yb;
                int shrink = h * tiers / Ht;
                double off = lean * (h / 14.0);
                double hx = w - shrink * (w - 2.0) / (tiers + 1), hz = dd - shrink * (dd - 2.0) / (tiers + 1);
                if (Math.abs(dx0 - off) <= hx / 2 && Math.abs(dz0) <= hz / 2) {
                    BlockState b = (h % 11 == 5) ? acc : st;
                    if (dz0 < -hz / 2 + 1 && h >= 10 && h < 32) {           // engraved face
                        int u = (int) Math.floor((dx0 - off + hx / 2) / Math.max(1, hx) * 11), v = 10 - (h - 10) / 2;
                        if (u >= 0 && u < 11 && v >= 0 && v < 11 && gl[v].charAt(u) == '#') b = mark;
                    }
                    g.set(x, y, z, b);
                }
            }
        }
        if (g.chance(.5 * g.p.chest) && lx - w / 2 - 2 >= 1 && lx - w / 2 - 2 <= 14 && lz >= 1 && lz <= 14) g.cache(lx - w / 2 - 2, yb + 1, lz);
    }

    // ------------------------------------------------------------------ dome
    private static void dome(Gen g, int lx, int lz, int yb, Random r, int R) {
        BlockState wall = g.S(Theme.WALL), glass = g.S(Theme.GLASS), floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT), stone = g.S(Theme.STONE);
        double slit = r.nextDouble() * Math.PI * 2;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - lx, dz = z - lz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 3) continue;
            level(g, x, z, yb, R + 4);
            double ang = Math.atan2(dz, dx);
            if (d <= R - 1.5) g.set(x, yb, z, floor);
            for (int y = yb + 1; y <= yb + R; y++) {
                int h = y - yb;
                double sph = Math.sqrt(d * d + h * h);
                if (sph <= R && sph >= R - 1.6) {
                    boolean door = dx > 0 && Math.abs(dz) <= 2 && h <= 4;
                    boolean sl = h > 4 && Math.abs(Math.atan2(Math.sin(ang - slit), Math.cos(ang - slit))) < .22;
                    if (door || sl) continue;
                    boolean panel = h > 3 && (((int) Math.floor((ang + Math.PI) * 4 / Math.PI)) + h / 4) % 3 != 0;
                    g.set(x, y, z, h <= 3 ? wall : (panel ? glass : (h % 8 == 0 ? acc : wall)));
                }
            }
            if (d < 2.2) for (int y = yb + 1; y <= yb + (int) (R * .6); y++) g.set(x, y, z, (d < 1.2 || y % 2 == 0) ? stone : acc);   // central instrument column
            if (d >= R - 4 && d <= R - 3) g.set(x, yb + 1, z, acc);
        }
        if (lx - 5 >= 1 && lx - 5 <= 14 && lz >= 1 && lz <= 14 && g.chance(.7 * g.p.chest)) g.cache(lx - 5, yb + 1, lz);
    }

    // ------------------------------------------------------------------ ziggurat
    private static void zig(Gen g, int lx, int lz, int yb, Random r) {
        BlockState st = g.S(Theme.STONE), wall = g.S(Theme.WALL), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK);
        int L = 4, s0 = 15;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;
            if (Math.abs(dx) > s0 + 22 || Math.abs(dz) > s0 + 2) continue;
            level(g, x, z, yb, 30);
            for (int lv = 0; lv < L; lv++) {
                int s = s0 - lv * 3 - 1;
                if (Math.abs(dx) <= s && Math.abs(dz) <= s) {
                    for (int y = yb + 1 + lv * 4; y <= yb + 4 + lv * 4; y++) g.set(x, y, z, (Math.abs(dx) == s || Math.abs(dz) == s) && y == yb + 4 + lv * 4 ? acc : (lv % 2 == 0 ? st : wall));
                }
            }
            if (Math.abs(dz) <= 1 && dx > 0) {                       // processional ramp up the +x face, 1 block rise per block
                int topH = Math.max(0, yb + 16 - (dx - (s0 - 3 * 3 - 1)));
                for (int y = yb + 1; y <= topH && y <= yb + 16; y++) g.set(x, y, z, st);
            }
            if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {            // temple on top
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int y = yb + 17; y <= yb + 20; y++) if (edge && !(dx == 3 && Math.abs(dz) <= 1)) g.set(x, y, z, wall);
                if (edge) g.set(x, yb + 21, z, st);
                else g.set(x, yb + 21, z, g.S(Theme.CEIL));
                if (dx == 0 && dz == 0) g.set(x, yb + 17, z, mark);
            }
        }
        if (lx + 1 >= 1 && lx + 1 <= 14 && lz >= 1 && lz <= 14 && g.chance(.8 * g.p.chest)) g.cache(lx - 1, yb + 17, lz + 1);
    }

    // ------------------------------------------------------------------ colossal arch
    private static void arch(Gen g, int lx, int lz, int yb, Random r) {
        int R = 12 + r.nextInt(5);
        BlockState wall = g.S(Theme.WALL), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK);
        boolean alongX = r.nextBoolean();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double u = alongX ? x - lx : z - lz, w = alongX ? z - lz : x - lx;
            if (Math.abs(w) > 2 || Math.abs(u) > R + 3) continue;
            level(g, x, z, yb, R + 6);
            for (int y = yb + 1; y <= yb + R + 3; y++) {
                double v = y - yb;
                double dd = Math.hypot(u, v);
                boolean ringB = dd <= R + 2 && dd >= R - 1;
                boolean leg = Math.abs(u) >= R - 1 && Math.abs(u) <= R + 2 && v <= 1.2;
                if (ringB && v >= 0) g.set(x, y, z, (Math.abs(u) < 1.2 && v > R - 1) ? mark : (dd > R + .6 ? acc : wall));
                else if (Math.abs(u) >= R - 1 && Math.abs(u) <= R + 2 && v < R && v >= 0 && !leg) g.set(x, y, z, wall);
            }
        }
    }

    // ------------------------------------------------------------------ ritual ring
    private static void ring(Gen g, int lx, int lz, int yb, Random r) {
        int R = 13 + r.nextInt(12), n = 5 + r.nextInt(8), pat = r.nextInt(4);
        BlockState st = g.S(Theme.STONE), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK), wall = g.S(Theme.WALL);
        double rot = r.nextDouble() * Math.PI;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - lx, dz = z - lz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 3) continue;
            level(g, x, z, yb, 14);
            double ang = Math.atan2(dz, dx) - rot;
            boolean decal = false;
            switch (pat) {
                case 0 -> decal = Math.abs(d - R) < .8 || Math.abs(d - R * .6) < .6 || d < 2.5;
                case 1 -> decal = d < R && Math.abs(Math.sin(ang * n / 2.0)) < .08 || d < 2.5;
                case 2 -> decal = Math.abs(d * Math.cos(ang - Math.round(ang / (2 * Math.PI / 3)) * 2 * Math.PI / 3) - R * .5) < .7 || d < 2.5;
                default -> decal = (Math.abs(d - R) < .7 && ((int) Math.floor((ang + 4 * Math.PI) * n / (2 * Math.PI)) % 2 == 0)) || d < 2.5;
            }
            if (decal) g.set(x, yb, z, ((int) d % 3 == 0) ? mark : acc);
        }
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n + rot;
            int sx = lx + (int) Math.round(Math.cos(a) * R), sz = lz + (int) Math.round(Math.sin(a) * R), h = 4 + r.nextInt(6);
            boolean fat = r.nextInt(3) == 0;
            for (int q = 0; q < (fat ? 4 : 1); q++) for (int y = yb + 1; y <= yb + h; y++) g.set(sx + (q & 1), y, sz + (q >> 1), (y == yb + h && pat == 3) ? light(g, acc) : (y % 5 == 0 ? acc : st));
        }
        for (int x = lx - 1; x <= lx + 1; x++) for (int z = lz - 1; z <= lz + 1; z++) { g.set(x, yb + 1, z, wall); }
        g.set(lx, yb + 2, lz, mark);
        if (lx >= 2 && lx <= 13 && lz >= 2 && lz <= 13 && g.chance(.6 * g.p.chest)) g.cache(lx, yb + 2, lz + 1 > 14 ? lz : lz + 1);
    }

    // ------------------------------------------------------------------ broken clock (vertical ring)
    private static void clock(Gen g, int lx, int lz, int yb, Random r) {
        int R = 17 + r.nextInt(8);
        boolean alongX = r.nextBoolean();
        int cy = yb + R + 4;
        BlockState ceil = g.S(Theme.CEIL), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK), stone = g.S(Theme.STONE), wall = g.S(Theme.WALL);
        double gap = r.nextDouble() * Math.PI * 2, h1 = r.nextDouble() * Math.PI * 2, h2 = r.nextDouble() * Math.PI * 2;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double u = alongX ? x - lx : z - lz, w = alongX ? z - lz : x - lx;
            if (Math.abs(w) > 2 || Math.abs(u) > R + 3) continue;
            level(g, x, z, yb, 4);
            if (Math.abs(w) <= 1 && Math.abs(u) <= R * .6 + 1 && (Math.abs(u) >= R * .6 - 1)) for (int y = yb + 1; y < cy - R + 2; y++) g.set(x, y, z, stone);   // support pylons
            for (int y = yb + 1; y <= cy + R + 1; y++) {
                double v = y - cy, d = Math.hypot(u, v);
                double a = Math.atan2(v, u);
                boolean inGap = Math.abs(Math.atan2(Math.sin(a - gap), Math.cos(a - gap))) < .45;
                if (d <= R && d >= R - 1.8 && Math.abs(w) <= 1 && !inGap) g.set(x, y, z, d > R - .8 ? acc : ceil);
                if (d < R - 1.8 && d > R - 5 && Math.abs(w) == 0) {                                  // 12 tick marks
                    double q = (a + 2 * Math.PI) % (Math.PI / 6);
                    if (q < .09 || q > Math.PI / 6 - .09) g.set(x, y, z, mark);
                }
                if (Math.abs(w) == 0 && d < R * .7 && Math.abs(Math.atan2(Math.sin(a - h1), Math.cos(a - h1))) < .045 && d < R * .55) g.set(x, y, z, wall);
                if (Math.abs(w) == 0 && d < R * .9 && Math.abs(Math.atan2(Math.sin(a - h2), Math.cos(a - h2))) < .035) g.set(x, y, z, stone);
                if (d < 1.8 && Math.abs(w) <= 1) g.set(x, y, z, mark);
            }
        }
    }

    // ------------------------------------------------------------------ giant root trunk with climbable root ramp
    private static void trunk(Gen g, int lx, int lz, int yb, Random r) {
        int R = 4 + r.nextInt(3);
        int top = grounded(g) && g.t.kind.equals("deep") ? Gen.deepCeil(g.t, g.bx + lx, g.bz + lz) : yb + 50;
        BlockState wall = g.S(Theme.WALL), acc = g.S(Theme.ACCENT), floor = g.S(Theme.FLOOR);
        int RR = R + 3, N = (int) Math.round(2 * Math.PI * (R + 2));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - lx, dz = z - lz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > RR + 5) continue;
            double ang = Math.atan2(dz, dx);
            double flare = Math.max(0, 8 - (0)) ;
            int t0 = top;
            for (int y = yb + 1; y <= t0; y++) {
                int h = y - yb;
                double rr = R + Math.max(0, 7 - h) * .6 + (Noise.value(g.t.seed + 33, y / 6.0, ang * 2) - .5) * 1.4;
                if (d <= rr) g.set(x, y, z, ((h + (int) (ang * 3)) % 9 == 0) ? acc : wall);
            }
            if (d > R + 1.2 && d <= R + 3.4) {
                int a = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * N) % N;
                for (int rev = 0; ; rev++) {
                    int y = yb + 1 + a + rev * N;
                    if (y > t0 - 5) break;
                    g.set(x, y, z, (a % 5 == 0) ? acc : floor);
                }
            }
        }
    }

    // ------------------------------------------------------------------ village (irregular plots, streets, wells)
    private static void village(Gen g, LM l, int yb, Random r0) {
        BlockState wall = g.S(Theme.WALL), roof = g.S(Theme.CEIL), floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT), stone = g.S(Theme.STONE);
        int cell = 23;
        int ox = l.wx - 46, oz = l.wz - 46;
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            int h = Noise.hash(l.seed, i, j);
            if (h % 100 < 22) continue;                         // empty plot (courtyard / field)
            int w = 5 + h % 5, d = 5 + (h >> 4) % 6;
            int px = ox + i * cell + 3 + ((h >> 8) & 7), pz = oz + j * cell + 3 + ((h >> 12) & 7);
            int lx = px - g.bx, lz = pz - g.bz;
            if (lx > 16 || lx + w < -1 || lz > 16 || lz + d < -1) continue;
            Random r = new Random(h * 977L + 13);
            g.r = r;
            int gy = (int) Math.round(avgGround(g, lx, lz, w, d));
            int hgt = 3 + (h >> 16) % 3 + ((h >> 20) % 7 == 0 ? 4 : 0);
            boolean big = (h >> 24) % 9 == 0;
            if (big) { w += 4; d += 4; hgt += 2; }
            int doorSide = (h >> 3) & 3;
            for (int x = lx - 1; x <= lx + w; x++) for (int z = lz - 1; z <= lz + d; z++) {
                for (int y = gy - 1; y > gy - 7 && y > 1; y--) { if (x < 0 || x > 15 || z < 0 || z > 15) break; if (g.get(x, y, z).isAir() || !g.get(x, y, z).getFluidState().isEmpty()) g.set(x, y, z, stone); else break; }
            }
            for (int x = lx; x < lx + w; x++) for (int z = lz; z < lz + d; z++) {
                boolean edge = x == lx || x == lx + w - 1 || z == lz || z == lz + d - 1;
                g.set(x, gy, z, floor);
                for (int y = gy + 1; y <= gy + hgt; y++) {
                    if (!edge) { g.set(x, y, z, Gen.AIR); continue; }
                    int hh = Noise.hash(l.seed + 5, x + g.bx, z + g.bz * 7 + y);
                    boolean door = (doorSide == 0 && z == lz && x == lx + w / 2) || (doorSide == 1 && z == lz + d - 1 && x == lx + w / 2) || (doorSide == 2 && x == lx && z == lz + d / 2) || (doorSide == 3 && x == lx + w - 1 && z == lz + d / 2);
                    if (door && y <= gy + 2) { g.set(x, y, z, Gen.AIR); continue; }
                    boolean win = y == gy + 2 && ((x + z) % 3 == 0);
                    if (win || hh % 100 < 9 + (y - gy) * 2) { g.set(x, y, z, Gen.AIR); continue; }     // broken walls, windows
                    g.set(x, y, z, (y == gy + hgt) ? acc : wall);
                }
                if (r.nextInt(100) < 45) g.set(x, gy + hgt + 1, z, roof);                        // partial roof
            }
            if (r.nextInt(100) < 30 * g.p.chest + 8) {
                int cx2 = lx + w / 2, cz2 = lz + d / 2;
                g.cache(cx2, gy + 1, cz2);
            }
            // well / courtyard decoration in some plots
            if ((h >> 5) % 5 == 0) {
                int wx = lx + w + 3, wz = lz + 2, wy = (int) Math.round(avgGround(g, wx, wz, 1, 1));
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) g.set(wx + a, wy + 1, wz + b, a == 0 && b == 0 ? Blocks.WATER.defaultBlockState() : stone);
            }
        }
        g.r = r0;
    }

    private static double avgGround(Gen g, int lx, int lz, int w, int d) {
        double s = 0; int n = 0;
        for (int a = 0; a <= 1; a++) for (int b = 0; b <= 1; b++) { s += g.groundAt(lx + a * (w - 1), lz + b * (d - 1)); n++; }
        return s / n;
    }

    // ------------------------------------------------------------------ chapel (gable nave, tower, apse)
    private static void chapel(Gen g, int lx, int lz, int yb, Random r) {
        BlockState wall = g.S(Theme.WALL), roof = g.S(Theme.CEIL), floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT), glass = g.S(Theme.GLASS),
                mark = g.S(Theme.MARK), stone = g.S(Theme.STONE);
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState();
        int W = 9, Ln = 21, wallH = 8;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;          // nave along +x, width along z
            if (dx < -4 || dx > Ln + 10 || Math.abs(dz) > 8) continue;
            level(g, x, z, yb, 24);
            boolean nave = dx >= 0 && dx < Ln && Math.abs(dz) <= W / 2;
            boolean apse = dx >= Ln && Math.hypot(dx - Ln, dz) <= W / 2 + .5;
            boolean tower = dx >= -4 && dx < 1 && Math.abs(dz) <= 2;
            if (nave || apse || tower) {
                boolean edge = nave ? (dx == 0 || dx == Ln - 1 || Math.abs(dz) == W / 2) : apse ? Math.hypot(dx - Ln, dz) > W / 2 - .6 : (dx == -4 || Math.abs(dz) == 2);
                g.set(x, yb, z, floor);
                int hgt = tower ? 20 : wallH;
                for (int y = yb + 1; y <= yb + hgt; y++) {
                    int h = y - yb;
                    if (!edge) { g.set(x, y, z, Gen.AIR); continue; }
                    boolean door = (dx == 0 && Math.abs(dz) <= 1 && h <= 4) && nave;
                    boolean win = nave && Math.abs(dz) == W / 2 && h >= 3 && h <= 6 && dx % 4 == 2;
                    boolean bell = tower && h >= 15 && h <= 18 && (Math.abs(dz) == 2 ? dx == -2 : dx == -4 || dx == 0);
                    if (door || bell) g.set(x, y, z, Gen.AIR);
                    else if (win) g.set(x, y, z, glass);
                    else g.set(x, y, z, h % 8 == 0 ? acc : wall);
                }
                if (nave) {                                                                // gable roof
                    int rh = wallH + 1 + (W / 2 - Math.abs(dz));
                    for (int y = yb + wallH + 1; y <= yb + rh; y++) if (y == yb + rh || Math.abs(dz) == W / 2) g.set(x, y, z, roof);
                } else g.set(x, yb + hgt + 1, z, roof);
                if (tower) { g.set(x, yb + hgt + 1, z, roof); if (dx == -2 && dz == 0) for (int y = yb + hgt + 2; y <= yb + hgt + 5; y++) g.set(x, y, z, stone); }
                if (nave && !edge && dx >= 3 && dx <= 14 && Math.abs(dz) >= 1 && Math.abs(dz) <= 3 && dx % 2 == 1) g.set(x, yb + 1, z, slab);          // pews
            }
        }
        if (lx + 17 >= 1 && lx + 17 <= 14 && lz >= 1 && lz <= 14) {
            g.set(lx + 18, yb + 1, lz, stone); g.set(lx + 18, yb + 2, lz, mark);
            if (g.chance(.8 * g.p.chest)) g.cache(lx + 17, yb + 1, lz + 2);
        }
    }

    // ------------------------------------------------------------------ monastery (cloister ring with towers)
    private static void monastery(Gen g, int lx, int lz, int yb, Random r) {
        BlockState wall = g.S(Theme.WALL), roof = g.S(Theme.CEIL), floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT), stone = g.S(Theme.STONE), mark = g.S(Theme.MARK);
        int S = 19;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m > S / 2 + 4) continue;
            level(g, x, z, yb, 22);
            boolean ringB = m <= S / 2 && m >= S / 2 - 4;
            boolean outer = m == S / 2, inner = m == S / 2 - 4;
            boolean corner = Math.abs(dx) >= S / 2 - 1 && Math.abs(dz) >= S / 2 - 1;
            if (ringB) {
                g.set(x, yb, z, floor);
                int hgt = corner ? 12 : 6;
                for (int y = yb + 1; y <= yb + hgt; y++) {
                    int h = y - yb;
                    boolean doorG = dz == S / 2 && Math.abs(dx) <= 1 && h <= 4;       // gate
                    boolean arcade = inner && !corner && (dx + dz) % 3 != 0 && h <= 4;
                    boolean cellWin = outer && h == 3 && (dx + dz) % 4 == 0;
                    boolean cellDoor = !outer && !inner && false;
                    if (doorG || arcade || cellWin || (corner && !outer && !(Math.abs(dx) == S / 2 - 1 || Math.abs(dz) == S / 2 - 1) && h < hgt)) { g.set(x, y, z, Gen.AIR); continue; }
                    if (outer || inner || corner) g.set(x, y, z, h % 6 == 0 ? acc : wall);
                    else if (!corner && (m == S / 2 - 2) && Math.abs(dx) != Math.abs(dz) && ((m % 2 == 0 && (Math.abs(dx) == m)) || (Math.abs(dz) == m)) && (dx + dz + h) % 6 != 0) g.set(x, y, z, wall);   // cell partitions
                    else g.set(x, y, z, Gen.AIR);
                }
                g.set(x, yb + (corner ? 13 : 7), z, roof);
            } else if (m < S / 2 - 4) {                                                   // garden court
                g.set(x, yb, z, ((dx * 7 + dz * 3) % 5 == 0) ? stone : g.S(Theme.GROUND));
                if (m <= 1) { g.set(x, yb + 1, z, stone); if (m == 0) g.set(x, yb + 2, z, mark); }
            }
        }
        if (lx >= 2 && lx <= 13 && lz + 6 >= 1 && lz + 6 <= 14 && g.chance(.8 * g.p.chest)) g.cache(lx, yb + 1, lz + 7 > 14 ? lz + 6 : lz + 7);
    }

    // ------------------------------------------------------------------ greenhouse (broken glass house)
    private static void greenhouse(Gen g, int lx, int lz, int yb, Random r) {
        BlockState glass = g.S(Theme.GLASS), frame = g.S(Theme.WALL), floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT);
        int W = 13, Ln = 19;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;
            if (dx < -1 || dx > Ln + 1 || Math.abs(dz) > W / 2 + 1) continue;
            level(g, x, z, yb, 12);
            boolean in = dx >= 0 && dx < Ln && Math.abs(dz) <= W / 2;
            if (!in) continue;
            boolean edge = dx == 0 || dx == Ln - 1 || Math.abs(dz) == W / 2;
            g.set(x, yb, z, floor);
            int ridge = 6 + (W / 2 - Math.abs(dz)) / 2;
            for (int y = yb + 1; y <= yb + ridge; y++) {
                int h = y - yb;
                int hh = Noise.hash(g.t.seed + 12, (g.bx + x) * 3 + y, g.bz + z);
                if (edge && h <= 5) { boolean post = (dx % 4 == 0) || (Math.abs(dz) == W / 2 && dz % 4 == 0); boolean door = dx == 0 && Math.abs(dz) <= 1 && h <= 3; if (door) g.set(x, y, z, Gen.AIR); else if (hh % 100 < 14) g.set(x, y, z, Gen.AIR); else g.set(x, y, z, post ? frame : glass); }
                else if (h == ridge && hh % 100 >= 20) g.set(x, y, z, (dx % 4 == 0) ? frame : glass);
                else g.set(x, y, z, Gen.AIR);
            }
            if (!edge && (dx * 5 + dz * 3) % 4 == 0) { g.set(x, yb + 1, z, Blocks.DEAD_BUSH.defaultBlockState()); }
            if (!edge && Math.abs(dz) <= 1 && dx % 5 == 2) g.set(x, yb + 1, z, acc);
        }
        if (lx + 8 >= 1 && lx + 8 <= 14 && lz >= 1 && lz <= 14 && g.chance(.8 * g.p.chest)) g.cache(lx + 9, yb + 1, lz);
    }

    // ------------------------------------------------------------------ signal core (central source)
    private static void core(Gen g, int lx, int lz, int yb, Random r) {
        BlockState st = g.S(Theme.STONE), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK), wall = g.S(Theme.WALL), glass = g.S(Theme.GLASS), floor = g.S(Theme.FLOOR);
        int top = g.t.kind.equals("deep") ? Gen.deepCeil(g.t, g.bx + lx, g.bz + lz) - 2 : yb + 60;
        int Ht = Math.max(30, top - yb);
        int R = 8, N = (int) Math.round(2 * Math.PI * (R - 2));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - lx, dz = z - lz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > 28) continue;
            double ang = Math.atan2(dz, dx);
            if (d <= 25) { level(g, x, z, yb, Ht + 4); if (d <= 24 && d >= 20) { g.set(x, yb, z, (int) (ang * 4 + 40) % 2 == 0 ? floor : acc); } if (d <= 20) g.set(x, yb, z, ((int) d % 4 == 0) ? acc : floor); }
            for (int k = 0; k < 8; k++) {                                              // conduits radiating from the core
                double a = k * Math.PI / 4, along = dx * Math.cos(a) + dz * Math.sin(a), side = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a));
                if (along > R && along < 28 && side < .7) g.set(x, yb, z, along % 4 < 1 ? light(g, acc) : acc);
            }
            if (d <= R + .5 && d >= R - 1) {
                for (int y = yb + 1; y <= yb + Ht; y++) {
                    int h = y - yb;
                    boolean door = dx > 0 && Math.abs(dz) <= 1 && h <= 4;
                    if (door) { g.set(x, y, z, Gen.AIR); continue; }
                    boolean slit = h % 9 == 4 && ((int) Math.floor((ang + Math.PI) * 4 / Math.PI)) % 2 == 0;
                    g.set(x, y, z, slit ? glass : (h % 9 == 0 ? acc : (h % 23 == 5 ? mark : wall)));
                }
            } else if (d < R - 1) {
                for (int y = yb + 1; y <= yb + Ht; y++) g.set(x, y, z, Gen.AIR);
                if (d >= R - 3.2) {
                    int a2 = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * N) % N;
                    for (int rev = 0; ; rev++) { int y = yb + 1 + a2 + rev * N; if (y > yb + Ht - 5) break; g.set(x, y, z, floor); }
                }
                if (d < 1.2) for (int y = yb + 1; y <= yb + Ht; y++) g.set(x, y, z, (y % 3 == 0) ? light(g, acc) : mark);       // the core itself
            }
            for (int band = 0; band < 3; band++) {                                   // glowing ring bands
                int by = yb + 14 + band * Math.max(8, Ht / 4);
                if (d > R + .5 && d <= R + 4 && by <= yb + Ht) { g.set(x, by, z, d > R + 3 ? light(g, acc) : acc); g.set(x, by + 1, z, d > R + 3 ? acc : Gen.AIR); }
            }
            if (d <= R + 1.5 && d >= R - 1) g.set(x, yb + Ht + 1, z, st);
        }
        if (lx + R + 4 >= 1 && lx + R + 4 <= 14 && lz >= 1 && lz <= 14 && g.chance(.9 * g.p.chest)) g.cache(lx + R + 4, yb + 1, lz);
    }

    // ------------------------------------------------------------------ glyph slab (floating, vertical)
    private static void slab(Gen g, int lx, int lz, int yb, Random r) {
        BlockState mark = g.S(Theme.MARK), st = g.S(Theme.STONE);
        boolean alongX = r.nextBoolean();
        int cy = yb + 18 + r.nextInt(14);
        String[] gl = g.t.glyph[r.nextInt(g.t.glyph.length)];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double u = alongX ? x - lx : z - lz, w = alongX ? z - lz : x - lx;
            if (Math.abs(w) > 1 || Math.abs(u) > 12) continue;
            for (int y = cy - 12; y <= cy + 12; y++) {
                int uu = (int) Math.floor((u + 11) / 2), vv = (int) Math.floor((cy + 11 - y) / 2.0);
                boolean frame = Math.abs(u) >= 11 || Math.abs(y - cy) >= 11;
                if (Math.abs(u) > 11 || Math.abs(y - cy) > 11) continue;
                if (frame) g.set(x, y, z, st);
                else if (uu >= 0 && uu < 11 && vv >= 0 && vv < 11 && gl[vv].charAt(uu) == '#') g.set(x, y, z, mark);
            }
        }
    }

    // ------------------------------------------------------------------ isolated geometric objects (white void)
    private static void geo(Gen g, int lx, int lz, int yb, Random r) {
        int type = r.nextInt(3), s = 9 + r.nextInt(6);
        BlockState a = g.S(Theme.WALL), b = g.S(Theme.ACCENT), m = g.S(Theme.MARK);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = x - lx, dz = z - lz;
            if (Math.abs(dx) > s || Math.abs(dz) > s) continue;
            level(g, x, z, yb, 2);
            for (int y = yb + 1; y <= yb + 2 * s; y++) {
                int h = y - yb;
                boolean in;
                if (type == 0) { int sh = Math.max(0, s - h / 2); in = Math.abs(dx) <= sh && Math.abs(dz) <= sh; }
                else if (type == 1) { in = Math.abs(dx) <= s / 2 + 1 && Math.abs(dz) <= s / 2 + 1 && h <= s + 2 && (Math.abs(dx) >= s / 2 || Math.abs(dz) >= s / 2 || h == s + 2 || h == 1); }
                else { in = Math.hypot(Math.hypot(dx, dz), h - s) <= s * .8 && Math.hypot(Math.hypot(dx, dz), h - s) >= s * .8 - 1.5; }
                if (in) g.set(x, y, z, (h % 6 == 0) ? b : ((dx == 0 && h % 4 == 1) ? m : a));
            }
        }
    }

    // ------------------------------------------------------------------ spire clusters
    private static void spires(Gen g, int lx, int lz, int yb, Random r) {
        int n = 8 + r.nextInt(7);
        BlockState acc = g.S(Theme.ACCENT), glass = g.S(Theme.GLASS), st = g.S(Theme.STONE);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, dd = r.nextDouble() * 16;
            int sx = lx + (int) Math.round(Math.cos(a) * dd), sz = lz + (int) Math.round(Math.sin(a) * dd), h = 8 + r.nextInt(20), rad = 1 + r.nextInt(3);
            BlockState b = r.nextInt(3) == 0 ? glass : (r.nextBoolean() ? acc : st);
            for (int y = 1; y <= h; y++) {
                int rr = Math.max(0, rad - y * (rad + 1) / (h + 1));
                for (int xx = -rr; xx <= rr; xx++) for (int zz = -rr; zz <= rr; zz++) if (xx * xx + zz * zz <= rr * rr + 1) {
                    int px = sx + xx, pz = sz + zz;
                    if (px < 0 || px > 15 || pz < 0 || pz > 15) continue;
                    int base = grounded(g) ? g.groundAt(px, pz) : yb;
                    g.set(px, base + y, pz, b);
                }
            }
        }
    }

    private Landmarks() {}
}
