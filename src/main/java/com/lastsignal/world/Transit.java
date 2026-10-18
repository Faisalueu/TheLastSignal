package com.lastsignal.world;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.world.gen.Landing;
import com.lastsignal.world.gen.Themes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Server-side logic of the Black (to another dimension, ~40 block fall) and White (to the Overworld, ~120 block fall) fracture blocks.
 * Safety: per-player cooldown; destination chosen deterministically per block; landing column verified free; a narrowly scoped,
 * time-limited fall-damage clamp (see TransitEvents) that can never kill and is removed after landing / timeout / logout.
 */
public final class Transit {
    public static final String CD = "ls_transit_cd", LAND_UNTIL = "ls_land_until", LAND_START = "ls_land_start";
    public static final int BLACK_FALL = 40, WHITE_FALL = 120, COOLDOWN = 100, PROTECT_TICKS = 20 * 60;

    public record Dest(String id, ServerLevel level, Landing.Plan plan) {}

    public static List<String> candidates(ServerPlayer sp, String currentPath) {
        List<String> ids = new ArrayList<>(Themes.slugs());
        PlayerStory st = StoryManager.get(sp);
        for (int i = 0; i < Dimensions.IDS.size(); i++) if (Dimensions.lockedReason(st, i) == null) ids.add(Dimensions.IDS.get(i));   // keep progression rules
        ids.remove(currentPath);
        return ids;
    }

    /** Deterministic: the same black block always leads to the same dimension (easy to test, can be learned by the player). */
    public static String pickDestination(List<String> cands, BlockPos pos, String currentPath) {
        if (cands.isEmpty()) return null;
        Random r = new Random(pos.asLong() * 31L + currentPath.hashCode());
        return cands.get(r.nextInt(cands.size()));
    }

    public static Dest planBlack(MinecraftServer server, ServerPlayer sp, BlockPos pos, String currentPath) {
        List<String> c = candidates(sp, currentPath);
        // try the deterministic choice first, then fall back through the list if a dimension is unavailable
        String first = pickDestination(c, pos, currentPath);
        if (first == null) return null;
        List<String> order = new ArrayList<>(); order.add(first); order.addAll(c); 
        for (String id : order) {
            ServerLevel dl = server.getLevel(Dimensions.key(id));
            if (dl == null) continue;
            Landing.Plan p = Landing.plan(dl, id, pos.asLong() ^ 0x5DEECE66DL, BLACK_FALL);
            if (p != null) return new Dest(id, dl, p);
        }
        return null;
    }

    public static Landing.Plan planOverworld(ServerLevel ow, PlayerStory st) {
        BlockPos base = st.returnDim.equals("minecraft:overworld") && st.returnPos != 0 ? BlockPos.of(st.returnPos) : ow.getSharedSpawnPos();
        for (int ring = 0; ring < 8; ring++) {
            for (int k = 0; k < (ring == 0 ? 1 : 8); k++) {
                int dx = ring == 0 ? 0 : (int) Math.round(Math.cos(k * Math.PI / 4) * ring * 8), dz = ring == 0 ? 0 : (int) Math.round(Math.sin(k * Math.PI / 4) * ring * 8);
                int x = base.getX() + dx, z = base.getZ() + dz;
                ow.getChunk(x >> 4, z >> 4);
                int g = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                Landing.Plan p = Landing.scan(ow, x, z, Math.max(g, ow.getMinBuildHeight() + 1), WHITE_FALL);
                if (p != null) return p;
            }
        }
        return null;
    }

    public static void touch(ServerPlayer sp, BlockPos pos, boolean black) {
        if (sp.isSpectator() || sp.isDeadOrDying()) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        long now = sp.level().getGameTime();
        CompoundTag pd = sp.getPersistentData();
        if (now < pd.getLong(CD)) return;
        pd.putLong(CD, now + COOLDOWN);                                    // set first: repeated collision ticks do nothing
        if (!LastSignalConfig.COMMON.enableDimensions.get() || !LastSignalConfig.COMMON.transitEnabled.get()) { sp.displayClientMessage(Component.literal("The fracture does nothing."), true); return; }
        String cur = sp.level().dimension().location().getPath();
        ServerLevel dest; Landing.Plan plan; String label;
        if (black) {
            Dest d = planBlack(server, sp, pos, cur);
            if (d == null) { sp.displayClientMessage(Component.literal("Nothing answers on the other side."), true); return; }
            dest = d.level(); plan = d.plan(); label = d.id();
        } else {
            dest = server.overworld();
            plan = planOverworld(dest, StoryManager.get(sp));
            if (plan == null) { sp.displayClientMessage(Component.literal("The way back is closed."), true); return; }
            label = "overworld";
        }
        PlayerStory st = StoryManager.get(sp);
        if (black && !Dimensions.isModDimension(sp.level()) && st.returnDim.isEmpty()) { st.returnDim = sp.level().dimension().location().toString(); st.returnPos = sp.blockPosition().asLong(); }
        BlockPos p = plan.pos();
        dest.getChunk(p);
        sp.teleportTo(dest, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot(), 0f);
        sp.fallDistance = 0;
        sp.setDeltaMovement(0, 0, 0);
        sp.hurtMarked = true;
        long t2 = dest.getGameTime();
        pd.putLong(CD, t2 + COOLDOWN);
        pd.putLong(LAND_START, t2);
        pd.putLong(LAND_UNTIL, t2 + PROTECT_TICKS);
        StoryManager.markDirty(sp);
        sp.displayClientMessage(black ? Component.translatable("lastsignal.dimension." + label) : Component.literal("You fall back into the world."), true);
        LastSignalMod.LOGGER.info("Transit {} -> {} at {} (fall {})", black ? "black" : "white", label, p, plan.fall());
    }

    public static void clear(ServerPlayer sp) {
        CompoundTag pd = sp.getPersistentData();
        pd.remove(LAND_UNTIL); pd.remove(LAND_START);
    }

    private Transit() {}
}
