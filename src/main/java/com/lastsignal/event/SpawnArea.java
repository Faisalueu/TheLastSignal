package com.lastsignal.event;

import com.lastsignal.sound.ModSounds;
import com.lastsignal.world.Houses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Small, temporary changes around the spawn anchor: footprints that lead toward the spawn (or, late in the story, toward the player),
 * and a warning sign whose text follows the player's phase. Every block placed is remembered with the original state and put back after
 * a few minutes (or on server stop); a block the player changed in the meantime is never overwritten.
 */
public final class SpawnArea {
    private record Temp(ServerLevel l, BlockPos pos, BlockState old, BlockState placed) {}
    private static final List<Temp> TEMP = new ArrayList<>();
    private static final int MAX_TEMP = 160;

    private static final String[] SIGN_TEXT = {
            "WHO LEFT THE LIGHT ON",
            "WE SAW YOU STAND HERE",
            "YOU CAME BACK TO THE SAME PLACE",
            "THE SIGNAL STARTED HERE"};

    static boolean put(ServerLevel l, BlockPos p, BlockState s, int ticks) {
        if (TEMP.size() >= MAX_TEMP || !l.hasChunkAt(p)) return false;
        Temp t = new Temp(l, p.immutable(), l.getBlockState(p), s);
        TEMP.add(t);
        l.setBlock(p, s, 3);
        Tasks.later(ticks, () -> restore(t));
        return true;
    }

    private static void restore(Temp t) {
        if (!TEMP.remove(t)) return;
        if (t.l.hasChunkAt(t.pos) && t.l.getBlockState(t.pos).is(t.placed.getBlock())) t.l.setBlock(t.pos, t.old, 3);
    }

    /** Server stop: put back everything still pending in loaded chunks. */
    public static void restoreAll() {
        for (Temp t : new ArrayList<>(TEMP)) {
            try { restore(t); } catch (RuntimeException ignored) { }
        }
        TEMP.clear();
    }

    static boolean footprints(EventManager.Ctx c) {
        BlockPos anchor = SpawnAnchor.of(c.l, c.p);
        if (anchor == null) return false;
        double rad = com.lastsignal.config.LastSignalConfig.COMMON.spawnAnchorRadius.get() * 0.9;
        double a = c.r.nextDouble() * Math.PI * 2;
        Vec3 from = new Vec3(anchor.getX() + Math.cos(a) * rad, 0, anchor.getZ() + Math.sin(a) * rad);
        // late story: the trail ends at the player, not at the spawn
        Vec3 to = c.stage >= 4 ? new Vec3(c.p.getX(), 0, c.p.getZ()) : new Vec3(anchor.getX(), 0, anchor.getZ());
        Vec3 dir = to.subtract(from);
        double len = dir.length() - (c.stage >= 4 ? 9 : 6);
        if (len < 8) return false;
        dir = dir.normalize();
        List<BlockPos> spots = new ArrayList<>();
        int steps = (int) Math.min(16, len / 1.7);
        for (int i = 0; i < steps; i++) {
            double side = (i % 2 == 0 ? 0.45 : -0.45);
            double x = from.x + dir.x * i * 1.7 - dir.z * side;
            double z = from.z + dir.z * i * 1.7 + dir.x * side;
            BlockPos g = Placement.surface(c.l, BlockPos.containing(x, c.p.getY(), z));
            if (g == null || !c.l.getFluidState(g).isEmpty() || !c.l.getBlockState(g).isAir()) continue;
            BlockState below = c.l.getBlockState(g.below());
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)) spots.add(g.below());
        }
        if (spots.size() < 5) return false;
        for (BlockPos s : spots) put(c.l, s, Blocks.COARSE_DIRT.defaultBlockState(), 5400 + c.r.nextInt(1200));
        Fx.privateSound(c.p, ModSounds.FOOTSTEP_FAR.get(), Vec3.atCenterOf(spots.get(0)), 0.8f, 0.9f);
        return true;
    }

    static boolean sign(EventManager.Ctx c) {
        BlockPos anchor = SpawnAnchor.of(c.l, c.p);
        if (anchor == null) return false;
        for (int i = 0; i < 20; i++) {
            double a = c.r.nextDouble() * Math.PI * 2, d = 7 + c.r.nextDouble() * 9;
            BlockPos g = Placement.surface(c.l, new BlockPos((int) (anchor.getX() + Math.cos(a) * d), anchor.getY(), (int) (anchor.getZ() + Math.sin(a) * d)));
            if (g == null || !Placement.standable(c.l, g) || !c.l.getBlockState(g.below()).is(Blocks.GRASS_BLOCK)) continue;
            if (g.distSqr(c.p.blockPosition()) < 64) continue;
            BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, c.r.nextInt(16));
            if (!put(c.l, g, sign, 9000)) return false;
            Houses.fillSign(c.l, g, SIGN_TEXT[Director.phaseOf(c.stage).ordinal()]);
            return true;
        }
        return false;
    }

    private SpawnArea() {}
}
