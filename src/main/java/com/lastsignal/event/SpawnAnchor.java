package com.lastsignal.event;

import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.world.Dimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Which "spawn" an event is centred on. PERSONAL = the player's own respawn point (bed / anchor), WORLD = the shared world spawn,
 * AUTO = personal when set in this dimension, else world spawn. Server-side state only; never moves the player.
 * Returns null when no anchor applies (mod dimensions, other dimensions without a spawn, player too far away to notice anything).
 */
public final class SpawnAnchor {
    public enum Mode { AUTO, PERSONAL, WORLD }

    private SpawnAnchor() {}

    public static BlockPos of(ServerLevel l, ServerPlayer p) {
        LastSignalConfig.Common c = LastSignalConfig.COMMON;
        if (!c.spawnAnchorEnabled.get() || Dimensions.isModDimension(l)) return null;
        Mode m = c.spawnAnchorMode.get();
        BlockPos pos = null;
        if (m != Mode.WORLD) {
            BlockPos rp = p.getRespawnPosition();
            if (rp != null && p.getRespawnDimension().equals(l.dimension())) pos = rp;
        }
        if (pos == null && m != Mode.PERSONAL && l.dimension().equals(Level.OVERWORLD)) pos = l.getSharedSpawnPos();
        if (pos == null) return null;
        double dx = pos.getX() - p.getX(), dz = pos.getZ() - p.getZ();
        double reach = c.spawnAnchorRadius.get() + 64;
        return dx * dx + dz * dz <= reach * reach ? pos : null;
    }
}
