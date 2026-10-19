package com.lastsignal.world.gen;

import com.lastsignal.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Golden Cloud Realm: a solid, walkable sea of cloud with sky islands of different size/height rising out of it, gilded pillars,
 * rare shrines and floating rocks. Gold is sparse (single blocks, ore flecks, one block per shrine). No chests, no loot tables.
 * Everything is a pure function of the world position (islands/objects are re-derived per chunk), so there are no cross-chunk writes.
 */
public final class SkyRealm {
    static final int BASE = 84;       // underside of the cloud bank
    static final int CELL = 72;       // island grid
    static final int PX = 8, PZ = 208, PY = 118;   // the Golden Palace: centre column and the Y of its terrace floor (see Palace)
    static final int DAIS_X = 8, DAIS_Z = -3;      // the exit dais on the spawn island

    static BlockState B(String id) { return ModBlocks.BY_ID.get(id).get().defaultBlockState(); }

    static int cloudTop(Theme t, int x, int z) {
        double n = Noise.fbm(t.seed + 1, x / 34.0, z / 34.0, 3), r = Noise.fbm(t.seed + 2, x / 90.0, z / 90.0, 2);
        double a = Math.max(0, Math.min(1, (Math.hypot(x - PX, z - PZ) - 40) / 70));      // the cloud floor flattens towards the palace, so its approach slope stays walkable
        return (int) Math.round(94 + (n - .5) * 12 * a + (r - .5) * 12 * a);
    }

    /** {has, centerX, centerZ, radius, topY} of the island owned by grid cell (gx,gz). The cell holding the spawn always has one. */
    static int[] island(Theme t, int gx, int gz) {
        if (gx == 0 && gz == 0) return new int[]{1, 8, 8, 36, cloudTop(t, 8, 8) + 14};
        int h = Noise.hash(t.seed + 11, gx, gz);
        if (h % 100 >= 72) return new int[]{0, 0, 0, 0, 0};
        int cx = gx * CELL + 12 + (h >> 8) % (CELL - 24), cz = gz * CELL + 12 + (h >> 16) % (CELL - 24);
        if (Math.hypot(cx - PX, cz - PZ) < 135) return new int[]{0, 0, 0, 0, 0};       // keep the palace approach free of hills
        int rad = 9 + (h >> 4) % 19, peak = 8 + (h >> 12) % 34;
        return new int[]{1, cx, cz, rad, cloudTop(t, cx, cz) + peak};
    }

    private static double sm(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    static int islandHeight(Theme t, int x, int z, int ct) {
        int fx = Math.floorDiv(x, CELL), fz = Math.floorDiv(z, CELL);
        double best = ct;
        double rn = (Noise.fbm(t.seed + 3, x / 20.0, z / 20.0, 2) - .5) * 2;
        for (int gx = fx - 1; gx <= fx + 1; gx++) for (int gz = fz - 1; gz <= fz + 1; gz++) {
            int[] is = island(t, gx, gz);
            if (is[0] == 0) continue;
            double dist = Math.hypot(x - is[1], z - is[2]);
            if (gx == 0 && gz == 0) {      // spawn island: a flat plateau (radius 16) that blends smoothly into the cloud, so the exit dais always stands on level ground
                double re = Math.max(24, is[3] * (1 + .28 * rn));
                if (dist >= re) continue;
                double s0 = dist <= 16 ? 1 : sm((re - dist) / (re - 16));
                double h0 = ct + (is[4] - ct) * s0;
                if (h0 > best) best = h0;
                continue;
            }
            double reff = is[3] * (1 + .28 * rn);
            double d = dist / reff;
            if (d >= 1) continue;
            double k = 1 - d;
            double s = k >= .7 ? 1 : sm(k / .7);
            double h = ct + (is[4] - ct) * s;
            if (s < 1) h += (Noise.fbm(t.seed + 4, x / 9.0, z / 9.0, 2) - .5) * 5 * s;
            if (h > best) best = h;
        }
        double dp = Math.hypot(x - PX, z - PZ);          // the palace island: flat out to 42 blocks, a linear ramp into the clouds by 90
        if (dp < 90) {
            double sp = dp <= 42 ? 1 : (90 - dp) / 48.0;            // linear ramp: <= 0.5 block per block, always climbable
            double hp = ct + (PY - ct) * sp;
            if (hp > best) best = hp;
        }
        return (int) Math.round(best);
    }

    static boolean nearPalace(int x, int z, int r) { return Math.hypot(x - PX, z - PZ) < r; }

    static int height(Theme t, int x, int z) { return islandHeight(t, x, z, cloudTop(t, x, z)); }

    static void run(Gen g) {
        Theme t = g.t;
        BlockState cloud = B("d49_cloud_bank"), stone = g.S(Theme.STONE), grass = g.S(Theme.GROUND), dirt = g.S(Theme.SUB), ore = Blocks.GOLD_ORE.defaultBlockState(), gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = g.bx + x, wz = g.bz + z;
            int ct = cloudTop(t, wx, wz), h = islandHeight(t, wx, wz, ct);
            g.gh[x][z] = h;
            for (int y = BASE; y <= ct; y++) g.set(x, y, z, cloud);
            if (h > ct) {
                for (int y = ct + 1; y <= h; y++) {
                    BlockState s = stone;
                    if (y == h && h > ct + 1) s = grass;
                    else if (y >= h - 2 && y < h && h > ct + 1) s = dirt;
                    else if (y == h) s = cloud;
                    else if (y <= h - 3 && y >= h - 9 && Noise.hash(t.seed + 21, wx * 31 + y, wz) % 700 == 0) s = ore;
                    g.set(x, y, z, s);
                }
                if (h > ct + 2) {
                    int hh = Noise.hash(t.seed + 22, wx, wz);
                    if (hh % 640 == 0 && Math.hypot(wx - 8, wz - 8) > 12 && !nearPalace(wx, wz, 100)) g.set(x, h, z, gold);                       // a lone gold block on the surface
                    else if (hh % 100 < 6) g.set(x, h + 1, z, flower(hh >> 8));
                }
            }
        }
        Palace.run(g);
        dais(g);
        objects(g);
    }

    /** The way out: a gold dais 11 blocks north of the arrival point. A vanilla beacon (on a gold 3x3) marks it from far away. Position-based trigger (see inDais). */
    static void dais(Gen g) {
        int top = height(g.t, DAIS_X, DAIS_Z);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > 16) continue;
            int lx = DAIS_X + dx - g.bx, lz = DAIS_Z + dz - g.bz;
            g.set(lx, top, lz, d2 <= 9 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());
            for (int y = top + 1; y <= top + 4; y++) g.set(lx, y, lz, Gen.AIR);
        }
        g.set(DAIS_X - g.bx, top + 1, DAIS_Z - g.bz, Blocks.BEACON.defaultBlockState());
    }

    /** True while standing on the dais (server polls this every 5 ticks, only in this dimension). */
    public static boolean inDais(Theme t, double x, double y, double z) {
        int top = height(t, DAIS_X, DAIS_Z);
        return Math.hypot(x - (DAIS_X + .5), z - (DAIS_Z + .5)) <= 3.3 && y >= top + .9 && y <= top + 3.0;
    }

    private static BlockState flower(int k) {
        return switch (Math.floorMod(k, 4)) {
            case 0 -> Blocks.DANDELION.defaultBlockState();
            case 1 -> Blocks.AZURE_BLUET.defaultBlockState();
            case 2 -> Blocks.OXEYE_DAISY.defaultBlockState();
            default -> Blocks.ALLIUM.defaultBlockState();
        };
    }


    /** Gilded pillars (grid 44), shrines (big islands), floating rocks (grid 80). All clipped to this chunk by Gen.set. */
    private static void objects(Gen g) {
        Theme t = g.t;
        BlockState marble = g.S(Theme.WALL), lamp = g.S(Theme.LIGHT), gold = Blocks.GOLD_BLOCK.defaultBlockState();
        // pillars
        int cell = 44;
        for (int gx = Math.floorDiv(g.bx - 6, cell); gx <= Math.floorDiv(g.bx + 22, cell); gx++)
            for (int gz = Math.floorDiv(g.bz - 6, cell); gz <= Math.floorDiv(g.bz + 22, cell); gz++) {
                int h = Noise.hash(t.seed + 31, gx, gz);
                if (h % 100 >= 38) continue;
                int px = gx * cell + 4 + (h >> 8) % (cell - 8), pz = gz * cell + 4 + (h >> 16) % (cell - 8);
                if (Math.abs(px - 8) < 12 && Math.abs(pz - 8) < 12 || nearPalace(px, pz, 110)) continue;
                int base = height(t, px, pz), ph = 9 + (h >> 4) % 13;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 && dz != 0) continue;
                    int lx = px + dx - g.bx, lz = pz + dz - g.bz;
                    int top = ph - (dx == 0 && dz == 0 ? 0 : 3);
                    for (int y = 1; y <= top; y++) g.set(lx, base + y, lz, marble);
                }
                g.set(px - g.bx, base + ph + 1, pz - g.bz, lamp);
            }
        // shrines on big islands (not the spawn island): 5x5 gilded floor, four posts, one gold block on an altar
        int sx0 = Math.floorDiv(g.bx - 30, CELL), sx1 = Math.floorDiv(g.bx + 46, CELL), sz0 = Math.floorDiv(g.bz - 30, CELL), sz1 = Math.floorDiv(g.bz + 46, CELL);
        for (int gx = sx0; gx <= sx1; gx++) for (int gz = sz0; gz <= sz1; gz++) {
            if (gx == 0 && gz == 0) continue;
            int[] is = island(t, gx, gz);
            if (is[0] == 0 || is[3] < 16 || Noise.hash(t.seed + 41, gx, gz) % 100 >= 30) continue;
            int cx = is[1] - g.bx, cz = is[2] - g.bz, y = is[4];
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                g.set(cx + dx, y, cz + dz, marble);
                for (int k = 1; k <= 5; k++) g.set(cx + dx, y + k, cz + dz, Gen.AIR);
            }
            for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                for (int k = 1; k <= 4; k++) g.set(cx + c[0], y + k, cz + c[1], marble);
                g.set(cx + c[0], y + 5, cz + c[1], lamp);
            }
            g.set(cx, y + 1, cz, marble);
            g.set(cx, y + 2, cz, gold);
        }
        // floating rocks
        int fc = 80;
        for (int gx = Math.floorDiv(g.bx - 10, fc); gx <= Math.floorDiv(g.bx + 26, fc); gx++)
            for (int gz = Math.floorDiv(g.bz - 10, fc); gz <= Math.floorDiv(g.bz + 26, fc); gz++) {
                int h = Noise.hash(t.seed + 51, gx, gz);
                if (h % 100 >= 55) continue;
                if (nearPalace(gx * fc + 40, gz * fc + 40, 130)) continue;
                int fx = gx * fc + 8 + (h >> 8) % (fc - 16), fz = gz * fc + 8 + (h >> 16) % (fc - 16);
                int rad = 3 + (h >> 4) % 4, fy = cloudTop(t, fx, fz) + 26 + (h >> 12) % 36;
                for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > rad) continue;
                    int depth = (int) Math.round((rad - d) * 1.3) + 1;
                    int lx = fx + dx - g.bx, lz = fz + dz - g.bz;
                    for (int k = 0; k < depth; k++) g.set(lx, fy - k, lz, k == 0 ? g.S(Theme.GROUND) : (k == 1 ? g.S(Theme.SUB) : g.S(Theme.STONE)));
                }
                if (h % 2 == 0) g.set(fx - g.bx, fy + 1, fz - g.bz, gold);
                else g.set(fx - g.bx, fy + 1, fz - g.bz, lamp);
            }
    }

    private SkyRealm() {}
}
