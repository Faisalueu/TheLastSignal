package com.lastsignal.world.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Rooms" terrain: a deterministic grid of enclosed spaces built chunk-by-chunk. Unlike the first version (one 12x12 room per chunk),
 * each theme mixes archetypes (see Profile): single cells, multi-chunk halls, pits, tall shafts, crawlspaces, organic caves, pillared rooms.
 * All walls/doors are derived from pure functions of chunk coordinates, so neighbours always agree.
 */
final class Rooms {
    static final int Y0 = Gen.Y0;

    static final class Desc { int arch, sx, sz; boolean hall; }

    static Desc desc(Theme t, Profile p, int cx, int cz) {
        Desc d = new Desc();
        int M = Math.max(1, p.sc);
        d.sx = Math.floorDiv(cx, M); d.sz = Math.floorDiv(cz, M);
        double total = 0; for (double w : p.arch) total += w;
        double hp = total <= 0 ? 0 : p.arch[Profile.HALL] / total;
        if (hp > 0 && (Noise.hash(t.seed + 401, d.sx, d.sz) % 10000) / 10000.0 < hp) { d.arch = Profile.HALL; d.hall = true; return d; }
        double rest = total - p.arch[Profile.HALL];
        double v = (Noise.hash(t.seed + 402, cx, cz) % 10000) / 10000.0 * rest;
        int pick = Profile.CELL;
        for (int i = 0; i < p.arch.length; i++) { if (i == Profile.HALL) continue; if (v < p.arch[i]) { pick = i; break; } v -= p.arch[i]; }
        if (cx == 0 && cz == 0 && (pick == Profile.PIT || pick == Profile.SHAFT || pick == Profile.ORGANIC)) pick = Profile.CELL;
        d.arch = pick;
        return d;
    }

    static boolean same(Theme t, Profile p, Desc a, int cx, int cz) {
        if (!a.hall) return false;
        Desc b = desc(t, p, cx, cz);
        return b.hall && b.sx == a.sx && b.sz == a.sz;
    }

    static boolean open(Theme t, Profile p, int ax, int az, int bx, int bz) {
        if (Math.abs(ax) <= 1 && Math.abs(az) <= 1 && Math.abs(bx) <= 1 && Math.abs(bz) <= 1) return true;
        Desc a = desc(t, p, ax, az), b = desc(t, p, bx, bz);
        if (a.arch == Profile.COLUMNS || b.arch == Profile.COLUMNS) return true;
        boolean horiz = ax != bx;
        int lim = (int) t.p[Theme.P_OPEN] + ((a.hall || b.hall) ? 15 : 0);
        return Noise.hash(t.seed + (horiz ? 101 : 202), Math.min(ax, bx), Math.min(az, bz)) % 100 < lim;
    }

    static int ceilH(Theme t, Profile p, Desc d, int cx, int cz) {
        int H = (int) t.p[Theme.P_H];
        switch (d.arch) {
            case Profile.HALL: return Math.max(p.hallH, H);
            case Profile.CRAWL: return 3;
            case Profile.SHAFT: return H + 36;
            case Profile.ORGANIC: return H + 3;
            case Profile.COLUMNS: return H + 3;
            case Profile.PIT: return H + 2;
            default: return Math.max(4, H + (Noise.hash(t.seed + 403, cx, cz) % 3) - 1);
        }
    }

    static void run(Gen g) {
        final Theme t = g.t; final Profile p = g.p;
        final int cx = g.cx, cz = g.cz;
        Desc d = desc(t, p, cx, cz);
        int H = ceilH(t, p, d, cx, cz);
        final int ceilY = Y0 + H;
        g.ceilY = ceilY;
        boolean wN = !same(t, p, d, cx, cz - 1), wS = !same(t, p, d, cx, cz + 1), wW = !same(t, p, d, cx - 1, cz), wE = !same(t, p, d, cx + 1, cz);
        boolean dN = wN && open(t, p, cx, cz - 1, cx, cz), dS = wS && open(t, p, cx, cz, cx, cz + 1),
                dW = wW && open(t, p, cx - 1, cz, cx, cz), dE = wE && open(t, p, cx, cz, cx + 1, cz);
        int bw = d.arch == Profile.SHAFT ? 3 : 2;
        int xlo = wW ? bw : 0, xhi = wE ? 15 - bw : 15, zlo = wN ? bw : 0, zhi = wS ? 15 - bw : 15;
        int dw = d.arch == Profile.CRAWL ? 2 : d.arch == Profile.COLUMNS ? 6 : 4;
        int dlo = 8 - dw / 2, dhi = dlo + dw - 1;
        int doorH = Math.min(H, 4);
        if (d.arch == Profile.CRAWL) doorH = 2;
        boolean[][] in = new boolean[16][16], door = new boolean[16][16];
        int[][] colCeil = new int[16][16];
        BlockState stone = g.S(Theme.STONE), floor = g.S(Theme.FLOOR), wall = g.S(Theme.WALL), ceil = g.S(Theme.CEIL);
        boolean rough = t.has("rough") || d.arch == Profile.CRAWL;
        boolean organic = d.arch == Profile.ORGANIC;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            colCeil[x][z] = ceilY;
            boolean inside = x >= xlo && x <= xhi && z >= zlo && z <= zhi;
            boolean dr = false;
            if (x >= dlo && x <= dhi) { if (z < bw && dN && wN) dr = true; if (z > 15 - bw && dS && wS) dr = true; }
            if (z >= dlo && z <= dhi) { if (x < bw && dW && wW) dr = true; if (x > 15 - bw && dE && wE) dr = true; }
            in[x][z] = inside; door[x][z] = dr;
        }
        if (organic) organic(g, d, in, door, colCeil, dN, dS, dW, dE, H);
        // ---- base column fill
        BlockState bed = Gen.BEDROCK;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            boolean i = in[x][z], dr = door[x][z];
            g.set(x, 0, z, bed);
            for (int y = 1; y < Y0 - 1; y++) g.set(x, y, z, stone);
            g.set(x, Y0 - 1, z, (i || dr) ? floor : stone);
            g.gh[x][z] = Y0 - 1;
            int cc = colCeil[x][z];
            if (i) {
                for (int y = cc + 1; y <= ceilY + 3; y++) g.set(x, y, z, organic ? wall : stone);
                g.set(x, cc, z, organic ? wall : ceil);
            } else if (dr) {
                for (int y = Y0 + doorH; y <= ceilY; y++) g.set(x, y, z, wall);
                for (int y = ceilY + 1; y <= ceilY + 3; y++) g.set(x, y, z, stone);
            } else {
                for (int y = Y0; y <= ceilY; y++) g.set(x, y, z, (rough && g.r.nextInt(5) < 2) ? stone : wall);
                for (int y = ceilY + 1; y <= ceilY + 3; y++) g.set(x, y, z, stone);
            }
            g.set(x, ceilY + 4, z, bed);
            if (i || dr) {
                int top = Math.min(cc, ceilY);
                for (int y = Y0; y < top; y++) g.set(x, y, z, Gen.AIR);
            }
        }
        // ---- archetype shaping
        switch (d.arch) {
            case Profile.PIT -> pit(g, in);
            case Profile.SHAFT -> shaft(g);
            case Profile.CRAWL -> { int n = g.rnd(4, 8); for (int k = 0; k < n; k++) { int px = g.rnd(3, 12), pz = g.rnd(3, 12); for (int y = Y0; y < ceilY; y++) g.set(px, y, pz, stone); } }
            case Profile.COLUMNS -> { for (int[] q : new int[][]{{4, 4}, {4, 11}, {11, 4}, {11, 11}}) for (int y = Y0; y < ceilY; y++) g.set(q[0], y, q[1], (y == Y0 || y == ceilY - 1) ? g.S(Theme.ACCENT) : wall); }
            case Profile.HALL -> hall(g, d, in, H);
            default -> { }
        }
        // ---- wall inlays on faces next to open air
        BlockState acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (in[x][z] || door[x][z]) continue;
            for (int y = Y0; y < ceilY; y++) {
                if (g.get(x, y, z) != wall) continue;
                if (!(g.get(x + 1, y, z).isAir() || g.get(x - 1, y, z).isAir() || g.get(x, y, z + 1).isAir() || g.get(x, y, z - 1).isAir())) continue;
                int h = Noise.hash(t.seed + 9, (g.bx + x) * 31 + y, (g.bz + z) * 17 + y * 3) % 1000;
                if (h < 35) g.set(x, y, z, acc); else if (h < 50) g.set(x, y, z, mark);
            }
        }
        boolean spawn = cx == 0 && cz == 0;
        // ---- decoration
        if (d.arch != Profile.SHAFT && d.arch != Profile.PIT && g.chance(t.p[Theme.P_MARK])) g.stampFloor(2 + g.r.nextInt(2), 2 + g.r.nextInt(2), Y0 - 1, false);
        if (H >= 12 && wN && g.chance(t.p[Theme.P_MURAL])) g.stampWall(true, 1, 2 + g.r.nextInt(2), Y0 + 1 + Math.max(0, (H - 12) / 2), wall);
        boolean small = d.arch == Profile.CELL || d.arch == Profile.CRAWL || d.arch == Profile.COLUMNS || d.arch == Profile.ORGANIC;
        if (!spawn && small && t.feats.length > 0 && g.chance(t.p[Theme.P_FEAT]))
            Features.build(t.feats[g.r.nextInt(t.feats.length)], g, Y0, Math.min(H, 8), true);
        int lamps = g.chance(t.p[Theme.P_LIGHT]) ? 1 + (g.chance(.4) ? 1 : 0) : 0;
        if (spawn) lamps = 2;
        if (d.arch == Profile.CRAWL) lamps = g.chance(.5) ? 1 : 0;
        for (int i = 0; i < lamps; i++) { int lx = g.rnd(3, 12), lz = g.rnd(3, 12); if (in[lx][lz] && colCeil[lx][lz] == ceilY) g.lampXZ(lx, lz, ceilY); }
        // ---- chests (rare; only on clear floor)
        double cp = (d.arch == Profile.HALL ? .30 : d.arch == Profile.CRAWL ? .22 : .17) * p.chest;
        if (!spawn && g.chance(cp)) {
            for (int tries = 0; tries < 6; tries++) {
                int qx = g.rnd(3, 12), qz = g.rnd(3, 12);
                if (!in[qx][qz]) continue;
                if (g.get(qx, Y0, qz).isAir() && g.get(qx, Y0 + 1, qz).isAir() && g.get(qx - 1, Y0, qz).isAir() && g.get(qx + 1, Y0, qz).isAir()
                        && g.get(qx, Y0 - 1, qz) == floor) { g.cache(qx, Y0, qz); break; }
            }
        }
    }

    // ------------------------------------------------------------------ archetypes
    private static void pit(Gen g, boolean[][] in) {
        BlockState floor = g.S(Theme.FLOOR);
        int depth = 8;
        for (int x = 4; x <= 11; x++) for (int z = 4; z <= 11; z++) {
            if (!in[x][z]) continue;
            g.set(x, Y0 - 1, z, Gen.AIR);
            for (int y = Y0 - depth; y < Y0 - 1; y++) g.set(x, y, z, Gen.AIR);
            g.set(x, Y0 - 1 - depth, z, floor);
            g.gh[x][z] = Y0 - 1 - depth;
        }
        for (int x = 4; x <= 11; x++) for (int z = 4; z <= 5; z++) {     // staircase down along the north side
            int top = Y0 - 2 - (x - 4);
            for (int y = Y0 - 1 - depth; y <= top; y++) g.set(x, y, z, floor);
        }
        g.set(8, Y0 - depth, 9, g.S(Theme.ACCENT));
        if (g.chance(.5 * g.p.chest)) g.cache(10, Y0 - depth, 10);
    }

    private static int ringIndex(int x, int z) {
        if (z == 3) return x - 3;
        if (x == 12) return 9 + (z - 3);
        if (z == 12) return 18 + (12 - x);
        if (x == 3) return 27 + (12 - z);
        return -1;
    }

    private static void shaft(Gen g) {
        BlockState floor = g.S(Theme.FLOOR), acc = g.S(Theme.ACCENT);
        for (int x = 3; x <= 12; x++) for (int z = 3; z <= 12; z++) {
            int k = ringIndex(x, z);
            if (k < 0) continue;
            g.set(x, Y0 + k, z, k % 6 == 0 ? acc : floor);
        }
        g.set(8, Y0 - 1, 8, g.S(Theme.MARK));
        if (g.chance(.6 * g.p.chest)) g.cache(3, Y0 + 36, 4);     // reward at the top of the spiral walkway
    }

    // ------------------------------------------------------------------ halls (multi-chunk)
    private static void hall(Gen g, Desc d, boolean[][] in, int H) {
        Theme t = g.t; Profile p = g.p;
        int S = Math.max(1, p.sc) * 16;
        int wx0 = d.sx * S, wz0 = d.sz * S;
        String style = p.hallStyle;
        BlockState wall = g.S(Theme.WALL), stone = g.S(Theme.STONE), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK), glass = g.S(Theme.GLASS),
                floor = g.S(Theme.FLOOR), ceil = g.S(Theme.CEIL);
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState();
        int ghh = Math.max(3, H / 2);
        boolean gallery = H >= 12 && (style.equals("shelves") || style.equals("colonnade") || style.equals("pews") || style.equals("machines") || style.equals("seats"));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (!in[x][z]) continue;
            int rx = g.bx + x - wx0, rz = g.bz + z - wz0;
            // hanging lights on a grid
            if (rx % 12 == 6 && rz % 12 == 6) g.lampXZ(x, z, Y0 + H);
            if (gallery) {
                if (rz >= 2 && rz <= 3 && rx >= 6 && rx <= 6 + ghh) {
                    int top = Y0 + (rx - 6);
                    for (int y = Y0; y <= top; y++) g.set(x, y, z, floor);
                } else if (rz <= 4 && rx >= 7 + ghh && rx <= S - 3) {
                    g.set(x, Y0 + ghh, z, acc);
                }
            }
            boolean core = rx >= 6 && rx <= S - 7 && rz >= 6 && rz <= S - 7;
            if (!core) continue;
            switch (style) {
                case "colonnade" -> {
                    if ((rx % 8 == 3 || rx % 8 == 4) && (rz % 8 == 3 || rz % 8 == 4)) for (int y = Y0; y < Y0 + H; y++) g.set(x, y, z, (y < Y0 + 2 || y >= Y0 + H - 2) ? acc : wall);
                }
                case "shelves" -> {
                    int hh = Math.min(H - 1, 7);
                    if ((rz % 7 == 1 || rz % 7 == 2) && !(rx % 16 >= 7 && rx % 16 <= 9)) {
                        for (int y = Y0; y < Y0 + hh; y++) g.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
                        g.set(x, Y0 + hh, z, acc);
                    }
                }
                case "seats" -> {
                    int stageEnd = S / 2 - 2;
                    if (rz < stageEnd) {
                        if (rz >= 8 && rx >= S / 4 && rx <= 3 * S / 4) { g.set(x, Y0, z, floor); g.set(x, Y0 + 1, z, floor); }
                        if (rz == 7 && rx % 3 == 0 && rx >= S / 4 - 2 && rx <= 3 * S / 4 + 2) for (int y = Y0; y < Y0 + H - 2; y++) g.set(x, y, z, acc);
                    } else {
                        int tier = Math.min(H - 4, (rz - stageEnd) / 3);
                        for (int y = Y0; y < Y0 + tier; y++) g.set(x, y, z, floor);
                        boolean aisle = rx % 8 == 3 || rx % 8 == 4;
                        if (!aisle && rx % 2 == 1 && rz % 3 != 0) g.set(x, Y0 + tier, z, slab);
                    }
                }
                case "tracks" -> {
                    int mid = S / 2;
                    if (rx >= mid - 2 && rx <= mid + 1) { g.set(x, Y0 - 1, z, (rx == mid - 2 || rx == mid + 1) ? stone : Blocks.RAIL.defaultBlockState()); if (rx == mid - 1 || rx == mid) g.set(x, Y0 - 1, z, stone); if (rx == mid - 1 || rx == mid) g.set(x, Y0, z, Blocks.RAIL.defaultBlockState()); }
                    if ((rx == mid - 6 || rx == mid + 5) && rz % 8 == 3) for (int y = Y0; y < Y0 + H; y++) g.set(x, y, z, wall);
                    if ((rx == mid - 3 || rx == mid + 2)) g.set(x, Y0 - 1, z, acc);
                }
                case "machines" -> {
                    int h = Noise.hash(t.seed + 77, (g.bx + x - ((rx % 8) )) >> 3, (g.bz + z - (rz % 8)) >> 3);
                    int mx = rx % 8, mz = rz % 8;
                    if (h % 100 < 48 && mx >= 1 && mx <= 5 && mz >= 1 && mz <= 5) {
                        int mh = 2 + h % 5;
                        for (int y = Y0; y < Y0 + mh; y++) g.set(x, y, z, (mx == 3 && mz == 3 && y == Y0 + mh - 1) ? mark : stone);
                        if (mx == 3 && mz == 3) g.set(x, Y0 + mh, z, acc);
                    } else if (h % 100 >= 48 && h % 100 < 55 && mx >= 2 && mx <= 5 && mz >= 2 && mz <= 5) {   // service pit
                        for (int y = Y0 - 6; y < Y0; y++) g.set(x, y, z, Gen.AIR);
                        g.set(x, Y0 - 7, z, stone);
                    }
                    if (rz % 16 == 0 && H >= 10) g.set(x, Y0 + H - 3, z, ceil);
                }
                case "pews" -> {
                    int mid = S / 2;
                    boolean aisle = rx >= mid - 2 && rx <= mid + 1;
                    if (rz < S - 9) {
                        if (!aisle && rz % 3 == 0 && rx % 8 != 0) g.set(x, Y0, z, slab);
                        if ((rx == 6 || rx == S - 7) && rz % 6 == 3) for (int y = Y0; y < Y0 + H - 1; y++) g.set(x, y, z, (y < Y0 + 2) ? acc : wall);
                    } else if (rz < S - 6 && rx >= mid - 4 && rx <= mid + 3) {
                        for (int y = Y0; y < Y0 + 2; y++) g.set(x, y, z, stone);
                        if (rz == S - 8 && (rx == mid - 1 || rx == mid)) g.set(x, Y0 + 2, z, mark);
                    }
                }
                case "cases" -> {
                    if (rx % 10 == 5 && rz % 10 == 5) { g.set(x, Y0, z, stone); g.set(x, Y0 + 1, z, glass); g.set(x, Y0 + 2, z, glass); g.set(x, Y0 + 3, z, acc); }
                }
                case "beds" -> {
                    if (rz % 6 == 3 && (rx % 4 == 1 || rx % 4 == 2) && rx % 16 > 2) { g.set(x, Y0, z, floor); if (rx % 4 == 1) g.set(x, Y0 + 1, z, acc); }
                    if (rz % 6 == 5 && rx % 8 == 0) for (int y = Y0; y < Y0 + 3; y++) g.set(x, y, z, glass);
                }
                case "mirrors" -> {
                    if (rz % 12 == 6 && rx % 14 >= 3 && rx % 14 <= 8) { for (int y = Y0; y < Y0 + 6; y++) g.set(x, y, z, glass); if (rx % 14 == 3 || rx % 14 == 8) for (int y = Y0; y < Y0 + 6; y++) g.set(x, y, z, acc); }
                }
                case "toys" -> {
                    int h = Noise.hash(t.seed + 78, (g.bx + x - rx % 10) / 10, (g.bz + z - rz % 10) / 10);
                    int tx = rx % 10, tz = rz % 10;
                    if (h % 100 < 55 && tx >= 1 && tx <= 6 && tz >= 1 && tz <= 6) {
                        int th = 2 + h % 7;
                        BlockState[] pal = {acc, mark, wall, floor};
                        for (int y = Y0; y < Y0 + th; y++) g.set(x, y, z, pal[(h / 7 + y / 3) % 4]);
                    }
                }
                case "monoliths" -> {
                    int tx = rx % 12, tz = rz % 12;
                    if (tx >= 4 && tx <= 6 && tz >= 4 && tz <= 6 && Noise.hash(t.seed + 79, (g.bx + x - tx) / 12, (g.bz + z - tz) / 12) % 100 < 70) {
                        for (int y = Y0; y < Y0 + H - 1; y++) g.set(x, y, z, (tx == 5 && tz == 4 && y % 3 == 1) ? mark : (y % 6 == 5 ? acc : wall));
                    }
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ organic chambers
    private static void organic(Gen g, Desc d, boolean[][] in, boolean[][] door, int[][] colCeil, boolean dN, boolean dS, boolean dW, boolean dE, int H) {
        Theme t = g.t;
        double ocx = 7.5 + (Noise.rnd(t.seed + 410, g.cx, g.cz) - .5) * 3, ocz = 7.5 + (Noise.rnd(t.seed + 411, g.cx, g.cz) - .5) * 3;
        double rad = 4.2 + Noise.rnd(t.seed + 412, g.cx, g.cz) * 3.2;
        int maxC = Y0 + H;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double dx = x - ocx, dz = z - ocz;
            double n = (Noise.value(t.seed + 413, (g.bx + x) / 3.5, (g.bz + z) / 3.5) - .5) * 2.4;
            double dist = Math.sqrt(dx * dx + dz * dz) + n;
            boolean inside = dist <= rad;
            in[x][z] = inside; door[x][z] = false;
            if (inside) {
                double q = 1 - Math.max(0, dist) / rad;
                colCeil[x][z] = Math.min(maxC, Y0 + 3 + (int) Math.round(q * (H - 2) + (Noise.value(t.seed + 414, (g.bx + x) / 2.5, (g.bz + z) / 2.5) - .5) * 2));
                if (colCeil[x][z] < Y0 + 3) colCeil[x][z] = Y0 + 3;
            }
        }
        // tunnels from the chamber centre to every open edge (centre line 7.5 so neighbours line up)
        boolean[] dirs = {dN, dE, dS, dW};
        for (int k = 0; k < 4; k++) {
            if (!dirs[k]) continue;
            for (int s = 0; s <= 8; s++) {
                for (int w = -2; w <= 1; w++) {
                    int x, z;
                    switch (k) { case 0 -> { x = 8 + w - 0; z = 7 - s; } case 1 -> { x = 8 + s; z = 8 + w; } case 2 -> { x = 8 + w; z = 8 + s; } default -> { x = 7 - s; z = 8 + w; } }
                    if (x < 0 || x > 15 || z < 0 || z > 15) continue;
                    if (in[x][z]) continue;
                    in[x][z] = true; colCeil[x][z] = Y0 + 4;
                }
            }
        }
    }
}
