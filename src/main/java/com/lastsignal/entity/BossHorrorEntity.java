package com.lastsignal.entity;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.item.ModItems;
import com.lastsignal.sound.ModSounds;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Buried King / The Unfinished / Signal Core. Health, phases, attacks and rewards are all server-side.
 * Difficulty comes from telegraphed patterns (slam, hazard columns, summons, pulse) rather than raw HP:
 * every pattern shows particles and plays a warning cue before it deals damage.
 */
public class BossHorrorEntity extends HorrorEntity {
    private final ServerBossEvent bar;
    private final Set<UUID> contributors = new HashSet<>();
    private final List<Vec3> hazardCols = new ArrayList<>();
    private int phase = 1;
    private int shield;               // invulnerability ticks after a phase change
    private int actionTimer = 100;
    private int telegraph;            // >0 while a telegraphed attack is charging
    private String pending = "";
    private double baseMax = -1;
    private int lastPlayers = 1;
    private boolean rewarded;

    public BossHorrorEntity(EntityType<? extends HorrorEntity> type, Level level) {
        super(type, level);
        BossEvent.BossBarColor color = profileId.equals("signal_core") ? BossEvent.BossBarColor.PINK
                : profileId.equals("buried_king") ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE;
        this.bar = new ServerBossEvent(Component.translatable("entity.lastsignal." + profileId), color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setDarkenScreen(false);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    protected void think() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (!LastSignalConfig.COMMON.enableBosses.get()) { discard(); return; }
        scaleForPlayers(sl);
        bar.setProgress(getHealth() / getMaxHealth());

        Player p = nearestPlayer(48, false);
        if (p == null) { setState(State.IDLE); return; }
        getLookControl().setLookAt(p, 30f, 30f);

        int newPhase = getHealth() > getMaxHealth() * 0.66 ? 1 : getHealth() > getMaxHealth() * 0.33 ? 2 : 3;
        if (newPhase != phase) {
            phase = newPhase;
            shield = 60;
            summon(sl, 2);
            broadcast(sl, "§5The " + profileId.replace('_', ' ') + " changes.");
            playSound(ModSounds.BOSS_ROAR.get(), 3f, 0.7f);
        }
        if (shield > 0) { shield -= 5; setState(State.SPECIAL); return; }

        if (telegraph > 0) {
            telegraph -= 5;
            drawTelegraph(sl);
            if (telegraph <= 0) resolveAttack(sl);
            return;
        }
        double d = distanceTo(p);
        actionTimer -= 5;
        if (actionTimer <= 0) {
            startAttack(sl, p);
            actionTimer = Math.max(40, 120 - phase * 20) + random.nextInt(40);
            return;
        }
        if (d <= getBbWidth() * 0.9 + 2.0) { setState(State.ATTACKING); tryMelee(p); }
        else if (canTargetPlayer(p)) { setState(State.CHASING); getNavigation().moveTo(p, 1.0); }
    }

    private void scaleForPlayers(ServerLevel sl) {
        if (baseMax < 0) baseMax = getMaxHealth();
        int n = Math.max(1, sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48), q -> !q.isSpectator()).size());
        if (n != lastPlayers) {
            double frac = getHealth() / getMaxHealth();
            double newMax = baseMax * (1 + LastSignalConfig.COMMON.multiplayerHealthScaling.get() * (n - 1));
            var inst = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (inst != null) inst.setBaseValue(newMax);
            setHealth((float) (newMax * frac));
            lastPlayers = n;
        }
    }

    private void startAttack(ServerLevel sl, Player target) {
        List<String> options = new ArrayList<>(List.of("slam", "hazard"));
        if (phase >= 2) options.add("summon");
        if (phase >= 3 || profileId.equals("signal_core") && phase >= 2) options.add("pulse");
        pending = options.get(random.nextInt(options.size()));
        telegraph = 40;
        setState(State.SPECIAL);
        hazardCols.clear();
        if (pending.equals("hazard")) {
            for (Player q : sl.players()) {
                if (q.distanceToSqr(this) < 48 * 48 && !q.isSpectator())
                    hazardCols.add(q.position().add((random.nextDouble() - .5) * 4, 0, (random.nextDouble() - .5) * 4));
            }
        }
        playSound(ModSounds.RUMBLE.get(), 2f, 1f);
    }

    private void drawTelegraph(ServerLevel sl) {
        switch (pending) {
            case "slam" -> ring(sl, position(), 6.0, ParticleTypes.CRIT);
            case "hazard" -> { for (Vec3 c : hazardCols) ring(sl, c, 2.0, ParticleTypes.FLAME); }
            case "pulse" -> ring(sl, position(), 14.0, ParticleTypes.ENCHANT);
            case "summon" -> sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 6, 1, 1, 1, 0.02);
            default -> { }
        }
    }

    private void ring(ServerLevel sl, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions pt) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            sl.sendParticles(pt, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void resolveAttack(ServerLevel sl) {
        float dmg = (float) (getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * aggression());
        switch (pending) {
            case "slam" -> {
                for (Player q : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6, 2, 6), q -> !q.isSpectator())) {
                    q.hurt(damageSources().mobAttack(this), dmg);
                    Vec3 k = q.position().subtract(position()).normalize().scale(1.2);
                    q.push(k.x, 0.5, k.z);
                    q.hurtMarked = true;
                }
                playSound(ModSounds.KNOCK.get(), 2f, 0.5f);
            }
            case "hazard" -> {
                for (Vec3 c : hazardCols) {
                    for (Player q : sl.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(2, 3, 2), q -> !q.isSpectator()))
                        q.hurt(damageSources().magic(), dmg * 0.8f);
                    sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 2, 0.3, 0.3, 0.3, 0);
                }
            }
            case "summon" -> summon(sl, 1 + phase);
            case "pulse" -> {
                for (Player q : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(14), q -> !q.isSpectator())) {
                    // Counterplay: a Protective Charm ward blocks the pulse.
                    if (q instanceof ServerPlayer sp && StoryManager.get(sp).wardUntil > sl.getGameTime()) continue;
                    q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                    q.hurt(damageSources().magic(), dmg * 0.5f);
                }
                playSound(ModSounds.STATIC_LOOP.get(), 2f, 1f);
            }
            default -> { }
        }
        pending = "";
    }

    private void summon(ServerLevel sl, int count) {
        String minion = profileId.equals("buried_king") ? "hollow" : profileId.equals("signal_core") ? "echo" : "facet";
        int existing = sl.getEntitiesOfClass(HorrorEntity.class, getBoundingBox().inflate(32), h -> h.profileId.equals(minion)).size();
        for (int i = 0; i < count && existing + i < 4; i++) {
            var type = ModEntities.BY_ID.get(minion).get();
            HorrorEntity h = type.create(sl);
            if (h == null) continue;
            h.moveTo(getX() + random.nextInt(9) - 4, getY(), getZ() + random.nextInt(9) - 4, random.nextFloat() * 360f, 0f);
            sl.addFreshEntity(h);
        }
    }

    private void broadcast(ServerLevel sl, String msg) {
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) < 64 * 64) p.sendSystemMessage(Component.literal(msg));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (shield > 0 || !LastSignalConfig.COMMON.enableBosses.get()) return false;
        if (src.getEntity() instanceof Player p) {
            contributors.add(p.getUUID());
            // Discovery-based weakness: the Signal Blade does extra damage to the Signal Core.
            if (profileId.equals("signal_core") && p.getMainHandItem().getItem() == ModItems.ITEMS_BY_ID.get("signal_blade").get()) amount *= 1.5f;
            // Void Lens reveals the Unfinished's illusions: holding it grants +25% damage.
            if (profileId.equals("unfinished") && p.getOffhandItem().getItem() == ModItems.ITEMS_BY_ID.get("void_lens").get()) amount *= 1.25f;
        }
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (level() instanceof ServerLevel sl && !rewarded) {
            rewarded = true;               // one-time, even if die() were somehow re-entered
            bar.removeAllPlayers();
            for (UUID id : contributors) {
                ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(id);
                if (sp == null) continue;
                PlayerStory st = StoryManager.get(sp);
                if (st.bossesDefeated.add(profileId)) {
                    reward(sp);
                    if (profileId.equals("signal_core")) st.evidence.add("core_defeated");
                    sp.sendSystemMessage(Component.literal("§6The " + profileId.replace('_', ' ') + " is gone. Something still listens."));
                    if (profileId.equals("signal_core"))
                        sp.sendSystemMessage(Component.literal("§eThe Signal waits for your answer. Use §f/lastsignal ending <id>§e to decide (see /lastsignal story)."));
                    StoryManager.markDirty(sp);
                }
            }
        }
    }

    private void reward(ServerPlayer sp) {
        String item = profileId.equals("buried_king") ? "buried_crown_shard" : profileId.equals("unfinished") ? "unfinished_effigy" : "signal_blade";
        ItemStack s = new ItemStack(ModItems.ITEMS_BY_ID.get(item).get());
        if (!sp.getInventory().add(s)) sp.drop(s, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("phase", phase);
        t.putBoolean("rewarded", rewarded);
        t.putDouble("baseMax", baseMax);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = Math.max(1, t.getInt("phase"));
        rewarded = t.getBoolean("rewarded");
        baseMax = t.contains("baseMax") ? t.getDouble("baseMax") : -1;
        if (hasCustomName()) bar.setName(getDisplayName());
    }
}
