package com.lastsignal.world.gen;

import com.lastsignal.block.DimensionGateBlock;
import com.lastsignal.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * Per-chunk terrain builder. Everything is chunk-local (no cross-chunk writes) and deterministic from (theme, chunk x/z),
 * so it is safe for async generation, multiplayer and re-generation. Kinds: rooms, open, islands, void, deep, sea, stairs.
 */
public final class Gen {
    static final int Y0 = 64;   // ROOMS: first air block above the floor
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();

    final Theme t;
    final ChunkAccess c;
    final int cx, cz, bx, bz;
    final Random r;
    final int[][] gh = new int[16][16];     // ground height (top solid y) per column, -1 = none
    int fx0 = 3, fx1 = 12, fz0 = 3, fz1 = 12;   // feature placement window
    int ceilY = 0;                                // rooms: ceiling row
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    Gen(Theme t, ChunkAccess c) {
        this.t = t; this.c = c;
        this.cx = c.getPos().x; this.cz = c.getPos().z;
        this.bx = cx << 4; this.bz = cz << 4;
        this.r = new Random(Noise.hash(t.seed, cx, cz));
        for (int[] row : gh) java.util.Arrays.fill(row, -1);
    }

    // ------------------------------------------------------------------ helpers
    BlockState S(int role) { return t.role(role); }

    void set(int x, int y, int z, BlockState s) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return;
        c.setBlockState(mp.set(bx + x, y, bz + z), s, false);
    }

    BlockState get(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return AIR;
        return c.getBlockState(mp.set(bx + x, y, bz + z));
    }

    void box(int x, int y, int z, int w, int h, int d, BlockState s) {
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) for (int k = 0; k < d; k++) set(x + i, y + j, z + k, s);
    }

    void hollow(int x, int y, int z, int w, int h, int d, BlockState s) {
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) for (int k = 0; k < d; k++)
            if (i == 0 || k == 0 || i == w - 1 || k == d - 1) set(x + i, y + j, z + k, s);
    }

    void col(int x, int y, int z, int h, BlockState s) {
        for (int j = 0; j < h; j++) set(x + 0, y + j, z, s);
    }

    void disc(int x0, int y, int z0, int rad, BlockState s) {
        for (int i = -rad; i <= rad; i++) for (int k = -rad; k <= rad; k++) if (i * i + k * k <= rad * rad + 1) set(x0 + i, y, z0 + k, s);
    }

    void ring(int x0, int y, int z0, int rad, BlockState s) {
        for (int i = -rad; i <= rad; i++) for (int k = -rad; k <= rad; k++) {
            int d2 = i * i + k * k;
            if (d2 <= rad * rad + 1 && d2 >= (rad - 1) * (rad - 1) + 1) set(x0 + i, y, z0 + k, s);
        }
    }

    boolean chance(double p) { return r.nextDouble() < p; }

    int rnd(int lo, int hi) { return lo + r.nextInt(Math.max(1, hi - lo + 1)); }

    int px() { return rnd(fx0, fx1); }

    int pz() { return rnd(fz0, fz1); }

    int groundAt(int x, int z) {
        x = Math.max(0, Math.min(15, x)); z = Math.max(0, Math.min(15, z));
        return gh[x][z];
    }

    /** lamp: lanterns hang from the ceiling, full-block lights replace the ceiling block. */
    void lamp(int x, int ceilRow) {
        BlockState l = S(Theme.LIGHT);
        int z = rnd(3, 12);
        if (l.getBlock() instanceof LanternBlock) set(x, ceilRow - 1, z, l.setValue(LanternBlock.HANGING, true));
        else set(x, ceilRow, z, l);
    }

    void lampAt(int x, int y, int z) {
        BlockState l = S(Theme.LIGHT);
        if (l.getBlock() instanceof LanternBlock) set(x, y, z, l);
        else set(x, y - 1, z, l);
    }

    /** Stamp the dimension's marking glyph (11x11) flat: pixels become the marking block. onlyOnGround skips air cells. */
    void stampFloor(int x0, int z0, int y, boolean replaceOnlySolid) {
        String[] g = t.glyph[t.glyph.length == 1 ? 0 : r.nextInt(t.glyph.length)];
        BlockState m = S(Theme.MARK);
        for (int j = 0; j < 11; j++) for (int i = 0; i < 11; i++)
            if (g[j].charAt(i) == '#' && (!replaceOnlySolid || !get(x0 + i, y, z0 + j).isAir())) set(x0 + i, y, z0 + j, m);
    }

    /** Vertical mural on a wall plane facing +z (wall at z) or facing +x (wall at x), starting at height y0. */
    void stampWall(boolean alongX, int fixed, int a0, int y0, BlockState onlyIfWall) {
        String[] g = t.glyph[0];
        BlockState m = S(Theme.MARK);
        for (int j = 0; j < 11; j++) for (int i = 0; i < 11; i++) {
            if (g[j].charAt(i) != '#') continue;
            int y = y0 + (10 - j);
            int x = alongX ? a0 + i : fixed, z = alongX ? fixed : a0 + i;
            if (get(x, y, z) == onlyIfWall) set(x, y, z, m);
        }
    }

    // ------------------------------------------------------------------ terrain functions (static: shared with getBaseHeight/arrival)
    static int openHeight(Theme t, int wx, int wz) {
        double sc = t.p[Theme.P_SCALE];
        double n = Noise.fbm(t.seed, wx / sc, wz / sc, 3);
        double base = t.p[Theme.P_BASE];
        double h = base + (n - .5) * 2 * t.p[Theme.P_AMP];
        double va = t.p[Theme.P_VA];
        if (t.has("dunes")) {
            double w = Noise.value(t.seed + 7, wx / 70.0, wz / 70.0) * 3;
            double ridge = Math.abs(Math.sin(wx * .021 + wz * .013 + w));
            h += va * ridge * ridge;
        }
        if (t.has("craters")) {
            int cs = 40, gx = Math.floorDiv(wx, cs), gz = Math.floorDiv(wz, cs);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int ccx = (gx + dx) * cs + (int) (Noise.rnd(t.seed + 11, gx + dx, gz + dz) * cs);
                int ccz = (gz + dz) * cs + (int) (Noise.rnd(t.seed + 12, gx + dx, gz + dz) * cs);
                double rad = 8 + Noise.rnd(t.seed + 13, gx + dx, gz + dz) * 10;
                double d = Math.hypot(wx - ccx, wz - ccz);
                if (d < rad) { double q = d / rad; h -= va * (1 - q * q); }
                else if (d < rad * 1.25) h += va * .25 * (1 - (d - rad) / (rad * .25));
            }
        }
        if (t.has("ravine")) {
            double v = Math.abs(Noise.fbm(t.seed + 21, wx / 90.0, wz / 90.0, 2) - .5);
            if (v < .04) h -= (.04 - v) / .04 * 12;
        }
        if (t.has("ridges")) {
            double v = 1 - Math.abs(Noise.fbm(t.seed + 31, wx / 60.0, wz / 60.0, 3) * 2 - 1);
            h += va * v * v;
        }
        double dsp = Math.hypot(wx - 8, wz - 8);
        if (dsp < 18) {
            double k = 1 - dsp / 18; k = k * k * (3 - 2 * k);
            double target = base + (t.p[Theme.P_SEA] > 0 ? 2 : 0);
            h = h + (target - h) * k;
        }
        return (int) Math.round(Math.max(6, Math.min(200, h)));
    }

    static int deepFloor(Theme t, int wx, int wz) {
        double h = 34 + (Noise.fbm(t.seed + 3, wx / 48.0, wz / 48.0, 3) - .5) * 28;
        double dsp = Math.hypot(wx - 8, wz - 8);
        if (dsp < 18) { double k = 1 - dsp / 18; k = k * k * (3 - 2 * k); h = h + (36 - h) * k; }
        return (int) Math.round(h);
    }

    static int deepCeil(Theme t, int wx, int wz) {
        double h = 96 + (Noise.fbm(t.seed + 5, wx / 64.0, wz / 64.0, 3) - .5) * 34;
        double dsp = Math.hypot(wx - 8, wz - 8);
        if (dsp < 18) { double k = 1 - dsp / 18; k = k * k * (3 - 2 * k); h = h + (100 - h) * k; }
        return Math.max(deepFloor(t, wx, wz) + 9, (int) Math.round(h));
    }

    static int seaFloor(Theme t, int wx, int wz) {
        double h = 26 + (Noise.fbm(t.seed + 9, wx / 50.0, wz / 50.0, 3) - .5) * 30;
        double dsp = Math.hypot(wx - 8, wz - 8);
        if (dsp < 18) { double k = 1 - dsp / 18; k = k * k * (3 - 2 * k); h = h + (40 - h) * k; }
        return (int) Math.round(h);
    }

    /** {has, centerX, centerZ, radius, topY, halfW, halfD} */
    static int[] island(Theme t, int cx, int cz) {
        int base = (int) t.p[Theme.P_BASE];
        if (cx == 0 && cz == 0) return new int[]{1, 8, 8, 7, base, 7, 7};
        int h = Noise.hash(t.seed + 77, cx, cz);
        boolean flat = t.kind.equals("void");
        boolean has = (h % 100) < (flat ? 62 : 60);
        int rad = 4 + ((h >> 16) & 0xff) % 4;
        int top = flat ? base + (((h >> 20) & 0xff) % 3) * 0 : base + (((h >> 20) & 0xff) % 60) - 20;
        return new int[]{has ? 1 : 0, 7 + ((h >> 8) & 0xff) % 3, 7 + ((h >> 12) & 0xff) % 3, rad, top, 5 + ((h >> 4) & 0xff) % 3, 5 + ((h >> 6) & 0xff) % 3};
    }

    static boolean doorOpen(Theme t, int ax, int az, int bx, int bz) {
        if (Math.abs(ax) <= 1 && Math.abs(az) <= 1 && Math.abs(bx) <= 1 && Math.abs(bz) <= 1) return true;
        int lx = Math.min(ax, bx), lz = Math.min(az, bz);
        boolean horiz = ax != bx;
        return Noise.hash(t.seed + (horiz ? 101 : 202), lx, lz) % 100 < (int) t.p[Theme.P_OPEN];
    }

    static int stairTop(int cx, int cz) { return 200 - (((cx + cz) & 1) == 1 ? 9 : 0); }

    static int stairK(int x, int z) {
        double ang = Math.atan2(z + .5 - 8, x + .5 - 8);
        double s = (ang + Math.PI) / (2 * Math.PI);
        return Math.max(0, Math.min(35, (int) Math.floor(s * 36)));
    }

    /** Where a player arrives (block coordinates inside chunk 0,0) and the first air Y. */
    public static int[] arrival(Theme t) {
        switch (t.kind) {
            case "rooms": return new int[]{8, Y0, 8};
            case "open": return new int[]{8, openHeight(t, 8, 8) + 1, 8};
            case "deep": return new int[]{8, deepFloor(t, 8, 8) + 1, 8};
            case "sea": return new int[]{8, (int) t.p[Theme.P_SEA] + 1, 8};
            case "stairs": return new int[]{4, stairTop(0, 0) - stairK(4, 6) / 2 + 1, 6};
            default: return new int[]{8, (int) t.p[Theme.P_BASE] + 1, 8};
        }
    }

    // ------------------------------------------------------------------ entry point
    void run() {
        switch (t.kind) {
            case "rooms" -> rooms();
            case "open" -> open();
            case "islands" -> islands(false);
            case "void" -> islands(true);
            case "deep" -> deep();
            case "sea" -> sea();
            case "stairs" -> stairs();
            default -> { }
        }
        if (cx == 0 && cz == 0) pad();
    }

    // ------------------------------------------------------------------ ROOMS
    private void rooms() {
        int H = (int) t.p[Theme.P_H];
        ceilY = Y0 + H;
        boolean dN = doorOpen(t, cx, cz - 1, cx, cz), dE = doorOpen(t, cx, cz, cx + 1, cz),
                dS = doorOpen(t, cx, cz, cx, cz + 1), dW = doorOpen(t, cx - 1, cz, cx, cz);
        BlockState stone = S(Theme.STONE), floor = S(Theme.FLOOR), wall = S(Theme.WALL), ceil = S(Theme.CEIL);
        boolean rough = t.has("rough");
        boolean courtyard = t.p[Theme.P_ROOF] > 0 && Noise.rnd(t.seed + 55, cx, cz) < t.p[Theme.P_ROOF] && !(cx == 0 && cz == 0);
        int doorH = Math.min(H, 4);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            boolean in = x >= 2 && x <= 13 && z >= 2 && z <= 13;
            boolean door = false;
            if (x >= 6 && x <= 9) { if (z < 2 && dN) door = true; if (z > 13 && dS) door = true; }
            if (z >= 6 && z <= 9) { if (x < 2 && dW) door = true; if (x > 13 && dE) door = true; }
            boolean yard = courtyard && x >= 5 && x <= 10 && z >= 5 && z <= 10;
            set(x, 0, z, BEDROCK);
            for (int y = 1; y < Y0 - 1; y++) set(x, y, z, stone);
            set(x, Y0 - 1, z, (in || door) ? floor : stone);
            gh[x][z] = Y0 - 1;
            if (in) {
                set(x, ceilY, z, yard ? AIR : ceil);
            } else if (door) {
                for (int y = Y0 + doorH; y <= ceilY; y++) set(x, y, z, wall);
            } else {
                for (int y = Y0; y <= ceilY; y++) set(x, y, z, (rough && r.nextInt(5) < 2) ? stone : wall);
            }
            for (int y = ceilY + 1; y <= ceilY + 3; y++) set(x, y, z, yard ? AIR : stone);
            set(x, ceilY + 4, z, BEDROCK);
        }
        // wall inlays: accent patches and the dimension's marking block on the inner faces
        BlockState acc = S(Theme.ACCENT), mark = S(Theme.MARK);
        for (int i = 2; i <= 13; i++) for (int y = Y0; y < ceilY; y++) {
            inlay(i, y, 1, wall, acc, mark); inlay(i, y, 14, wall, acc, mark); inlay(1, y, i, wall, acc, mark); inlay(14, y, i, wall, acc, mark);
        }
        boolean spawn = cx == 0 && cz == 0;
        if (chance(t.p[Theme.P_MARK])) stampFloor(2 + r.nextInt(2), 2 + r.nextInt(2), Y0 - 1, false);
        if (H >= 12 && chance(t.p[Theme.P_MURAL])) stampWall(true, 1, 2 + r.nextInt(2), Y0 + 1 + Math.max(0, (H - 12) / 2), wall);
        if (!spawn && t.feats.length > 0 && chance(t.p[Theme.P_FEAT])) Features.build(t.feats[r.nextInt(t.feats.length)], this, Y0, H, true);
        int lamps = chance(t.p[Theme.P_LIGHT]) ? 1 + (chance(.4) ? 1 : 0) : 0;
        if (spawn) lamps = 2;
        for (int i = 0; i < lamps; i++) lamp(rnd(3, 12), ceilY);
    }

    private void inlay(int x, int y, int z, BlockState wall, BlockState acc, BlockState mark) {
        if (get(x, y, z) != wall) return;
        int h = Noise.hash(t.seed + 9, (bx + x) * 31 + y, (bz + z) * 17 + y * 3) % 1000;
        if (h < 35) set(x, y, z, acc);
        else if (h < 50) set(x, y, z, mark);
    }

    // ------------------------------------------------------------------ OPEN
    private void open() {
        int sea = (int) t.p[Theme.P_SEA];
        BlockState g = S(Theme.GROUND), sub = S(Theme.SUB), st = S(Theme.STONE);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int h = openHeight(t, bx + x, bz + z);
            gh[x][z] = h;
            set(x, 0, z, BEDROCK);
            for (int y = 1; y <= h - 4; y++) set(x, y, z, st);
            for (int y = Math.max(1, h - 3); y < h; y++) set(x, y, z, sub);
            set(x, h, z, g);
            if (h < sea) for (int y = h + 1; y <= sea; y++) set(x, y, z, WATER);
        }
        boolean spawn = cx == 0 && cz == 0;
        if (chance(t.p[Theme.P_MARK]) && !spawn) {
            int x0 = 2 + r.nextInt(2), z0 = 2 + r.nextInt(2);
            int lvl = gh[x0 + 5][z0 + 5];
            flatten(x0, z0, 11, 11, lvl, 6);
            stampFloor(x0, z0, lvl, false);
        }
        if (!spawn) {
            double f = t.p[Theme.P_FEAT];
            int n = (chance(f) ? 1 : 0) + (chance(f * .6) ? 1 : 0) + (chance(f * .3) ? 1 : 0);
            for (int i = 0; i < n && t.feats.length > 0; i++) Features.build(t.feats[r.nextInt(t.feats.length)], this, 0, 40, false);
        }
    }

    /** level the area to height lvl (ground/sub below, air above up to clear blocks). */
    void flatten(int x0, int z0, int w, int d, int lvl, int clear) {
        BlockState g = S(Theme.GROUND), sub = S(Theme.SUB);
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            if (x < 0 || x > 15 || z < 0 || z > 15) continue;
            for (int y = lvl + 1; y <= lvl + clear; y++) if (y > t.p[Theme.P_SEA]) set(x, y, z, AIR); else set(x, y, z, WATER);
            for (int y = Math.max(1, lvl - 3); y < lvl; y++) set(x, y, z, sub);
            set(x, lvl, z, g);
            gh[x][z] = lvl;
        }
    }

    // ------------------------------------------------------------------ ISLANDS / VOID
    private void islands(boolean flat) {
        int[] is = island(t, cx, cz);
        BlockState g = S(Theme.GROUND), sub = S(Theme.SUB), st = S(Theme.STONE);
        if (is[0] != 0) {
            int icx = is[1], icz = is[2], rad = is[3], top = is[4];
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int dx = x - icx, dz = z - icz;
                if (flat) {
                    if (Math.abs(dx) <= is[5] && Math.abs(dz) <= is[6]) {
                        set(x, top, z, g); set(x, top - 1, z, sub); gh[x][z] = top;
                    }
                } else {
                    double d = Math.sqrt(dx * dx + dz * dz) + (Noise.value(t.seed + 5, (bx + x) / 4.0, (bz + z) / 4.0) - .5) * 2.0;
                    if (d <= rad) {
                        set(x, top, z, g); set(x, top - 1, z, sub); gh[x][z] = top;
                        int depth = (int) ((rad - d) * 1.3) + rad / 2;
                        for (int k = 2; k <= depth + 1; k++) set(x, top - k, z, st);
                    }
                }
            }
            fx0 = Math.max(2, icx - 2); fx1 = Math.min(13, icx + 2); fz0 = Math.max(2, icz - 2); fz1 = Math.min(13, icz + 2);
            if (!flat) {
                BlockState acc = S(Theme.ACCENT);
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int d = 0; d < 4; d++) {
                    if (Noise.hash(t.seed + 300 + d, cx, cz) % 100 >= 45) continue;
                    for (int k = rad + 1; k < 16; k++) {
                        int x = icx + dirs[d][0] * k, z = icz + dirs[d][1] * k;
                        if (x < 0 || x > 15 || z < 0 || z > 15) break;
                        if ((k * 7 + d) % 5 == 0) continue;
                        set(x, top, z, (k & 1) == 0 ? st : acc);
                    }
                }
            }
            boolean spawn = cx == 0 && cz == 0;
            if (chance(t.p[Theme.P_MARK]) && rad >= 5 && !spawn) {
                int x0 = Math.max(0, Math.min(5, icx - 5)), z0 = Math.max(0, Math.min(5, icz - 5));
                stampFloor(x0, z0, top, true);
            }
            if (!spawn && t.feats.length > 0) {
                double f = t.p[Theme.P_FEAT];
                int n = (chance(f) ? 1 : 0) + (chance(f * .4) ? 1 : 0);
                for (int i = 0; i < n; i++) Features.build(t.feats[r.nextInt(t.feats.length)], this, top, 40, false);
            }
        }
        if (t.has("glitch")) {
            BlockState[] pal = {S(Theme.ACCENT), S(Theme.MARK), S(Theme.GLASS), S(Theme.STONE)};
            int base = (int) t.p[Theme.P_BASE];
            for (int i = 0; i < 6; i++) {
                int sx = r.nextInt(14), sz = r.nextInt(14), sy = base - 12 + r.nextInt(40), s = 1 + r.nextInt(3);
                BlockState b = pal[r.nextInt(pal.length)];
                for (int a = 0; a < s; a++) for (int bq = 0; bq < s; bq++) for (int cc = 0; cc < (r.nextBoolean() ? 1 : s); cc++) set(sx + a, sy + cc, sz + bq, b);
            }
        }
    }

    // ------------------------------------------------------------------ DEEP
    private void deep() {
        BlockState g = S(Theme.GROUND), sub = S(Theme.SUB), st = S(Theme.STONE), ceil = S(Theme.CEIL);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int f = deepFloor(t, bx + x, bz + z), cl = deepCeil(t, bx + x, bz + z);
            gh[x][z] = f;
            set(x, 0, z, BEDROCK);
            for (int y = 1; y < f; y++) set(x, y, z, y >= f - 3 ? sub : st);
            set(x, f, z, g);
            set(x, cl, z, ceil);
            for (int y = cl + 1; y <= cl + 6; y++) set(x, y, z, st);
            set(x, cl + 7, z, BEDROCK);
        }
        boolean spawn = cx == 0 && cz == 0;
        if (chance(t.p[Theme.P_MARK]) && !spawn) {
            int x0 = 2 + r.nextInt(2), z0 = 2 + r.nextInt(2);
            flatten(x0, z0, 11, 11, gh[x0 + 5][z0 + 5], 8);
            stampFloor(x0, z0, gh[x0 + 5][z0 + 5], false);
        }
        if (!spawn) {
            double f = t.p[Theme.P_FEAT];
            int n = (chance(f) ? 1 : 0) + (chance(f * .7) ? 1 : 0);
            for (int i = 0; i < n && t.feats.length > 0; i++) Features.build(t.feats[r.nextInt(t.feats.length)], this, 0, 60, false);
        }
        if (chance(.35) || spawn) lampAt(rnd(3, 12), gh[8][8] + 1 + 0, rnd(3, 12));
    }

    // ------------------------------------------------------------------ SEA
    private void sea() {
        int sea = (int) t.p[Theme.P_SEA];
        BlockState g = S(Theme.GROUND), sub = S(Theme.SUB), st = S(Theme.STONE);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int f = seaFloor(t, bx + x, bz + z);
            gh[x][z] = f;
            set(x, 0, z, BEDROCK);
            for (int y = 1; y < f; y++) set(x, y, z, y >= f - 3 ? sub : st);
            set(x, f, z, g);
            for (int y = f + 1; y <= sea; y++) set(x, y, z, WATER);
        }
        boolean spawn = cx == 0 && cz == 0;
        if (chance(t.p[Theme.P_MARK]) && !spawn) {
            int x0 = 2 + r.nextInt(2), z0 = 2 + r.nextInt(2);
            flatten(x0, z0, 11, 11, gh[x0 + 5][z0 + 5], 0);
            for (int x = x0; x < x0 + 11; x++) for (int z = z0; z < z0 + 11; z++) for (int y = gh[x0 + 5][z0 + 5] + 1; y <= sea; y++) set(x, y, z, WATER);
            stampFloor(x0, z0, gh[x0 + 5][z0 + 5], false);
        }
        if (!spawn) {
            double f = t.p[Theme.P_FEAT];
            int n = (chance(f) ? 1 : 0) + (chance(f * .6) ? 1 : 0);
            for (int i = 0; i < n && t.feats.length > 0; i++) Features.build(t.feats[r.nextInt(t.feats.length)], this, 0, 40, false);
        }
    }

    // ------------------------------------------------------------------ STAIRS
    private void stairs() {
        int ytop = stairTop(cx, cz), ybot = 24;
        ceilY = ytop + 4;
        BlockState wall = S(Theme.WALL), stone = S(Theme.STONE), floor = S(Theme.FLOOR), ceil = S(Theme.CEIL), mark = S(Theme.MARK), acc = S(Theme.ACCENT);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            double cheb = Math.max(Math.abs(x - 7.5), Math.abs(z - 7.5));
            set(x, 0, z, BEDROCK);
            for (int y = 1; y < ybot; y++) set(x, y, z, stone);
            gh[x][z] = ybot;
            if (cheb > 4.5) {
                for (int y = ybot; y <= ytop + 4; y++) set(x, y, z, (y % 9 == 0) ? stone : wall);
            } else {
                set(x, ybot, z, floor);
                set(x, ytop + 4, z, ceil);
            }
            for (int y = ytop + 5; y <= ytop + 6; y++) set(x, y, z, stone);
            set(x, ytop + 7, z, BEDROCK);
            if (cheb == 2.5 || cheb == 3.5 || cheb == 4.5) {
                int k = stairK(x, z);
                for (int rev = 0; ; rev++) {
                    int y = ytop - k / 2 - rev * 18;
                    if (y < ybot + 3) break;
                    set(x, y, z, (cheb == 4.5 && k % 6 == 0) ? mark : (cheb == 3.5 && k % 6 == 3 ? acc : floor));
                    if (cheb == 2.5) { set(x, y + 1, z, wall); set(x, y + 2, z, wall); }
                }
            }
        }
        // landings: tunnels to neighbouring shafts at levels that align modulo 18 (see stairTop parity)
        for (int rev = 0; ; rev++) {
            int L = ytop - rev * 18;
            if (L - 13 < ybot + 3) break;
            doorTunnel(0, L, cx * 37 + cz * 1013, 301, 0, 6, 3, 2, true, false);      // W
            doorTunnel(1, L - 4, cx * 37 + cz * 1013, 302, 7, 0, 2, 3, false, true);  // N
            doorTunnel(2, L - 9, (cx + 1) * 37 + cz * 1013, 301, 13, 7, 3, 2, true, false); // E
            doorTunnel(3, L - 13, cx * 37 + (cz + 1) * 1013, 302, 7, 13, 2, 3, false, true); // S
        }
        if (chance(.0)) stampFloor(2, 2, ybot, false);
    }

    private void doorTunnel(int dir, int y, int edgeKey, int salt, int x0, int z0, int w, int d, boolean alongX, boolean alongZ) {
        boolean spawnNear = Math.abs(cx) <= 1 && Math.abs(cz) <= 1;
        if (!spawnNear && Noise.hash(t.seed + salt, edgeKey, y) % 100 >= (int) t.p[Theme.P_OPEN]) return;
        for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) for (int h = 1; h <= 3; h++) set(x0 + i, y + h, z0 + k, AIR);
    }

    // ------------------------------------------------------------------ arrival pad (chunk 0,0): clear space, floor, exit gate, beacon
    private void pad() {
        int[] a = arrival(t);
        int ax = a[0], ay = a[1], az = a[2];
        Block gate = ModBlocks.BY_ID.get("signal_gate").get(), beacon = ModBlocks.BY_ID.get("checkpoint_beacon").get();
        BlockState floorS = t.kind.equals("stairs") ? S(Theme.FLOOR) : (t.kind.equals("rooms") ? S(Theme.FLOOR) : S(Theme.GROUND));
        if (t.kind.equals("stairs")) {
            set(4, ay - 1, 5, floorS); set(4, ay, 5, gate.defaultBlockState().setValue(DimensionGateBlock.TARGET, 0));
            set(4, ay + 1, 5, AIR); set(4, ay + 2, 5, AIR);
            set(3, ay - 1, 6, floorS); set(3, ay, 6, beacon.defaultBlockState());
            set(4, ay - 1, 6, floorS); set(4, ay, 6, AIR); set(4, ay + 1, 6, AIR); set(4, ay + 2, 6, AIR);
            return;
        }
        for (int x = ax - 3; x <= ax + 3; x++) for (int z = az - 3; z <= az + 3; z++) {
            set(x, ay - 1, z, floorS);
            for (int y = ay; y <= ay + 3; y++) set(x, y, z, t.kind.equals("sea") && y <= (int) t.p[Theme.P_SEA] ? AIR : AIR);
            gh[Math.max(0, Math.min(15, x))][Math.max(0, Math.min(15, z))] = ay - 1;
        }
        set(ax, ay, az - 3, gate.defaultBlockState().setValue(DimensionGateBlock.TARGET, 0));
        set(ax, ay, az + 3, beacon.defaultBlockState());
        if (t.kind.equals("sea")) for (int x = ax - 3; x <= ax + 3; x++) for (int z = az - 3; z <= az + 3; z++) set(x, ay - 2, z, floorS);
    }
}
