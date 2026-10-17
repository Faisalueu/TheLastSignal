package com.lastsignal.world;

import com.lastsignal.LastSignalMod;
import com.lastsignal.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 20 code-built structure templates. Placement is server-driven (events / commands / dimension builders),
 * never touches an area containing player-made blocks (see {@link #isClear}), and every structure is
 * recorded in {@link StructureData} so /lastsignal locate and story events can find it.
 */
public final class Structures {
    public static final List<String> IDS = List.of(
            "cabin", "research_station", "radio_tower", "ruined_village", "sealed_entrance", "shrine", "watchtower",
            "camp", "hidden_basement", "underground_archive", "train_platform", "cemetery", "empty_hall", "strange_well",
            "bunker", "containment_chamber", "broken_bridge", "ancient_door", "relay_station", "endgame_secret");

    /** Structures that live underground: origin y is used as the room floor, not the surface. */
    public static boolean underground(String id) {
        return id.equals("hidden_basement") || id.equals("underground_archive") || id.equals("bunker")
                || id.equals("containment_chamber") || id.equals("endgame_secret");
    }

    // ------------------------------------------------------------------------------------ builder
    static final class B {
        final ServerLevel l; final BlockPos o; final RandomSource r;
        B(ServerLevel l, BlockPos o) { this.l = l; this.o = o; this.r = l.random; }

        void set(int x, int y, int z, BlockState s) { l.setBlock(o.offset(x, y, z), s, 2); }
        void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, b);
        }
        void air(int x1, int y1, int z1, int x2, int y2, int z2) { fill(x1, y1, z1, x2, y2, z2, Blocks.AIR); }
        /** Walled box with solid floor/roof and air inside. */
        void room(int x1, int z1, int x2, int z2, int h, Block wall, Block floor, Block roof) {
            fill(x1, 0, z1, x2, 0, z2, floor);
            fill(x1, 1, z1, x2, h, z2, wall);
            air(x1 + 1, 1, z1 + 1, x2 - 1, h, z2 - 1);
            fill(x1, h + 1, z1, x2, h + 1, z2, roof);
        }
        void foundation(int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
                for (int y = -1; y >= -6; y--) {
                    BlockPos p = o.offset(x, y, z);
                    if (l.getBlockState(p).isAir() || !l.getFluidState(p).isEmpty()) l.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
                    else break;
                }
        }
        void ruin(int x1, int y1, int z1, int x2, int y2, int z2, double chance) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
                if (r.nextDouble() < chance) set(x, y, z, Blocks.AIR);
        }
        void chest(int x, int y, int z, String tier) {
            set(x, y, z, Blocks.CHEST);
            BlockPos p = o.offset(x, y, z);
            if (l.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c)
                c.setLootTable(new ResourceLocation(LastSignalMod.MODID, "chests/" + tier), r.nextLong());
        }
        void ladder(int x, int y1, int y2, int z, Direction facing) {
            for (int y = y1; y <= y2; y++) set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }
        Block m(String id) { return ModBlocks.BY_ID.get(id).get(); }
    }

    // ------------------------------------------------------------------------------------ safety
    /** True when no player-made blocks are within r of the centre (sampled), so we never overwrite a base. */
    public static boolean isClear(ServerLevel l, BlockPos c, int r) {
        for (int x = -r; x <= r; x += 2)
            for (int z = -r; z <= r; z += 2)
                for (int y = -3; y <= 6; y += 3) {
                    BlockState s = l.getBlockState(c.offset(x, y, z));
                    if (s.is(BlockTags.PLANKS) || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)
                            || s.is(Blocks.CHEST) || s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.FURNACE) || s.is(Blocks.TORCH)
                            || s.is(Blocks.WALL_TORCH) || s.is(Blocks.BARREL) || s.is(Blocks.ENDER_CHEST) || s.is(Blocks.LANTERN)) return false;
                }
        return true;
    }

    public static boolean place(ServerLevel l, String id, BlockPos o) {
        if (!IDS.contains(id)) return false;
        if (!isClear(l, o, 10)) return false;
        B b = new B(l, o);
        switch (id) {
            case "cabin" -> {
                b.foundation(0, 0, 6, 5);
                b.room(0, 0, 6, 5, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB);
                b.air(3, 1, 0, 3, 2, 0); b.set(2, 2, 5, Blocks.GLASS_PANE); b.set(4, 2, 5, Blocks.AIR);
                b.set(1, 1, 1, b.m("lore_desk")); b.chest(5, 1, 1, "common"); b.set(2, 3, 2, Blocks.COBWEB);
            }
            case "research_station" -> {
                b.foundation(0, 0, 12, 8);
                b.room(0, 0, 12, 8, 4, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_SLAB);
                b.air(6, 1, 0, 6, 2, 0); b.set(6, 1, 0, b.m("sealed_door_block")); b.set(6, 2, 0, b.m("sealed_door_block"));
                b.set(2, 1, 2, b.m("archive_terminal")); b.set(2, 1, 6, b.m("radio_receiver")); b.set(10, 1, 2, b.m("abandoned_machinery"));
                b.set(10, 1, 6, b.m("memory_log")); b.chest(11, 1, 4, "archive"); b.set(6, 4, 4, Blocks.REDSTONE_LAMP);
            }
            case "radio_tower" -> {
                b.foundation(0, 0, 2, 2);
                for (int y = 1; y <= 18; y++) { b.set(0, y, 0, Blocks.IRON_BARS); b.set(2, y, 0, Blocks.IRON_BARS); b.set(0, y, 2, Blocks.IRON_BARS); b.set(2, y, 2, Blocks.IRON_BARS); }
                b.ladder(1, 1, 18, 0, Direction.SOUTH);
                b.fill(-1, 19, -1, 3, 19, 3, Blocks.SPRUCE_SLAB); b.set(1, 20, 1, b.m("radio_receiver")); b.set(1, 21, 1, Blocks.LIGHTNING_ROD);
                b.set(1, 1, 1, b.m("corrupted_relay"));
            }
            case "ruined_village" -> {
                for (int i = 0; i < 3; i++) {
                    int ox = i * 9;
                    b.foundation(ox, 0, ox + 5, 5);
                    b.room(ox, 0, ox + 5, 5, 3, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_SLAB);
                    b.air(ox + 2, 1, 0, ox + 2, 2, 0);
                    b.ruin(ox, 1, 0, ox + 5, 4, 5, 0.25);
                }
                b.set(4, 1, 8, b.m("memory_log")); b.chest(13, 1, 2, "common");
            }
            case "sealed_entrance" -> {
                b.foundation(0, 0, 8, 6);
                b.fill(0, 1, 0, 8, 5, 0, Blocks.MOSSY_STONE_BRICKS);
                b.air(3, 1, 0, 5, 3, 0); b.fill(3, 1, 0, 5, 3, 0, b.m("sealed_door_block"));
                b.room(2, 1, 6, 6, 3, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS);
                b.chest(4, 1, 5, "archive");
            }
            case "shrine" -> {
                b.foundation(0, 0, 6, 6);
                b.fill(0, 0, 0, 6, 0, 6, Blocks.POLISHED_DEEPSLATE);
                for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) for (int y = 1; y <= 3; y++) b.set(c[0], y, c[1], b.m("strange_pillar"));
                b.set(3, 1, 3, b.m("signal_crystal")); b.set(3, 1, 1, Blocks.SOUL_LANTERN); b.set(3, 1, 5, Blocks.SOUL_LANTERN);
            }
            case "watchtower" -> {
                b.foundation(0, 0, 4, 4);
                for (int y = 1; y <= 8; y++) for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.set(c[0], y, c[1], Blocks.COBBLESTONE_WALL);
                b.ladder(2, 1, 8, 0, Direction.SOUTH);
                b.fill(0, 9, 0, 4, 9, 4, Blocks.SPRUCE_PLANKS); b.chest(3, 10, 3, "common"); b.set(1, 10, 1, b.m("lore_desk"));
            }
            case "camp" -> {
                b.set(2, 0, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                b.set(0, 0, 0, Blocks.GRAY_CARPET); b.set(4, 0, 0, Blocks.BLACK_CARPET); b.set(0, 0, 4, Blocks.RED_CARPET);
                b.chest(4, 0, 4, "common"); b.set(2, 0, 4, b.m("lore_desk"));
            }
            case "hidden_basement" -> {
                b.set(0, 0, 0, Blocks.OAK_TRAPDOOR); b.fill(-1, -1, -1, 1, -1, 1, Blocks.AIR);
                b.fill(-3, -6, -3, 3, -3, 3, Blocks.STONE_BRICKS); b.air(-2, -5, -2, 2, -4, 2);
                b.air(0, -3, 0, 0, -1, 0); b.ladder(0, -5, -1, 0, Direction.SOUTH);
                b.set(-2, -5, -2, b.m("memory_log")); b.chest(2, -5, 2, "common"); b.set(0, -4, -2, Blocks.SOUL_LANTERN);
            }
            case "underground_archive" -> {
                b.fill(0, -7, 0, 10, 0, 10, Blocks.DEEPSLATE_BRICKS); b.air(1, -6, 1, 9, -2, 9);
                for (int x = 1; x <= 9; x += 2) { b.fill(x, -6, 1, x, -4, 3, Blocks.BOOKSHELF); b.fill(x, -6, 7, x, -4, 9, Blocks.BOOKSHELF); }
                b.set(5, -6, 5, b.m("archive_terminal")); b.set(5, -6, 0, b.m("sealed_door_block")); b.set(5, -5, 0, b.m("sealed_door_block"));
                b.chest(9, -6, 5, "archive"); b.set(1, -6, 5, b.m("puzzle_block_a")); b.set(2, -6, 5, b.m("puzzle_block_b")); b.set(3, -6, 5, b.m("puzzle_block_b")); b.set(4, -6, 5, b.m("puzzle_block_a"));
            }
            case "train_platform" -> {
                b.foundation(0, 0, 14, 4);
                b.fill(0, 0, 0, 14, 0, 2, Blocks.SMOOTH_STONE_SLAB); b.fill(0, 0, 3, 14, 0, 3, Blocks.GRAVEL);
                for (int x = 0; x <= 14; x++) b.set(x, 0, 4, Blocks.RAIL);
                b.set(2, 1, 0, Blocks.SPRUCE_STAIRS); b.set(12, 1, 0, Blocks.SPRUCE_STAIRS); b.set(7, 1, 1, b.m("radio_receiver")); b.set(7, 1, 0, Blocks.LANTERN);
            }
            case "cemetery" -> {
                b.foundation(0, 0, 8, 8);
                for (int i = 0; i <= 8; i++) { b.set(i, 1, 0, Blocks.IRON_BARS); b.set(i, 1, 8, Blocks.IRON_BARS); b.set(0, 1, i, Blocks.IRON_BARS); b.set(8, 1, i, Blocks.IRON_BARS); }
                b.air(4, 1, 0, 4, 1, 0);
                for (int i = 0; i < 6; i++) b.set(1 + b.r.nextInt(7), 1, 1 + b.r.nextInt(7), Blocks.COBBLED_DEEPSLATE_SLAB);
                b.set(4, 1, 4, b.m("memory_log"));
            }
            case "empty_hall" -> {
                b.foundation(0, 0, 4, 16);
                b.room(0, 0, 4, 16, 3, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
                b.air(2, 1, 0, 2, 2, 0); b.set(2, 3, 5, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                b.set(2, 1, 15, b.m("radio_receiver"));
            }
            case "strange_well" -> {
                b.foundation(0, 0, 4, 4);
                b.fill(0, 0, 0, 4, 1, 4, Blocks.COBBLESTONE); b.air(1, 1, 1, 3, 1, 3);
                b.fill(2, -10, 2, 2, 0, 2, Blocks.AIR); b.set(2, -11, 2, b.m("void_glass")); b.set(2, -5, 2, b.m("memory_log"));
            }
            case "bunker" -> {
                b.fill(0, -6, 0, 8, 0, 8, Blocks.GRAY_CONCRETE); b.air(1, -5, 1, 7, -2, 7);
                b.set(4, -5, 0, b.m("sealed_door_block")); b.set(4, -4, 0, b.m("sealed_door_block"));
                b.set(1, -5, 1, Blocks.RED_BED); b.set(6, -5, 6, b.m("archive_terminal")); b.set(1, -5, 6, b.m("checkpoint_beacon")); b.chest(6, -5, 1, "common");
            }
            case "containment_chamber" -> {
                b.fill(0, -8, 0, 8, 0, 8, b.m("void_glass")); b.air(1, -7, 1, 7, -2, 7);
                b.set(4, -7, 4, b.m("signal_crystal"));
                for (int[] c : new int[][]{{3, 3}, {5, 3}, {3, 5}, {5, 5}}) b.set(c[0], -7, c[1], Blocks.IRON_BARS);
                b.set(1, -7, 1, b.m("corrupted_relay")); b.set(7, -7, 7, b.m("corrupted_relay")); b.chest(7, -7, 1, "archive");
            }
            case "broken_bridge" -> {
                for (int x = 0; x <= 14; x++) {
                    if (x == 6 || x == 7 || x == 10) continue;
                    for (int z = 0; z <= 2; z++) b.set(x, 4, z, Blocks.SPRUCE_PLANKS);
                    b.set(x, 5, 0, Blocks.SPRUCE_FENCE); b.set(x, 5, 2, Blocks.SPRUCE_FENCE);
                }
                for (int y = 0; y <= 3; y++) { b.set(0, y, 1, Blocks.SPRUCE_LOG); b.set(14, y, 1, Blocks.SPRUCE_LOG); b.set(4, y, 1, Blocks.SPRUCE_LOG); }
                b.set(13, 5, 1, b.m("lore_desk"));
            }
            case "ancient_door" -> {
                b.foundation(0, 0, 6, 2);
                b.fill(0, 1, 0, 6, 7, 0, Blocks.DEEPSLATE_BRICKS); b.air(2, 1, 0, 4, 5, 0);
                b.fill(2, 1, 0, 4, 5, 0, b.m("sealed_door_block")); b.set(3, 7, 0, b.m("strange_pillar"));
            }
            case "relay_station" -> {
                b.foundation(0, 0, 4, 4);
                b.room(0, 0, 4, 4, 3, Blocks.WHITE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE);
                b.air(2, 1, 0, 2, 2, 0); b.set(2, 1, 2, b.m("radio_receiver")); b.set(2, 5, 2, b.m("corrupted_relay"));
                b.set(0, 5, 0, Blocks.LIGHTNING_ROD); b.set(4, 5, 4, Blocks.LIGHTNING_ROD); b.chest(3, 1, 3, "common");
            }
            case "endgame_secret" -> {
                b.fill(0, -8, 0, 14, 0, 14, b.m("red_stone")); b.air(1, -7, 1, 13, -2, 13);
                for (int[] c : new int[][]{{3, 3}, {11, 3}, {3, 11}, {11, 11}}) b.set(c[0], -7, c[1], b.m("signal_crystal"));
                b.set(7, -7, 7, b.m("checkpoint_beacon")); b.set(7, -7, 0, b.m("sealed_door_block")); b.set(7, -6, 0, b.m("sealed_door_block"));
                b.set(7, -7, 11, b.m("memory_log")); b.chest(7, -7, 13, "endgame");
            }
            default -> { return false; }
        }
        l.getDataStorage().computeIfAbsent(StructureData::load, StructureData::new, StructureData.NAME).add(id, o);
        return true;
    }

    public static StructureData data(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(StructureData::load, StructureData::new, StructureData.NAME);
    }

    private Structures() {}
}
