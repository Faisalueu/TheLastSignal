package com.lastsignal.entity;

import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.item.ModItems;
import com.lastsignal.sound.ModSounds;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One server-authoritative state machine shared by every horror creature. What differs per creature is the
 * {@link Arch} (behaviour archetype) and its profile (model/texture). The synced {@link State} drives the
 * client-side procedural animations, so animation and AI cannot disagree.
 */
public class HorrorEntity extends PathfinderMob implements GeoEntity {
    public enum State { IDLE, ROAMING, WATCHING, STALKING, INVESTIGATING, SEARCHING, CHASING, ATTACKING, RETREATING, DISAPPEARING, SPECIAL }

    public enum Arch { WATCHER, HUNTER, LURE, BLOCKER, TRADER, ECHO, BOSS }

    protected static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(HorrorEntity.class, EntityDataSerializers.INT);

    private static final Set<String> KEYS = Set.of("archive_key", "long_hall_key", "red_key", "rusted_keycard");

    public final String profileId;
    public final Arch arch;

    protected int stateTicks, observedTicks, lostSight, attackCd, scared, disappearTicks = -1, lifeTicks;
    protected Vec3 lastSeen, investigatePos;
    protected UUID targetId;
    protected boolean revealed;
    private final ArrayDeque<Vec3> history = new ArrayDeque<>();

    public HorrorEntity(EntityType<? extends HorrorEntity> type, Level level) {
        super(type, level);
        var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        this.profileId = key == null ? "observer" : key.getPath();
        this.arch = archOf(profileId);
        this.xpReward = 0;
    }

    public static Arch archOf(String id) {
        return switch (id) {
            case "observer", "witness", "lost_child", "pale_herd", "facet", "signalman" -> Arch.WATCHER;
            case "hollow", "crawler", "burrower" -> Arch.HUNTER;
            case "mimic", "lantern_widow" -> Arch.LURE;
            case "doorman" -> Arch.BLOCKER;
            case "archivist" -> Arch.TRADER;
            case "echo" -> Arch.ECHO;
            default -> Arch.BOSS;
        };
    }

    public static AttributeSupplier.Builder createAttributes(String id) {
        double hp = switch (id) {
            case "signal_core" -> 600; case "buried_king" -> 400; case "unfinished" -> 300;
            case "burrower" -> 80; case "doorman" -> 60; case "hollow", "crawler" -> 30; default -> 20;
        };
        double speed = switch (id) {
            case "hollow" -> 0.27; case "crawler" -> 0.33; case "observer" -> 0.25; case "burrower" -> 0.22;
            case "lantern_widow" -> 0.3; case "mimic" -> 0.3; default -> 0.23;
        };
        double dmg = switch (id) {
            case "signal_core" -> 10; case "buried_king" -> 9; case "unfinished" -> 7; case "burrower" -> 8;
            case "doorman", "hollow" -> 5; case "crawler", "mimic", "lantern_widow" -> 4; default -> 3;
        };
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, hp)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, dmg)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, hp >= 200 ? 1.0 : 0.3);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, State.IDLE.ordinal());
    }

    public State getHorrorState() {
        int o = this.entityData.get(DATA_STATE);
        State[] v = State.values();
        return v[Math.max(0, Math.min(v.length - 1, o))];
    }

    protected void setState(State s) {
        if (getHorrorState() != s) {
            this.entityData.set(DATA_STATE, s.ordinal());
            stateTicks = 0;
        }
    }

    // -------------------------------------------------------------------------------------------- GeckoLib
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::animate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation." + profileId + "." + name);
    }

    /** Animation is a pure function of the SYNCED AI state (+ movement), so what you see is what the server is doing. */
    private PlayState animate(AnimationState<HorrorEntity> st) {
        if (isDeadOrDying())
            return st.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation." + profileId + ".death"));
        boolean moving = st.isMoving();
        String a;
        switch (getHorrorState()) {
            case DISAPPEARING -> a = "vanish";
            case ATTACKING -> a = "attack";
            case SPECIAL -> a = "special";
            case CHASING -> a = moving ? "run" : "stalk";
            case STALKING -> a = moving ? "stalk" : "watch";
            case WATCHING -> a = "watch";
            case RETREATING -> a = moving ? "retreat" : "idle";
            case INVESTIGATING, SEARCHING -> a = moving ? "walk" : "search";
            case ROAMING -> a = moving ? "walk" : "idle";
            default -> a = moving ? "walk" : "idle";
        }
        return st.setAndContinue(loop(a));
    }

    // -------------------------------------------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        stateTicks++;
        lifeTicks++;
        if (attackCd > 0) attackCd--;
        if (scared > 0) scared--;
        if (disappearTicks >= 0) {
            if (disappearTicks-- == 0) {
                if (level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3, getBbHeight() / 3, 0.3, 0.01);
                discard();
            }
            return;
        }
        if (arch != Arch.BOSS && lifeTicks > 20 * 60 * 6) { vanish(); return; }
        if (tickCount % 5 == 0) {
            try {
                think();
            } catch (RuntimeException ex) {
                // A bug in one creature must never take the server down.
                com.lastsignal.LastSignalMod.LOGGER.error("Horror AI error in {}", profileId, ex);
                setState(State.IDLE);
            }
        }
    }

    protected void think() {
        switch (arch) {
            case WATCHER -> thinkWatcher();
            case HUNTER -> thinkHunter();
            case LURE -> thinkLure();
            case BLOCKER -> thinkBlocker();
            case ECHO -> thinkEcho();
            case TRADER -> thinkTrader();
            default -> { }
        }
    }

    // ------------------------------------------------------------------------------------- targeting
    protected double aggression() {
        return LastSignalConfig.COMMON.entityAggression.get() * LastSignalConfig.COMMON.horrorIntensity.get();
    }

    /** Whether the player may be hunted/attacked (watching is always allowed for survival+creative). */
    public boolean canTargetPlayer(Player p) {
        if (p.isSpectator() || p.isCreative() || !p.isAlive()) return false;
        if (p instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
            PlayerStory st = StoryManager.get(sp);
            if (st.wardUntil > sl.getGameTime()) return false;
            if (nearBeacon(sl, p.blockPosition())) return false;
        }
        return true;
    }

    private static boolean nearBeacon(ServerLevel sl, BlockPos c) {
        var beacon = com.lastsignal.block.ModBlocks.BY_ID.get("checkpoint_beacon").get();
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -4; dz <= 4; dz++)
                    if (sl.getBlockState(c.offset(dx, dy, dz)).getBlock() == beacon) return true;
        return false;
    }

    protected Player nearestPlayer(double range, boolean needSight) {
        Player best = null;
        double bd = range * range;
        UUID keep = targetId;
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d > bd) continue;
            if (needSight && !hasLineOfSight(p)) continue;
            // hysteresis: stick with the current target unless another is clearly closer
            double score = d - (p.getUUID().equals(keep) ? 36 : 0);
            if (best == null || score < bd) { best = p; bd = Math.max(score, 0); }
        }
        return best;
    }

    protected boolean isLookedAt(Player p, double maxDist) {
        if (distanceTo(p) > maxDist) return false;
        Vec3 look = p.getViewVector(1f).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ()).normalize();
        return look.dot(to) > 0.96 && p.hasLineOfSight(this);
    }

    protected int stageOf(Player p) {
        return p instanceof ServerPlayer sp ? Math.min(StoryManager.get(sp).stage(), LastSignalConfig.COMMON.maxThreatStage.get()) : 0;
    }

    // ------------------------------------------------------------------------------------- behaviours
    protected void thinkWatcher() {
        Player p = nearestPlayer(56, false);
        if (p == null) { setState(State.IDLE); return; }
        getLookControl().setLookAt(p, 30f, 30f);
        if (scared > 0) { retreatFrom(p); return; }
        double d = distanceTo(p);
        boolean observed = isLookedAt(p, 24);
        observedTicks = observed ? observedTicks + 5 : Math.max(0, observedTicks - 5);
        int stage = stageOf(p);
        // Observer/Facet/etc. never melee unless the world is at breakdown; The Herd & Witness can.
        boolean mayAttack = stage >= 5 && (profileId.equals("witness") || profileId.equals("pale_herd")) && canTargetPlayer(p);
        if (mayAttack && d < 12) { chase(p, 1.1); return; }
        boolean herd = profileId.equals("pale_herd"); // the herd holds its ground while watched; it only leaves if approached
        if ((observedTicks >= 40 && !herd) || d < (herd ? 3.5 : 5)) { vanish(); return; }
        setState(State.WATCHING);
        getNavigation().stop();
        if (!observed && stateTicks > 100 && stage >= 2 && random.nextInt(8) == 0) creepCloser(p);
    }

    /** Move 3-5 blocks toward the player while not observed (the "changes position when you look away" behaviour). */
    protected void creepCloser(Player p) {
        Vec3 dir = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
        if (dir.length() < 12) return;
        dir = dir.normalize().scale(3 + random.nextInt(3));
        double nx = getX() + dir.x, nz = getZ() + dir.z;
        BlockPos g = groundAt(nx, nz);
        if (g == null) return;
        Vec3 np = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        if (p.distanceToSqr(np) < 100) return;
        if (isLookedAt(p, 80)) return; // never move while observed
        teleportTo(np.x, np.y, np.z);
        stateTicks = 0;
    }

    protected BlockPos groundAt(double x, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, getY() + 3, z);
        for (int i = 0; i < 8; i++) {
            if (!level().getBlockState(m).isAir() && level().getBlockState(m.above()).isAir() && level().getBlockState(m.above(2)).isAir())
                return m.above().immutable();
            m.move(0, -1, 0);
        }
        return null;
    }

    protected void thinkHunter() {
        long now = level().getGameTime();
        Player p = nearestPlayer(profileId.equals("burrower") ? 28 : 32, true);
        if (p != null && canTargetPlayer(p) && scared == 0) {
            targetId = p.getUUID();
            lastSeen = p.position();
            lostSight = 0;
            double d = distanceTo(p);
            if (getHorrorState() == State.CHASING || getHorrorState() == State.ATTACKING || d < 9 || stateTicks > 140 && getHorrorState() == State.STALKING) {
                chase(p, 1.25 + 0.1 * stageOf(p) / 6.0);
            } else {
                setState(State.STALKING);
                getNavigation().moveTo(p, 0.75);
                getLookControl().setLookAt(p, 30f, 30f);
            }
            return;
        }
        if (scared > 0) {
            Player any = nearestPlayer(32, false);
            if (any != null) retreatFrom(any);
            return;
        }
        if (lastSeen != null && lostSight < 120) {
            lostSight += 5;
            if (position().distanceToSqr(lastSeen) < 4) setState(State.SEARCHING);
            else { setState(State.INVESTIGATING); getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, 1.0); }
            return;
        }
        lastSeen = null;
        NoiseBus.Noise n = NoiseBus.nearest(level().dimension().location().toString(), position(), now, 24);
        if (n != null) {
            investigatePos = n.pos();
            setState(State.INVESTIGATING);
            getNavigation().moveTo(n.pos().x, n.pos().y, n.pos().z, 0.9);
            return;
        }
        if (stateTicks > 160 && getHorrorState() != State.ROAMING) setState(State.ROAMING);
        if (getHorrorState() == State.ROAMING && getNavigation().isDone()) {
            Vec3 r = position().add((random.nextDouble() - .5) * 16, 0, (random.nextDouble() - .5) * 16);
            getNavigation().moveTo(r.x, r.y, r.z, 0.6);
        } else if (getHorrorState() != State.ROAMING) setState(State.IDLE);
    }

    protected void thinkLure() {
        Player p = nearestPlayer(32, false);
        if (p == null) { setState(State.IDLE); return; }
        double d = distanceTo(p);
        if (profileId.equals("mimic") && !revealed) {
            setState(State.SPECIAL);          // motionless, pretending
            getNavigation().stop();
            getLookControl().setLookAt(p, 10f, 10f);
            if (d < 4 && canTargetPlayer(p)) {
                revealed = true;
                playSound(ModSounds.MIMIC_CRY.get(), 1.5f, 1f);
            }
            return;
        }
        if (scared > 0) { retreatFrom(p); return; }
        if (!canTargetPlayer(p)) { setState(State.WATCHING); getLookControl().setLookAt(p, 30f, 30f); return; }
        if (profileId.equals("lantern_widow")) {
            // ambush from the dark; after a bite it retreats instead of fighting on
            if (getHorrorState() == State.RETREATING && stateTicks < 100) { retreatFrom(p); return; }
            if (d < 14 && stageOf(p) >= 2) { chase(p, 1.3); return; }
            setState(State.WATCHING);
            getLookControl().setLookAt(p, 30f, 30f);
            return;
        }
        chase(p, 1.2);
    }

    protected void thinkBlocker() {
        Player p = nearestPlayer(10, true);
        if (p == null) { setState(State.IDLE); return; }
        getLookControl().setLookAt(p, 30f, 30f);
        boolean hasKey = false;
        for (ItemStack s : p.getInventory().items) {
            for (String k : KEYS) {
                var ro = ModItems.ITEMS_BY_ID.get(k);
                if (ro != null && s.getItem() == ro.get()) hasKey = true;
            }
        }
        double d = distanceTo(p);
        if (hasKey || !canTargetPlayer(p)) { setState(State.WATCHING); return; }
        if (d < 2.8) { tryMelee(p); setState(State.ATTACKING); }
        else if (d < 6) setState(State.SPECIAL); // warning pose: arms spread, blocking
        else setState(State.WATCHING);
    }

    protected void thinkEcho() {
        Player p = nearestPlayer(40, false);
        if (p == null) { setState(State.IDLE); return; }
        history.addLast(p.position());
        while (history.size() > 40) history.pollFirst();
        if (history.size() < 30) { getLookControl().setLookAt(p, 20f, 20f); return; }
        // follows the player's own route ~6 s behind (limited, safe history of positions)
        Vec3 t = history.peekFirst();
        if (t != null && distanceToSqr(t) > 2.5) {
            setState(State.STALKING);
            getNavigation().moveTo(t.x, t.y, t.z, 1.0);
        } else setState(State.WATCHING);
        if (distanceTo(p) < 3 && canTargetPlayer(p)) { tryMelee(p); }
        if (isLookedAt(p, 30) && random.nextInt(20) == 0) vanish();
    }

    protected void thinkTrader() {
        Player p = nearestPlayer(8, false);
        if (p != null) { getLookControl().setLookAt(p, 20f, 20f); setState(State.WATCHING); }
        else setState(State.IDLE);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (arch == Arch.TRADER && !level().isClientSide && player instanceof ServerPlayer sp) {
            ItemStack held = player.getItemInHand(hand);
            var coin = ModItems.ITEMS_BY_ID.get("ancient_coin").get();
            var stamp = ModItems.ITEMS_BY_ID.get("archivist_stamp").get();
            if (held.getItem() == coin) {
                held.shrink(1);
                // Controlled clue system: trades a coin for a document. Never removes anything else.
                player.addItem(new ItemStack(ModItems.ITEMS_BY_ID.get("sealed_envelope").get()));
                if (random.nextInt(3) == 0) player.addItem(new ItemStack(stamp));
                sp.sendSystemMessage(Component.literal("§7The Archivist: \"One true thing, as agreed.\""));
            } else {
                sp.sendSystemMessage(Component.literal("§7The Archivist: \"Bring me something that was once true.\""));
            }
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------------------------- actions
    protected void chase(Player p, double speed) {
        targetId = p.getUUID();
        getLookControl().setLookAt(p, 40f, 40f);
        double d = distanceTo(p);
        double reach = getBbWidth() * 1.2 + 1.2;
        if (d <= reach) {
            setState(State.ATTACKING);
            tryMelee(p);
        } else {
            setState(State.CHASING);
            getNavigation().moveTo(p, speed);
        }
    }

    protected void tryMelee(Player p) {
        if (attackCd > 0 || !canTargetPlayer(p)) return;
        attackCd = 25;
        swing(InteractionHand.MAIN_HAND);
        float dmg = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * aggression());
        p.hurt(damageSources().mobAttack(this), dmg);
        if (profileId.equals("lantern_widow")) { setState(State.RETREATING); }
    }

    protected void retreatFrom(Player p) {
        setState(State.RETREATING);
        Vec3 away = position().subtract(p.position()).normalize().scale(10);
        getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.3);
    }

    /** Server-side nudge: make a hunter investigate this position as if it heard something there. */
    public void alertTo(Vec3 where) {
        lastSeen = where;
        lostSight = 0;
    }

    /** Flare / light countermeasure. */
    public void scareFromLight(int ticks) {
        if (arch == Arch.BOSS || arch == Arch.BLOCKER || arch == Arch.TRADER) return;
        scared = ticks;
    }

    public void vanish() {
        if (disappearTicks >= 0) return;
        setState(State.DISAPPEARING);
        disappearTicks = 8;
        getNavigation().stop();
    }

    // ------------------------------------------------------------------------------------- misc
    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && arch != Arch.BOSS && arch != Arch.BLOCKER && getHealth() < getMaxHealth() * 0.3f && scared == 0) scared = 120;
        return r;
    }

    @Override
    public boolean removeWhenFarAway(double d) { return arch != Arch.BOSS; }

    @Override
    public boolean isPersistenceRequired() { return arch == Arch.BOSS; }

    @Override
    protected SoundEvent getAmbientSound() {
        return getHorrorState() == State.CHASING ? ModSounds.CREATURE_BREATH.get() : (random.nextInt(4) == 0 ? ModSounds.CREATURE_BREATH.get() : null);
    }

    @Override
    public int getAmbientSoundInterval() { return 160; }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.GLITCH.get(); }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.GLITCH.get(); }

    @Override
    protected float getSoundVolume() { return (float) (0.8 * 1.0); }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState st) {
        if (arch == Arch.WATCHER && random.nextInt(3) != 0) return; // watchers are mostly silent
        playSound(SoundEvents.STONE_STEP, 0.15f, 0.6f);
    }

    @Override
    public boolean isPushable() { return arch != Arch.BLOCKER && arch != Arch.BOSS; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("revealed", revealed);
        t.putInt("life", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        revealed = t.getBoolean("revealed");
        lifeTicks = t.getInt("life");
    }

    public static List<HorrorEntity> near(ServerLevel sl, Vec3 c, double r) {
        return sl.getEntitiesOfClass(HorrorEntity.class, new AABB(c, c).inflate(r));
    }
}
