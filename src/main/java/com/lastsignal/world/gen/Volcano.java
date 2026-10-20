package com.lastsignal.world.gen;

import com.lastsignal.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Infernal Volcano. A bounded island of ash and basalt (lava ocean beyond radius ~470) with lava lakes, cones with calderas, lava
 * fissures and a guaranteed, flat "ash road" from the spawn to a rock-ringed bowl that hides the one real exit crater. Ordinary craters
 * (decoys) exist elsewhere and are NOT exits: only the zone at the bottom of the real crater is (see inExit). No chests, no loot, no gate.
 * Pure function of position, so chunks agree without cross-chunk writes.
 */
public final class Volcano {
    public static final int LAVA = 62, FLAT = 72, BOWL = 68, CRATER_DEPTH = 12, CRATER_R = 14, FLOOR_R = 3, RING_R = 17, CONE_CELL = 110, PIT_CELL = 90;

    private static final class Route {
        double[][] pts; double[] cum; int ex, ez; double gapX, gapZ;
    }

    private static volatile Route route;

    private static Route route(Theme t) {
        Route r = route;
        if (r != null) return r;
        synchronized (Volcano.class) {
            if (route != null) return route;
            r = new Route();
            double th = Noise.rnd(t.seed + 11, 1, 1) * Math.PI * 2, R = 285 + Noise.hash(t.seed + 12, 1, 1) % 60;
            r.ex = (int) Math.round(Math.cos(th) * R); r.ez = (int) Math.round(Math.sin(th) * R);
            double ux = Math.cos(th), uz = Math.sin(th), px = -uz, pz = ux;
            r.pts = new double[6][];
            r.pts[0] = new double[]{8, 8};
            for (int i = 1; i <= 4; i++) {
                double f = i / 5.0, off = 55 * Math.sin(i * 1.7 + th * 3) * (i == 4 ? .5 : 1);
                r.pts[i] = new double[]{8 + (r.ex - 8) * f + px * off, 8 + (r.ez - 8) * f + pz * off};
            }
            r.pts[5] = new double[]{r.ex - ux * 40, r.ez - uz * 40};          // the road ends 40 blocks short of the bowl, on the spawn side
            r.cum = new double[6];
            for (int i = 1; i < 6; i++) r.cum[i] = r.cum[i - 1] + Math.hypot(r.pts[i][0] - r.pts[i - 1][0], r.pts[i][1] - r.pts[i - 1][1]);
            // the opening of the ring faces away from the road, rotated 70 degrees, so the player has to walk around the ring
            double a = th + Math.PI + Math.toRadians(70);
            r.gapX = Math.cos(a); r.gapZ = Math.sin(a);
            route = r;
            return r;
        }
    }

    public static int exitX(Theme t) { return route(t).ex; }
    public static int exitZ(Theme t) { return route(t).ez; }
    public static int exitBottomY(Theme t) { return height(t, route(t).ex, route(t).ez); }
    public static double[][] roadPoints(Theme t) { return route(t).pts; }

    /** True only inside the real crater's floor. Used server side by the exit logic. */
    public static boolean inExit(Theme t, double x, double y, double z) {
        Route r = route(t);
        return Math.hypot(x - (r.ex + .5), z - (r.ez + .5)) <= FLOOR_R + .2 && y <= exitBottomY(t) + 3.5 && y >= exitBottomY(t) - 1;
    }

    private static double sm(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    static double roadP(double s) { return 72 + 4 * Math.sin(s / 41.0) + 2.5 * Math.sin(s / 19.0 + 1); }

    /** distance to the road centre line, plus the road height at the nearest point in out[1] */
    private static double roadDist(Route r, double x, double z, double[] out) {
        double best = 1e9, bs = 0;
        for (int i = 0; i < 5; i++) {
            double ax = r.pts[i][0], az = r.pts[i][1], bx = r.pts[i + 1][0], bz = r.pts[i + 1][1];
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double u = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double d = Math.hypot(x - (ax + dx * u), z - (az + dz * u));
            if (d < best) { best = d; bs = r.cum[i] + Math.sqrt(l2) * u; }
        }
        out[1] = roadP(bs);
        return best;
    }

    /** terrain height at a column; lava level (0 = none) in out[0] if given */
    private static int terrain(Theme t, int x, int z, int[] lavaOut, boolean[] crackOut) {
        Route r = route(t);
        double dsp = Math.hypot(x - 8, z - 8), dE = Math.hypot(x - r.ex, z - r.ez);
        double h = 72 + (Noise.fbm(t.seed + 1, x / 70.0, z / 70.0, 3) - .5) * 28;
        int lava = LAVA;
        // cones with calderas
        int fx = Math.floorDiv(x, CONE_CELL), fz = Math.floorDiv(z, CONE_CELL);
        for (int gx = fx - 1; gx <= fx + 1; gx++) for (int gz = fz - 1; gz <= fz + 1; gz++) {
            int hh = Noise.hash(t.seed + 21, gx, gz);
            if (hh % 100 >= 60) continue;
            int cx = gx * CONE_CELL + 20 + (hh >> 8) % (CONE_CELL - 40), cz = gz * CONE_CELL + 20 + (hh >> 16) % (CONE_CELL - 40);
            if (Math.hypot(cx - 8, cz - 8) < 75 || Math.hypot(cx - r.ex, cz - r.ez) < 80) continue;
            double rad = 26 + (hh >> 4) % 19, peak = 16 + (hh >> 12) % 30, d = Math.hypot(x - cx, z - cz) / rad;
            if (d >= 1) continue;
            h += peak * Math.pow(1 - d, 1.4);
            if ((hh >> 20) % 2 == 0 && d < .2) {                   // caldera: a perched lava lake inside solid walls
                double rimH = 72 + peak * Math.pow(.8, 1.4);
                h = Math.min(h, rimH - 12 + 10 * (d / .2) * (d / .2));
                lava = Math.max(lava, (int) Math.round(rimH - 8));
            }
        }
        double n2 = Noise.fbm(t.seed + 5, x / 55.0, z / 55.0, 3);
        if (n2 < .36) h -= (.36 - n2) * 130;                           // lava lake basins
        // spawn plateau (flat, lava free) and exit bowl
        if (dsp < 46) h += (FLAT - h) * sm(1 - (dsp - 14) / 32);
        if (dE < 44) {
            double k = sm(1 - (dE - 24) / 20);
            h += (BOWL - h) * k;
            lava = dE < 34 ? 0 : lava;
            if (dE < 34) lava = 0;
            double ang = Math.atan2(z - r.ez, x - r.ex);
            double cosGap = ((x - r.ex) * r.gapX + (z - r.ez) * r.gapZ) / Math.max(.001, dE);
            double ring = Math.exp(-Math.pow((dE - RING_R) / 3.0, 2)) * 11 * sm((1 - cosGap) / .45);   // low (open) where cosGap -> 1
            h += ring;
            if (dE < CRATER_R) {
                double f = dE <= FLOOR_R ? 0 : sm((dE - FLOOR_R) / (double) (CRATER_R - FLOOR_R));
                h = BOWL + ring - CRATER_DEPTH * (1 - f);
            }
        }
        // decoy pits (ordinary craters: dry, shallow, walkable, never exits)
        int px = Math.floorDiv(x, PIT_CELL), pz = Math.floorDiv(z, PIT_CELL);
        for (int gx = px - 1; gx <= px + 1; gx++) for (int gz = pz - 1; gz <= pz + 1; gz++) {
            int hh = Noise.hash(t.seed + 31, gx, gz);
            if (hh % 100 >= 40) continue;
            int cx = gx * PIT_CELL + 12 + (hh >> 8) % (PIT_CELL - 24), cz = gz * PIT_CELL + 12 + (hh >> 16) % (PIT_CELL - 24);
            if (Math.hypot(cx - 8, cz - 8) < 55 || Math.hypot(cx - r.ex, cz - r.ez) < 65) continue;
            double d = Math.hypot(x - cx, z - cz);
            if (d < 8) { double f = sm(d / 8.0); double base = Math.max(h, 68); h = Math.min(h, base - 7 * (1 - f)); if (h < 62) h = 62; }
        }
        // the ash road: flat corridor, smooth banks (causeway over lakes)
        double[] ro = new double[2];
        double rd = roadDist(r, x, z, ro);
        if (rd < 12) h = ro[1] + (h - ro[1]) * sm((rd - 2) / 10.0);
        boolean nearRoad = rd < 6;
        // lava sea beyond the island edge
        double rr = Math.hypot(x, z);
        if (rr > 440) h += (40 - h) * sm((rr - 440) / 70.0);
        if (rr > 440) lava = LAVA;
        h = Math.max(30, Math.min(150, h));
        int hi = (int) Math.round(h);
        if (crackOut != null) {
            double nc = Noise.fbm(t.seed + 9, x / 30.0, z / 30.0, 2);
            crackOut[0] = Math.abs(nc - .5) < .012 && hi > LAVA + 3 && dsp > 34 && dE > 40 && !nearRoad && rr < 420;
        }
        if (lavaOut != null) lavaOut[0] = lava;
        return hi;
    }

    public static int height(Theme t, int x, int z) { return terrain(t, x, z, null, null); }

    static void run(Gen g) {
        Theme t = g.t;
        BlockState ash = g.S(Theme.GROUND), cinder = g.S(Theme.SUB), basalt = g.S(Theme.STONE), ember = g.S(Theme.LIGHT), rim = g.S(Theme.ACCENT);
        BlockState lavaS = Blocks.LAVA.defaultBlockState();
        Route r = route(t);
        int[] lv = new int[1]; boolean[] crack = new boolean[1];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = g.bx + x, wz = g.bz + z;
            int h = terrain(t, wx, wz, lv, crack);
            g.gh[x][z] = h;
            g.set(x, 0, z, Gen.BEDROCK);
            for (int y = 1; y <= h - 4; y++) g.set(x, y, z, basalt);
            for (int y = Math.max(1, h - 3); y < h; y++) g.set(x, y, z, cinder);
            double dE = Math.hypot(wx - r.ex, wz - r.ez);
            BlockState top = ash;
            int hh = Noise.hash(t.seed + 61, wx, wz);
            if (Noise.fbm(t.seed + 7, wx / 12.0, wz / 12.0, 2) > .62) top = cinder;
            if (h <= LAVA + 1 || h > 105) top = basalt;
            if (hh % 90 == 0 && h > LAVA + 2 && dE > 22) top = ember;                        // glowing vents: light in the dark, harmless
            if (dE >= CRATER_R - 2.5 && dE <= CRATER_R + 1.5 && h <= BOWL) top = rim;      // pale-orange rim circle around the real crater: the visual hint
            if (dE <= 1.2) top = ember;                                                      // exit vent
            g.set(x, h, z, top);
            if (crack[0]) { g.set(x, h, z, lavaS); g.set(x, h - 1, z, basalt); }
            else if (lv[0] > h) for (int y = h + 1; y <= lv[0]; y++) g.set(x, y, z, lavaS);
        }
        lavafalls(g);
        statues(g);
        // smoke signals on two crest points of the ring (campfires with signal smoke on hay): visible from the road end, not on the way
        for (int k = -1; k <= 1; k += 2) {
            double a = Math.atan2(r.gapZ, r.gapX) + Math.PI + k * Math.toRadians(55);
            int sx = r.ex + (int) Math.round(Math.cos(a) * RING_R), sz = r.ez + (int) Math.round(Math.sin(a) * RING_R);
            int sy = height(t, sx, sz);
            int lx = sx - g.bx, lz = sz - g.bz;
            g.set(lx, sy + 1, lz, Blocks.HAY_BLOCK.defaultBlockState());
            g.set(lx, sy + 2, lz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true).setValue(CampfireBlock.LIT, true));
        }
    }

    // ---------------------------------------------------------------------------------------------------- lavafalls
    /**
     * Lavafalls: a floating basalt ledge 28-42 blocks above the ground with a lava pool on top; its centre column pours falling lava (level 8) down to a
     * small lava pool on the ground. Placed as STATIC fluid blocks at generation time (no scheduled fluid ticks, nothing spreads until something disturbs it, and
     * then vanilla flow is bounded). Anchors keep clear of the spawn plateau, the exit bowl and the ash road (>= 30 blocks), so no route depends on them.
     * Pure function of position; the client uses the same function for particles and sound.
     */
    public static final int FALL_CELL = 72;
    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> ANCHORS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] NONE = new int[0];

    /** {x, z, ledgeTopY, groundY} or null */
    public static int[] lavafall(Theme t, int gx, int gz) {
        int[] a = ANCHORS.computeIfAbsent(((long) gx << 32) ^ (gz & 0xffffffffL), k -> compute(t, gx, gz));
        return a.length == 0 ? null : a;
    }

    private static int[] compute(Theme t, int gx, int gz) {
        int hh = Noise.hash(t.seed + 71, gx, gz);
        if (hh % 100 >= 55) return NONE;
        int ax = gx * FALL_CELL + 14 + (hh >> 8) % (FALL_CELL - 28), az = gz * FALL_CELL + 14 + (hh >> 16) % (FALL_CELL - 28);
        Route r = route(t);
        if (Math.hypot(ax - 8, az - 8) < 90 || Math.hypot(ax - r.ex, az - r.ez) < 90 || Math.hypot(ax, az) > 400) return NONE;
        if (roadDistance(t, ax, az) < 30) return NONE;
        int[] lv = new int[1];
        int ground = terrain(t, ax, az, lv, null);
        if (ground < LAVA + 4 || lv[0] > ground) return NONE;
        for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}})      // the landing pool needs level ground
            if (Math.abs(terrain(t, ax + d[0], az + d[1], null, null) - ground) > 1) return NONE;
        int top = Math.min(185, ground + 28 + (hh >> 4) % 15);
        return new int[]{ax, az, top, ground};
    }

    /** distance from a column to the ash road's centre line (public: spawn rules keep monsters off the route) */
    public static double roadDistance(Theme t, double x, double z) { return roadDist(route(t), x, z, new double[2]); }

    /** all lavafalls whose anchor lies within r blocks of (x, z) */
    public static java.util.List<int[]> lavafallsNear(Theme t, double x, double z, double r) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int gx = Math.floorDiv((int) (x - r), FALL_CELL); gx <= Math.floorDiv((int) (x + r), FALL_CELL); gx++)
            for (int gz = Math.floorDiv((int) (z - r), FALL_CELL); gz <= Math.floorDiv((int) (z + r), FALL_CELL); gz++) {
                int[] a = lavafall(t, gx, gz);
                if (a != null && Math.hypot(a[0] - x, a[1] - z) <= r) out.add(a);
            }
        return out;
    }

    private static void lavafalls(Gen g) {
        Theme t = g.t;
        BlockState basalt = g.S(Theme.STONE), lava = Blocks.LAVA.defaultBlockState(), falling = lava.setValue(LiquidBlock.LEVEL, 8);
        for (int gx = Math.floorDiv(g.bx - 8, FALL_CELL); gx <= Math.floorDiv(g.bx + 24, FALL_CELL); gx++)
            for (int gz = Math.floorDiv(g.bz - 8, FALL_CELL); gz <= Math.floorDiv(g.bz + 24, FALL_CELL); gz++) {
                int[] a = lavafall(t, gx, gz);
                if (a == null) continue;
                int cx = a[0] - g.bx, cz = a[1] - g.bz, top = a[2], ground = a[3];
                if (cx < -8 || cx > 24 || cz < -8 || cz > 24) continue;
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 6.3) continue;
                    for (int y = top - 2; y <= top; y++) g.set(cx + dx, y, cz + dz, basalt);
                    if (d <= 3.7) g.set(cx + dx, top - 3, cz + dz, basalt);
                    if (d <= 1.7) g.set(cx + dx, top - 4, cz + dz, basalt);
                    if (d <= 2.5) g.set(cx + dx, top, cz + dz, lava);                       // the pool on the ledge
                    for (int y = top + 1; y <= top + 3; y++) g.set(cx + dx, y, cz + dz, Gen.AIR);
                    if (d >= 4.8 && d <= 6.3) g.set(cx + dx, top + 1, cz + dz, basalt);   // a low rim keeps the pool in
                }
                for (int y = top - 1; y > ground + 1; y--) g.set(cx, y, cz, falling);      // the fall (replaces ledge and stalactite at the centre)
                g.set(cx, ground + 1, cz, falling);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {         // landing pool
                    g.set(cx + dx, ground, cz + dz, lava);
                    g.set(cx + dx, ground - 1, cz + dz, basalt);
                }
            }
    }


    // ---------------------------------------------------------------------------------------------------- the congregation
    /**
     * Charred figures: small groups of 3-5 blackstone humanoids (arms raised, magma-block heads that glow faintly) standing 8-24 blocks from the ash road,
     * all turned toward it. They are only blocks - nothing here moves, attacks or spawns. Pure function of position like the lavafalls; the client uses
     * {@link #statuesNear} for the "they are closer than before" cue. Never on the road, within 40 of the spawn plateau, or within 30 of the exit bowl.
     */
    public static final int STAT_CELL = 36;

    private static double[] roadNearest(Route r, double x, double z) {
        double best = 1e9; double[] p = new double[2];
        for (int i = 0; i < 5; i++) {
            double ax = r.pts[i][0], az = r.pts[i][1], dx = r.pts[i + 1][0] - ax, dz = r.pts[i + 1][1] - az, l2 = dx * dx + dz * dz;
            double u = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double d = Math.hypot(x - (ax + dx * u), z - (az + dz * u));
            if (d < best) { best = d; p[0] = ax + dx * u; p[1] = az + dz * u; }
        }
        return p;
    }
    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> STAT = new java.util.concurrent.ConcurrentHashMap<>();

    /** {x, z, dirX, dirZ} of the group's centre and the unit direction (scaled x100) toward the road, or null */
    public static int[] statueGroup(Theme t, int gx, int gz) {
        int[] a = STAT.computeIfAbsent(((long) gx << 32) ^ (gz & 0xffffffffL), k -> computeStat(t, gx, gz));
        return a.length == 0 ? null : a;
    }

    private static int[] computeStat(Theme t, int gx, int gz) {
        int hh = Noise.hash(t.seed + 313, gx, gz);
        if (hh % 100 >= 85) return NONE;
        int ax = gx * STAT_CELL + 8 + (hh >> 8) % (STAT_CELL - 16), az = gz * STAT_CELL + 8 + (hh >> 16) % (STAT_CELL - 16);
        Route r = route(t);
        if (Math.hypot(ax - 8, az - 8) < 40 || Math.hypot(ax - r.ex, az - r.ez) < 30) return NONE;
        double[] out = new double[2];
        double rd = roadDist(r, ax, az, out);
        if (rd < 8 || rd > 32) return NONE;
        int[] lv = new int[1];
        int ground = terrain(t, ax, az, lv, null);
        if (ground < LAVA + 3 || lv[0] > ground || ground > 100) return NONE;
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}})
            if (Math.abs(terrain(t, ax + d[0], az + d[1], null, null) - ground) > 3) return NONE;
        double[] np = roadNearest(r, ax, az);
        double dx = np[0] - ax, dz = np[1] - az, len = Math.max(1e-6, Math.hypot(dx, dz));
        return new int[]{ax, az, (int) Math.round(dx / len * 100), (int) Math.round(dz / len * 100)};
    }

    public static java.util.List<int[]> statuesNear(Theme t, double x, double z, double r) {
        java.util.List<int[]> res = new java.util.ArrayList<>();
        for (int gx = Math.floorDiv((int) (x - r), STAT_CELL); gx <= Math.floorDiv((int) (x + r), STAT_CELL); gx++)
            for (int gz = Math.floorDiv((int) (z - r), STAT_CELL); gz <= Math.floorDiv((int) (z + r), STAT_CELL); gz++) {
                int[] a = statueGroup(t, gx, gz);
                if (a != null && Math.hypot(a[0] - x, a[1] - z) <= r) res.add(a);
            }
        return res;
    }

    /** figure offsets (dx, dz) around the group centre; the count (3-5) is fixed by the cell hash */
    public static int[][] statueFigures(Theme t, int[] a) {
        int n = 3 + (Noise.hash(t.seed + 314, a[0], a[1]) & 0x7fffffff) % 3;
        int[][] f = new int[n][2];
        double px = -a[3] / 100.0, pz = a[2] / 100.0;          // perpendicular to the facing direction: they stand in a line across the road's view
        for (int i = 0; i < n; i++) {
            double s = (i - (n - 1) / 2.0) * 3.0, jitter = ((Noise.hash(t.seed + 315 + i, a[0], a[1]) & 0xff) / 255.0 - .5) * 2.0;
            f[i][0] = (int) Math.round(px * s - a[2] / 100.0 * jitter); f[i][1] = (int) Math.round(pz * s - a[3] / 100.0 * jitter);
        }
        return f;
    }

    private static void statues(Gen g) {
        Theme t = g.t;
        BlockState black = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), rough = Blocks.BLACKSTONE.defaultBlockState(), head = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState(), wall = Blocks.BLACKSTONE_WALL.defaultBlockState();
        for (int gx = Math.floorDiv(g.bx - 12, STAT_CELL); gx <= Math.floorDiv(g.bx + 28, STAT_CELL); gx++)
            for (int gz = Math.floorDiv(g.bz - 12, STAT_CELL); gz <= Math.floorDiv(g.bz + 28, STAT_CELL); gz++) {
                int[] a = statueGroup(t, gx, gz);
                if (a == null) continue;
                double fx = a[2] / 100.0, fz = a[3] / 100.0;
                for (int[] o : statueFigures(t, a)) {
                    int wx = a[0] + o[0], wz = a[1] + o[1];
                    int lx = wx - g.bx, lz = wz - g.bz;
                    if (lx < -2 || lx > 17 || lz < -2 || lz > 17) continue;
                    int[] lv = new int[1];
                    int y0 = terrain(t, wx, wz, lv, null);
                    if (lv[0] > y0 || y0 <= LAVA + 1) continue;
                    // the arms reach along the axis perpendicular to the facing direction
                    int ax = Math.abs(fz) > Math.abs(fx) ? 1 : 0, az = 1 - ax;
                    g.set(lx, y0, lz, rough);
                    for (int y = 1; y <= 2; y++) g.set(lx, y0 + y, lz, black);                         // legs
                    for (int y = 3; y <= 4; y++) g.set(lx, y0 + y, lz, rough);                         // torso
                    g.set(lx, y0 + 5, lz, head);                                                        // head
                    g.set(lx + ax, y0 + 4, lz + az, wall); g.set(lx - ax, y0 + 4, lz - az, wall);       // shoulders
                    g.set(lx + ax, y0 + 5, lz + az, wall); g.set(lx - ax, y0 + 5, lz - az, wall);       // arms raised toward the sky
                    g.set(lx + ax, y0 + 6, lz + az, wall); g.set(lx - ax, y0 + 6, lz - az, wall);
                    // bones at the feet, one side only (they were kneeling once)
                    g.set(lx + (int) Math.signum(fx), y0 + 1, lz + (int) Math.signum(fz), bone);
                }
            }
    }

    private Volcano() {}
}
