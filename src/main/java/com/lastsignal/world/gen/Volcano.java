package com.lastsignal.world.gen;

import com.lastsignal.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Infernal Volcano. A bounded island of ash and basalt (lava ocean beyond radius ~470) with lava lakes, cones with calderas, lava
 * fissures and a guaranteed, flat "ash road" from the spawn to a rock-ringed bowl that hides the one real exit crater. Ordinary craters
 * (decoys) exist elsewhere and are NOT exits: only the zone at the bottom of the real crater is (see inExit). No chests, no loot, no gate.
 * Pure function of position, so chunks agree without cross-chunk writes.
 */
public final class Volcano {
    static final int LAVA = 62, FLAT = 72, BOWL = 68, CRATER_DEPTH = 12, CRATER_R = 14, FLOOR_R = 3, RING_R = 17, CONE_CELL = 110, PIT_CELL = 90;

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

    private Volcano() {}
}
