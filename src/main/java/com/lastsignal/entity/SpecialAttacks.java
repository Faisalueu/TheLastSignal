package com.lastsignal.entity;

import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Special-attack framework. Each creature may own at most one special: a named move with a cooldown, a range band, a minimum story stage and an
 * effect that is bounded by construction (status effects, impulses, particles, small damage). Nothing here breaks blocks, spawns entities or changes the
 * world, every call is wrapped so an error can never reach the server loop, and the whole framework can be disabled in the config.
 * Used by HorrorEntity.tryMelee (hunters/lures/blockers) and tickPassive (watchers).
 */
public final class SpecialAttacks {
    interface Use { boolean run(HorrorEntity e, ServerPlayer p); }

    public record Def(String name, int cooldown, double minRange, double maxRange, int minStage, boolean melee, Use use) {}

    public static final Map<String, Def> BY_ENTITY = new HashMap<>();

    private static void reg(String id, Def d) { BY_ENTITY.put(id, d); }

    private static void sound(HorrorEntity e, SoundEvent s, float vol, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.HOSTILE, vol, pitch);
    }

    private static void burst(HorrorEntity e, net.minecraft.core.particles.ParticleOptions o, int n, double spread) {
        if (e.level() instanceof ServerLevel sl) sl.sendParticles(o, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), n, spread, e.getBbHeight() / 3, spread, 0.02);
    }

    private static void impulse(HorrorEntity e, Player p, double h, double v) {
        Vec3 d = new Vec3(p.getX() - e.getX(), 0, p.getZ() - e.getZ());
        if (d.lengthSqr() < 1e-4) return;
        d = d.normalize();
        p.setDeltaMovement(p.getDeltaMovement().add(d.x * h, v, d.z * h));
        p.hurtMarked = true;
    }

    private static void effect(Player p, net.minecraft.world.effect.MobEffect m, int ticks, int amp) {
        p.addEffect(new MobEffectInstance(m, ticks, amp, false, false, true));
    }

    static {
        reg("hollow", new Def("lunge", 160, 3, 9, 3, false, (e, p) -> {                  // a sudden dash that ends in a swipe
            Vec3 d = new Vec3(p.getX() - e.getX(), 0, p.getZ() - e.getZ());
            if (d.lengthSqr() < 1e-4) return false;
            d = d.normalize().scale(1.1);
            e.setDeltaMovement(d.x, 0.25, d.z);
            sound(e, ModSounds.GLITCH.get(), 0.8f, 0.6f);
            burst(e, ParticleTypes.SMOKE, 12, 0.3);
            return true;
        }));
        reg("crawler", new Def("ankle_grab", 200, 0, 3, 3, true, (e, p) -> {
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
            p.hurt(e.damageSources().mobAttack(e), 2f);
            sound(e, net.minecraft.sounds.SoundEvents.BONE_BLOCK_BREAK, 1f, 0.5f);
            return true;
        }));
        reg("burrower", new Def("tremor_slam", 240, 0, 6, 3, true, (e, p) -> {
            for (Player q : e.level().getEntitiesOfClass(Player.class, e.getBoundingBox().inflate(5, 2, 5))) {
                if (q.isCreative() || q.isSpectator()) continue;
                q.hurt(e.damageSources().mobAttack(e), 4f);
                impulse(e, q, 0.9, 0.35);
            }
            sound(e, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.7f, 0.5f);
            burst(e, ParticleTypes.POOF, 25, 1.5);
            return true;
        }));
        reg("tall_black", new Def("step_behind", 400, 6, 20, 4, false, (e, p) -> {       // it is suddenly behind you, and the lights go out for a moment
            Vec3 look = p.getLookAngle();
            Vec3 at = new Vec3(p.getX() - look.x * 2.6, p.getY(), p.getZ() - look.z * 2.6);
            AABB box = e.getBoundingBox().move(at.x - e.getX(), at.y - e.getY(), at.z - e.getZ());
            if (!e.level().noCollision(e, box) || !e.level().getBlockState(net.minecraft.core.BlockPos.containing(at.x, at.y - 1, at.z)).blocksMotion()) return false;
            e.teleportTo(at.x, at.y, at.z);
            effect(p, MobEffects.DARKNESS, 70, 0);
            sound(e, ModSounds.KNOCK.get(), 1f, 0.5f);
            return true;
        }));
        reg("glass_walker", new Def("shatter", 260, 0, 5, 4, true, (e, p) -> {
            effect(p, MobEffects.WEAKNESS, 120, 0);
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
            p.hurt(e.damageSources().mobAttack(e), 3f);
            sound(e, net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1f, 0.7f);
            burst(e, ParticleTypes.CRIT, 30, 0.8);
            return true;
        }));
        reg("stair_thing", new Def("pull_down", 300, 3, 10, 3, false, (e, p) -> {
            Vec3 d = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
            if (d.lengthSqr() < 1e-4) return false;
            d = d.normalize().scale(0.8);
            p.setDeltaMovement(p.getDeltaMovement().add(d.x, 0.1, d.z));
            p.hurtMarked = true;
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 50, 1);
            sound(e, ModSounds.CREATURE_BREATH.get(), 1f, 0.5f);
            return true;
        }));
        reg("mimic", new Def("wide_bite", 220, 0, 3, 3, true, (e, p) -> {
            p.hurt(e.damageSources().mobAttack(e), 5f);
            effect(p, MobEffects.HUNGER, 100, 1);
            return true;
        }));
        reg("drowned_choir", new Def("choir_wail", 300, 0, 14, 3, false, (e, p) -> {
            effect(p, MobEffects.CONFUSION, 90, 0);
            sound(e, ModSounds.DISTANT_CALL.get(), 1f, 0.5f);
            burst(e, ParticleTypes.BUBBLE_POP, 20, 1.0);
            return true;
        }));
        reg("echo", new Def("echo_cry", 300, 0, 12, 3, false, (e, p) -> {
            effect(p, MobEffects.CONFUSION, 60, 0);
            sound(e, ModSounds.WHISPER.get(), 1f, 0.8f);
            return true;
        }));
        reg("witness", new Def("stare", 300, 0, 18, 4, false, (e, p) -> {                // watchers do not touch you; they only make the world heavier
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            effect(p, MobEffects.DARKNESS, 60, 0);
            return true;
        }));
        reg("black_witness", new Def("black_stare", 300, 0, 22, 3, false, (e, p) -> {
            effect(p, MobEffects.DARKNESS, 100, 0);
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
            sound(e, ModSounds.HEARTBEAT.get(), 1f, 0.7f);
            return true;
        }));
        reg("candle_bearer", new Def("snuff", 260, 0, 8, 3, false, (e, p) -> {
            effect(p, MobEffects.BLINDNESS, 50, 0);
            sound(e, net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH, 1f, 0.8f);
            return true;
        }));
        reg("salt_mother", new Def("salt_wall", 360, 0, 5, 5, true, (e, p) -> {
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 100, 2);
            impulse(e, p, 0.7, 0.2);
            return true;
        }));
        reg("hanging_man", new Def("dread", 400, 0, 16, 3, false, (e, p) -> {
            effect(p, MobEffects.WEAKNESS, 100, 0);
            sound(e, ModSounds.CREATURE_BREATH.get(), 0.8f, 0.4f);
            return true;
        }));
    }

    /** Ticks the cooldown; call once per entity AI step from HorrorEntity.tickCooldown. */
    public static boolean enabled() {
        return LastSignalConfig.COMMON.enableSpecialAttacks.get();
    }

    /** Natural use from the AI: requires the framework to be enabled, cooldown ready, range band, stage, and a 1-in-3 roll. Never throws. */
    public static boolean tryUse(HorrorEntity e, Player target, boolean meleeContext) {
        try {
            Def d = BY_ENTITY.get(e.profileId);
            if (d == null || !enabled() || !(target instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return false;
            if (e.specialCd > 0 || d.melee() != meleeContext) return false;
            double dist = e.distanceTo(sp);
            if (dist < d.minRange() || dist > d.maxRange() || e.stageOf(sp) < d.minStage()) return false;
            if (e.getRandom().nextInt(3) != 0 || !e.hasLineOfSight(sp)) return false;
            return fire(e, sp, d);
        } catch (RuntimeException ex) {
            com.lastsignal.LastSignalMod.LOGGER.error("Special attack error in {}", e.profileId, ex);
            return false;
        }
    }

    /** Runs the move unconditionally (used by the CI test and /lastsignal tools); still sets the cooldown. */
    public static boolean force(HorrorEntity e, ServerPlayer p) {
        Def d = BY_ENTITY.get(e.profileId);
        return d != null && fire(e, p, d);
    }

    private static boolean fire(HorrorEntity e, ServerPlayer p, Def d) {
        boolean ok = d.use().run(e, p);
        if (ok) e.specialCd = d.cooldown();
        return ok;
    }

    private SpecialAttacks() {}
}
