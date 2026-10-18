package com.lastsignal.world.gen;

import com.lastsignal.block.ModBlocks;
import com.lastsignal.config.LastSignalConfig;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Natural placement of the Black / White fracture blocks. World is divided into 5x5-chunk cells; each cell may hold one black and one white block,
 * in different chunks at least 2 chunks apart (never adjacent). Deterministic per theme; chunk-local; needs a solid floor with 3 free blocks above.
 */
final class Fractures {
    static final int CELL = 5;

    static double density() {
        try { return LastSignalConfig.COMMON.transitDensity.get(); } catch (IllegalStateException e) { return 1.0; }
    }

    static void apply(Gen g) {
        if (g.cx == 0 && g.cz == 0) return;
        double dens = density();
        if (dens <= 0) return;
        int sx = Math.floorDiv(g.cx, CELL), sz = Math.floorDiv(g.cz, CELL);
        int h = Noise.hash(g.t.seed + 7001, sx, sz);
        int bx = Math.floorMod(h, CELL), bz = Math.floorMod(h >> 8, CELL);
        int wx = Math.floorMod(h >> 16, CELL), wz = Math.floorMod(h >> 24, CELL);
        boolean haveBlack = Math.floorMod(Noise.hash(g.t.seed + 7002, sx, sz), 100) < 75 * dens;
        boolean haveWhite = Math.floorMod(Noise.hash(g.t.seed + 7003, sx, sz), 100) < 60 * dens && Math.max(Math.abs(bx - wx), Math.abs(bz - wz)) >= 2;
        int lx = Math.floorMod(g.cx, CELL), lz = Math.floorMod(g.cz, CELL);
        if (haveBlack && lx == bx && lz == bz) place(g, "black_fracture", 1);
        else if (haveWhite && lx == wx && lz == wz) place(g, "white_fracture", 2);
    }

    private static void place(Gen g, String id, int salt) {
        Random rr = new Random(Noise.hash(g.t.seed + 7100 + salt, g.cx, g.cz));
        boolean stairs = g.t.kind.equals("stairs");
        BlockState s = ModBlocks.BY_ID.get(id).get().defaultBlockState();
        for (int a = 0; a < 40; a++) {
            int x = stairs ? 7 + rr.nextInt(2) : 3 + rr.nextInt(10), z = stairs ? 7 + rr.nextInt(2) : 3 + rr.nextInt(10);
            int ground = g.gh[x][z];
            if (ground < 2 || ground > 240) continue;
            BlockState fl = g.get(x, ground, z);
            if (fl.isAir() || !fl.getFluidState().isEmpty() || !fl.blocksMotion()) continue;
            if (!g.get(x, ground + 1, z).isAir() || !g.get(x, ground + 2, z).isAir() || !g.get(x, ground + 3, z).isAir()) continue;
            boolean clear = true;                                   // keep the 4 neighbours walkable so it is reachable
            int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int open = 0;
            for (int[] d : n) { int ax = x + d[0], az = z + d[1]; if (ax >= 0 && ax < 16 && az >= 0 && az < 16 && g.get(ax, ground + 1, az).isAir()) open++; }
            if (open < 2) clear = false;
            if (!clear) continue;
            g.set(x, ground + 1, z, s);
            g.set(x, ground + 2, z, s);
            return;
        }
    }

    private Fractures() {}
}
