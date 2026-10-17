package com.lastsignal.world;

import com.lastsignal.LastSignalMod;
import com.lastsignal.block.DimensionGateBlock;
import com.lastsignal.block.ModBlocks;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.event.HorrorSpawner;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Travel + generation for the six dimensions. Terrain comes from the datapack (flat/noise); the interesting
 * layouts (Long Hall maze, Archive facility, platforms, structures) are built ONCE on first entry and
 * remembered in {@link DimState}. Entry is by Signal Gate; exits are gates (target 0), the /lastsignal recover
 * command, checkpoint beacons, and an automatic void-fall rescue.
 */
public final class Dimensions {
    public static final List<String> IDS = List.of("quiet_fields", "long_hall", "archive_below", "red_silence", "memory_woods", "signal_void");

    /** The six gameplay dimensions + all Phase-1 horror dimensions (admin/command access). */
    public static List<String> allIds() {
        List<String> l = new ArrayList<>(IDS);
        l.addAll(com.lastsignal.world.gen.Themes.slugs());
        return l;
    }

    public static boolean isPhase1(String id) { return com.lastsignal.world.gen.Themes.isPhase1(id); }

    public static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(LastSignalMod.MODID, id));
    }

    public static boolean isModDimension(Level l) {
        return l.dimension().location().getNamespace().equals(LastSignalMod.MODID);
    }

    // ------------------------------------------------------------------ state
    public static class DimState extends SavedData {
        public static final String NAME = "lastsignal_dimstate";
        public boolean built;
        public long arrival;
        public long extra;   // dimension-specific anchor (e.g. boss arena / maze goal)
        public static DimState load(CompoundTag t) {
            DimState d = new DimState();
            d.built = t.getBoolean("built"); d.arrival = t.getLong("arrival"); d.extra = t.getLong("extra");
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("built", built); t.putLong("arrival", arrival); t.putLong("extra", extra);
            return t;
        }
    }

    public static DimState state(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(DimState::load, DimState::new, DimState.NAME);
    }

    // ------------------------------------------------------------------ requirements
    /** Human-readable reason, or null when the player may enter. Requirements are evidence-based and fair. */
    public static String lockedReason(PlayerStory st, int index) {
        int stage = st.stage();
        return switch (index) {
            case 0 -> stage >= 2 ? null : "The field is not ready for you.";
            case 1 -> stage >= 3 ? null : "The hall has not noticed you yet.";
            case 2 -> st.evidence.contains("archive_location") ? null : "You do not know where the archive is.";
            case 3 -> stage >= 5 ? null : "The silence is not yet red enough.";
            case 4 -> st.notes.size() >= 10 ? null : "You have not read enough to remember the woods.";
            case 5 -> st.evidence.contains("signal_core_unlocked") ? null : "Nothing is calling from the void yet.";
            default -> "Unknown.";
        };
    }

    // ------------------------------------------------------------------ travel
    public static void useGate(ServerPlayer sp, int target) {
        if (!LastSignalConfig.COMMON.enableDimensions.get()) {
            sp.displayClientMessage(Component.literal("The gate is inert."), true);
            return;
        }
        if (target == 0) { returnHome(sp, false); return; }
        PlayerStory st = StoryManager.get(sp);
        String reason = lockedReason(st, target - 1);
        if (reason != null) { sp.displayClientMessage(Component.literal(reason), true); return; }
        enter(sp, IDS.get(target - 1), false);
    }

    /** Force=true skips requirements (admin command). */
    public static boolean enter(ServerPlayer sp, String dimId, boolean force) {
        MinecraftServer server = sp.getServer();
        ServerLevel dest = server.getLevel(key(dimId));
        if (dest == null) return false;
        PlayerStory st = StoryManager.get(sp);
        if (!isModDimension(sp.level())) {
            st.returnDim = sp.level().dimension().location().toString();
            st.returnPos = sp.blockPosition().asLong();
        }
        BlockPos arrival = ensureBuilt(dest, dimId);
        dest.getChunk(arrival);
        sp.teleportTo(dest, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, sp.getYRot(), 0f);
        sp.displayClientMessage(Component.translatable("lastsignal.dimension." + dimId), true);
        StoryManager.markDirty(sp);
        return true;
    }

    public static void returnHome(ServerPlayer sp, boolean silent) {
        MinecraftServer server = sp.getServer();
        PlayerStory st = StoryManager.get(sp);
        ServerLevel dest = null;
        BlockPos pos;
        if (!st.returnDim.isEmpty()) {
            dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(st.returnDim)));
        }
        if (dest == null) dest = server.overworld();
        if (st.returnDim.isEmpty()) {
            pos = dest.getSharedSpawnPos();
        } else {
            pos = BlockPos.of(st.returnPos);
        }
        dest.getChunk(pos);
        int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (dest.getBlockState(pos).isAir() && dest.getBlockState(pos.above()).isAir()) y = pos.getY();
        sp.teleportTo(dest, pos.getX() + 0.5, Math.max(y, dest.getMinBuildHeight() + 1), pos.getZ() + 0.5, sp.getYRot(), 0f);
        st.dimensionsEscaped++;
        StoryManager.markDirty(sp);
        if (!silent) sp.displayClientMessage(Component.literal("You are back. The static follows."), true);
    }

    /** /lastsignal recover and void-fall rescue. Prefers the attuned checkpoint, else the way home. */
    public static void recover(ServerPlayer sp) {
        PlayerStory st = StoryManager.get(sp);
        if (!st.checkpointDim.isEmpty()) {
            ServerLevel l = sp.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(st.checkpointDim)));
            if (l != null) {
                BlockPos p = BlockPos.of(st.checkpointPos);
                l.getChunk(p);
                sp.teleportTo(l, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot(), 0f);
                sp.displayClientMessage(Component.literal("You wake at the last place that felt safe."), true);
                return;
            }
        }
        returnHome(sp, false);
    }

    // ------------------------------------------------------------------ building
    public static BlockPos ensureBuilt(ServerLevel l, String id) {
        if (isPhase1(id)) {
            var t = com.lastsignal.world.gen.Themes.get(id);
            int[] a = com.lastsignal.world.gen.Gen.arrival(t);
            return new BlockPos(a[0], a[1], a[2]);
        }
        DimState ds = state(l);
        if (ds.built) return BlockPos.of(ds.arrival);
        RandomSource r = RandomSource.create(l.getSeed() ^ id.hashCode());
        BlockPos arrival;
        switch (id) {
            case "long_hall" -> arrival = buildLongHall(l, ds, r);
            case "archive_below" -> arrival = buildArchive(l, ds);
            case "signal_void" -> arrival = buildVoid(l, ds);
            case "red_silence" -> arrival = buildSurfaceDim(l, 90, id, new String[]{"shrine", "watchtower", "shrine"}, "red_stone");
            case "memory_woods" -> arrival = buildSurfaceDim(l, -1, id, new String[]{"cabin", "camp", "watchtower", "cabin", "camp", "shrine"}, "quiet_soil");
            default -> arrival = buildSurfaceDim(l, -1, id, new String[]{"cabin", "ruined_village", "camp", "radio_tower"}, "quiet_soil");
        }
        ds.arrival = arrival.asLong();
        ds.built = true;
        ds.setDirty();
        LastSignalMod.LOGGER.info("Built dimension {} (arrival {})", id, arrival);
        return arrival;
    }

    private static void placeGate(ServerLevel l, BlockPos p, int target) {
        var g = ModBlocks.BY_ID.get("signal_gate").get();
        l.setBlock(p, g.defaultBlockState().setValue(DimensionGateBlock.TARGET, target), 3);
    }

    private static BlockPos buildSurfaceDim(ServerLevel l, int fixedY, String id, String[] structs, String platformBlock) {
        int y;
        if (fixedY >= 0) y = fixedY;
        else { l.getChunk(0, 0); y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0); }
        Block plat = ModBlocks.BY_ID.get(platformBlock).get();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            l.setBlock(new BlockPos(x, y - 1, z), plat.defaultBlockState(), 2);
            for (int h = 0; h < 4; h++) l.setBlock(new BlockPos(x, y + h, z), Blocks.AIR.defaultBlockState(), 2);
        }
        placeGate(l, new BlockPos(0, y, -3), 0);
        l.setBlock(new BlockPos(0, y, 3), ModBlocks.BY_ID.get("checkpoint_beacon").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(3, y, 0), ModBlocks.BY_ID.get("radio_receiver").get().defaultBlockState(), 2);
        // surrounding structures at fixed offsets (deterministic), each on the local surface
        int i = 0;
        for (String s : structs) {
            double a = i * Math.PI * 2 / structs.length;
            int sx = (int) (Math.cos(a) * (30 + 12 * (i % 3))), sz = (int) (Math.sin(a) * (30 + 12 * (i % 3)));
            l.getChunk(sx >> 4, sz >> 4);
            int sy = fixedY >= 0 ? fixedY : l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            if (fixedY >= 0) {
                for (int x = -2; x <= 12; x++) for (int z = -2; z <= 12; z++) {
                    l.getChunk((sx + x) >> 4, (sz + z) >> 4);
                    l.setBlock(new BlockPos(sx + x, sy - 1, sz + z), plat.defaultBlockState(), 2);
                }
            }
            Structures.place(l, s, new BlockPos(sx, sy, sz));
            i++;
        }
        return new BlockPos(0, y, 0);
    }

    // ---------------- The Long Hall: perfect maze (DFS) + loops, key, locked goal, false exits, secret rooms
    private static BlockPos buildLongHall(ServerLevel l, DimState ds, RandomSource r) {
        final int N = 15, P = 4, Y = 64, H = 4;           // N x N cells, period P blocks, floor Y
        Block wall = ModBlocks.BY_ID.get("hall_wallpaper").get(), tile = ModBlocks.BY_ID.get("hall_tile").get();
        boolean[][][] open = new boolean[N][N][4];          // E, S, W, N
        boolean[][] seen = new boolean[N][N];
        int[] dx = {1, 0, -1, 0}, dz = {0, 1, 0, -1};
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[]{0, 0}); seen[0][0] = true;
        int[][] depth = new int[N][N];
        while (!stack.isEmpty()) {
            int[] c = stack.get(stack.size() - 1);
            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(dirs, new java.util.Random(r.nextLong()));
            boolean moved = false;
            for (int d : dirs) {
                int nx = c[0] + dx[d], nz = c[1] + dz[d];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N || seen[nx][nz]) continue;
                open[c[0]][c[1]][d] = true; open[nx][nz][(d + 2) % 4] = true;
                seen[nx][nz] = true; depth[nx][nz] = stack.size();
                stack.add(new int[]{nx, nz}); moved = true; break;
            }
            if (!moved) stack.remove(stack.size() - 1);
        }
        // extra loops so it is not a single-solution tree (and so there are alternate paths)
        for (int i = 0; i < N * 2; i++) {
            int x = r.nextInt(N - 1), z = r.nextInt(N - 1), d = r.nextInt(2);
            int nx = x + dx[d], nz = z + dz[d];
            open[x][z][d] = true; open[nx][nz][(d + 2) % 4] = true;
        }
        int total = N * P + 1;
        l.getChunk(0, 0);
        for (int x = -1; x <= total; x++) for (int z = -1; z <= total; z++) {
            l.getChunk(x >> 4, z >> 4);
            l.setBlock(new BlockPos(x, Y - 1, z), tile.defaultBlockState(), 2);
            for (int h = 0; h <= H; h++) l.setBlock(new BlockPos(x, Y + h, z), wall.defaultBlockState(), 2);
            l.setBlock(new BlockPos(x, Y + H + 1, z), tile.defaultBlockState(), 2);
        }
        // carve cells (3x3 interior) and openings
        for (int cx = 0; cx < N; cx++) for (int cz = 0; cz < N; cz++) {
            int bx = cx * P + 1, bz = cz * P + 1;
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) for (int h = 0; h < H; h++)
                l.setBlock(new BlockPos(bx + x, Y + h, bz + z), Blocks.AIR.defaultBlockState(), 2);
            if (open[cx][cz][0]) for (int z = 0; z < 3; z++) for (int h = 0; h < H; h++) l.setBlock(new BlockPos(bx + 3, Y + h, bz + z), Blocks.AIR.defaultBlockState(), 2);
            if (open[cx][cz][1]) for (int x = 0; x < 3; x++) for (int h = 0; h < H; h++) l.setBlock(new BlockPos(bx + x, Y + h, bz + 3), Blocks.AIR.defaultBlockState(), 2);
            if ((cx + cz) % 3 == 0) l.setBlock(new BlockPos(bx + 1, Y + H, bz + 1), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
        }
        // start
        placeGate(l, new BlockPos(1, Y, 1), 0);
        l.setBlock(new BlockPos(3, Y, 1), ModBlocks.BY_ID.get("checkpoint_beacon").get().defaultBlockState(), 2);
        // goal = deepest cell; reached only through a sealed door needing the Long Hall Key
        int gx = 0, gz = 0, best = -1;
        for (int cx = 0; cx < N; cx++) for (int cz = 0; cz < N; cz++) if (depth[cx][cz] > best) { best = depth[cx][cz]; gx = cx; gz = cz; }
        int bx = gx * P + 1, bz = gz * P + 1;
        for (int d = 0; d < 4; d++) if (open[gx][gz][d]) { // seal every opening of the goal cell
            int sx = bx + (d == 0 ? 3 : d == 2 ? -1 : 1), sz = bz + (d == 1 ? 3 : d == 3 ? -1 : 1);
            for (int h = 0; h < 2; h++) l.setBlock(new BlockPos(sx, Y + h, sz), ModBlocks.BY_ID.get("sealed_door_block").get().defaultBlockState(), 2);
        }
        placeGate(l, new BlockPos(bx + 1, Y, bz + 1), 0);
        l.setBlock(new BlockPos(bx, Y, bz), Blocks.CHEST.defaultBlockState(), 2);
        if (l.getBlockEntity(new BlockPos(bx, Y, bz)) instanceof RandomizableContainerBlockEntity c)
            c.setLootTable(new ResourceLocation(LastSignalMod.MODID, "chests/endgame"), r.nextLong());
        ds.extra = new BlockPos(bx + 1, Y, bz + 1).asLong();
        // key: in a dead end that is NOT the goal and is reachable (every cell is reachable in a connected maze)
        List<int[]> deadEnds = new ArrayList<>();
        for (int cx = 0; cx < N; cx++) for (int cz = 0; cz < N; cz++) {
            int n = 0; for (int d = 0; d < 4; d++) if (open[cx][cz][d]) n++;
            if (n == 1 && !(cx == gx && cz == gz) && !(cx == 0 && cz == 0)) deadEnds.add(new int[]{cx, cz});
        }
        if (!deadEnds.isEmpty()) {
            int[] k = deadEnds.get(r.nextInt(deadEnds.size()));
            BlockPos kp = new BlockPos(k[0] * P + 2, Y, k[1] * P + 2);
            l.setBlock(kp, Blocks.CHEST.defaultBlockState(), 2);
            if (l.getBlockEntity(kp) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity cbe)
                cbe.setItem(13, new ItemStack(com.lastsignal.item.ModItems.ITEMS_BY_ID.get("long_hall_key").get()));
            deadEnds.remove(k);
        }
        // false exits: fake doors with a note in other dead ends
        for (int[] d : deadEnds) {
            if (r.nextInt(3) != 0) continue;
            int px = d[0] * P + 2, pz = d[1] * P + 2;
            l.setBlock(new BlockPos(px, Y, pz), ModBlocks.BY_ID.get("lore_desk").get().defaultBlockState(), 2);
        }
        // doorman guards the goal door
        for (int d = 0; d < 4; d++) if (open[gx][gz][d]) {
            int sx = bx + (d == 0 ? 5 : d == 2 ? -3 : 1), sz = bz + (d == 1 ? 5 : d == 3 ? -3 : 1);
            HorrorSpawner.spawnAt(l, "doorman", new BlockPos(sx, Y, sz), null);
            break;
        }
        return new BlockPos(2, Y, 2);
    }

    // ---------------- The Archive Below: chain of rooms: entry -> storage(power cell) -> observation(key) -> containment (locked) -> terminal room
    private static BlockPos buildArchive(ServerLevel l, DimState ds) {
        final int Y = 64;
        Block wall = Blocks.DEEPSLATE_BRICKS, floor = Blocks.POLISHED_DEEPSLATE;
        int[] xs = {0, 16, 32, 48, 64};
        for (int i = 0; i < xs.length; i++) {
            int x0 = xs[i];
            for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) {
                l.getChunk((x0 + x) >> 4, z >> 4);
                l.setBlock(new BlockPos(x0 + x, Y - 1, z), floor.defaultBlockState(), 2);
                for (int h = 0; h <= 5; h++) {
                    boolean edge = x == 0 || z == 0 || x == 12 || z == 12 || h == 5;
                    l.setBlock(new BlockPos(x0 + x, Y + h, z), edge ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
            if (i > 0) { // corridor from previous room
                for (int x = xs[i - 1] + 12; x <= x0; x++) for (int z = 5; z <= 7; z++) {
                    l.setBlock(new BlockPos(x, Y - 1, z), floor.defaultBlockState(), 2);
                    for (int h = 0; h <= 3; h++) l.setBlock(new BlockPos(x, Y + h, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            l.setBlock(new BlockPos(x0 + 6, Y + 4, 6), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
        }
        // room 0: entry
        placeGate(l, new BlockPos(2, Y, 2), 0);
        l.setBlock(new BlockPos(4, Y, 2), ModBlocks.BY_ID.get("checkpoint_beacon").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(2, Y, 10), ModBlocks.BY_ID.get("radio_receiver").get().defaultBlockState(), 2);
        // room 1: storage - power cell
        chestWith(l, new BlockPos(xs[1] + 10, Y, 10), "power_cell");
        for (int z = 2; z <= 10; z += 2) l.setBlock(new BlockPos(xs[1] + 2, Y, z), Blocks.BOOKSHELF.defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[1] + 6, Y, 2), ModBlocks.BY_ID.get("abandoned_machinery").get().defaultBlockState(), 2);
        // room 2: observation - archive key
        chestWith(l, new BlockPos(xs[2] + 10, Y, 2), "archive_key");
        l.setBlock(new BlockPos(xs[2] + 6, Y, 10), ModBlocks.BY_ID.get("memory_log").get().defaultBlockState(), 2);
        for (int x = 3; x <= 9; x++) l.setBlock(new BlockPos(xs[2] + x, Y + 1, 0), Blocks.GLASS.defaultBlockState(), 2);
        // door into containment (room 3): sealed door in corridor
        for (int h = 0; h < 2; h++) for (int z = 5; z <= 7; z++) l.setBlock(new BlockPos(xs[3] - 2, Y + h, z), ModBlocks.BY_ID.get("sealed_door_block").get().defaultBlockState(), 2);
        // room 3: containment - crystal and lore, enemies guard
        l.setBlock(new BlockPos(xs[3] + 6, Y, 6), ModBlocks.BY_ID.get("signal_crystal").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[3] + 2, Y, 2), ModBlocks.BY_ID.get("corrupted_relay").get().defaultBlockState(), 2);
        chestWith(l, new BlockPos(xs[3] + 10, Y, 10), "archive");
        // room 4: terminal room, powers need cell; leads to evidence
        l.setBlock(new BlockPos(xs[4] + 6, Y, 6), ModBlocks.BY_ID.get("archive_terminal").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[4] + 2, Y, 2), ModBlocks.BY_ID.get("puzzle_block_a").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[4] + 3, Y, 2), ModBlocks.BY_ID.get("puzzle_block_b").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[4] + 4, Y, 2), ModBlocks.BY_ID.get("puzzle_block_b").get().defaultBlockState(), 2);
        l.setBlock(new BlockPos(xs[4] + 5, Y, 2), ModBlocks.BY_ID.get("puzzle_block_a").get().defaultBlockState(), 2);
        placeGate(l, new BlockPos(xs[4] + 10, Y, 10), 0);
        ds.extra = new BlockPos(xs[3] + 6, Y, 6).asLong();
        HorrorSpawner.spawnAt(l, "archivist", new BlockPos(xs[2] + 4, Y, 6), null);
        HorrorSpawner.spawnAt(l, "crawler", new BlockPos(xs[3] + 8, Y, 8), null);
        return new BlockPos(3, Y, 3);
    }

    private static void chestWith(ServerLevel l, BlockPos p, String itemId) {
        l.setBlock(p, Blocks.CHEST.defaultBlockState(), 2);
        if (l.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c) {
            if (itemId.equals("archive")) c.setLootTable(new ResourceLocation(LastSignalMod.MODID, "chests/archive"), l.random.nextLong());
            else c.setItem(13, new ItemStack(com.lastsignal.item.ModItems.ITEMS_BY_ID.get(itemId).get()));
        }
    }

    // ---------------- The Signal Void: platform + boss arena
    private static BlockPos buildVoid(ServerLevel l, DimState ds) {
        final int Y = 90;
        Block glass = ModBlocks.BY_ID.get("void_glass").get(), stone = ModBlocks.BY_ID.get("red_stone").get();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            l.getChunk(x >> 4, z >> 4);
            l.setBlock(new BlockPos(x, Y - 1, z), glass.defaultBlockState(), 2);
            for (int h = 0; h < 4; h++) l.setBlock(new BlockPos(x, Y + h, z), Blocks.AIR.defaultBlockState(), 2);
        }
        placeGate(l, new BlockPos(0, Y, -6), 0);
        l.setBlock(new BlockPos(0, Y, 6), ModBlocks.BY_ID.get("checkpoint_beacon").get().defaultBlockState(), 2);
        // bridge to the arena (suspended geometry)
        for (int x = 9; x <= 40; x++) for (int z = -1; z <= 1; z++) {
            l.getChunk(x >> 4, z >> 4);
            l.setBlock(new BlockPos(x, Y - 1, z), stone.defaultBlockState(), 2);
        }
        for (int x = 41; x <= 72; x++) for (int z = -15; z <= 15; z++) {
            l.getChunk(x >> 4, z >> 4);
            if ((x - 56) * (x - 56) + z * z <= 225) l.setBlock(new BlockPos(x, Y - 1, z), stone.defaultBlockState(), 2);
        }
        for (int[] c : new int[][]{{50, -8}, {50, 8}, {62, -8}, {62, 8}})
            for (int h = 0; h < 4; h++) l.setBlock(new BlockPos(c[0], Y + h, c[1]), ModBlocks.BY_ID.get("signal_crystal").get().defaultBlockState(), 2);
        ds.extra = new BlockPos(56, Y, 0).asLong();
        return new BlockPos(0, Y, 0);
    }

    /** Spawn the Signal Core at the arena (called by the final-transmission event or /lastsignal spawn). */
    public static boolean spawnCore(ServerLevel voidLevel) {
        DimState ds = state(voidLevel);
        if (!ds.built) ensureBuilt(voidLevel, "signal_void");
        BlockPos arena = BlockPos.of(ds.extra);
        if (!voidLevel.getEntitiesOfClass(HorrorEntity.class, new net.minecraft.world.phys.AABB(arena).inflate(48), h -> h.profileId.equals("signal_core")).isEmpty()) return false;
        voidLevel.getChunk(arena);
        return HorrorSpawner.spawnAt(voidLevel, "signal_core", arena, null) != null;
    }

    private Dimensions() {}
}
