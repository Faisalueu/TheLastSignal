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
                for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) l.getChunk(cx, cz);
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
                sb.append("OK arrival=").append(feet.toShortString()).append(" sampledSolid=").append(solid).append(" markSamples=").append(mark).append(sea ? " (sea)" : "");
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
