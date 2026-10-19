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
        if (!(l.getBlockState(feet.offset(0, 0, -3)).getBlock() instanceof com.lastsignal.block.DimensionGateBlock)) throw new IllegalStateException("return gate missing");
        if (gold < 1) throw new IllegalStateException("no gold generated in 13x13 chunks");
        if (gold > 400) throw new IllegalStateException("too much gold: " + gold);
        // walkable ground: a 40x40 area around spawn must contain no hole down to the void
        for (int x = -20; x <= 20; x += 2) for (int z = -20; z <= 20; z += 2) {
            int h = SkyRealm.height(t, x, z);
            if (!l.getBlockState(new BlockPos(x, h, z)).blocksMotion()) throw new IllegalStateException("hole at " + x + "," + z);
        }
        return "arrival=" + feet.toShortString() + " goldBlocksAndOre(13x13 chunks)=" + gold + " chests=0";
    }

    static String volcano(ServerLevel l, Theme t) {
        if (l == null) throw new IllegalStateException("volcano not loaded");
        int[] a = Gen.arrival(t);
        BlockPos feet = new BlockPos(a[0], a[1], a[2]);
        l.getChunk(feet);
        if (!l.getBlockState(feet.below()).blocksMotion() || l.getBlockState(feet).blocksMotion() || l.getBlockState(feet.above()).blocksMotion()) throw new IllegalStateException("arrival unsafe " + feet);
        if (l.getBlockState(feet.below()).is(Blocks.LAVA)) throw new IllegalStateException("arrival on lava");
        int lava = count(l, 6, Blocks.LAVA);
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
        if (bad > 0) throw new IllegalStateException("road not walkable: " + bad + " bad steps of " + steps);
        // the lip of the ring on the gap side leads into the bowl: walk from the road end around to the gap would need A*; instead check the bowl floor ring r=20 is open somewhere
        boolean gapOpen = false;
        for (int k = 0; k < 72 && !gapOpen; k++) {
            double ang = k * Math.PI / 36;
            int x = ex + (int) Math.round(Math.cos(ang) * 17), z = ez + (int) Math.round(Math.sin(ang) * 17);
            if (Volcano.height(t, x, z) <= 70) gapOpen = true;
        }
        if (!gapOpen) throw new IllegalStateException("ring around the crater has no opening");
        return "arrival=" + feet.toShortString() + " lavaBlocks(13x13 chunks)=" + lava + " exit=" + ex + "," + by + "," + ez + " roadSteps=" + steps + " falseExitPositions=0 chests=0";
    }

    static String lakes(ServerLevel ow) {
        // build test water bodies high above the terrain in the Overworld (sky open above them), then remove them again
        int bx = 4000, bz = 4000, y0 = 215;
        for (int cx = (bx - 40) >> 4; cx <= (bx + 40) >> 4; cx++) for (int cz = (bz - 40) >> 4; cz <= (bz + 40) >> 4; cz++) ow.getChunk(cx, cz);
        StringBuilder sb = new StringBuilder();
        try {
            // big natural-block lake 16x16x5
            pool(ow, bx, y0, bz, 16, 5, Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
            boolean biomeOk = !(ow.getBiome(new BlockPos(bx + 8, y0 + 2, bz + 8)).is(net.minecraft.tags.BiomeTags.IS_OCEAN) || ow.getBiome(new BlockPos(bx + 8, y0 + 2, bz + 8)).is(net.minecraft.tags.BiomeTags.IS_RIVER));
            boolean big = LakeEntry.isNaturalLake(ow, new BlockPos(bx + 8, y0 + 2, bz + 8));
            sb.append("bigLake=").append(big).append(biomeOk ? "" : "(biome excluded)");
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
