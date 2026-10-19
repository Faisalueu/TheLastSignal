package com.lastsignal.world.gen;

import com.lastsignal.world.Dimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.util.Random;

/** Picks a safe landing point (and how far above the ground to drop the player) in a destination dimension. */
public final class Landing {
    public record Plan(BlockPos pos, int ground, int fall) {}

    private static boolean solid(BlockState s) { return s.blocksMotion(); }

    private static boolean bad(BlockState s) {
        return s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.FIRE) || s.getFluidState().is(Fluids.LAVA);
    }

    /** Scan up from a ground block g at (x,z): feet end up min(wanted, free column) blocks above the ground. null if the ground is unsafe. */
    public static Plan scan(ServerLevel l, int x, int z, int g, int wanted) {
        BlockPos gp = new BlockPos(x, g, z);
        BlockState gs = l.getBlockState(gp);
        if (bad(gs) || (!solid(gs) && gs.getFluidState().isEmpty())) return null;
        int top = l.getMaxBuildHeight() - 4;
        if (solid(l.getBlockState(gp.above())) || solid(l.getBlockState(gp.above(2)))) return null;
        int n = 0;
        while (n < wanted && g + 2 + n < top && !solid(l.getBlockState(new BlockPos(x, g + 2 + n, z)))) n++;
        while (n > 0 && solid(l.getBlockState(new BlockPos(x, g + 2 + n, z)))) n--;
        return new Plan(new BlockPos(x, g + 1 + n, z), g, n);
    }

    public static Plan plan(ServerLevel l, String id, long seed, int wanted) {
        Theme t = Themes.get(id);
        if (t == null) {                                   // gameplay dimension: use its built arrival pad
            BlockPos a = Dimensions.ensureBuilt(l, id);
            l.getChunk(a);
            return scan(l, a.getX(), a.getZ(), a.getY() - 1, wanted);
        }
        Profile p = Themes.profile(t);
        Random r = new Random(seed);
        Plan best = null;
        for (int a = 0; a < 40; a++) {
            int cx = r.nextInt(11) - 5, cz = r.nextInt(11) - 5;
            int wx = cx * 16 + 6 + r.nextInt(5), wz = cz * 16 + 6 + r.nextInt(5);
            int g;
            switch (t.kind) {
                case "rooms" -> {
                    Rooms.Desc d = Rooms.desc(t, p, cx, cz);
                    if (d.arch == Profile.PIT || d.arch == Profile.SHAFT || d.arch == Profile.ORGANIC || d.arch == Profile.CRAWL) continue;
                    g = Gen.Y0 - 1;
                    l.getChunk(cx, cz);
                }
                case "open" -> { l.getChunk(cx, cz); g = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1; }
                case "deep" -> { l.getChunk(cx, cz); g = Gen.deepFloor(t, wx, wz); }
                case "islands", "void" -> {
                    int[] is = Gen.island(t, cx, cz);
                    if (is[0] == 0) continue;
                    wx = cx * 16 + is[1]; wz = cz * 16 + is[2]; g = is[4];
                    l.getChunk(cx, cz);
                }
                case "sea" -> { l.getChunk(cx, cz); g = (int) t.p[Theme.P_SEA]; }
                default -> { l.getChunk(cx, cz); g = 24; wx = cx * 16 + 8; wz = cz * 16 + 8; }   // stairs: shaft centre column, floor at 24
            }
            Plan pl = scan(l, wx, wz, g, wanted);
            if (pl == null) continue;
            if (best == null || pl.fall() > best.fall()) best = pl;
            if (pl.fall() >= Math.min(wanted, 12)) return pl;
        }
        if (best != null) return best;
        BlockPos a = Dimensions.ensureBuilt(l, id);
        l.getChunk(a);
        return new Plan(a, a.getY() - 1, 0);
    }

    private Landing() {}
}
