package com.lastsignal.entity;

import com.lastsignal.sound.EntitySounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * The White Presence: a completely white, strictly peaceful creature of the Golden Cloud Realm. It has NO attack, NO target selection and NO harmful
 * effect. It walks, now and then stands still and looks around, and from time to time takes off (crouch, rise), flies between spots near the ground,
 * hovers, lands and walks on. The server owns a small state machine (WALK, TAKEOFF, FLY, HOVER, LAND) that is synced as one int, so the animation is
 * a pure function of the server's state (see {@link #animate}). Flying uses vanilla FlyingMoveControl / FlyingPathNavigation; walking uses the
 * ordinary ground navigation; the two are swapped on mode change.
 */
public class WhitePresenceEntity extends PathfinderMob implements GeoEntity {
    public enum Mode { WALK, TAKEOFF, FLY, HOVER, LAND }

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(WhitePresenceEntity.class, EntityDataSerializers.INT);
    private static final String A = "animation.white_presence.";
    public static final ResourceLocation CLOUD_DIM = new ResourceLocation("lastsignal", "golden_cloud_realm");

    private final PathNavigation groundNav;
    private final MoveControl groundMove;
    private final FlyingPathNavigation airNav;
    private final FlyingMoveControl airMove;
    private int modeTicks, walkLeft, flyLeft, hoverLeft, repath;
    private BlockPos landSpot;

    public WhitePresenceEntity(EntityType<? extends WhitePresenceEntity> type, Level level) {
        super(type, level);
        this.groundNav = this.navigation;
        this.groundMove = this.moveControl;
        this.airNav = new FlyingPathNavigation(this, level);
        this.airNav.setCanOpenDoors(false);
        this.airNav.setCanFloat(true);
        this.airNav.setCanPassDoors(true);
        this.airMove = new FlyingMoveControl(this, 20, true);
        this.walkLeft = 20 * (10 + random.nextInt(30));
        this.xpReward = 0;
        setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Spawn rule: only in the Golden Cloud Realm, on solid dry ground (never over the void or in a fluid). */
    public static boolean checkSpawn(EntityType<WhitePresenceEntity> t, ServerLevelAccessor l, MobSpawnType r, BlockPos p, RandomSource rnd) {
        if (!l.getLevel().dimension().location().equals(CLOUD_DIM)) return false;
        BlockState below = l.getBlockState(p.below());
        return below.blocksMotion() && l.getFluidState(p.below()).isEmpty() && l.getFluidState(p).isEmpty() && p.getY() > l.getMinBuildHeight() + 8;
    }

    // ------------------------------------------------------------------------------------------------ data / goals
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, Mode.WALK.ordinal());
    }

    public Mode getMode() {
        int o = this.entityData.get(DATA_MODE);
        Mode[] v = Mode.values();
        return v[Math.max(0, Math.min(v.length - 1, o))];
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new WalkAboutGoal());
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // deliberately no target goals and no attack goals: this creature can never be hostile
    }

    /** Occasionally strolls a few blocks; most of the time it simply stands (idle animation). Only while walking. */
    private class WalkAboutGoal extends Goal {
        private Vec3 dest;

        WalkAboutGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (getMode() != Mode.WALK || !onGround() || random.nextInt(90) != 0) return false;
            dest = DefaultRandomPos.getPos(WhitePresenceEntity.this, 9, 4);
            return dest != null;
        }

        @Override
        public boolean canContinueToUse() { return getMode() == Mode.WALK && !getNavigation().isDone(); }

        @Override
        public void start() { getNavigation().moveTo(dest.x, dest.y, dest.z, 0.9); }

        @Override
        public void stop() { getNavigation().stop(); }
    }

    // ------------------------------------------------------------------------------------------------ state machine (server)
    private void setMode(Mode m) {
        if (level().isClientSide) return;
        Mode old = getMode();
        if (old == m) return;
        this.entityData.set(DATA_MODE, m.ordinal());
        modeTicks = 0;
        boolean air = m != Mode.WALK, wasAir = old != Mode.WALK;
        if (air != wasAir) {
            this.navigation.stop();
            this.navigation = air ? airNav : groundNav;
            this.moveControl = air ? airMove : groundMove;
            setNoGravity(air);
        }
        if (m == Mode.TAKEOFF) level().playSound(null, getX(), getY(), getZ(), EntitySounds.WP_TAKEOFF.get(), SoundSource.NEUTRAL, 0.45f, 1.0f);
    }

    private boolean headroom(int blocks) {
        return level().noCollision(this, getBoundingBox().move(0, blocks, 0));
    }

    private int groundY(int x, int z) {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        this.fallDistance = 0;
        Mode m = getMode();
        if (m == Mode.WALK && isNoGravity()) setNoGravity(false);       // e.g. after a reload while it was airborne
        if (getY() < level().getMinBuildHeight() + 6) { rescue(); return; }
        switch (m) {
            case WALK -> {
                if (--walkLeft <= 0) {
                    if (onGround() && headroom(4)) { flyLeft = 20 * (15 + random.nextInt(30)); setMode(Mode.TAKEOFF); }
                    else walkLeft = 100;
                }
            }
            case TAKEOFF -> {
                Vec3 d = getDeltaMovement();
                if (modeTicks > 5) setDeltaMovement(d.x * 0.9, Math.min(0.16, d.y + 0.05), d.z * 0.9);   // wait for the crouch, then rise
                if (modeTicks >= 26) { repath = 0; setMode(Mode.FLY); }
                else if (modeTicks > 8 && !headroom(1)) setMode(Mode.LAND);
            }
            case FLY -> {
                if (--flyLeft <= 0) { beginLanding(); break; }
                if (--repath <= 0 || getNavigation().isDone()) {
                    if (random.nextInt(4) == 0) { hoverLeft = 30 + random.nextInt(70); getNavigation().stop(); setMode(Mode.HOVER); }
                    else { pickFlyTarget(); repath = 60 + random.nextInt(60); }
                }
            }
            case HOVER -> {
                flyLeft--;
                if (--hoverLeft <= 0) setMode(flyLeft <= 0 ? Mode.LAND : Mode.FLY);
            }
            case LAND -> {
                if (landSpot == null) landSpot = findLandingSpot();
                if (onGround() || modeTicks > 240) { landSpot = null; walkLeft = 20 * (15 + random.nextInt(40)); setMode(Mode.WALK); }
                else if (modeTicks % 20 == 1 || getNavigation().isDone()) getNavigation().moveTo(landSpot.getX() + 0.5, landSpot.getY() + 0.05, landSpot.getZ() + 0.5, 0.8);
            }
        }
        if (m != Mode.WALK && tickCount % 18 == 0)
            level().playSound(null, getX(), getY(), getZ(), EntitySounds.WP_WINGS.get(), SoundSource.NEUTRAL, 0.35f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void beginLanding() {
        landSpot = findLandingSpot();
        getNavigation().stop();
        setMode(Mode.LAND);
    }

    /** A nearby standable spot (dry solid ground below, two free cells above); falls back to straight down. */
    private BlockPos findLandingSpot() {
        for (int i = 0; i < 12; i++) {
            int x = Mth.floor(getX()) + random.nextInt(17) - 8, z = Mth.floor(getZ()) + random.nextInt(17) - 8;
            int y = groundY(x, z);
            if (y <= level().getMinBuildHeight() + 2) continue;
            BlockPos p = new BlockPos(x, y, z);
            if (level().getBlockState(p.below()).blocksMotion() && level().getFluidState(p.below()).isEmpty() && level().getFluidState(p).isEmpty()
                    && level().getBlockState(p).getCollisionShape(level(), p).isEmpty() && level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty()) return p;
        }
        int y = Math.max(groundY(Mth.floor(getX()), Mth.floor(getZ())), level().getMinBuildHeight() + 2);
        return new BlockPos(Mth.floor(getX()), y, Mth.floor(getZ()));
    }

    /** A free air cell 3..11 blocks above the ground, 8..18 blocks away; never over a void column. */
    private void pickFlyTarget() {
        for (int i = 0; i < 8; i++) {
            double ang = random.nextDouble() * Math.PI * 2, dist = 8 + random.nextInt(11);
            int x = Mth.floor(getX() + Math.cos(ang) * dist), z = Mth.floor(getZ() + Math.sin(ang) * dist);
            int g = groundY(x, z);
            if (g <= level().getMinBuildHeight() + 2) continue;
            int y = Math.min(g + 3 + random.nextInt(9), level().getMaxBuildHeight() - 6);
            if (!level().noCollision(new AABB(x + 0.1, y, z + 0.1, x + 0.9, y + getBbHeight(), z + 0.9))) continue;
            if (getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0)) return;
        }
    }

    private void rescue() {
        int g = groundY(Mth.floor(getX()), Mth.floor(getZ()));
        if (g > level().getMinBuildHeight() + 2) { teleportTo(getX(), g + 1, getZ()); setDeltaMovement(Vec3.ZERO); setMode(Mode.WALK); }
        else discard();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && isAlive()) {          // it flees upward, it never fights back
            Mode m = getMode();
            if (m == Mode.LAND || (m == Mode.WALK && onGround() && headroom(4))) { flyLeft = 20 * 12; setMode(m == Mode.LAND ? Mode.FLY : Mode.TAKEOFF); }
        }
        return r;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) { }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) { return false; }

    // ------------------------------------------------------------------------------------------------ sounds
    @Override
    protected SoundEvent getAmbientSound() { return EntitySounds.WP_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) { return EntitySounds.WP_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return EntitySounds.WP_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(EntitySounds.WP_STEP.get(), 0.18f, 0.95f + random.nextFloat() * 0.1f); }

    @Override
    public int getAmbientSoundInterval() { return 400; }

    @Override
    protected float getSoundVolume() { return 0.6f; }

    // ------------------------------------------------------------------------------------------------ GeckoLib
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::animate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    private PlayState animate(AnimationState<WhitePresenceEntity> st) {
        if (isDeadOrDying()) return st.setAndContinue(RawAnimation.begin().thenPlayAndHold(A + "death"));
        boolean moving = st.isMoving();
        RawAnimation a;
        switch (getMode()) {
            case TAKEOFF -> a = RawAnimation.begin().thenPlay(A + "takeoff").thenLoop(A + "fly");
            case FLY -> a = RawAnimation.begin().thenLoop(A + (moving ? "fly" : "hover"));
            case HOVER -> a = RawAnimation.begin().thenLoop(A + "hover");
            case LAND -> a = RawAnimation.begin().thenPlay(A + "land").thenLoop(A + "idle");
            default -> {
                boolean turning = !moving && Math.abs(Mth.wrapDegrees(this.yBodyRot - this.yBodyRotO)) > 3.5f;
                a = RawAnimation.begin().thenLoop(A + (moving ? "walk" : turning ? "turn" : "idle"));
            }
        }
        return st.setAndContinue(a);
    }
}
