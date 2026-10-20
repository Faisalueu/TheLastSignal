package com.lastsignal.entity;

import com.lastsignal.world.gen.ExtraThemes;
import com.lastsignal.world.gen.Themes;
import com.lastsignal.world.gen.Volcano;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Natural spawn rule for the hostile creatures of the Infernal Volcano (hollow, crawler). Only in that dimension, only on dry solid ground, never on the
 * arrival plateau, near the exit crater or on the ash road, and never more than two horrors within 96 blocks. Spawns via spawn egg / command / story
 * events do not consult this rule.
 */
public final class VolcanoSpawns {
    public static boolean check(EntityType<HorrorEntity> type, ServerLevelAccessor l, MobSpawnType reason, BlockPos p, RandomSource rnd) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        if (!l.getLevel().dimension().location().getPath().equals(ExtraThemes.VOLCANO)) return false;
        if (!l.getBlockState(p.below()).blocksMotion() || !l.getFluidState(p.below()).isEmpty() || !l.getFluidState(p).isEmpty()) return false;
        var t = Themes.get(ExtraThemes.VOLCANO);
        if (Math.hypot(p.getX() - 8, p.getZ() - 8) < 80) return false;
        if (Math.hypot(p.getX() - Volcano.exitX(t), p.getZ() - Volcano.exitZ(t)) < 70) return false;
        if (Volcano.roadDistance(t, p.getX(), p.getZ()) < 10) return false;
        return l.getLevel().getEntitiesOfClass(HorrorEntity.class, new AABB(p).inflate(96)).size() < 2;
    }

    private VolcanoSpawns() {}
}
