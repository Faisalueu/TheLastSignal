package com.lastsignal.world;

import com.lastsignal.world.Structures.B;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Haunted houses HOUSE_01..HOUSE_30. Every house has a unique id, a unique design and Sign-only story text in English.
 * Only the ids in {@link #BUILT} have a design yet; the rest are reserved and refuse to place (never a placeholder house).
 * Placement is server-driven, never overwrites player-made blocks, keeps a minimum spacing from every other recorded
 * structure and records the house in {@link StructureData} so /lastsignal locate works.
 */
public final class Houses {
    public static final List<String> IDS = new ArrayList<>();
    static { for (int i = 1; i <= 30; i++) IDS.add(String.format("HOUSE_%02d", i)); }

    public static final List<String> BUILT = IDS;

    /** Footprint (x size, z size) used for the terrain check. */
    private static final int W = 13, D = 13;
    public static final int MIN_HOUSE_SPACING = 160;
    public static final int MIN_ANY_SPACING = 40;
    public static final int MAX_LINE = 15;

    public static String title(String id) {
        String more = HousesB.title(id);
        if (more != null) return more;
        return switch (id) {
            case "HOUSE_01" -> "The Silent House";
            case "HOUSE_02" -> "The Watching House";
            case "HOUSE_03" -> "The Upside-Down House";
            case "HOUSE_04" -> "The Endless Hall";
            case "HOUSE_05" -> "The Buried House";
            default -> "Reserved";
        };
    }

    public static boolean isHouse(String id) { return IDS.contains(id); }
    public static boolean isBuilt(String id) { return BUILT.contains(id); }

    // ------------------------------------------------------------------------------------ signs
    /** Word-wrap to lines of at most MAX_LINE characters. Words longer than that stay on their own line. */
    public static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            if (w.isEmpty()) continue;
            if (cur.length() > 0 && cur.length() + 1 + w.length() > MAX_LINE) { lines.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** All sign texts per house, used by the builders and by the CI check. */
    public static List<String> texts(String id) {
        return switch (id) {
            case "HOUSE_01" -> List.of(
                    "DO NOT KNOCK. It listens for knocks.",
                    "feeLoLzy wrote here. We stopped talking on day three.",
                    "Write, do not speak. Whatever answers, answers in ink.",
                    "The radio is off. It was never turned off by us.");
            case "HOUSE_02" -> List.of(
                    "Count the windows. There is one more each time.",
                    "majoo03 counted nineteen. The house had eighteen.",
                    "Do not look back at it from the road.",
                    "Every chair faces the wall. Do not be seen.");
            case "HOUSE_03" -> List.of(
                    "The door was on the ground. Now it is not.",
                    "aloneo3 said the floor was above him. He was right.",
                    "Smoke goes down here. Follow it.",
                    "The cellar is under the chimney, not the house.");
            case "HOUSE_04" -> List.of(
                    "Twelve steps in. Twelve steps back. Count again.",
                    "HidontmindMe33 walked this hall. The end never came.",
                    "Every door leads back to this hall.",
                    "Stop walking. Turn around. It is the same hall.");
            case "HOUSE_05" -> List.of(
                    "Only the chimney showed. It was still breathing.",
                    "0hunl0 was here. The ladder is warm.",
                    "Houses do not bury themselves.",
                    "GOLD3002 left the last page. It says: do not dig further.");
            default -> HousesB.texts(id);
        };
    }

    static void wallSign(B b, int x, int y, int z, Direction facing, String text) {
        BlockState st = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        b.set(x, y, z, st);
        fillSign(b.l, b.o.offset(x, y, z), text);
    }

    static void standSign(B b, int x, int y, int z, int rot, String text) {
        b.set(x, y, z, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot));
        fillSign(b.l, b.o.offset(x, y, z), text);
    }

    public static void fillSign(ServerLevel l, BlockPos p, String text) {
        if (!(l.getBlockEntity(p) instanceof SignBlockEntity s)) return;
        List<String> lines = wrap(text);
        net.minecraft.world.level.block.entity.SignText t = new net.minecraft.world.level.block.entity.SignText();
        for (int i = 0; i < Math.min(4, lines.size()); i++) t = t.setMessage(i, Component.literal(lines.get(i)));
        t = t.setColor(DyeColor.WHITE);
        s.setText(t, true);
        s.setWaxed(true);
        s.setChanged();
        l.sendBlockUpdated(p, s.getBlockState(), s.getBlockState(), 3);
    }

    // ------------------------------------------------------------------------------------ placement safety
    /** Terrain/biome/spacing suitability for a house whose min corner would be o (o.y = chosen floor level). */
    public static boolean suitable(ServerLevel l, BlockPos o, String id) {
        if (!l.hasChunksAt(o.offset(-1, 0, -1), o.offset(W + 1, 0, D + 1))) return false;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = 0; x < W; x += 4) for (int z = 0; z < D; z += 4) {
            BlockPos p = o.offset(x, 0, z);
            int y = l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ());
            if (!l.getFluidState(new BlockPos(p.getX(), y - 1, p.getZ())).isEmpty()) return false;
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return false;
        if (l.getBiome(o).is(BiomeTags.IS_OCEAN) || l.getBiome(o).is(BiomeTags.IS_RIVER)) return false;
        if (!Structures.isClear(l, o.offset(W / 2, 0, D / 2), 10)) return false;
        StructureData d = Structures.data(l);
        for (StructureData.Entry e : d.entries) {
            double dist = Math.sqrt(e.blockPos().distSqr(o));
            if (dist < MIN_ANY_SPACING) return false;
            if (e.type().startsWith("HOUSE_") && dist < MIN_HOUSE_SPACING) return false;
            if (e.type().equals(id)) return false; // each house exists once per dimension
        }
        return true;
    }

    /** Safe placement entry point (events, commands). Returns false when the spot is unsuitable or the house is reserved. */
    public static boolean place(ServerLevel l, String id, BlockPos groundAround) {
        if (!isBuilt(id)) return false;
        int y = Integer.MIN_VALUE;
        BlockPos c = groundAround.offset(-W / 2, 0, -D / 2);
        for (int x = 0; x < W; x += 4) for (int z = 0; z < D; z += 4)
            y = Math.max(y, l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX() + x, c.getZ() + z));
        BlockPos o = new BlockPos(c.getX(), y, c.getZ());
        if (!suitable(l, o, id)) return false;
        build(l, id, o);
        return true;
    }

    /** Builds without the suitability checks (used by CI on controlled ground). Records the structure. */
    public static void build(ServerLevel l, String id, BlockPos o) {
        B b = new B(l, o);
        boolean buried = id.equals("HOUSE_05");
        if (!buried) clearVolume(l, o);
        switch (id) {
            case "HOUSE_01" -> silent(b, texts(id));
            case "HOUSE_02" -> watching(b, texts(id));
            case "HOUSE_03" -> upsideDown(b, texts(id));
            case "HOUSE_04" -> endlessHall(b, texts(id));
            case "HOUSE_05" -> buried(b, texts(id));
            default -> { if (!HousesB.build(b, id, texts(id))) return; }
        }
        Structures.data(l).add(id, o);
    }

    /** Removes natural clutter (leaves, logs, plants, snow) inside the building volume. Player blocks were excluded by isClear. */
    private static void clearVolume(ServerLevel l, BlockPos o) {
        for (int x = -1; x <= W; x++) for (int z = -1; z <= D; z++) for (int y = 0; y <= 18; y++) {
            BlockPos p = o.offset(x, y, z);
            BlockState s = l.getBlockState(p);
            if (!s.isAir() && (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.getBlock() instanceof BushBlock || s.is(BlockTags.SNOW) || s.is(BlockTags.FLOWERS)))
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------------------------ HOUSE_01 silent
    /** Pale, windowless, carpet-muffled house: nothing in it makes a noise, and the only door is closed. */
    private static void silent(B b, List<String> t) {
        b.foundation(0, 0, 12, 12);
        b.room(1, 1, 11, 11, 4, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_SLAB);
        b.fill(2, 1, 2, 10, 1, 10, Blocks.WHITE_CARPET);
        b.air(6, 1, 1, 6, 2, 1);
        door(b, 6, 1, 1, Blocks.BIRCH_DOOR);
        // furniture: a table and two chairs set for a conversation nobody has
        b.set(5, 1, 6, Blocks.QUARTZ_SLAB); b.set(7, 1, 6, Blocks.QUARTZ_SLAB); b.set(6, 1, 6, Blocks.QUARTZ_SLAB);
        b.set(5, 1, 5, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        b.set(7, 1, 7, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        b.set(6, 2, 6, Blocks.WHITE_CANDLE);
        b.set(10, 1, 10, b.m("radio_receiver"));
        b.set(2, 1, 10, b.m("lore_desk"));
        b.chest(2, 1, 2, "common");
        wallSign(b, 5, 2, 0, Direction.NORTH, t.get(0));          // outside, left of the door
        wallSign(b, 3, 3, 2, Direction.SOUTH, t.get(1));
        wallSign(b, 9, 3, 2, Direction.SOUTH, t.get(2));
        wallSign(b, 10, 3, 10, Direction.NORTH, t.get(3));
        b.set(6, 0, 0, Blocks.SMOOTH_QUARTZ);
    }

    // ------------------------------------------------------------------------------------ HOUSE_02 watching
    private static void watching(B b, List<String> t) {
        b.foundation(0, 0, 12, 12);
        b.room(1, 1, 11, 11, 4, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_SLAB);
        // 18 windows (2 per face position) - the sign says there is always one more
        for (int i = 3; i <= 9; i += 3) {
            for (int[] f : new int[][]{{i, 1}, {i, 11}, {1, i}, {11, i}}) { b.set(f[0], 2, f[1], Blocks.BLACK_STAINED_GLASS_PANE); }
        }
        for (int i = 3; i <= 9; i += 3) { b.set(i, 3, 1, Blocks.JACK_O_LANTERN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH)); }
        b.air(6, 1, 1, 6, 2, 1);
        door(b, 6, 1, 1, Blocks.DARK_OAK_DOOR);
        // every chair turns its back to the room
        for (int i = 3; i <= 9; i += 3) {
            b.set(i, 1, 2, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM));
            b.set(i, 1, 10, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        }
        b.set(6, 1, 6, b.m("memory_log"));
        b.set(10, 1, 6, b.m("lore_desk"));
        b.chest(2, 1, 10, "common");
        wallSign(b, 4, 2, 0, Direction.NORTH, t.get(0));
        wallSign(b, 3, 3, 2, Direction.SOUTH, t.get(1));
        wallSign(b, 9, 3, 2, Direction.SOUTH, t.get(2));
        wallSign(b, 6, 3, 10, Direction.NORTH, t.get(3));
        b.set(6, 0, 0, Blocks.DEEPSLATE_TILES);
    }

    // ------------------------------------------------------------------------------------ HOUSE_03 upside down
    /** The whole interior is inverted: furniture hangs from the ceiling, the chimney points into the ground. */
    private static void upsideDown(B b, List<String> t) {
        b.foundation(0, 0, 12, 12);
        b.room(1, 1, 11, 11, 5, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB);
        b.air(4, 1, 1, 4, 2, 1);
        door(b, 4, 1, 1, Blocks.SPRUCE_DOOR);
        // ceiling furniture (inverted stairs and slabs)
        for (int x = 4; x <= 8; x++) b.set(x, 5, 6, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        b.set(3, 5, 6, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.EAST));
        b.set(9, 5, 6, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.WEST));
        b.set(6, 4, 6, Blocks.CHAIN.defaultBlockState());
        b.set(6, 3, 6, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(2, 1, 10, b.m("lore_desk"));
        // chimney pointing down: a ladder shaft in the corner of the floor leads to a cellar chest
        b.fill(8, 6, 8, 8, 8, 8, Blocks.BRICKS);          // stump above the roof
        b.air(7, -5, 5, 9, -4, 8);                        // cellar
        b.fill(7, -6, 5, 9, -6, 8, Blocks.BRICKS);
        for (int y = -3; y <= 0; y++) { b.set(7, y, 8, Blocks.BRICKS); b.set(9, y, 8, Blocks.BRICKS); b.set(8, y, 7, Blocks.BRICKS); }
        for (int y = -5; y <= 0; y++) {
            b.set(8, y, 9, Blocks.BRICKS);
            b.set(8, y, 8, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.chest(8, -5, 6, "common");
        wallSign(b, 3, 2, 0, Direction.NORTH, t.get(0));
        wallSign(b, 3, 3, 2, Direction.SOUTH, t.get(1));
        wallSign(b, 9, 3, 2, Direction.SOUTH, t.get(2));
        wallSign(b, 6, 3, 10, Direction.NORTH, t.get(3));
        b.set(6, 0, 0, Blocks.SPRUCE_PLANKS);
    }

    // ------------------------------------------------------------------------------------ HOUSE_04 endless hall
    private static void endlessHall(B b, List<String> t) {
        b.foundation(0, 0, 4, 12);
        // a house whose only room is a long hall; the doors lining it are only door-shaped panels
        b.room(0, 0, 4, 12, 4, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_SLAB);
        b.air(2, 1, 0, 2, 2, 0);
        door(b, 2, 1, 0, Blocks.DARK_OAK_DOOR);
        b.fill(2, 1, 1, 2, 1, 11, Blocks.RED_CARPET);
        for (int z = 3; z <= 10; z += 2) {
            b.set(1, 3, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            b.set(3, 3, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            b.set(0, 1, z, Blocks.SPRUCE_PLANKS); b.set(0, 2, z, Blocks.SPRUCE_PLANKS);
            b.set(4, 1, z, Blocks.SPRUCE_PLANKS); b.set(4, 2, z, Blocks.SPRUCE_PLANKS);
        }
        b.set(2, 1, 11, b.m("lore_desk"));
        b.chest(1, 1, 11, "common");
        wallSign(b, 1, 2, -1, Direction.NORTH, t.get(0));
        wallSign(b, 1, 3, 4, Direction.EAST, t.get(1));
        wallSign(b, 3, 3, 8, Direction.WEST, t.get(2));
        wallSign(b, 2, 3, 11, Direction.NORTH, t.get(3));
        b.set(2, 0, 0, Blocks.POLISHED_ANDESITE);
    }

    // ------------------------------------------------------------------------------------ HOUSE_05 buried
    /** Only a chimney and a trapdoor are visible; the house itself is 4-9 blocks below the surface. */
    private static void buried(B b, List<String> t) {
        // the buried house: floor y=-9, interior y=-8..-4, roof y=-3, covered by a dirt layer up to the surface
        b.fill(0, -9, 3, 12, -9, 12, Blocks.COBBLED_DEEPSLATE);
        b.fill(0, -8, 3, 12, -3, 12, Blocks.SPRUCE_PLANKS);
        b.air(1, -8, 4, 11, -4, 11);
        b.fill(0, -2, 3, 12, -1, 12, Blocks.DIRT);
        // shaft (x=4, z=2): ladder faces south, backed by bricks at z=1; the north wall of the house is open at the shaft foot
        for (int y = -8; y <= -1; y++) {
            b.set(4, y, 1, Blocks.STONE_BRICKS); b.set(3, y, 2, Blocks.STONE_BRICKS); b.set(5, y, 2, Blocks.STONE_BRICKS);
            b.set(4, y, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        b.air(4, -8, 3, 4, -5, 3);
        b.set(4, 0, 2, Blocks.SPRUCE_TRAPDOOR);
        // chimney stump on the surface, directly above the cold hearth
        b.fill(8, 0, 8, 8, 2, 8, Blocks.BRICKS);
        b.air(8, -3, 8, 8, -1, 8);
        b.set(8, -3, 8, Blocks.BRICKS); // sealed: the chimney is a stump, not a way in
        b.fill(7, -8, 7, 9, -8, 9, Blocks.RED_CARPET);
        b.set(8, -8, 9, Blocks.FURNACE);
        b.set(10, -8, 5, Blocks.RED_BED);
        b.set(2, -5, 10, Blocks.COBWEB); b.set(10, -5, 10, Blocks.COBWEB);
        b.set(10, -8, 10, b.m("lore_desk"));
        b.set(2, -8, 10, b.m("memory_log"));
        b.chest(2, -8, 5, "common");
        b.set(6, -5, 6, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(6, -4, 6, Blocks.CHAIN);
        wallSign(b, 7, -6, 4, Direction.SOUTH, t.get(1));    // attached to the house's north wall (z=3)
        wallSign(b, 9, -6, 4, Direction.SOUTH, t.get(2));
        wallSign(b, 6, -6, 11, Direction.NORTH, t.get(3));   // attached to the south wall (z=12)
        standSign(b, 5, 0, 4, 4, t.get(0));
    }

    static void door(B b, int x, int y, int z, Block door) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        b.set(x, y, z, lower);
        b.set(x, y + 1, z, lower.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    private Houses() {}
}
