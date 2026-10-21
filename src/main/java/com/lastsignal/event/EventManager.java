package com.lastsignal.event;

import com.lastsignal.ModParticles;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.item.ModItems;
import com.lastsignal.sound.ModSounds;
import com.lastsignal.story.Notes;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.story.Transmissions;
import com.lastsignal.world.Dimensions;
import com.lastsignal.world.StructureData;
import com.lastsignal.world.Structures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data-driven, server-authoritative horror event system: 60 distinct events.
 * Every event declares: id, category, targeting mode, weight, cooldown, stage window, story requirement,
 * dimension/time/biome-ish restrictions, duration and cancellation (an action returns false = "false alarm",
 * nothing happens and no cooldown is consumed). Shared/area/party/global events use a WORLD-wide cooldown so
 * two nearby players can never trigger the same event twice; per-player events use a per-player cooldown.
 */
public final class EventManager {
    public enum Cat { SIGHTING, SOUND, ENVIRONMENT, PLAYER, PURSUIT, STORY }

    public enum Target { PER_PLAYER, SHARED, AREA, PARTY, GLOBAL }

    public static final class Ctx {
        public final ServerLevel l;
        public final ServerPlayer p;
        public final PlayerStory st;
        public final int stage;
        public final RandomSource r;

        Ctx(ServerPlayer p) {
            this.p = p;
            this.l = p.serverLevel();
            this.st = StoryManager.get(p);
            this.stage = Math.min(st.stage(), LastSignalConfig.COMMON.maxThreatStage.get());
            this.r = p.getRandom();
        }

        public List<ServerPlayer> party(double radius) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer q : l.players()) if (q.distanceToSqr(p) <= radius * radius && !q.isSpectator()) out.add(q);
            return out;
        }
    }

    @FunctionalInterface
    public interface Action { boolean run(Ctx c); }

    public static final class Def {
        public final String id; public final Cat cat; public final Target tgt; public final int weight, cooldownSec, minStage, maxStage;
        public boolean night, day, surface, underground, rain, jumpscare, triggerOnly;
        public String evidence, item;
        public String dimPrefix;            // null = overworld-like anywhere; "mod" = only mod dimensions; "vanilla" = not mod dims
        public int durationTicks = 200;
        public final Action action;

        Def(String id, Cat cat, Target tgt, int weight, int cd, int min, int max, Action a) {
            this.id = id; this.cat = cat; this.tgt = tgt; this.weight = weight; this.cooldownSec = cd;
            this.minStage = min; this.maxStage = max; this.action = a;
        }
        Def night() { night = true; return this; }
        Def day() { day = true; return this; }
        Def surface() { surface = true; return this; }
        Def underground() { underground = true; return this; }
        Def rain() { rain = true; return this; }
        Def jump() { jumpscare = true; return this; }
        Def trig() { triggerOnly = true; return this; }
        Def ev(String e) { evidence = e; return this; }
        Def item(String i) { item = i; return this; }
        Def dim(String d) { dimPrefix = d; return this; }
        Def dur(int t) { durationTicks = t; return this; }
    }

    public static final Map<String, Def> EVENTS = new LinkedHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();   // key: uuid:id or world:id
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Map<Long, Long>> VISITED = new HashMap<>();
    private static final List<BlockPos[]> TEMP_LIGHTS = new ArrayList<>();
    /** /lastsignal debug: tells players which events fire. */
    public static boolean debug = false;

    private static Def reg(String id, Cat cat, Target tgt, int w, int cd, int min, int max, Action a) {
        Def d = new Def(id, cat, tgt, w, cd, min, max, a);
        EVENTS.put(id, d);
        return d;
    }

    // ------------------------------------------------------------------------------------ scheduling
    public static void tickPlayer(ServerPlayer p) {
        if (!LastSignalConfig.COMMON.enableEvents.get() || p.isSpectator() || !p.isAlive()) return;
        long now = p.serverLevel().getGameTime();
        UUID id = p.getUUID();
        if (now % 200 == 0) noteVisit(p, now);
        if (now % 6000 == 0 && COOLDOWNS.size() > 200) COOLDOWNS.values().removeIf(t -> t < now);   // expired cooldowns never accumulate (LOCAL_GROUP keys are per region)
        Long next = NEXT.get(id);
        if (next == null) { NEXT.put(id, now + 20L * 60); return; }  // first minute after joining is always quiet
        if (now < next) return;
        Ctx c = new Ctx(p);
        double gap = LastSignalConfig.COMMON.minSecondsBetweenEvents.get() * 20.0 * (1 + c.r.nextDouble() * 1.5)
                / Math.max(0.1, LastSignalConfig.COMMON.eventFrequency.get() * Math.max(0.1, LastSignalConfig.COMMON.horrorIntensity.get()));
        double[] stageFactor = {6, 3, 2, 1.4, 1, 0.8, 0.7};
        gap *= stageFactor[c.stage];
        NEXT.put(id, now + (long) gap);
        if (LastSignalConfig.COMMON.horrorIntensity.get() <= 0) return;
        Def pick = choose(c, now);
        if (pick == null) return;
        if (!run(pick, c, now)) NEXT.put(id, now + 20L * (20 + c.r.nextInt(30)));   // false alarm: try again soon
    }

    private static Def choose(Ctx c, long now) {
        List<Def> ok = new ArrayList<>();
        int total = 0;
        for (Def d : EVENTS.values()) {
            if (!eligible(d, c, now)) continue;
            ok.add(d);
            total += d.weight;
        }
        if (ok.isEmpty() || total <= 0) return null;
        int roll = c.r.nextInt(total);
        for (Def d : ok) { roll -= d.weight; if (roll < 0) return d; }
        return ok.get(0);
    }

    public static boolean eligible(Def d, Ctx c, long now) {
        if (d.triggerOnly || d.weight <= 0) return false;
        if (com.lastsignal.world.gen.Themes.isPhase1(c.l.dimension().location().getPath()) && Dimensions.isModDimension(c.l)) return false;  // Phase 1: no events/monsters
        if (c.stage < d.minStage || c.stage > d.maxStage) return false;
        if (d.evidence != null && !c.st.evidence.contains(d.evidence)) return false;
        if (d.item != null && !hasItem(c.p, d.item)) return false;
        boolean mod = Dimensions.isModDimension(c.l);
        if ("mod".equals(d.dimPrefix) != mod && d.dimPrefix != null) return false;
        if (d.dimPrefix == null && mod) return false;     // default events belong to the "real" world
        if (d.night && !c.l.isNight()) return false;
        if (d.day && c.l.isNight()) return false;
        boolean sky = c.l.canSeeSky(c.p.blockPosition());
        if (d.surface && !sky) return false;
        if (d.underground && sky) return false;
        if (d.rain && !c.l.isRaining()) return false;
        if (d.jumpscare && (!LastSignalConfig.COMMON.allowJumpscares.get())) return false;
        if (!Director.phaseAllows(d, c.stage)) return false;
        if (d.cat == Cat.PURSUIT && HorrorSpawner.countNear(c.l, c.p) > 0) return false;  // never overlap major encounters
        Long cd = COOLDOWNS.get(cdKey(d, c.p));
        return cd == null || now >= cd;
    }

    private static String cdKey(Def d, ServerPlayer p) {
        return Director.cooldownKey(d, p);
    }

    /** Quiet period after a death / respawn / dimension change: no new event for this player until the given game time. */
    public static void restUntil(UUID id, long gameTime) {
        NEXT.merge(id, gameTime, Math::max);
    }

    private static boolean run(Def d, Ctx c, long now) {
        boolean ok;
        try {
            ok = d.action.run(c);
        } catch (RuntimeException ex) {
            com.lastsignal.LastSignalMod.LOGGER.error("Event {} failed", d.id, ex);
            ok = false;
        }
        if (debug) c.p.sendSystemMessage(Component.literal("§7[LS debug] " + d.id + " -> " + (ok ? "fired" : "false alarm")));
        if (ok) {
            COOLDOWNS.put(cdKey(d, c.p), now + d.cooldownSec * 20L);
            if (d.cat == Cat.PURSUIT) c.st.encounters++;
            if (d.cat == Cat.SIGHTING && SpawnAnchor.of(c.l, c.p) != null) c.st.sightings = Math.min(c.st.sightings + 1, 8);
            StoryManager.markDirty(c.p);
        }
        return ok;
    }

    /** CI audit only: runs the action directly and lets exceptions escape (the normal path logs and swallows them). */
    public static boolean auditRun(ServerPlayer p, Def d) { return d.action.run(new Ctx(p)); }

    /** Admin / hook trigger. force = ignore eligibility but still respect the action's own preconditions. */
    public static boolean trigger(ServerPlayer p, String id, boolean force) {
        Def d = EVENTS.get(id);
        if (d == null) return false;
        Ctx c = new Ctx(p);
        long now = c.l.getGameTime();
        if (!force && !eligible(d, c, now)) return false;
        return run(d, c, now);
    }

    public static void forget(UUID id) {
        NEXT.remove(id);
        VISITED.remove(id);
    }

    public static void shutdown() {
        SpawnArea.restoreAll(); COOLDOWNS.clear(); NEXT.clear(); VISITED.clear(); TEMP_LIGHTS.clear();
        Tasks.clear();
    }

    private static void noteVisit(ServerPlayer p, long now) {
        if (p.serverLevel().canSeeSky(p.blockPosition())) return;
        VISITED.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).putIfAbsent(new net.minecraft.world.level.ChunkPos(p.blockPosition()).toLong(), now);
        Map<Long, Long> m = VISITED.get(p.getUUID());
        if (m.size() > 600) m.clear();
    }

    // ------------------------------------------------------------------------------------ helpers
    static boolean hasItem(ServerPlayer p, String id) {
        var ro = ModItems.ITEMS_BY_ID.get(id);
        if (ro == null) return false;
        for (ItemStack s : p.getInventory().items) if (s.getItem() == ro.get()) return true;
        return p.getOffhandItem().getItem() == ro.get();
    }

    /** Players a world-wide event reaches: same dimension, alive, not spectating. */
    static List<ServerPlayer> everyone(Ctx c) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer q : c.l.players()) if (q.isAlive() && !q.isSpectator()) out.add(q);
        return out;
    }

    private static Vec3 behind(ServerPlayer p, double dist) {
        Vec3 look = p.getLookAngle();
        Vec3 h = new Vec3(look.x, 0, look.z);
        if (h.lengthSqr() < 1e-4) h = new Vec3(0, 0, 1);
        return p.position().subtract(h.normalize().scale(dist));
    }

    private static void step(ServerPlayer p, Vec3 at) {
        Fx.privateSound(p, ModSounds.FOOTSTEP_FAR.get(), at, 0.9f, 0.85f + p.getRandom().nextFloat() * 0.3f);
    }

    private static boolean watch(Ctx c, String ent, Placement pl, double min, double max) {
        return HorrorSpawner.spawnNear(c.l, c.p, ent, pl, min, max) != null;
    }

    private static HorrorEntity vanishLater(Ctx c, HorrorEntity e, int ticks) {
        if (e != null) Tasks.later(ticks, () -> { if (e.isAlive()) e.vanish(); });
        return e;
    }

    private static StructureData.Entry nearestStructure(Ctx c, double max, String... types) {
        StructureData sd = Structures.data(c.l);
        StructureData.Entry best = null;
        double bd = max * max;
        for (StructureData.Entry e : sd.entries) {
            boolean match = types.length == 0;
            for (String t : types) if (e.type().equals(t)) match = true;
            if (!match) continue;
            double d = e.blockPos().distSqr(c.p.blockPosition());
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static String corrupt(String s, RandomSource r, double ratio) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch != ' ' && r.nextDouble() < ratio ? '▒' : ch);
        return b.toString();
    }

    private static final String[] WHISPERS = {
            "§8§oyou are not the first to listen", "§8§oyour name is easy to say", "§8§oit knows the path you walk",
            "§8§odo not turn around yet", "§8§oanswer me", "§8§owe kept your place", "§8§oyou left the door open",
            "§8§othe fourth step is always louder"};

    private static final String[] FAKE_WARNINGS = {
            "§c[Warning] §7Chunk data mismatch at your position. Stay where you are until it resolves.",
            "§e[Server] §7Autosave delayed: the world is currently being read by something else.",
            "§c[Error] §7Entity count (visible) 0 does not match entity count (actual) 1.",
            "§e[Notice] §7A player has joined the game. (No such player is online.)",
            "§c[Warning] §7Signal strength exceeded safe limit. Disconnecting is not recommended."};

    // ------------------------------------------------------------------------------------ the 60 events
    static {
        // ---- Distant sightings (10)
        reg("sight_forest_silhouette", Cat.SIGHTING, Target.SHARED, 10, 240, 1, 6, c -> watch(c, "observer", Placement.FOREST_EDGE, 30, 50)).surface();
        reg("sight_hill_watcher", Cat.SIGHTING, Target.SHARED, 8, 300, 1, 6, c -> watch(c, "observer", Placement.HIGH_GROUND, 30, 60)).surface();
        reg("sight_cave_eyes", Cat.SIGHTING, Target.PER_PLAYER, 8, 240, 1, 6, c -> vanishLater(c, HorrorSpawner.spawnNear(c.l, c.p, "witness", Placement.DARK_CAVE, 10, 20), 80) != null).underground();
        reg("sight_window_figure", Cat.SIGHTING, Target.SHARED, 7, 360, 2, 6, c -> watch(c, "witness", Placement.OUTSIDE_WINDOW, 4, 14)).night();
        reg("sight_mountain", Cat.SIGHTING, Target.SHARED, 6, 400, 2, 6, c -> watch(c, "observer", Placement.MOUNTAIN, 50, 80)).surface();
        reg("sight_corridor_end", Cat.SIGHTING, Target.PER_PLAYER, 6, 300, 2, 6, c -> watch(c, "observer", Placement.CORRIDOR_END, 14, 40)).underground();
        reg("sight_behind_waterfall", Cat.SIGHTING, Target.SHARED, 4, 500, 2, 6, c -> watch(c, "lost_child", Placement.BEHIND_WATER, 10, 28)).surface();
        reg("sight_field_group", Cat.SIGHTING, Target.SHARED, 6, 420, 2, 6, c -> {
            BlockPos center = Placement.OPEN_FIELD.find(c.l, c.p, 30, 50);
            if (center == null) return false;
            int n = 0;
            for (int i = 0; i < 5; i++) {
                BlockPos q = center.offset(c.r.nextInt(9) - 4, 0, c.r.nextInt(9) - 4);
                BlockPos g = Placement.surface(c.l, q);
                if (g != null && Placement.standable(c.l, g) && HorrorSpawner.spawnAt(c.l, "pale_herd", g, c.p) != null) n++;
            }
            return n >= 3;
        }).surface().day();
        reg("sight_rooftop", Cat.SIGHTING, Target.SHARED, 5, 400, 2, 6, c -> watch(c, "witness", Placement.ROOFTOP, 12, 40)).surface();
        reg("sight_vanish_behind_obstacle", Cat.SIGHTING, Target.PER_PLAYER, 7, 300, 1, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "observer", Placement.ANY_GROUND, 20, 36);
            if (e == null) return false;
            Tasks.every(20, 10, 40, i -> {
                if (!e.isAlive() || c.p.isRemoved()) return false;
                if (!c.p.hasLineOfSight(e)) { e.vanish(); return false; }
                return true;
            });
            return true;
        }).surface();

        // ---- Sound events (10)
        reg("snd_footsteps_behind", Cat.SOUND, Target.PER_PLAYER, 10, 180, 1, 6, c -> {
            Tasks.every(0, 10, 6, i -> { step(c.p, behind(c.p, 7 - i * 1.1)); return !c.p.isRemoved(); });
            return true;
        });
        reg("snd_footsteps_circle_shelter", Cat.SOUND, Target.PER_PLAYER, 8, 300, 2, 6, c -> {
            Tasks.every(0, 6, 16, i -> {
                double a = i * Math.PI / 8;
                step(c.p, c.p.position().add(Math.cos(a) * 7, 0, Math.sin(a) * 7));
                return !c.p.isRemoved();
            });
            return true;
        }).underground().night();
        reg("snd_knock_wall", Cat.SOUND, Target.PER_PLAYER, 8, 300, 1, 6, c -> {
            BlockPos wall = null;
            outer:
            for (int d = 1; d <= 5; d++)
                for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos b = c.p.blockPosition().relative(dir, d);
                    if (c.l.getBlockState(b).isSolid()) { wall = b; break outer; }
                }
            if (wall == null) return false;
            final BlockPos w = wall;
            Tasks.every(0, 14, 3, i -> { Fx.privateSound(c.p, ModSounds.KNOCK.get(), Vec3.atCenterOf(w), 1.2f, 1f); return true; });
            return true;
        }).underground();
        reg("snd_whisper_underground", Cat.SOUND, Target.PER_PLAYER, 8, 240, 1, 6, c -> {
            Fx.privateSound(c.p, ModSounds.WHISPER.get(), c.p.position().add(0, -6, 0), 0.8f, 1f);
            return true;
        }).underground();
        reg("snd_distant_call", Cat.SOUND, Target.PER_PLAYER, 8, 240, 1, 6, c -> {
            double a = c.r.nextDouble() * Math.PI * 2;
            Fx.privateSound(c.p, ModSounds.DISTANT_CALL.get(), c.p.position().add(Math.cos(a) * 40, 0, Math.sin(a) * 40), 1.4f, 1f);
            return true;
        }).surface();
        reg("snd_wrong_direction", Cat.SOUND, Target.PER_PLAYER, 7, 300, 2, 6, c -> {
            boolean sky = c.l.canSeeSky(c.p.blockPosition());
            Fx.privateSound(c.p, sky ? SoundEvents.COW_AMBIENT : SoundEvents.ZOMBIE_AMBIENT, behind(c.p, 9), 1f, 0.8f);
            return true;
        });
        reg("snd_radio_static_incomplete", Cat.SOUND, Target.PER_PLAYER, 9, 300, 1, 6, c -> {
            Fx.privateSound(c.p, ModSounds.STATIC_LOOP.get(), c.p.position(), 0.6f, 1f);
            Transmissions.Entry e = Transmissions.ALL.get(c.r.nextInt(Transmissions.ALL.size()));
            String t = e.text();
            Tasks.later(40, () -> Fx.message(c.p, "§8[radio] §7" + t.substring(0, Math.max(8, t.length() * 2 / 5)) + "… §8—krrsh—"));
            return true;
        }).item("broken_radio");
        reg("snd_rumble_below", Cat.SOUND, Target.PER_PLAYER, 6, 360, 2, 6, c -> {
            Fx.privateSound(c.p, ModSounds.RUMBLE.get(), c.p.position().add(0, -10, 0), 1.2f, 0.8f);
            Fx.effect(c.p, "shake", 40, 0.4f);
            return true;
        }).underground();
        reg("snd_footsteps_stop_with_player", Cat.SOUND, Target.PER_PLAYER, 8, 300, 2, 6, c -> {
            final double[] last = {c.p.getX(), c.p.getZ()};
            Tasks.every(0, 8, 14, i -> {
                if (c.p.isRemoved()) return false;
                double moved = Math.hypot(c.p.getX() - last[0], c.p.getZ() - last[1]);
                last[0] = c.p.getX(); last[1] = c.p.getZ();
                if (i > 1 && moved < 0.3) return false;      // you stop, it stops
                step(c.p, behind(c.p, 5));
                return true;
            });
            return true;
        }).surface();
        reg("snd_unnatural_silence", Cat.SOUND, Target.PER_PLAYER, 6, 420, 1, 6, c -> {
            Tasks.every(0, 10, 20, i -> {
                if (c.p.isRemoved()) return false;
                Fx.stopSounds(c.p, SoundSource.NEUTRAL); Fx.stopSounds(c.p, SoundSource.HOSTILE); Fx.stopSounds(c.p, SoundSource.WEATHER);
                return true;
            });
            return true;
        }).surface();

        // ---- Environmental anomalies (10)
        reg("env_torches_flicker", Cat.ENVIRONMENT, Target.AREA, 8, 300, 2, 6, c -> {
            List<BlockPos> found = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            for (BlockPos b : BlockPos.betweenClosed(c.p.blockPosition().offset(-8, -3, -8), c.p.blockPosition().offset(8, 4, 8))) {
                BlockState s = c.l.getBlockState(b);
                if ((s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) && found.size() < 3) { found.add(b.immutable()); states.add(s); }
            }
            if (found.isEmpty()) return false;
            for (BlockPos b : found) c.l.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            Tasks.later(100 + c.r.nextInt(100), () -> {
                for (int i = 0; i < found.size(); i++)
                    if (c.l.getBlockState(found.get(i)).isAir()) c.l.setBlock(found.get(i), states.get(i), 3);
            });
            return true;
        });
        reg("env_static_particles", Cat.ENVIRONMENT, Target.PER_PLAYER, 8, 200, 1, 6, c -> {
            Fx.particles(c.l, ModParticles.STATIC.get(), c.p.position().add(0, 1, 0), 30, 2.5);
            return true;
        });
        reg("env_abandoned_structure", Cat.ENVIRONMENT, Target.SHARED, 5, 900, 2, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            String[] pool = {"cabin", "camp", "cemetery", "watchtower", "strange_well"};
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 45, 80);
            return at != null && Structures.place(c.l, pool[c.r.nextInt(pool.length)], at);
        }).surface();
        reg("env_haunted_house", Cat.ENVIRONMENT, Target.SHARED, 2, 2400, 1, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            if (c.r.nextDouble() >= LastSignalConfig.COMMON.hauntedHouseFrequency.get() / 4.0) return false;
            var recorded = Structures.data(c.l).entries;
            var free = new java.util.ArrayList<String>();
            for (String h : com.lastsignal.world.Houses.BUILT) {
                int no = Integer.parseInt(h.substring(6));
                if (no > 6 * c.stage) continue;                                                       // houses unlock with the story stage (6 per stage)
                if (h.equals("HOUSE_30") && recorded.stream().noneMatch(e -> e.type().equals("HOUSE_29"))) continue;   // its key lies in HOUSE_29
                if (recorded.stream().noneMatch(e -> e.type().equals(h))) free.add(h);
            }
            if (free.isEmpty()) return false;
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 70, 130);
            return at != null && com.lastsignal.world.Houses.place(c.l, free.get(c.r.nextInt(free.size())), at);
        }).surface();
        // ---- Stage-gated roster events (the entities added in the third roster pass)
        reg("sight_black_witness", Cat.SIGHTING, Target.SHARED, 4, 900, 3, 6, c -> watch(c, "black_witness", Placement.FOREST_EDGE, 40, 70)).surface().night();
        reg("sight_borrowed_skin", Cat.SIGHTING, Target.SHARED, 5, 900, 2, 4, c -> vanishLater(c, HorrorSpawner.spawnNear(c.l, c.p, "borrowed_skin", Placement.FOREST_EDGE, 24, 40), 160) != null).surface();
        reg("sight_moon_watcher", Cat.SIGHTING, Target.SHARED, 4, 1200, 2, 6, c -> watch(c, "moon_watcher", Placement.HIGH_GROUND, 60, 90)).surface().night();
        reg("sight_hanging_man", Cat.SIGHTING, Target.SHARED, 4, 1200, 3, 6, c -> watch(c, "hanging_man", Placement.ANY_GROUND, 28, 48)).surface().night();
        reg("sight_paper_man", Cat.SIGHTING, Target.SHARED, 4, 1000, 2, 6, c -> watch(c, "paper_man", Placement.OUTSIDE_WINDOW, 4, 12)).night();
        reg("hunt_tall_black", Cat.PURSUIT, Target.PER_PLAYER, 5, 1500, 4, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "tall_black", Placement.ANY_GROUND, 30, 42);
            if (e == null) return false;
            e.alertTo(c.p.position());
            Fx.effect(c.p, "tension", 400, 0.7f);
            return true;
        }).surface().night();
        reg("hunt_glass_walker", Cat.PURSUIT, Target.PER_PLAYER, 5, 900, 4, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "glass_walker", Placement.DARK_CAVE, 18, 30);
            if (e == null) return false;
            e.alertTo(c.p.position());
            return true;
        }).underground();
        reg("hunt_stair_thing", Cat.PURSUIT, Target.PER_PLAYER, 5, 900, 3, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "stair_thing", Placement.DEEP_UNDERGROUND, 14, 26);
            if (e == null) return false;
            e.alertTo(c.p.position());
            return true;
        }).underground();
        reg("lure_candle_bearer", Cat.PURSUIT, Target.PER_PLAYER, 5, 1000, 3, 6, c -> watch(c, "candle_bearer", Placement.ANY_GROUND, 20, 32)).surface().night();
        reg("lure_radio_shepherd", Cat.PURSUIT, Target.SHARED, 4, 1200, 3, 6, c -> {
            if (nearestStructure(c, 90, "radio_tower", "relay_station", "research_station", "HOUSE_10", "HOUSE_28") == null) return false;
            return watch(c, "radio_shepherd", Placement.ANY_GROUND, 22, 40);
        }).surface();
        reg("echo_drowned_choir", Cat.PURSUIT, Target.SHARED, 4, 1200, 3, 6, c -> watch(c, "drowned_choir", Placement.BEHIND_WATER, 10, 26)).surface();
        reg("block_salt_mother", Cat.PURSUIT, Target.SHARED, 3, 1800, 5, 6, c -> {
            if (nearestStructure(c, 90, "sealed_entrance", "shrine", "ancient_door", "HOUSE_30") == null) return false;
            return watch(c, "salt_mother", Placement.ANY_GROUND, 16, 28);
        }).surface();
        reg("env_impossible_door", Cat.ENVIRONMENT, Target.SHARED, 4, 900, 2, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            BlockPos at = Placement.OPEN_FIELD.find(c.l, c.p, 25, 50);
            if (at == null) return false;
            BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState();
            c.l.setBlock(at, lower, 3);
            c.l.setBlock(at.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            Structures.data(c.l).add("lone_door", at);
            return true;
        }).surface();
        reg("env_strange_footprints", Cat.ENVIRONMENT, Target.PER_PLAYER, 7, 300, 1, 6, c -> {
            Vec3 start = behind(c.p, 22);
            Vec3 dir = c.p.position().subtract(start).multiply(1, 0, 1).normalize();
            Tasks.every(0, 10, 20, i -> {
                if (c.p.isRemoved()) return false;
                Vec3 at = start.add(dir.scale(i * 1.1)).add(-dir.z * (i % 2 == 0 ? 0.25 : -0.25), 0, dir.x * (i % 2 == 0 ? 0.25 : -0.25));
                BlockPos g = Placement.surface(c.l, BlockPos.containing(at));
                if (g != null) c.l.sendParticles(ParticleTypes.ASH, at.x, g.getY() + 0.1, at.z, 6, 0.1, 0.0, 0.1, 0.0);
                return true;
            });
            return true;
        }).surface();
        reg("env_visual_distortion", Cat.ENVIRONMENT, Target.PER_PLAYER, 6, 300, 2, 6, c -> { Fx.effect(c.p, "distort", 80, 0.5f); return true; });
        reg("env_fog_thickens", Cat.ENVIRONMENT, Target.AREA, 7, 400, 1, 6, c -> {
            for (ServerPlayer q : c.party(48)) Fx.effect(q, "fog", 400, 0.7f);
            return true;
        }).surface();
        reg("env_bell_rings", Cat.ENVIRONMENT, Target.AREA, 5, 500, 1, 6, c -> {
            for (BlockPos b : BlockPos.betweenClosed(c.p.blockPosition().offset(-32, -8, -32), c.p.blockPosition().offset(32, 8, 32))) {
                if (c.l.getBlockState(b).is(Blocks.BELL)) { Fx.sharedSound(c.l, Vec3.atCenterOf(b), SoundEvents.BELL_BLOCK, 2f, 0.7f); return true; }
            }
            Fx.privateSound(c.p, ModSounds.BELL_DISTANT.get(), c.p.position().add(c.r.nextInt(40) - 20, 0, c.r.nextInt(40) - 20), 1.2f, 1f);
            return true;
        });
        reg("env_structure_gains_clue", Cat.ENVIRONMENT, Target.SHARED, 5, 600, 3, 6, c -> {
            StructureData.Entry e = nearestStructure(c, 300);
            if (e == null) return false;
            BlockPos o = e.blockPos();
            c.l.getChunk(o);
            Block log = com.lastsignal.block.ModBlocks.BY_ID.get("memory_log").get();
            for (int dx = 0; dx <= 6; dx++) for (int dz = 0; dz <= 6; dz++) {
                BlockPos q = o.offset(dx, 1, dz);
                if (c.l.getBlockState(q).isAir() && c.l.getBlockState(q.below()).isSolid()) { c.l.setBlock(q, log.defaultBlockState(), 3); return true; }
            }
            return false;
        });
        reg("env_rare_structure_biome", Cat.ENVIRONMENT, Target.SHARED, 3, 1200, 3, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 60, 100);
            if (at == null) return false;
            var biome = c.l.getBiome(at);
            String type = biome.is(BiomeTags.IS_FOREST) ? "shrine" : biome.is(BiomeTags.IS_MOUNTAIN) ? "radio_tower"
                    : biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) ? "broken_bridge" : "sealed_entrance";
            return Structures.place(c.l, type, at);
        }).surface();

        // ---- Player-focused (10)
        reg("plr_cryptic_chat", Cat.PLAYER, Target.PER_PLAYER, 10, 180, 1, 6, c -> { Fx.message(c.p, WHISPERS[c.r.nextInt(WHISPERS.length)]); return true; });
        reg("plr_note_new_line", Cat.PLAYER, Target.PER_PLAYER, 6, 600, 2, 6, c -> {
            Notes.Note n = Notes.nextUnread(c.st, c.r.nextInt());
            if (n == null) return false;
            c.st.notes.add(n.id());
            StoryManager.onNoteFound(c.p, c.st, n);
            Fx.message(c.p, "§7A new line has appeared in your notebook: §e" + n.title());
            return true;
        }).item("research_notebook");
        reg("plr_radio_rebroadcast", Cat.PLAYER, Target.PER_PLAYER, 7, 300, 1, 6, c -> {
            if (c.st.recordings.isEmpty()) return false;
            List<String> ids = new ArrayList<>(c.st.recordings);
            Transmissions.Entry e = Transmissions.byId(ids.get(c.r.nextInt(ids.size())));
            if (e == null) return false;
            Fx.message(c.p, "§8[radio] §7" + corrupt(e.text(), c.r, 0.25));
            return true;
        }).item("broken_radio");
        reg("plr_fake_warning", Cat.PLAYER, Target.PER_PLAYER, 6, 600, 2, 6, c -> { Fx.message(c.p, FAKE_WARNINGS[c.r.nextInt(FAKE_WARNINGS.length)]); return true; });
        reg("plr_shadow_overlay", Cat.PLAYER, Target.PER_PLAYER, 7, 240, 1, 6, c -> { Fx.effect(c.p, "shadow", 40, 0.6f); return true; });
        reg("plr_footsteps_answer", Cat.PLAYER, Target.PER_PLAYER, 7, 300, 2, 6, c -> {
            final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
            Tasks.every(0, 5, 30, i -> {
                if (c.p.isRemoved()) return false;
                trail.addLast(c.p.position());
                if (trail.size() > 4) step(c.p, trail.pollFirst());
                return true;
            });
            return true;
        }).surface();
        reg("plr_nearby_mob_freezes", Cat.PLAYER, Target.PER_PLAYER, 5, 420, 2, 6, c -> {
            List<Mob> mobs = c.l.getEntitiesOfClass(Mob.class, c.p.getBoundingBox().inflate(16), m -> !(m instanceof HorrorEntity) && !m.isNoAi());
            for (Mob m : mobs) {
                Vec3 look = c.p.getViewVector(1f).normalize();
                Vec3 to = m.position().subtract(c.p.position()).normalize();
                if (look.dot(to) > 0.9 && c.p.hasLineOfSight(m)) {
                    m.setNoAi(true);
                    Tasks.later(100, () -> { if (m.isAlive()) m.setNoAi(false); });
                    return true;
                }
            }
            return false;
        });
        reg("plr_sleep_clue", Cat.PLAYER, Target.PER_PLAYER, 0, 600, 1, 6, c -> {
            Notes.Note n = Notes.nextUnread(c.st, c.r.nextInt());
            if (n == null) return false;
            c.st.notes.add(n.id());
            StoryManager.onNoteFound(c.p, c.st, n);
            Fx.message(c.p, "§7You wake with words in your mind: §e" + n.title() + "§7 — " + n.text());
            return true;
        }).trig();
        reg("plr_cave_remembers", Cat.PLAYER, Target.PER_PLAYER, 6, 420, 2, 6, c -> {
            Map<Long, Long> m = VISITED.get(c.p.getUUID());
            if (m == null) return false;
            Long t = m.get(new net.minecraft.world.level.ChunkPos(c.p.blockPosition()).toLong());
            if (t == null || c.l.getGameTime() - t < 6000) return false;
            Fx.privateSound(c.p, ModSounds.WHISPER.get(), c.p.position(), 0.7f, 0.8f);
            Fx.message(c.p, "§8§oyou have been here before. the cave remembers.");
            return true;
        }).underground();
        reg("plr_strange_symbol", Cat.PLAYER, Target.PER_PLAYER, 5, 900, 2, 6, c -> {
            for (ItemStack s : c.p.getInventory().items) {
                if (s.getItem() == ModItems.ITEMS_BY_ID.get("research_notebook").get() || s.getItem() == ModItems.ITEMS_BY_ID.get("broken_radio").get()) {
                    CompoundTag display = s.getOrCreateTagElement("display");
                    ListTag lore = new ListTag();
                    lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("◯ ◬ ◬ ◯").withStyle(ChatFormatting.DARK_PURPLE))));
                    display.put("Lore", lore);
                    Fx.message(c.p, "§7A symbol has appeared on your " + s.getHoverName().getString() + ".");
                    return true;
                }
            }
            return false;
        });

        // ---- Pursuit and danger (10)
        reg("hunt_hollow_stalks", Cat.PURSUIT, Target.PER_PLAYER, 8, 420, 3, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "hollow", Placement.DARK_CAVE, 20, 32);
            if (e == null) return false;
            e.alertTo(c.p.position());
            return true;
        }).underground();
        reg("hunt_crawler_follows", Cat.PURSUIT, Target.PER_PLAYER, 7, 480, 3, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "crawler", Placement.DEEP_UNDERGROUND, 16, 28);
            if (e == null) return false;
            e.alertTo(c.p.position());
            return true;
        }).underground();
        reg("hunt_mimic_lure", Cat.PURSUIT, Target.PER_PLAYER, 6, 600, 3, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "mimic", Placement.ANY_GROUND, 18, 26);
            if (e == null) return false;
            Tasks.every(10, 40, 3, i -> {
                if (!e.isAlive() || c.p.isRemoved()) return false;
                Fx.privateSound(c.p, SoundEvents.COW_AMBIENT, e.position(), 1.2f, 0.9f);
                return true;
            });
            return true;
        }).surface().night();
        reg("hunt_herd_surrounds", Cat.PURSUIT, Target.AREA, 6, 700, 3, 6, c -> {
            int n = 0;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                BlockPos g = Placement.surface(c.l, BlockPos.containing(c.p.getX() + Math.cos(a) * 12, c.p.getY(), c.p.getZ() + Math.sin(a) * 12));
                if (g != null && Placement.standable(c.l, g) && HorrorSpawner.spawnAt(c.l, "pale_herd", g, c.p) != null) n++;
            }
            return n >= 4;
        }).surface();
        reg("hunt_widow_false_light", Cat.PURSUIT, Target.PER_PLAYER, 6, 600, 3, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "lantern_widow", Placement.FOREST_EDGE, 24, 34);
            if (e == null) return false;
            Vec3 look = c.p.getLookAngle();
            BlockPos g = Placement.surface(c.l, BlockPos.containing(c.p.getX() + look.x * 10, c.p.getY(), c.p.getZ() + look.z * 10));
            if (g != null && Placement.standable(c.l, g)) {
                c.l.setBlock(g, Blocks.LANTERN.defaultBlockState(), 3);
                TEMP_LIGHTS.add(new BlockPos[]{g});
                Tasks.later(600, () -> { if (c.l.getBlockState(g).is(Blocks.LANTERN)) c.l.setBlock(g, Blocks.AIR.defaultBlockState(), 3); });
            }
            return true;
        }).surface().night();
        reg("hunt_observers_multiply", Cat.PURSUIT, Target.SHARED, 5, 600, 3, 6, c -> {
            if (!(c.st.evidence.contains("voss_logs") || c.st.evidence.contains("truth_ledger") || c.st.evidence.contains("archive_location") || c.st.evidence.contains("tower_hint"))) return false;
            int want = 1 + c.stage / 2, n = 0;
            for (int i = 0; i < want; i++) if (watch(c, "observer", Placement.ANY_GROUND, 35, 60)) n++;
            return n > 0;
        }).surface();
        reg("hunt_corrupted_chase", Cat.PURSUIT, Target.PER_PLAYER, 5, 800, 4, 6, c -> {
            if (nearestStructure(c, 80, "research_station", "relay_station", "bunker", "containment_chamber") == null) return false;
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "hollow", Placement.ANY_GROUND, 20, 30);
            if (e == null) return false;
            e.alertTo(c.p.position());
            Fx.effect(c.p, "tension", 400, 0.6f);
            return true;
        });
        reg("hunt_follows_to_shelter", Cat.PURSUIT, Target.PER_PLAYER, 5, 700, 4, 6, c -> {
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "hollow", Placement.ANY_GROUND, 38, 48);
            if (e == null) return false;
            e.alertTo(c.p.position());
            return true;
        }).night();
        reg("hunt_cursed_artifact", Cat.PURSUIT, Target.PER_PLAYER, 6, 900, 2, 6, c -> {
            if (!(hasItem(c.p, "memory_shard") || hasItem(c.p, "silent_bell") || hasItem(c.p, "void_lens"))) return false;
            HorrorEntity e = HorrorSpawner.spawnNear(c.l, c.p, "echo", Placement.BEHIND_PLAYER, 6, 9);
            if (e == null) return false;
            Fx.effect(c.p, "glitch", 30, 0.7f);
            Fx.privateSound(c.p, ModSounds.GLITCH.get(), c.p.position(), 1f, 1f);
            return true;
        }).jump();
        reg("hunt_rare_forbidden_burrower", Cat.PURSUIT, Target.AREA, 3, 1500, 5, 6, c -> {
            if (c.p.getY() > 10) return false;
            Fx.effect(c.p, "shake", 80, 0.6f);
            Fx.privateSound(c.p, ModSounds.RUMBLE.get(), c.p.position().add(0, -4, 0), 1.6f, 0.6f);
            Tasks.later(80, () -> { if (!c.p.isRemoved()) HorrorSpawner.spawnNear(c.l, c.p, "burrower", Placement.DARK_CAVE, 14, 22); });
            return true;
        }).underground();

        // ---- Story and dimension events (10)
        // ---- Spawn-area events and world-wide events
        reg("env_spawn_footprints", Cat.ENVIRONMENT, Target.PER_PLAYER, 6, 900, 1, 6, SpawnArea::footprints).surface();
        reg("env_spawn_sign", Cat.ENVIRONMENT, Target.PER_PLAYER, 3, 3000, 1, 6, SpawnArea::sign).surface();
        reg("world_distant_calls", Cat.SOUND, Target.GLOBAL, 5, 3600, 2, 6, c -> {
            List<ServerPlayer> all = everyone(c);
            for (ServerPlayer q : all) {
                double a = c.r.nextDouble() * Math.PI * 2;
                Fx.privateSound(q, ModSounds.DISTANT_CALL.get(), q.position().add(Math.cos(a) * 60, 0, Math.sin(a) * 60), 1.5f, 0.8f);
            }
            return !all.isEmpty();
        }).surface();
        reg("world_static_wave", Cat.ENVIRONMENT, Target.GLOBAL, 5, 4800, 2, 6, c -> {
            List<ServerPlayer> all = everyone(c);
            for (ServerPlayer q : all) {
                Fx.privateSound(q, ModSounds.STATIC_LOOP.get(), q.position(), 0.6f, 0.9f);
                Fx.effect(q, "distort", 80, 0.4f);
            }
            return !all.isEmpty();
        });
        reg("world_hush", Cat.SOUND, Target.GLOBAL, 4, 5400, 3, 6, c -> {
            List<ServerPlayer> all = everyone(c);
            Tasks.every(0, 10, 24, i -> {
                for (ServerPlayer q : all) {
                    if (q.isRemoved()) continue;
                    Fx.stopSounds(q, SoundSource.NEUTRAL); Fx.stopSounds(q, SoundSource.HOSTILE); Fx.stopSounds(q, SoundSource.WEATHER);
                }
                return true;
            });
            return !all.isEmpty();
        }).surface();
        reg("world_knock_chorus", Cat.SOUND, Target.GLOBAL, 4, 5000, 3, 6, c -> {
            List<ServerPlayer> all = everyone(c);
            Tasks.every(0, 16, 4, i -> {
                for (ServerPlayer q : all) {
                    if (q.isRemoved()) continue;
                    double a = (i * 1.3 + q.getId()) % (Math.PI * 2);
                    Fx.privateSound(q, ModSounds.KNOCK.get(), q.position().add(Math.cos(a) * 9, 1, Math.sin(a) * 9), 1.1f, 0.9f);
                }
                return true;
            });
            return !all.isEmpty();
        }).night();
        reg("world_everyone_watched", Cat.SIGHTING, Target.GLOBAL, 4, 6000, 3, 6, c -> {
            int n = 0;
            for (ServerPlayer q : everyone(c)) {
                if (!q.serverLevel().canSeeSky(q.blockPosition())) continue;
                if (HorrorSpawner.spawnNear(c.l, q, "observer", Placement.FOREST_EDGE, 40, 70) != null) n++;
            }
            return n > 0;
        }).surface().night();

        reg("story_first_transmission", Cat.STORY, Target.PER_PLAYER, 50, 100000, 0, 6, c -> {
            if (!c.st.recordings.isEmpty()) return false;
            Transmissions.Entry e = Transmissions.byId("t01_first");
            Fx.privateSound(c.p, ModSounds.STATIC_LOOP.get(), c.p.position(), 0.8f, 1f);
            Tasks.later(50, () -> {
                Fx.message(c.p, "§8[radio] §7" + e.text());
                c.st.recordings.add(e.id());
                StoryManager.onRecordingFound(c.p, c.st, e);
            });
            return true;
        }).item("broken_radio");
        reg("story_station_accessible", Cat.STORY, Target.GLOBAL, 8, 1800, 2, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            if (nearestStructure(c, 300, "research_station") != null) return false;
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 70, 110);
            if (at == null || !Structures.place(c.l, "research_station", at)) return false;
            Structures.place(c.l, "radio_tower", at.offset(16, 0, 0));
            double a = Math.toDegrees(Math.atan2(at.getZ() - c.p.getZ(), at.getX() - c.p.getX()));
            String[] n = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
            Fx.message(c.p, "§8[radio] §7...a tower is lit to the " + n[(int) Math.floorMod(Math.round(a / 45.0), 8)] + ". Station Meridian is open. Come and read what we left.");
            return true;
        }).surface();
        reg("story_hidden_door_opens", Cat.STORY, Target.AREA, 10, 1800, 2, 6, c -> {
            if (c.st.puzzles.contains("hidden_door_done") || !c.st.puzzles.contains("sequence")) return false;
            StructureData.Entry e = nearestStructure(c, 500, "bunker", "research_station", "underground_archive");
            if (e == null) return false;
            c.l.getChunk(e.blockPos());
            BlockPos at = e.blockPos().offset(-14, 0, -14);
            boolean placed = Structures.place(c.l, "hidden_basement", Placement.surface(c.l, at) == null ? at : Placement.surface(c.l, at));
            if (!placed) return false;
            c.st.puzzles.add("hidden_door_done");
            Fx.message(c.p, "§7Somewhere, a wall that was a wall has become a door.");
            return true;
        }).ev("sequence_solved");
        reg("story_quiet_place_anomaly", Cat.STORY, Target.SHARED, 5, 900, 3, 6, c -> {
            StructureData.Entry e = nearestStructure(c, 200);
            if (e == null) return false;
            BlockPos q = e.blockPos().offset(2, 1, -2);
            c.l.getChunk(q);
            if (!c.l.getBlockState(q).isAir()) return false;
            c.l.setBlock(q, com.lastsignal.block.ModBlocks.BY_ID.get("corrupted_relay").get().defaultBlockState(), 3);
            c.l.sendParticles(ParticleTypes.PORTAL, q.getX() + .5, q.getY() + 1, q.getZ() + .5, 30, .5, .5, .5, .1);
            return true;
        });
        reg("story_recording_reveal", Cat.STORY, Target.PER_PLAYER, 12, 100000, 3, 6, c -> {
            if (c.st.recordings.size() < 8 || c.st.evidence.contains("reframed")) return false;
            c.st.evidence.add("reframed");
            Fx.message(c.p, "§6You lay the recordings side by side. Every one ends with someone answering. §7The Signal never attacked anyone. It asked, and they replied, and each reply taught it a word. §6It is not hunting you. It is learning you.");
            return true;
        });
        reg("story_dimension_entrance", Cat.STORY, Target.SHARED, 8, 900, 3, 6, c -> {
            if (!LastSignalConfig.COMMON.enableDimensions.get()) return false;
            int target = -1;
            for (int i = 0; i < Dimensions.IDS.size(); i++)
                if (Dimensions.lockedReason(c.st, i) == null && !c.st.evidence.contains("gate_" + i)) { target = i; break; }
            if (target < 0) return false;
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 8, 16);
            if (at == null) return false;
            var pillar = com.lastsignal.block.ModBlocks.BY_ID.get("strange_pillar").get().defaultBlockState();
            for (int y = 0; y < 3; y++) { c.l.setBlock(at.offset(-1, y, 0), pillar, 3); c.l.setBlock(at.offset(1, y, 0), pillar, 3); }
            c.l.setBlock(at.offset(0, 2, 0), pillar, 3);
            c.l.setBlock(at, com.lastsignal.block.ModBlocks.BY_ID.get("signal_gate").get().defaultBlockState()
                    .setValue(com.lastsignal.block.DimensionGateBlock.TARGET, target + 1), 3);
            Structures.data(c.l).add("gate_" + Dimensions.IDS.get(target), at);
            c.st.evidence.add("gate_" + target);
            Fx.message(c.p, "§8A doorway has appeared nearby that you are certain was not there.");
            return true;
        }).surface();
        reg("story_exit_blocked", Cat.STORY, Target.AREA, 6, 600, 0, 6, c -> {
            for (BlockPos b : BlockPos.betweenClosed(c.p.blockPosition().offset(-24, -6, -24), c.p.blockPosition().offset(24, 6, 24))) {
                if (c.l.getBlockState(b).is(com.lastsignal.block.ModBlocks.BY_ID.get("signal_gate").get())) {
                    BlockPos spot = b.offset(0, 0, 3);
                    if (!Placement.standable(c.l, spot)) spot = b.offset(3, 0, 0);
                    if (!Placement.standable(c.l, spot)) continue;
                    return HorrorSpawner.spawnAt(c.l, "doorman", spot, c.p) != null;
                }
            }
            return false;
        }).dim("mod");
        reg("story_hidden_room_revealed", Cat.STORY, Target.PER_PLAYER, 4, 1500, 4, 6, c -> {
            if (!LastSignalConfig.COMMON.enableStructureEvents.get()) return false;
            BlockPos at = Placement.ANY_GROUND.find(c.l, c.p, 20, 40);
            if (at == null || !Structures.place(c.l, "hidden_basement", at)) return false;
            Fx.message(c.p, "§7There is a trapdoor you are certain you walked past before.");
            return true;
        }).surface().ev("archive_location");
        reg("story_milestone_pursuit", Cat.STORY, Target.GLOBAL, 40, 100000, 4, 6, c -> {
            if (c.st.puzzles.contains("milestone4")) return false;
            c.st.puzzles.add("milestone4");
            Fx.effect(c.p, "static", 60, 0.5f);
            for (ServerPlayer q : c.party(64)) Fx.message(q, "§4The watchers have stopped watching.");
            return true;
        });
        reg("story_core_transmission", Cat.STORY, Target.GLOBAL, 50, 100000, 6, 6, c -> {
            if (!LastSignalConfig.COMMON.enableBosses.get() || c.st.puzzles.contains("core_called")) return false;
            c.st.puzzles.add("core_called");
            Transmissions.Entry t = Transmissions.byId("t29_core");
            for (ServerPlayer q : c.party(128)) {
                Fx.effect(q, "static", 120, 0.8f);
                Fx.message(q, "§8[radio] §7" + t.text());
            }
            ServerLevel voidLevel = c.l.getServer().getLevel(Dimensions.key("signal_void"));
            if (voidLevel != null) Tasks.later(100, () -> Dimensions.spawnCore(voidLevel));
            return true;
        }).ev("signal_core_unlocked");
    }

    private EventManager() {}
}
