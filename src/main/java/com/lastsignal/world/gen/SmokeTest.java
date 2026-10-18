package com.lastsignal.world.gen;

import com.lastsignal.LastSignalMod;
import com.lastsignal.block.DimensionGateBlock;
import com.lastsignal.block.ModBlocks;
import com.lastsignal.world.Dimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless dedicated-server smoke test (only active with -Dlastsignal.smoketest=true, used by CI): generates a 7x7 chunk area in
 * every Phase-1 dimension and checks the arrival pad. This proves generation does not crash and the pad is walkable; it does NOT
 * prove how the dimensions look or feel.
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID)
public final class SmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (!Boolean.getBoolean("lastsignal.smoketest")) return;
        List<String> out = new ArrayList<>();
        int fails = 0;
        for (Theme t : ThemeData.ALL) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%02d %-22s %-7s ", t.index, t.slug, t.kind));
            try {
                for (int r = 0; r < t.roleIds.length; r++) {
                    String id = t.roleIds[r];
                    if (id.isEmpty()) continue;
                    Block b = id.indexOf(':') >= 0 ? ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)) : (ModBlocks.BY_ID.get(id) == null ? null : ModBlocks.BY_ID.get(id).get());
                    if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) throw new IllegalStateException("role " + r + " block missing: " + id);
                }
                ServerLevel l = e.getServer().getLevel(Dimensions.key(t.slug));
                if (l == null) throw new IllegalStateException("dimension not loaded");
                int chestN = 0, lootBad = 0, blackN = 0, whiteN = 0;
                Block blk = ModBlocks.BY_ID.get("black_fracture").get(), wht = ModBlocks.BY_ID.get("white_fracture").get();
                ResourceLocation tbl = new ResourceLocation(LastSignalMod.MODID, "chests/dim/" + Gen.lootName(t));
                for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) {
                    net.minecraft.world.level.chunk.LevelChunk ch = l.getChunk(cx, cz);
                    for (net.minecraft.world.level.block.entity.BlockEntity be : new ArrayList<>(ch.getBlockEntities().values())) {
                        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity cb) {
                            chestN++;
                            String lt = cb.saveWithFullMetadata().getString("LootTable");
                            if (!lt.isEmpty() && !lt.equals(tbl.toString())) lootBad++;
                        }
                    }
                    net.minecraft.world.level.chunk.LevelChunkSection[] secs = ch.getSections();
                    for (int si = 0; si < secs.length; si++) {
                        net.minecraft.world.level.chunk.LevelChunkSection sec = secs[si];
                        if (sec.hasOnlyAir()) continue;
                        if (!sec.maybeHas(st -> st.is(blk) || st.is(wht))) continue;
                        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                            BlockState st = sec.getBlockState(x, y, z);
                            if (st.is(blk)) blackN++; else if (st.is(wht)) whiteN++;
                        }
                    }
                }
                net.minecraft.world.level.storage.loot.LootTable table = e.getServer().getLootData().getLootTable(tbl);
                if (table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) throw new IllegalStateException("loot table missing " + tbl);
                int rolled = 0;
                for (int k = 0; k < 20; k++) {
                    net.minecraft.world.level.storage.loot.LootParams lp = new net.minecraft.world.level.storage.loot.LootParams.Builder(l)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                    for (net.minecraft.world.item.ItemStack is : table.getRandomItems(lp, 1000L + k)) if (!is.isEmpty()) rolled++;
                }
                if (rolled == 0) throw new IllegalStateException("loot table rolls nothing " + tbl);
                if (lootBad > 0) throw new IllegalStateException(lootBad + " chests with wrong/missing loot table");
                com.lastsignal.world.gen.Landing.Plan lp0 = Landing.plan(l, t.slug, 4242L, 40);
                if (lp0 == null) throw new IllegalStateException("no landing plan");
                BlockPos lf = lp0.pos();
                if (l.getBlockState(lf).blocksMotion() || l.getBlockState(lf.above()).blocksMotion() || !l.getBlockState(lf.below()).blocksMotion() && lp0.fall() == 0)
                    throw new IllegalStateException("unsafe landing " + lf);
                for (int dy = 0; dy < lp0.fall(); dy++) if (l.getBlockState(lf.above(dy)).blocksMotion()) throw new IllegalStateException("fall column blocked at " + lf.above(dy));
                int[] a = Gen.arrival(t);
                BlockPos feet = new BlockPos(a[0], a[1], a[2]);
                BlockState below = l.getBlockState(feet.below()), f = l.getBlockState(feet), h = l.getBlockState(feet.above());
                boolean sea = t.kind.equals("sea");
                if (below.isAir()) throw new IllegalStateException("no floor under arrival " + feet);
                if (f.blocksMotion() || h.blocksMotion()) throw new IllegalStateException("arrival blocked at " + feet + " " + f + " / " + h);
                BlockPos gp = t.kind.equals("stairs") ? new BlockPos(a[0], a[1], a[2] - 1) : new BlockPos(a[0], a[1], a[2] - 3);
                BlockState g = l.getBlockState(gp);
                if (!(g.getBlock() instanceof DimensionGateBlock) || g.getValue(DimensionGateBlock.TARGET) != 0) throw new IllegalStateException("exit gate missing at " + gp + " found " + g);
                int solid = 0, mark = 0;
                Block markB = t.role(Theme.MARK).getBlock();
                for (int x = -48; x < 64; x += 2) for (int z = -48; z < 64; z += 2) for (int y = 1; y < 255; y += 3) {
                    BlockState s = l.getBlockState(new BlockPos(x, y, z));
                    if (!s.isAir()) solid++;
                    if (s.getBlock() == markB) mark++;
                }
                sb.append("OK arrival=").append(feet.toShortString()).append(" sampledSolid=").append(solid).append(" markSamples=").append(mark).append(" chests=").append(chestN).append(" black=").append(blackN).append(" white=").append(whiteN).append(" landFall=").append(lp0.fall()).append(sea ? " (sea)" : "");
            } catch (Throwable ex) {
                fails++;
                sb.append("FAIL ").append(ex);
                LastSignalMod.LOGGER.error("smoke test failure in " + t.slug, ex);
            }
            out.add(sb.toString());
        }
        out.add("SMOKE_RESULT dimensions=" + ThemeData.ALL.length + " failures=" + fails);
        try { Files.write(Path.of("smoke-result.txt"), out); } catch (Exception ignored) { }
        for (String s : out) LastSignalMod.LOGGER.info("SMOKE {}", s);
        e.getServer().halt(false);
    }

    private SmokeTest() {}
}
