package com.lastsignal.event;

import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * The ONLY place mod creatures are spawned. Spawning is always initiated by the server, honours the
 * simultaneous-hostile cap, and the creature has its own lifetime so nothing accumulates.
 */
public final class HorrorSpawner {
    /** @return the spawned entity or null if capped/invalid. */
    public static HorrorEntity spawnAt(ServerLevel l, String id, BlockPos pos, ServerPlayer near) {
        var ro = ModEntities.BY_ID.get(id);
        if (ro == null) return null;
        if (near != null && countNear(l, near) >= LastSignalConfig.COMMON.maxSimultaneousHostiles.get()) return null;
        HorrorEntity e = ro.get().create(l);
        if (e == null) return null;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, l.random.nextFloat() * 360f, 0f);
        if (near != null) {
            e.setYRot((float) (Math.toDegrees(Math.atan2(near.getZ() - e.getZ(), near.getX() - e.getX())) - 90));
        }
        if (!l.noCollision(e)) return null;
        l.addFreshEntity(e);
        return e;
    }

    public static HorrorEntity spawnNear(ServerLevel l, ServerPlayer p, String id, Placement rule, double min, double max) {
        BlockPos pos = rule.find(l, p, min, max);
        return pos == null ? null : spawnAt(l, id, pos, p);
    }

    public static int countNear(ServerLevel l, ServerPlayer p) {
        return l.getEntitiesOfClass(HorrorEntity.class, new AABB(p.blockPosition()).inflate(64)).size();
    }

    private HorrorSpawner() {}
}
