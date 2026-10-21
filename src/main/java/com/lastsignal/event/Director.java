package com.lastsignal.event;

import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.story.PlayerStory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * The director's rules layer (design lock): who an event concerns (Scope), which of the four progression phases a player is in (Phase, derived from the
 * story state and its flags), when cooldowns are shared, and what is cleaned up when a player dies, leaves or changes world.
 */
public final class Director {
    /** PLAYER: only that player sees it. LOCAL_GROUP: players in the same area share it (and its cooldown). WORLD: once for the whole world. STORY: world-level and tied to a story flag. */
    public enum Scope { PLAYER, LOCAL_GROUP, WORLD, STORY }

    /** The four progression phases. FINAL_SIGNAL needs the story flag signal_core_unlocked or a defeated boss (see PlayerStory.stage). */
    public enum Phase { SUSPICION, OBSERVATION, PURSUIT, FINAL_SIGNAL }

    public static Scope scopeOf(EventManager.Def d) {
        if (d.cat == EventManager.Cat.STORY) return Scope.STORY;
        return switch (d.tgt) {
            case PER_PLAYER -> Scope.PLAYER;
            case SHARED, AREA, PARTY -> Scope.LOCAL_GROUP;
            case GLOBAL -> Scope.WORLD;
        };
    }

    public static Phase phaseOf(int stage) {
        return stage <= 2 ? Phase.SUSPICION : stage == 3 ? Phase.OBSERVATION : stage <= 5 ? Phase.PURSUIT : Phase.FINAL_SIGNAL;
    }

    public static Phase phaseOf(PlayerStory st) { return phaseOf(st.stage()); }

    /** Cooldown key by scope: PLAYER per player; LOCAL_GROUP per dimension + 64-block region (friends standing together share it); WORLD/STORY once per world. */
    public static String cooldownKey(EventManager.Def d, ServerPlayer p) {
        return switch (scopeOf(d)) {
            case PLAYER -> p.getUUID() + ":" + d.id;
            case LOCAL_GROUP -> p.level().dimension().location() + ":" + (p.blockPosition().getX() >> 6) + "," + (p.blockPosition().getZ() >> 6) + ":" + d.id;
            case WORLD, STORY -> "world:" + d.id;
        };
    }

    /** Pursuit-category events never start before the Observation phase unless they are gated by a specific item the player chose to carry. */
    public static boolean phaseAllows(EventManager.Def d, int stage) {
        if (d.cat != EventManager.Cat.PURSUIT || d.item != null) return true;
        return phaseOf(stage).ordinal() >= Phase.OBSERVATION.ordinal();
    }

    /** Removes the non-boss creatures that were hunting this player (death, logout, world change) so nothing keeps acting on a player who is gone. */
    public static int cleanup(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel sl)) return 0;
        int n = 0;
        for (HorrorEntity h : sl.getEntitiesOfClass(HorrorEntity.class, new AABB(p.position(), p.position()).inflate(96))) {
            if (h.isBoss() || !h.isAlive()) continue;
            if (p.getUUID().equals(h.targetUuid()) || h.distanceToSqr(p) < 48 * 48) { h.vanish(); n++; }
        }
        return n;
    }

    private Director() {}
}
