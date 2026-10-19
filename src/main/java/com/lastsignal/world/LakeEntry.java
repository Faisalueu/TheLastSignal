package com.lastsignal.world;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.world.gen.ExtraThemes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Diving in a natural Overworld lake (head under water for lakeDiveSeconds) leads - 50/50 - to the Golden Cloud Realm or the Infernal Volcano.
 * Server authoritative; checked every 10 ticks per player (never per frame); the expensive lake validation runs only once per qualifying dive and
 * a failed validation backs the player off for 10 s. State is per player, in memory, and dropped on logout / death / dimension change / server stop.
 *
 * Limits (honest): "natural lake" is a heuristic - a big, open-sky water body of at least 300 water blocks and 8x8 extent, in a non-ocean, non-river
 * biome, whose banks are made (almost) only of natural blocks. A huge player-built pool made of natural blocks would also count; small ponds,
 * buckets, aquariums, glass/brick pools and covered/underground water do not.
 */
public final class LakeEntry {
    private static final Map<UUID, Integer> SUBMERGED = new HashMap<>();   // ticks submerged (negative = back-off after a failed check)
    private static final int MIN_WATER = 300, MIN_EXTENT = 8, CAP = 1400, RADIUS_XZ = 28, RADIUS_Y = 14;

    /** 50/50 on every successful dive; pure so it can be tested statistically. true = Golden Cloud Realm, false = Infernal Volcano. */
    public static boolean pickCloud(RandomSource r) { return r.nextBoolean(); }

    public static void reset(UUID id) { SUBMERGED.remove(id); }

    public static void resetAll() { SUBMERGED.clear(); }

    /** called every server tick of a player; only does work every 10th tick */
    public static void tick(ServerPlayer sp) {
        if (sp.tickCount % 10 != 0) return;
        UUID id = sp.getUUID();
        if (!(sp.level() instanceof ServerLevel lvl) || lvl.dimension() != Level.OVERWORLD) { SUBMERGED.remove(id); return; }
        boolean enabled;
        try { enabled = LastSignalConfig.COMMON.lakeEntryEnabled.get() && LastSignalConfig.COMMON.enableDimensions.get(); } catch (IllegalStateException e) { enabled = false; }
        if (!enabled || sp.isSpectator() || sp.isDeadOrDying() || !sp.isEyeInFluid(FluidTags.WATER) || sp.isPassenger()) { SUBMERGED.remove(id); return; }
        int t = SUBMERGED.merge(id, 10, Integer::sum);
        int need;
        try { need = (int) Math.round(LastSignalConfig.COMMON.lakeDiveSeconds.get() * 20); } catch (IllegalStateException e) { need = 40; }
        if (t < need) return;
        if (lvl.getGameTime() < sp.getPersistentData().getLong(Transit.CD)) { SUBMERGED.put(id, 0); return; }
        BlockPos eye = BlockPos.containing(sp.getEyePosition());
        if (!isNaturalLake(lvl, eye)) { SUBMERGED.put(id, -200); return; }       // retry only after ~10 more seconds under water
        SUBMERGED.remove(id);
        dive(sp, lvl);
    }

    private static void dive(ServerPlayer sp, ServerLevel ow) {
        CompoundTag pd = sp.getPersistentData();
        pd.putLong(Transit.CD, ow.getGameTime() + Transit.cooldownTicks());       // set first: nothing can start a second transition
        boolean cloud = pickCloud(sp.getRandom());
        String id = cloud ? ExtraThemes.CLOUD : ExtraThemes.VOLCANO;
        try {
            if (sp.getServer().getLevel(Dimensions.key(id)) == null) { sp.displayClientMessage(Component.literal("The water is only water."), true); return; }
            BlockPos shore = findShore(ow, sp.blockPosition());
            if (!Dimensions.enter(sp, id, true)) { sp.displayClientMessage(Component.literal("The water is only water."), true); return; }
            PlayerStory st = StoryManager.get(sp);
            st.returnDim = "minecraft:overworld";                                  // come back on dry land next to the lake, never into the water
            st.returnPos = shore.asLong();
            StoryManager.markDirty(sp);
            sp.getPersistentData().putLong(Transit.CD, sp.level().getGameTime() + Transit.cooldownTicks());
            Transit.clear(sp);
            LastSignalMod.LOGGER.info("Lake dive: {} -> {}", sp.getGameProfile().getName(), id);
        } catch (RuntimeException ex) {
            LastSignalMod.LOGGER.error("Lake dive failed", ex);
            sp.displayClientMessage(Component.literal("The water is only water."), true);
        }
    }

    /** nearest dry, solid, 2-block-free ground within ~40 blocks (rings, 16 angles each); falls back to the world spawn */
    public static BlockPos findShore(ServerLevel l, BlockPos from) {
        for (int ring = 2; ring <= 40; ring += 2) for (int k = 0; k < 16; k++) {
            int x = from.getX() + (int) Math.round(Math.cos(k * Math.PI / 8) * ring), z = from.getZ() + (int) Math.round(Math.sin(k * Math.PI / 8) * ring);
            if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos g = new BlockPos(x, y, z);
            BlockState s = l.getBlockState(g);
            if (!s.getFluidState().isEmpty() || !s.blocksMotion()) continue;
            if (l.getBlockState(g.above()).blocksMotion() || !l.getBlockState(g.above()).getFluidState().isEmpty() || l.getBlockState(g.above(2)).blocksMotion()) continue;
            return g.above();
        }
        return l.getSharedSpawnPos();
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
            || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES)
            || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CLAY)
            || s.is(Blocks.TUFF) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.PACKED_ICE)
            || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.CORAL_BLOCKS) || s.is(Blocks.BEDROCK) || s.is(Blocks.MAGMA_BLOCK)
            || s.is(Blocks.LILY_PAD) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)
            || s.is(Blocks.BAMBOO) || s.is(Blocks.VINE) || s.is(Blocks.CAVE_VINES) || s.is(Blocks.BIG_DRIPLEAF) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** Flood-fills the water body around the player's eye (bounded) and validates size, extent, open sky, biome and natural banks. */
    public static boolean isNaturalLake(ServerLevel l, BlockPos eye) {
        var biome = l.getBiome(eye);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return false;
        if (!l.getFluidState(eye).is(Fluids.WATER) && !l.getFluidState(eye).is(Fluids.FLOWING_WATER)) return false;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(eye); seen.add(eye.asLong());
        int count = 0, minX = eye.getX(), maxX = minX, minZ = eye.getZ(), maxZ = minZ, bad = 0, bank = 0;
        boolean sky = false;
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty() && count < CAP) {
            BlockPos p = q.poll();
            count++;
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX()); minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            if (!sky && l.getFluidState(p.above()).isEmpty() && l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) <= p.getY() + 1) sky = true;   // heightmap, not light: instant and exact
            for (int[] o : d) {
                BlockPos n = p.offset(o[0], o[1], o[2]);
                if (Math.abs(n.getX() - eye.getX()) > RADIUS_XZ || Math.abs(n.getZ() - eye.getZ()) > RADIUS_XZ || Math.abs(n.getY() - eye.getY()) > RADIUS_Y) continue;
                if (!l.hasChunkAt(n)) continue;
                BlockState s = l.getBlockState(n);
                if (s.getFluidState().is(FluidTags.WATER) && (s.getFluidState().is(Fluids.WATER) || s.getFluidState().is(Fluids.FLOWING_WATER))) {
                    if (seen.add(n.asLong())) q.add(n);
                } else if (!s.isAir()) {
                    bank++;
                    if (!natural(s)) bad++;
                }
            }
        }
        if (count < MIN_WATER || !sky) return false;
        if (maxX - minX + 1 < MIN_EXTENT || maxZ - minZ + 1 < MIN_EXTENT) return false;
        return bank == 0 || bad * 100 <= bank * 3;          // at most 3 % non-natural neighbours
    }

    private LakeEntry() {}
}
