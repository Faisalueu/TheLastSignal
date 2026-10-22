package com.lastsignal.world.gen;

import com.lastsignal.LastSignalMod;
import com.lastsignal.world.LakeEntry;
import com.lastsignal.world.Dimensions;
import com.lastsignal.world.Transit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

/** Server-side checks for the two lake-entry dimensions, the lake picker/validator and the fall protection (CI smoke test only). */
final class ExtraSmoke {
    static int run(MinecraftServer server, List<String> out) {
        int fails = 0;
        fails += check(out, "dims-registered", () -> {
            StringBuilder sb = new StringBuilder();
            for (Theme t : Themes.all()) {
                if (server.getLevel(Dimensions.key(t.slug)) == null) throw new IllegalStateException("not loaded: " + t.slug);
            }
            return Themes.all().size() + " dimensions loaded (48 horror + 2 lake-entry)";
        });
        fails += check(out, "cloud-realm", () -> cloud(server.getLevel(Dimensions.key(ExtraThemes.CLOUD)), Themes.get(ExtraThemes.CLOUD)));
        fails += check(out, "volcano", () -> volcano(server.getLevel(Dimensions.key(ExtraThemes.VOLCANO)), Themes.get(ExtraThemes.VOLCANO)));
        fails += check(out, "white-presence", () -> whitePresence(server));
        fails += check(out, "director", () -> director(server.overworld()));
        fails += check(out, "spawn-anchor", () -> spawnAnchor(server.overworld()));
        fails += check(out, "tile-rules", () -> tileRules(server));
        fails += check(out, "special-attacks", () -> specials(server.overworld()));
        fails += check(out, "roster", () -> roster(server.overworld()));
        fails += check(out, "haunted-houses", () -> houses(server.overworld()));
        fails += check(out, "lake-5050", () -> {
            RandomSource r = RandomSource.create(20261019L);
            int n = 20000, c = 0;
            for (int i = 0; i < n; i++) if (LakeEntry.pickCloud(r)) c++;
            double dev = Math.abs(c - n / 2.0), limit = 4.5 * Math.sqrt(n * .25);
            if (dev > limit) throw new IllegalStateException("cloud=" + c + " volcano=" + (n - c) + " deviates more than 4.5 sigma");
            return "cloud=" + c + " volcano=" + (n - c) + " of " + n + " (limit +-" + (int) limit + ")";
        });
        fails += check(out, "lake-validation", () -> lakes(server.overworld()));
        fails += check(out, "fall-protection", () -> protection(server.overworld()));
        return fails;
    }

    static String roster(ServerLevel l) {
        int n = 0;
        java.util.Set<String> bosses = java.util.Set.of("signal_core", "buried_king", "unfinished");
        for (var pr : com.lastsignal.entity.EntityProfiles.ALL) {
            var arch = com.lastsignal.entity.HorrorEntity.archOf(pr.id());
            if (arch == com.lastsignal.entity.HorrorEntity.Arch.BOSS && !bosses.contains(pr.id())) throw new IllegalStateException(pr.id() + " has no behaviour archetype");
            var type = com.lastsignal.entity.ModEntities.BY_ID.get(pr.id()).get();
            var e = type.create(l);
            if (e == null) throw new IllegalStateException("cannot create " + pr.id());
            e.moveTo(3000.5, l.getSeaLevel() + 40, 3000.5, 0, 0);
            l.getChunk(3000 >> 4, 3000 >> 4);
            l.addFreshEntity(e);
            for (int i = 0; i < 100; i++) e.tick();
            if (e.getY() < -100) throw new IllegalStateException(pr.id() + " fell out of the world");
            e.discard();
            n++;
        }
        if (n < 29) throw new IllegalStateException("roster too small: " + n);
        for (String id : com.lastsignal.sound.EntitySounds.ROSTER) for (String g : new String[]{"ambient", "hurt", "death"}) {
            if (com.lastsignal.sound.EntitySounds.of(id, g) == null) throw new IllegalStateException("missing sound event " + id + "." + g);
            for (String f : g.equals("ambient") ? new String[]{"ambient1", "ambient2", "ambient3"} : new String[]{g})
                if (ExtraSmoke.class.getResource("/assets/lastsignal/sounds/entity/" + id + "/" + f + ".ogg") == null) throw new IllegalStateException("missing ogg " + id + "/" + f);
        }
        return n + " horror profiles instantiated and ticked 100 ticks (+ White Presence = " + (n + 1) + ")";
    }

    static String director(ServerLevel l) {
        var evs = com.lastsignal.event.EventManager.EVENTS.values();
        if (evs.size() < 60) throw new IllegalStateException("only " + evs.size() + " events");
        int[] byScope = new int[4];
        int minCd = Integer.MAX_VALUE;
        for (var d : evs) {
            var sc = com.lastsignal.event.Director.scopeOf(d);
            byScope[sc.ordinal()]++;
            if (d.cat == com.lastsignal.event.EventManager.Cat.STORY && sc != com.lastsignal.event.Director.Scope.STORY) throw new IllegalStateException(d.id + " story event without STORY scope");
            if (d.weight < 0 || d.minStage < 0 || d.maxStage > 6 || d.minStage > d.maxStage) throw new IllegalStateException(d.id + " has an invalid stage range / weight");
            if (d.cooldownSec < 0) throw new IllegalStateException(d.id + " has a negative cooldown");
            minCd = Math.min(minCd, d.cooldownSec);
        }
        if (com.lastsignal.event.Director.phaseOf(0) != com.lastsignal.event.Director.Phase.SUSPICION || com.lastsignal.event.Director.phaseOf(3) != com.lastsignal.event.Director.Phase.OBSERVATION
                || com.lastsignal.event.Director.phaseOf(5) != com.lastsignal.event.Director.Phase.PURSUIT || com.lastsignal.event.Director.phaseOf(6) != com.lastsignal.event.Director.Phase.FINAL_SIGNAL)
            throw new IllegalStateException("phase mapping wrong");
        if (byScope[com.lastsignal.event.Director.Scope.WORLD.ordinal()] < 5) throw new IllegalStateException("only " + byScope[com.lastsignal.event.Director.Scope.WORLD.ordinal()] + " WORLD-scope events");
        var a = FakePlayerFactory.getMinecraft(l);
        var b = FakePlayerFactory.get(l, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000b2"), "ls_audit_b"));
        a.moveTo(100.5, 90, 100.5, 0, 0); b.moveTo(110.5, 90, 105.5, 0, 0);
        var any = evs.iterator().next();
        var shared = evs.stream().filter(d -> com.lastsignal.event.Director.scopeOf(d) == com.lastsignal.event.Director.Scope.LOCAL_GROUP).findFirst().orElseThrow();
        var solo = evs.stream().filter(d -> com.lastsignal.event.Director.scopeOf(d) == com.lastsignal.event.Director.Scope.PLAYER).findFirst().orElseThrow();
        if (!com.lastsignal.event.Director.cooldownKey(shared, a).equals(com.lastsignal.event.Director.cooldownKey(shared, b))) throw new IllegalStateException("friends in one area do not share a LOCAL_GROUP cooldown");
        if (com.lastsignal.event.Director.cooldownKey(solo, a).equals(com.lastsignal.event.Director.cooldownKey(solo, b))) throw new IllegalStateException("PLAYER cooldown is shared between players");
        // run every event once on a fake player in the overworld; exceptions are failures, "false alarm" results are fine
        long t0 = System.currentTimeMillis();
        int ran = 0, fired = 0;
        java.util.List<String> broke = new java.util.ArrayList<>();
        for (var d : evs) {
            if (System.currentTimeMillis() - t0 > 60000) break;
            try { if (com.lastsignal.event.EventManager.auditRun(a, d)) fired++; ran++; }
            catch (Throwable ex) { broke.add(d.id + ": " + ex); }
        }
        if (!broke.isEmpty()) throw new IllegalStateException("events threw: " + broke);
        int cleaned = com.lastsignal.event.Director.cleanup(a);
        com.lastsignal.event.SpawnArea.restoreAll();
        for (var h : com.lastsignal.entity.HorrorEntity.near(l, a.position(), 200)) h.discard();
        return evs.size() + " events, scopes PLAYER/LOCAL_GROUP/WORLD/STORY=" + java.util.Arrays.toString(byScope) + ", minCooldown=" + minCd + "s, executed " + ran + " (" + fired + " fired, rest false alarms), cleanup removed " + cleaned;
    }

    static String spawnAnchor(ServerLevel l) {
        var sp = l.getSharedSpawnPos();
        var fp = FakePlayerFactory.getMinecraft(l);
        fp.moveTo(sp.getX() + 5.5, sp.getY(), sp.getZ() + 5.5, 0, 0);
        var anchor = com.lastsignal.event.SpawnAnchor.of(l, fp);
        if (anchor == null) throw new IllegalStateException("no anchor next to world spawn");
        var r = com.lastsignal.config.LastSignalConfig.COMMON.spawnAnchorRadius.get();
        int near = 0, found = 0;
        for (int i = 0; i < 40; i++) {
            var g = com.lastsignal.event.Placement.ANY_GROUND.find(l, fp, 30, 60);
            if (g == null) continue;
            found++;
            if (Math.sqrt(g.distSqr(anchor)) <= r + 6) near++;
            if (!l.getBlockState(g).isAir() || !l.getBlockState(g.below()).isSolid()) throw new IllegalStateException("unsafe spot " + g);
        }
        fp.moveTo(sp.getX() + 5000.5, 90, sp.getZ() + 5000.5, 0, 0);
        if (com.lastsignal.event.SpawnAnchor.of(l, fp) != null) throw new IllegalStateException("anchor used for a player 5000 blocks away");
        return "anchor=" + anchor.toShortString() + ", radius " + r + ": " + found + "/40 placements found, " + near + " inside the spawn radius";
    }

    static String tileRules(net.minecraft.server.MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        for (var en : Wonder.RULES.entrySet()) {
            Theme t = Themes.get(en.getKey());
            Wonder.Rule w = en.getValue();
            if (t == null || !"rooms".equals(t.kind)) throw new IllegalStateException("not a rooms theme: " + en.getKey());
            Profile p = Themes.profile(t);
            int same = 0, tested = 0, anomalies = 0;
            for (int cx = 3; cx < 40; cx++) for (int cz = 3; cz < 40; cz++) {
                if (Wonder.anomaly(t, w, cx, cz) || Wonder.anomaly(t, w, cx + w.period, cz) || Wonder.anomaly(t, w, cx, cz + w.period)) { anomalies++; continue; }
                tested++;
                var a = Rooms.desc(t, p, cx, cz); var b = Rooms.desc(t, p, cx + w.period, cz); var c = Rooms.desc(t, p, cx, cz + w.period);
                if (a.arch == b.arch && a.arch == c.arch) same++;
                if (Rooms.open(t, p, cx, cz, cx + 1, cz) != Rooms.open(t, p, cx + w.period, cz, cx + w.period + 1, cz)) throw new IllegalStateException(t.slug + ": door differs between repeats at " + cx + "," + cz);
            }
            if (same != tested) throw new IllegalStateException(t.slug + ": layout does not repeat (" + same + "/" + tested + ")");
            if (w.lattice > 0) {
                int open = 0, n = 0;
                for (int k = 3; k < 60; k++) if (!Wonder.anomaly(t, w, k, 0) && !Wonder.anomaly(t, w, k + 1, 0)) { n++; if (Rooms.open(t, p, k, 0, k + 1, 0)) open++; }
                if (open != n) throw new IllegalStateException(t.slug + ": lattice corridor row has closed doors (" + open + "/" + n + ")");
            }
            sb.append(t.slug).append(" P=").append(w.period).append(" L=").append(w.lattice).append(w.mono ? " mono" : "").append(" repeat ").append(same).append("/").append(tested).append(" anomalies~").append(anomalies).append("; ");
        }
        // really generate a block of chunks in each of the tile dimensions (no exception, bedrock ceiling in place)
        for (String slug : Wonder.RULES.keySet()) {
            ServerLevel l = server.getLevel(Dimensions.key(slug));
            if (l == null) throw new IllegalStateException("dimension not loaded: " + slug);
            for (int cx = 5; cx <= 8; cx++) for (int cz = -2; cz <= 1; cz++) {
                var ch = l.getChunk(cx, cz);
                boolean solidTop = false;
                for (int y = 66; y < 140 && !solidTop; y++) if (ch.getBlockState(new net.minecraft.core.BlockPos(cx * 16 + 8, y, cz * 16 + 8)).is(net.minecraft.world.level.block.Blocks.BEDROCK)) solidTop = true;
                if (!solidTop) throw new IllegalStateException(slug + ": no bedrock ceiling in chunk " + cx + "," + cz);
            }
        }
        return sb.toString();
    }

    static String specials(ServerLevel l) {
        var fp = FakePlayerFactory.getMinecraft(l);
        l.getChunk(3000 >> 4, 3000 >> 4);
        int ok = 0;
        for (var en : com.lastsignal.entity.SpecialAttacks.BY_ENTITY.entrySet()) {
            var e = com.lastsignal.entity.ModEntities.BY_ID.get(en.getKey()).get().create(l);
            if (e == null) throw new IllegalStateException("cannot create " + en.getKey());
            fp.moveTo(3000.5, l.getSeaLevel() + 40, 3000.5, 0, 0);
            e.moveTo(3003.5, l.getSeaLevel() + 40, 3000.5, 0, 0);
            l.addFreshEntity(e);
            float hp = fp.getHealth();
            com.lastsignal.entity.SpecialAttacks.force(e, fp);        // may legitimately decline (e.g. no room to step behind)
            if (Float.isNaN(fp.getHealth()) || fp.getHealth() > hp) throw new IllegalStateException(en.getKey() + " corrupted player health");
            if (!l.getBlockState(net.minecraft.core.BlockPos.containing(3000.5, l.getSeaLevel() + 39, 3000.5)).isAir()) throw new IllegalStateException(en.getKey() + " changed blocks");
            for (int i = 0; i < 20; i++) e.tick();
            e.discard();
            fp.removeAllEffects();
            ok++;
        }
        if (ok < 14) throw new IllegalStateException("too few specials: " + ok);
        return ok + " special attacks executed without error, no block changes, no health corruption";
    }

    static String houses(ServerLevel l) {
        var ids = com.lastsignal.world.Houses.IDS;
        if (ids.size() != 30 || new java.util.HashSet<>(ids).size() != 30) throw new IllegalStateException("need 30 unique house ids");
        int signs = 0;
        var seen = new java.util.HashSet<String>();
        for (String id : com.lastsignal.world.Houses.BUILT) {
            var t = com.lastsignal.world.Houses.texts(id);
            if (t.size() < 4) throw new IllegalStateException(id + " has too few signs");
            for (String x : t) {
                var lines = com.lastsignal.world.Houses.wrap(x);
                if (lines.isEmpty() || lines.size() > 4) throw new IllegalStateException(id + " sign does not fit 4 lines: " + x);
                for (String ln : lines) if (ln.length() > com.lastsignal.world.Houses.MAX_LINE) throw new IllegalStateException(id + " line too long: " + ln);
                if (!seen.add(x)) throw new IllegalStateException("duplicate sign text: " + x);
                signs++;
            }
        }
        // build all five on controlled ground, 60 blocks apart, then count the signs that really exist in the world
        int base = l.getSeaLevel() + 20, n = 0, found = 0;
        for (String id : com.lastsignal.world.Houses.BUILT) {
            int ox = 3000 + n * 60;
            for (int cx = ox >> 4; cx <= (ox + 14) >> 4; cx++) for (int cz = 3000 >> 4; cz <= (3000 + 14) >> 4; cz++) l.getChunk(cx, cz);
            net.minecraft.core.BlockPos o = new net.minecraft.core.BlockPos(ox, base, 3000);
            for (int x = -1; x <= 14; x++) for (int z = -1; z <= 14; z++) for (int y = -12; y <= 12; y++)
                l.setBlock(o.offset(x, y, z), y <= -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            com.lastsignal.world.Houses.build(l, id, o);
            int here = 0;
            for (int x = -1; x <= 14; x++) for (int z = -1; z <= 14; z++) for (int y = -12; y <= 12; y++)
                if (l.getBlockEntity(o.offset(x, y, z)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s
                        && !s.getFrontText().getMessage(0, false).getString().isEmpty()) here++;
            if (here < 4) throw new IllegalStateException(id + " has only " + here + " readable signs in the world");
            found += here; n++;
        }
        // progression chain: HOUSE_29 holds the red key, HOUSE_30's entrance is a sealed door
        net.minecraft.core.BlockPos o29 = new net.minecraft.core.BlockPos(3000 + 28 * 60, base, 3000), o30 = new net.minecraft.core.BlockPos(3000 + 29 * 60, base, 3000);
        if (!(l.getBlockEntity(o29.offset(11, 0, 11)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ch) || ch.getItem(13).getItem() != com.lastsignal.item.ModItems.ITEMS_BY_ID.get("red_key").get())
            throw new IllegalStateException("HOUSE_29 does not hold the red key");
        if (l.getBlockState(o30.offset(6, 1, 0)).getBlock() != com.lastsignal.block.ModBlocks.BY_ID.get("sealed_door_block").get()) throw new IllegalStateException("HOUSE_30 entrance is not sealed");
        var d = com.lastsignal.world.Structures.data(l);
        for (String id : com.lastsignal.world.Houses.BUILT) if (d.nearest(id, new net.minecraft.core.BlockPos(3000, base, 3000), 5000) == null) throw new IllegalStateException(id + " not recorded");
        if (com.lastsignal.world.Houses.place(l, "HOUSE_31", new net.minecraft.core.BlockPos(3000, base, 3000))) throw new IllegalStateException("unknown house placed");
        if (com.lastsignal.world.Houses.place(l, "HOUSE_01", new net.minecraft.core.BlockPos(3020, base, 3000))) throw new IllegalStateException("spacing rule failed: second HOUSE_01 placed");
        return "30 ids, " + com.lastsignal.world.Houses.BUILT.size() + " built, " + signs + " sign texts fit 4x" + com.lastsignal.world.Houses.MAX_LINE + ", " + found + " signs found in world, recorded, spacing + reserved checks ok";
    }

    interface Body { String go() throws Exception; }

    static int check(List<String> out, String name, Body b) {
        try { out.add("EX " + name + " OK " + b.go()); return 0; }
        catch (Throwable ex) { LastSignalMod.LOGGER.error("extra smoke failure " + name, ex); out.add("EX " + name + " FAIL " + ex); return 1; }
    }

    private static int count(ServerLevel l, int r, net.minecraft.world.level.block.Block... blocks) {
        int n = 0;
        for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
            LevelChunk ch = l.getChunk(cx, cz);
            for (var be : ch.getBlockEntities().values()) if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) throw new IllegalStateException("chest in chunk " + cx + "," + cz);
            for (LevelChunkSection sec : ch.getSections()) {
                if (sec.hasOnlyAir() || !sec.maybeHas(s -> { for (var b : blocks) if (s.is(b)) return true; return false; })) continue;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    BlockState s = sec.getBlockState(x, y, z);
                    for (var b : blocks) if (s.is(b)) { n++; break; }
                }
            }
        }
        return n;
    }

    static String cloud(ServerLevel l, Theme t) {
        if (l == null) throw new IllegalStateException("cloud realm not loaded");
        int gold = count(l, 6, Blocks.GOLD_BLOCK, Blocks.GOLD_ORE);
        int[] a = Gen.arrival(t);
        BlockPos feet = new BlockPos(a[0], a[1], a[2]);
        if (!l.getBlockState(feet.below()).blocksMotion() || l.getBlockState(feet).blocksMotion() || l.getBlockState(feet.above()).blocksMotion()) throw new IllegalStateException("arrival unsafe " + feet);
        int dtop = SkyRealm.height(t, SkyRealm.DAIS_X, SkyRealm.DAIS_Z);
        l.getChunk(SkyRealm.DAIS_X >> 4, SkyRealm.DAIS_Z >> 4);
        if (!l.getBlockState(new BlockPos(SkyRealm.DAIS_X, dtop + 1, SkyRealm.DAIS_Z)).is(Blocks.BEACON)) throw new IllegalStateException("dais beacon missing");
        if (!l.getBlockState(new BlockPos(SkyRealm.DAIS_X + 2, dtop, SkyRealm.DAIS_Z)).is(Blocks.GOLD_BLOCK)) throw new IllegalStateException("dais gold missing");
        if (!SkyRealm.inDais(t, SkyRealm.DAIS_X + 1.5, dtop + 1, SkyRealm.DAIS_Z + .5)) throw new IllegalStateException("dais does not trigger on the dais");
        if (SkyRealm.inDais(t, 8.5, feet.getY(), 8.5) || SkyRealm.inDais(t, SkyRealm.DAIS_X + 20, dtop + 1, SkyRealm.DAIS_Z)) throw new IllegalStateException("dais triggers away from the dais");
        for (var blk : new String[]{"signal_gate", "checkpoint_beacon"}) {
            var obsolete = com.lastsignal.block.ModBlocks.BY_ID.get(blk).get();
            for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) {
                LevelChunk ch = l.getChunk(cx, cz);
                for (LevelChunkSection sec : ch.getSections()) {
                    if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.is(obsolete))) continue;
                    throw new IllegalStateException("obsolete block " + blk + " generated in chunk " + cx + "," + cz);
                }
            }
        }
        if (gold < 1) throw new IllegalStateException("no gold generated in 13x13 chunks");
        if (gold > 400) throw new IllegalStateException("too much gold: " + gold);
        // walkable ground: a 40x40 area around spawn must contain no hole down to the void
        for (int x = -20; x <= 20; x += 2) for (int z = -20; z <= 20; z += 2) {
            int h = SkyRealm.height(t, x, z);
            if (!l.getBlockState(new BlockPos(x, h, z)).blocksMotion()) throw new IllegalStateException("hole at " + x + "," + z);
        }
        return "arrival=" + feet.toShortString() + " goldBlocksAndOre(13x13 chunks)=" + gold + " chests=0 dais=ok no-signal_gate/checkpoint_beacon; " + palace(l, t);
    }

    // ------------------------------------------------------------------------------------------------------ white presence
    /** Registry/biome wiring + a 4500-tick manual simulation of one creature on the spawn island (state machine, safety, peacefulness). */
    static String whitePresence(MinecraftServer server) {
        ServerLevel l = server.getLevel(Dimensions.key(ExtraThemes.CLOUD));
        Theme t = Themes.get(ExtraThemes.CLOUD);
        var biome = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).get(new net.minecraft.resources.ResourceLocation("lastsignal", ExtraThemes.CLOUD));
        if (biome == null) throw new IllegalStateException("cloud biome not found");
        boolean listed = false;
        for (var sd : biome.getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.CREATURE).unwrap()) if (sd.type == com.lastsignal.entity.ModEntities.WHITE_PRESENCE.get()) listed = true;
        if (!listed) throw new IllegalStateException("white_presence missing from the cloud biome creature spawners");
        if (!biome.getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).unwrap().isEmpty()) throw new IllegalStateException("hostile spawners in the cloud biome");
        int[] a = Gen.arrival(t);
        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) l.getChunk(cx, cz);
        var e = com.lastsignal.entity.ModEntities.WHITE_PRESENCE.get().create(l);
        if (e == null) throw new IllegalStateException("could not create white_presence");
        e.moveTo(a[0] + .5, a[1], a[2] + .5, 0, 0);
        l.addFreshEntity(e);
        if (!e.targetSelector.getAvailableGoals().isEmpty()) throw new IllegalStateException("white presence has target goals");
        for (var g : e.goalSelector.getAvailableGoals()) if (g.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal || g.getGoal() instanceof net.minecraft.world.entity.ai.goal.RangedAttackGoal) throw new IllegalStateException("white presence has an attack goal");
        java.util.EnumSet<com.lastsignal.entity.WhitePresenceEntity.Mode> seen = java.util.EnumSet.noneOf(com.lastsignal.entity.WhitePresenceEntity.Mode.class);
        double minRel = 1e9, maxRel = -1e9, far = 0, x0 = e.getX(), z0 = e.getZ();
        float hp = e.getHealth();
        for (int i = 0; i < 4500 && e.isAlive(); i++) {
            e.tick();
            seen.add(e.getMode());
            int g = SkyRealm.height(t, (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()));
            double rel = e.getY() - (g + 1);
            minRel = Math.min(minRel, rel); maxRel = Math.max(maxRel, rel);
            far = Math.max(far, Math.hypot(e.getX() - x0, e.getZ() - z0));
            if (e.getY() < 60) throw new IllegalStateException("white presence fell to y=" + e.getY());
        }
        boolean alive = e.isAlive();
        float hp2 = e.getHealth();
        com.lastsignal.entity.WhitePresenceEntity.Mode last = e.getMode();
        e.discard();
        if (!alive || hp2 != hp) throw new IllegalStateException("white presence lost health/died during the simulation");
        for (var m : new com.lastsignal.entity.WhitePresenceEntity.Mode[]{com.lastsignal.entity.WhitePresenceEntity.Mode.WALK, com.lastsignal.entity.WhitePresenceEntity.Mode.TAKEOFF, com.lastsignal.entity.WhitePresenceEntity.Mode.FLY, com.lastsignal.entity.WhitePresenceEntity.Mode.LAND})
            if (!seen.contains(m)) throw new IllegalStateException("mode never reached: " + m + " (seen " + seen + ")");
        if (maxRel > 40) throw new IllegalStateException("flew too high above the ground: " + maxRel);
        if (minRel < -3) throw new IllegalStateException("sank into the ground: " + minRel);
        if (far < 4) throw new IllegalStateException("never moved (" + far + ")");
        return "modes=" + seen + " lastMode=" + last + " heightAboveGround=[" + String.format("%.1f", minRel) + "," + String.format("%.1f", maxRel) + "] maxDistance=" + String.format("%.0f", far) + " no target/attack goals, no hostile spawners";
    }

    // ------------------------------------------------------------------------------------------------------ palace
    private static boolean free(ServerLevel l, int x, int y, int z, java.util.Set<Long> panels) {
        BlockPos p = new BlockPos(x, y, z);
        if (panels != null && panels.contains(p.asLong())) return true;
        return !l.getBlockState(p).blocksMotion() && l.getFluidState(p).isEmpty();
    }

    /** Walkable-region flood fill (step up <= 1 with headroom, drop <= 3) from the palace entrance; returns the set of reached standing cells. */
    private static java.util.Set<Long> flood(ServerLevel l, int sx, int sy, int sz, java.util.Set<Long> panels) {
        int x0 = SkyRealm.PX - 28, x1 = SkyRealm.PX + 28, z0 = SkyRealm.PZ - 38, z1 = SkyRealm.PZ + 30, y0 = Palace.F - 3, y1 = Palace.F + 46;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{sx, sy, sz}); seen.add(BlockPos.asLong(sx, sy, sz));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) for (int dy = 1; dy >= -3; dy--) {
                int nx = c[0] + d[0], ny = c[1] + dy, nz = c[2] + d[1];
                if (nx < x0 || nx > x1 || nz < z0 || nz > z1 || ny < y0 || ny > y1) continue;
                if (!free(l, nx, ny, nz, panels) || !free(l, nx, ny + 1, nz, panels)) continue;
                if (l.getBlockState(new BlockPos(nx, ny - 1, nz)).getCollisionShape(l, new BlockPos(nx, ny - 1, nz)).isEmpty() || !l.getFluidState(new BlockPos(nx, ny - 1, nz)).isEmpty()) continue;
                if (dy == 1 && !free(l, c[0], c[1] + 2, c[2], panels)) continue;
                if (dy < 0 && (!free(l, nx, c[1], nz, panels) || !free(l, nx, c[1] + 1, nz, panels))) continue;
                if (seen.add(BlockPos.asLong(nx, ny, nz))) q.add(new int[]{nx, ny, nz});
                break;
            }
        }
        return seen;
    }

    static String palace(ServerLevel l, Theme t) {
        int PX = SkyRealm.PX, PZ = SkyRealm.PZ, F = Palace.F;
        for (int cx = (PX - 32) >> 4; cx <= (PX + 32) >> 4; cx++) for (int cz = (PZ - 40) >> 4; cz <= (PZ + 32) >> 4; cz++) l.getChunk(cx, cz);
        int chests = 0, gold = 0;
        for (int cx = (PX - 32) >> 4; cx <= (PX + 32) >> 4; cx++) for (int cz = (PZ - 40) >> 4; cz <= (PZ + 32) >> 4; cz++) {
            LevelChunk ch = l.getChunk(cx, cz);
            for (var be : ch.getBlockEntities().values()) if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) chests++;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = F - 2; y <= F + 45; y++)
                if (ch.getBlockState(new BlockPos(ch.getPos().getMinBlockX() + x, y, ch.getPos().getMinBlockZ() + z)).is(Blocks.GOLD_BLOCK)) gold++;
        }
        boolean cfgOn = true;
        try { cfgOn = com.lastsignal.config.LastSignalConfig.COMMON.chestDensity.get() > 0; } catch (IllegalStateException e) { }
        if (cfgOn && chests != 3) throw new IllegalStateException("palace chests=" + chests + " (expected exactly 3)");
        if (gold < 100 || gold > 900) throw new IllegalStateException("palace gold blocks out of range: " + gold);
        for (int[] c : Palace.CHESTS) {
            BlockPos p = new BlockPos(PX + c[0], c[1], PZ + c[2]);
            if (cfgOn && !l.getBlockState(p).is(Blocks.CHEST)) throw new IllegalStateException("chest missing at " + p);
        }
        if (!l.getBlockState(new BlockPos(PX, F + 23, PZ + 7)).blocksMotion() && !l.getBlockState(new BlockPos(PX, F + 24, PZ + 7)).blocksMotion()) throw new IllegalStateException("dome apex missing");
        // the approach ramp from the north: |dh| <= 1 per block along the centre line
        int prev = Integer.MIN_VALUE, steep = 0;
        for (int z = PZ - 100; z <= PZ - 34; z++) {
            int h = SkyRealm.height(t, PX, z);
            if (prev != Integer.MIN_VALUE && Math.abs(h - prev) > 1) steep++;
            prev = h;
        }
        if (steep > 0) throw new IllegalStateException("palace approach has " + steep + " steps taller than 1 block");
        int sy = SkyRealm.height(t, PX, PZ - 35) + 1;
        java.util.Set<Long> panels = new java.util.HashSet<>();
        for (int[] pn : Palace.PANELS) for (int y = F + 1; y <= F + 2; y++) {
            BlockPos pp = new BlockPos(PX + pn[0], y, PZ + pn[1]);
            if (!l.getBlockState(pp).blocksMotion()) throw new IllegalStateException("hidden panel is not solid at " + pp);
            panels.add(pp.asLong());
        }
        java.util.Set<Long> open = flood(l, PX, sy, PZ - 35, null), withPanels = flood(l, PX, sy, PZ - 35, panels);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Palace.CHESTS.length; i++) {
            int[] c = Palace.CHESTS[i];
            boolean a = false, b = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long k = BlockPos.asLong(PX + c[0] + d[0], c[1], PZ + c[2] + d[1]);
                a |= open.contains(k); b |= withPanels.contains(k);
            }
            boolean hidden = i < 2;
            if (!b) throw new IllegalStateException("chest " + i + " unreachable even after breaking the panel");
            if (hidden && a) throw new IllegalStateException("hidden chest " + i + " reachable without breaking a panel");
            if (!hidden && !a) throw new IllegalStateException("tower chest unreachable on foot");
            sb.append(i == 0 ? "west-vault" : i == 1 ? "east-archive" : "tower-top").append(hidden ? "(hidden,reachable via panel) " : "(open) ");
        }
        return "palace chests=" + chests + " gold=" + gold + " approach-ramp-ok walkable-cells=" + open.size() + " " + sb;
    }

    static String volcano(ServerLevel l, Theme t) {
        if (l == null) throw new IllegalStateException("volcano not loaded");
        int[] a = Gen.arrival(t);
        BlockPos feet = new BlockPos(a[0], a[1], a[2]);
        l.getChunk(feet);
        if (!l.getBlockState(feet.below()).blocksMotion() || l.getBlockState(feet).blocksMotion() || l.getBlockState(feet.above()).blocksMotion()) throw new IllegalStateException("arrival unsafe " + feet);
        if (l.getBlockState(feet.below()).is(Blocks.LAVA)) throw new IllegalStateException("arrival on lava");
        int lava = count(l, 6, Blocks.LAVA);
        // lavafalls: anchors exist, keep >= 30 blocks from the road, and the generated column/ledge/pool are really there
        java.util.List<int[]> falls = Volcano.lavafallsNear(t, 0, 0, 400);
        if (falls.size() < 3) throw new IllegalStateException("too few lavafalls: " + falls.size());
        for (int[] f : falls) if (Volcano.roadDistance(t, f[0], f[1]) < 30) throw new IllegalStateException("lavafall on the road at " + f[0] + "," + f[1]);
        int[] f0 = falls.get(0);
        l.getChunk(f0[0] >> 4, f0[1] >> 4);
        if (!l.getBlockState(new BlockPos(f0[0], f0[2] - 6, f0[1])).is(Blocks.LAVA)) throw new IllegalStateException("lavafall column missing at " + f0[0] + "," + (f0[2] - 6) + "," + f0[1] + " found " + l.getBlockState(new BlockPos(f0[0], f0[2] - 6, f0[1])));
        if (!l.getBlockState(new BlockPos(f0[0], f0[2], f0[1])).is(Blocks.LAVA) || !l.getBlockState(new BlockPos(f0[0] + 4, f0[2], f0[1])).blocksMotion()) throw new IllegalStateException("lavafall ledge malformed");
        if (!l.getBlockState(new BlockPos(f0[0] + 1, f0[3], f0[1])).is(Blocks.LAVA)) throw new IllegalStateException("lavafall landing pool missing");
        // no monsters in the volcano: the biome has no spawners of any category
        var mobs = l.getBiome(new BlockPos(8, 80, 8)).value().getMobSettings();
        for (var cat : net.minecraft.world.entity.MobCategory.values())
            if (!mobs.getMobs(cat).unwrap().isEmpty()) throw new IllegalStateException("volcano biome still has " + cat + " spawners: " + mobs.getMobs(cat).unwrap());
        // the charred congregation exists, stays off the road and has its glowing heads in the world
        java.util.List<int[]> groups = Volcano.statuesNear(t, 0, 0, 400);
        if (groups.size() < 4) throw new IllegalStateException("too few statue groups: " + groups.size());
        int heads = 0;
        for (int[] gr : groups) {
            if (Volcano.roadDistance(t, gr[0], gr[1]) < 8) throw new IllegalStateException("statue group on the road");
            for (int[] o : Volcano.statueFigures(t, gr)) {
                int wx = gr[0] + o[0], wz = gr[1] + o[1];
                l.getChunk(wx >> 4, wz >> 4);
                int y0 = Volcano.height(t, wx, wz);
                if (l.getBlockState(new BlockPos(wx, y0 + 5, wz)).is(Blocks.MAGMA_BLOCK)) heads++;
            }
        }
        if (heads < 6) throw new IllegalStateException("statue heads not found in the world (" + heads + ")");
        // exit crater: ember vent at the bottom, air above, trigger true inside, false outside, false at spawn
        int ex = Volcano.exitX(t), ez = Volcano.exitZ(t), by = Volcano.exitBottomY(t);
        for (int dx = -16; dx <= 16; dx += 16) for (int dz = -16; dz <= 16; dz += 16) l.getChunk((ex + dx) >> 4, (ez + dz) >> 4);
        if (!l.getBlockState(new BlockPos(ex, by, ez)).is(t.role(Theme.LIGHT).getBlock())) throw new IllegalStateException("exit vent missing at " + ex + "," + by + "," + ez + " found " + l.getBlockState(new BlockPos(ex, by, ez)));
        if (l.getBlockState(new BlockPos(ex, by + 1, ez)).blocksMotion() || l.getBlockState(new BlockPos(ex, by + 2, ez)).blocksMotion()) throw new IllegalStateException("exit blocked");
        if (!Volcano.inExit(t, ex + .5, by + 1, ez + .5)) throw new IllegalStateException("exit zone does not trigger at the vent");
        if (Volcano.inExit(t, 8, feet.getY(), 8)) throw new IllegalStateException("spawn triggers exit");
        int falsePos = 0;
        RandomSource r = RandomSource.create(77);
        for (int i = 0; i < 4000; i++) {
            double x = r.nextInt(900) - 450, z = r.nextInt(900) - 450;
            if (Math.hypot(x - ex, z - ez) > 6 && Volcano.inExit(t, x, 60 + r.nextInt(60), z)) falsePos++;
        }
        if (falsePos > 0) throw new IllegalStateException("exit zone triggers away from the real crater (" + falsePos + ")");
        // the ash road: walk the centre line spawn -> road end -> crater lip; every step standable and <= 1 block up/down, no lava
        double[][] pts = Volcano.roadPoints(t);
        int steps = 0, bad = 0, prev = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < pts.length; i++) {
            double dx = pts[i + 1][0] - pts[i][0], dz = pts[i + 1][1] - pts[i][1], len = Math.hypot(dx, dz);
            for (double s = 0; s < len; s += 1.0) {
                int x = (int) Math.floor(pts[i][0] + dx * s / len), z = (int) Math.floor(pts[i][1] + dz * s / len);
                l.getChunk(x >> 4, z >> 4);
                int y = Volcano.height(t, x, z);
                BlockState g = l.getBlockState(new BlockPos(x, y, z));
                if (!g.blocksMotion() || g.is(Blocks.LAVA) || l.getBlockState(new BlockPos(x, y + 1, z)).blocksMotion() || l.getBlockState(new BlockPos(x, y + 2, z)).blocksMotion()
                        || !l.getFluidState(new BlockPos(x, y + 1, z)).isEmpty()) bad++;
                if (prev != Integer.MIN_VALUE && Math.abs(y - prev) > 1) bad++;
                prev = y; steps++;
            }
        }
        // BFS on the real terrain function from the road end to the crater floor (steps up <= 1, down <= 3, no lava columns): proves the hidden crater is reachable
        int sx = (int) Math.round(pts[5][0]), sz = (int) Math.round(pts[5][1]), R = 75;
        int W = 2 * R + 1;
        int cx0 = ex - R, cz0 = ez - R;
        int[][] hmap = new int[W][W];
        for (int i = 0; i < W; i++) for (int j = 0; j < W; j++) hmap[i][j] = Volcano.height(t, cx0 + i, cz0 + j);
        boolean[][] seen = new boolean[W][W];
        java.util.ArrayDeque<int[]> dq = new java.util.ArrayDeque<>();
        int si = Math.max(0, Math.min(W - 1, sx - cx0)), sj = Math.max(0, Math.min(W - 1, sz - cz0));
        dq.add(new int[]{si, sj}); seen[si][sj] = true;
        boolean reached = false;
        while (!dq.isEmpty() && !reached) {
            int[] c = dq.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int ni = c[0] + d[0], nj = c[1] + d[1];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W || seen[ni][nj]) continue;
                int dh = hmap[ni][nj] - hmap[c[0]][c[1]];
                if (dh > 1 || dh < -3 || (hmap[ni][nj] <= Volcano.LAVA && Math.hypot(cx0 + ni - ex, cz0 + nj - ez) >= 34)) continue;   // lava below level 62 except inside the crater bowl
                seen[ni][nj] = true; dq.add(new int[]{ni, nj});
                if (Math.hypot(cx0 + ni - ex, cz0 + nj - ez) <= 2.5) reached = true;
            }
        }
        if (!reached) throw new IllegalStateException("crater floor is not reachable on foot from the road end");
        if (bad > 0) throw new IllegalStateException("road not walkable: " + bad + " bad steps of " + steps);
        // the lip of the ring on the gap side leads into the bowl: walk from the road end around to the gap would need A*; instead check the bowl floor ring r=20 is open somewhere
        boolean gapOpen = false;
        for (int k = 0; k < 72 && !gapOpen; k++) {
            double ang = k * Math.PI / 36;
            int x = ex + (int) Math.round(Math.cos(ang) * 17), z = ez + (int) Math.round(Math.sin(ang) * 17);
            if (Volcano.height(t, x, z) <= 70) gapOpen = true;
        }
        if (!gapOpen) throw new IllegalStateException("ring around the crater has no opening");
        return "arrival=" + feet.toShortString() + " lavaBlocks(13x13 chunks)=" + lava + " exit=" + ex + "," + by + "," + ez + " roadSteps=" + steps + " falseExitPositions=0 craterReachableFromRoadEnd=true chests=0";
    }

    static String lakes(ServerLevel ow) {
        // build test water bodies high above the terrain in the Overworld (sky open above them), then remove them again
        int bx = 4000, bz = 4000, y0 = 215;
        for (int k = 0; k < 60; k++) {                                    // find a land (non-ocean, non-river) biome so the positive case is really tested
            int tx = 1500 + k * 331, tz = -900 + k * 457;
            var bio = ow.getBiome(new BlockPos(tx, y0, tz));
            if (!bio.is(net.minecraft.tags.BiomeTags.IS_OCEAN) && !bio.is(net.minecraft.tags.BiomeTags.IS_RIVER) && !bio.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) { bx = tx; bz = tz; break; }
        }
        for (int cx = (bx - 40) >> 4; cx <= (bx + 40) >> 4; cx++) for (int cz = (bz - 40) >> 4; cz <= (bz + 40) >> 4; cz++) ow.getChunk(cx, cz);
        StringBuilder sb = new StringBuilder();
        try {
            // big natural-block lake 16x16x5
            pool(ow, bx, y0, bz, 16, 5, Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
            boolean biomeOk = !(ow.getBiome(new BlockPos(bx + 8, y0 + 2, bz + 8)).is(net.minecraft.tags.BiomeTags.IS_OCEAN) || ow.getBiome(new BlockPos(bx + 8, y0 + 2, bz + 8)).is(net.minecraft.tags.BiomeTags.IS_RIVER));
            boolean big = LakeEntry.isNaturalLake(ow, new BlockPos(bx + 8, y0 + 2, bz + 8));
            sb.append("bigLake=").append(big).append(biomeOk ? "" : "(biome excluded)").append(" at ").append(bx).append(",").append(bz);
            if (biomeOk && !big) throw new IllegalStateException("a 16x16x5 dirt/stone lake under open sky was rejected");
            clear(ow, bx, y0, bz, 16, 5);
            // bucket pond 3x3x2
            pool(ow, bx, y0, bz, 3, 2, Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
            boolean small = LakeEntry.isNaturalLake(ow, new BlockPos(bx + 1, y0 + 1, bz + 1));
            sb.append(" smallPond=").append(small);
            if (small) throw new IllegalStateException("a 3x3 pond counted as a lake");
            clear(ow, bx, y0, bz, 3, 2);
            // big artificial pool lined with glass/bricks
            pool(ow, bx, y0, bz, 16, 5, Blocks.GLASS.defaultBlockState(), Blocks.BRICKS.defaultBlockState());
            boolean art = LakeEntry.isNaturalLake(ow, new BlockPos(bx + 8, y0 + 2, bz + 8));
            sb.append(" glassPool=").append(art);
            if (art) throw new IllegalStateException("a glass/brick pool counted as a natural lake");
            clear(ow, bx, y0, bz, 16, 5);
            // covered pool (roof)
            pool(ow, bx, y0, bz, 16, 5, Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) ow.setBlock(new BlockPos(bx + x, y0 + 7, bz + z), Blocks.STONE.defaultBlockState(), 2);
            boolean roofed = LakeEntry.isNaturalLake(ow, new BlockPos(bx + 8, y0 + 2, bz + 8));
            sb.append(" coveredPool=").append(roofed);
            if (roofed) throw new IllegalStateException("a roofed pool counted as a natural lake");
        } finally {
            for (int x = -2; x < 20; x++) for (int z = -2; z < 20; z++) for (int y = -2; y < 12; y++) ow.setBlock(new BlockPos(bx + x, y0 + y, bz + z), Blocks.AIR.defaultBlockState(), 2);
        }
        return sb.toString();
    }

    private static void pool(ServerLevel l, int bx, int y0, int bz, int size, int depth, BlockState wall, BlockState floor) {
        for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) for (int y = -1; y <= depth; y++) {
            boolean edge = x == -1 || z == -1 || x == size || z == size;
            BlockPos p = new BlockPos(bx + x, y0 + y, bz + z);
            if (y == -1) l.setBlock(p, floor, 2);
            else if (edge) l.setBlock(p, wall, 2);
            else l.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
        }
    }

    private static void clear(ServerLevel l, int bx, int y0, int bz, int size, int depth) {
        for (int x = -2; x <= size + 1; x++) for (int z = -2; z <= size + 1; z++) for (int y = -2; y <= depth + 8; y++) l.setBlock(new BlockPos(bx + x, y0 + y, bz + z), Blocks.AIR.defaultBlockState(), 2);
    }

    static String protection(ServerLevel ow) {
        FakePlayer fp = FakePlayerFactory.getMinecraft(ow);
        StringBuilder sb = new StringBuilder();
        // 1) protected landing: no damage, effect + tag removed, only once
        Transit.protect(fp, ow, 40);
        if (!fp.hasEffect(MobEffects.SLOW_FALLING)) throw new IllegalStateException("no Slow Falling after protect()");
        int dur = fp.getEffect(MobEffects.SLOW_FALLING).getDuration();
        if (dur < 400) throw new IllegalStateException("Slow Falling too short: " + dur);
        Transit.protect(fp, ow, 40);                                       // touching again must not stack
        if (fp.getEffect(MobEffects.SLOW_FALLING).getDuration() > dur + 5) throw new IllegalStateException("effect stacks");
        LivingHurtEvent e1 = new LivingHurtEvent(fp, fp.damageSources().fall(), 12f);
        MinecraftForge.EVENT_BUS.post(e1);
        if (!e1.isCanceled()) throw new IllegalStateException("transition fall damage not cancelled");
        if (fp.getPersistentData().contains(Transit.LAND_UNTIL) || fp.hasEffect(MobEffects.SLOW_FALLING)) throw new IllegalStateException("protection not removed after landing");
        sb.append("transitFall=cancelled, protectionRemoved=true, slowFallTicks=").append(dur);
        // 2) after that, normal fall damage works again
        LivingHurtEvent e2 = new LivingHurtEvent(fp, fp.damageSources().fall(), 12f);
        MinecraftForge.EVENT_BUS.post(e2);
        if (e2.isCanceled() || e2.getAmount() < 12f) throw new IllegalStateException("normal fall damage was blocked after the landing");
        sb.append(", normalFallAfter=damages");
        // 3) other damage is never affected, even while protected
        Transit.protect(fp, ow, 40);
        DamageSource lava = fp.damageSources().lava();
        LivingHurtEvent e3 = new LivingHurtEvent(fp, lava, 4f);
        MinecraftForge.EVENT_BUS.post(e3);
        if (e3.isCanceled()) throw new IllegalStateException("non-fall damage cancelled during protection");
        // 4) expired protection does nothing and cleans itself
        fp.getPersistentData().putLong(Transit.LAND_UNTIL, ow.getGameTime() - 5);
        LivingHurtEvent e4 = new LivingHurtEvent(fp, fp.damageSources().fall(), 12f);
        MinecraftForge.EVENT_BUS.post(e4);
        if (e4.isCanceled()) throw new IllegalStateException("expired protection still cancels");
        Transit.clear(fp);
        sb.append(", lavaDamageUnaffected=true, expiredProtection=ignored");
        return sb.toString();
    }

    private ExtraSmoke() {}
}
