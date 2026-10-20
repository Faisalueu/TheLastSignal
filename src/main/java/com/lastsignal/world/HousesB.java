package com.lastsignal.world;

import com.lastsignal.world.Structures.B;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.List;

/** Haunted houses HOUSE_06..HOUSE_30: one hand-written design each (see Houses for the framework and the first five). */
final class HousesB {
    static String title(String id) {
        return switch (id) {
            case "HOUSE_06" -> "The Mirror House";
            case "HOUSE_07" -> "The Flooded House";
            case "HOUSE_08" -> "The Burnt House";
            case "HOUSE_09" -> "The Nursery";
            case "HOUSE_10" -> "The Radio House";
            case "HOUSE_11" -> "The House on Stilts";
            case "HOUSE_12" -> "The Lantern House";
            case "HOUSE_13" -> "The Library";
            case "HOUSE_14" -> "The Seven Chairs";
            case "HOUSE_15" -> "The Door House";
            case "HOUSE_16" -> "The Cold Hearth";
            case "HOUSE_17" -> "The Moss House";
            case "HOUSE_18" -> "The Frozen House";
            case "HOUSE_19" -> "The Sunken House";
            case "HOUSE_20" -> "The Split House";
            case "HOUSE_21" -> "The Clock House";
            case "HOUSE_22" -> "The Dead Garden";
            case "HOUSE_23" -> "The Red Room";
            case "HOUSE_24" -> "The Echo House";
            case "HOUSE_25" -> "The Sleepers";
            case "HOUSE_26" -> "The Black Witness";
            case "HOUSE_27" -> "The White Presence";
            case "HOUSE_28" -> "The Signal House";
            case "HOUSE_29" -> "The Last Camp";
            case "HOUSE_30" -> "The Final House";
            default -> null;
        };
    }

    static List<String> texts(String id) {
        return switch (id) {
            case "HOUSE_06" -> List.of("Two of everything. One of them is wrong.", "ca0444 stood here and saw himself blink first.", "Do not wave. The other one learns.", "The glass holds nothing. It holds you, later.");
            case "HOUSE_07" -> List.of("The water is not rising. The house is sinking.", "Hold your breath. Count to forty. Rise.", "feeLoLzy heard knocking under the floor.", "Something warm lives in the cold water.");
            case "HOUSE_08" -> List.of("It burned before we arrived.", "The fire was never put out. It left.", "Ash on the floor spells our names.", "Seven names. Two are already gone.");
            case "HOUSE_09" -> List.of("Quiet. Someone is sleeping.", "Small beds. Small shoes. No children.", "aloneo3 heard singing. Nobody was singing.", "Do not wake what the lullaby keeps down.");
            case "HOUSE_10" -> List.of("Tune to 115.5. Do not answer.", "The signal repeats our words a day early.", "HidontmindMe33 spoke into it. It spoke back.", "Turn it off. It stays on.");
            case "HOUSE_11" -> List.of("Nothing touches the ground here.", "The ground is not safe. Ask the hole.", "GOLD3002 built this to stay above it.", "Looking down is how it finds you.");
            case "HOUSE_12" -> List.of("Keep the light lit. It reads the dark.", "The lamps turn toward the nearest friend.", "0hunl0 saw them point at a black shape.", "If the light goes out, stay still.");
            case "HOUSE_13" -> List.of("Every book is the same page.", "The page is about you. Read less.", "majoo03 read it twice. Now he whispers.", "Burn nothing. Close the book.");
            case "HOUSE_14" -> List.of("Seven chairs. Seven plates. One is warm.", "Names are on the plates. Read them aloud.", "A plate is turned over. Nobody turned it.", "Eat nothing. Leave one chair empty.");
            case "HOUSE_15" -> List.of("Five doors. Four lead nowhere.", "Which one did you come in by?", "ca0444 chose the fifth.", "Knock first. Then step back.");
            case "HOUSE_16" -> List.of("The fire is cold. The smoke is not.", "Do not light it. It remembers heat.", "aloneo3 warmed his hands. They stayed warm.", "Something is cooking. We brought no food.");
            case "HOUSE_17" -> List.of("It grows where we sleep.", "The walls breathe slower than we do.", "feeLoLzy cut it. It grew back by morning.", "Do not rest here. Do not stand still.");
            case "HOUSE_18" -> List.of("Winter came into the house first.", "Footprints lead in. None lead out.", "Count the footprints twice.", "Cold is quiet. That is its trick.");
            case "HOUSE_19" -> List.of("Look up. The house is below you.", "The roof is the doorstep. Go down.", "HidontmindMe33 left by the floor.", "The way down is shorter than the way up.");
            case "HOUSE_20" -> List.of("A clean cut. Nothing here is torn.", "Half the house is somewhere else.", "Do not cross the gap. Walk around.", "The other half is looking at this one.");
            case "HOUSE_21" -> List.of("The clock stopped at 11:55.", "Five minutes are missing. Do not ask.", "ca0444 moved the hands. The sky changed.", "When the clock strikes, be indoors.");
            case "HOUSE_22" -> List.of("Nothing alive grows. Everything grows.", "A seed was planted. Something answered.", "The scarecrow is facing the house.", "Do not turn your back on the scarecrow.");
            case "HOUSE_23" -> List.of("Red is the color it sees us in.", "This room is warm. It should not be.", "aloneo3 stayed one hour. He lost one day.", "Leave before the walls dim.");
            case "HOUSE_24" -> List.of("Say a word. It returns changed.", "feeLoLzy said hello. It said goodbye.", "The echo is a few seconds ahead.", "Stop speaking. Write instead.");
            case "HOUSE_25" -> List.of("Seven beds. All slept in.", "Nobody here remembers lying down.", "Do not sleep. It waits for dreams.", "One bed is yours. Do not find it.");
            case "HOUSE_26" -> List.of("It saw everything. It said nothing.", "0hunl0 met it. It stood three meters tall.", "It does not chase. It arrives.", "Look away. Then walk, never run.");
            case "HOUSE_27" -> List.of("It is kind. That is the danger.", "White feathers. Cold hands. No face.", "It lifted majoo03 and set him down gently.", "It asks nothing. Give it nothing.");
            case "HOUSE_28" -> List.of("The source is under this floor.", "The crystal hums at 115.5.", "Every friend came here. Alone.", "Speak softly. It was never spoken to.");
            case "HOUSE_29" -> List.of("We camped here the last night.", "Seven bedrolls. Three are empty.", "GOLD3002 kept the fire until dawn.", "Dawn did not come. Something did.");
            case "HOUSE_30" -> List.of("You followed the signal to the end.", "It was a lighthouse. We were the ship.", "Whatever you decide, decide gently.", "It only ever wanted to be answered.");
            default -> List.of();
        };
    }

    /** Standard 11x11 shell: foundation, room, doorway with a door in the north wall, doorstep. */
    static void std(B b, Block wall, Block floor, Block roof, int h, Block door) {
        b.foundation(0, 0, 12, 12);
        b.room(1, 1, 11, 11, h, wall, floor, roof);
        b.air(6, 1, 1, 6, 2, 1);
        Houses.door(b, 6, 1, 1, door);
        b.set(6, 0, 0, floor);
    }

    static void signs(B b, List<String> t, int h) {
        int y = Math.min(3, h);
        Houses.wallSign(b, 4, 2, 0, Direction.NORTH, t.get(0));
        Houses.wallSign(b, 3, y, 2, Direction.SOUTH, t.get(1));
        Houses.wallSign(b, 9, y, 2, Direction.SOUTH, t.get(2));
        Houses.wallSign(b, 6, y, 10, Direction.NORTH, t.get(3));
    }

    static void bed(B b, int x, int y, int z, Block bed, Direction f) {
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, f).setValue(BedBlock.PART, BedPart.FOOT);
        b.set(x, y, z, foot);
        b.set(x + f.getStepX(), y, z + f.getStepZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    static BlockState stair(Block s, Direction f, boolean top) {
        return s.defaultBlockState().setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState hanging(Block lantern) { return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true); }

    static boolean build(B b, String id, List<String> t) {
        switch (id) {
            case "HOUSE_06" -> { // mirror: everything exists twice on either side of a glass partition
                std(b, Blocks.QUARTZ_BLOCK, Blocks.POLISHED_DIORITE, Blocks.QUARTZ_SLAB, 4, Blocks.BIRCH_DOOR);
                for (int z = 3; z <= 10; z++) { b.set(6, 1, z, Blocks.GLASS_PANE); b.set(6, 2, z, Blocks.GLASS_PANE); b.set(6, 3, z, Blocks.GLASS_PANE); }
                for (int s = -1; s <= 1; s += 2) {
                    int x = 6 + s * 3;
                    bed(b, x, 1, 4, Blocks.WHITE_BED, Direction.SOUTH);
                    b.set(x, 1, 9, Blocks.LECTERN); b.set(x + s, 1, 9, Blocks.WHITE_CANDLE);
                    b.set(6 + s * 4, 3, 6, Blocks.SEA_LANTERN);
                }
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 4);
            }
            case "HOUSE_07" -> { // flooded: a pool where the floor was, a walkway around it
                std(b, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICK_SLAB, 4, Blocks.SPRUCE_DOOR);
                b.fill(3, -3, 3, 9, -3, 9, Blocks.STONE_BRICKS);
                b.fill(3, -2, 3, 9, 0, 9, Blocks.WATER);
                b.set(6, -2, 6, b.m("memory_log"));
                b.set(2, 1, 2, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                for (int x = 3; x <= 9; x += 3) b.set(x, 4, 6, Blocks.CHAIN);
                signs(b, t, 4);
            }
            case "HOUSE_08" -> { // burnt: charred shell, open roof, soul fire
                std(b, Blocks.BLACKSTONE, Blocks.COAL_BLOCK, Blocks.BLACKSTONE_SLAB, 4, Blocks.DARK_OAK_DOOR);
                b.ruin(1, 5, 1, 11, 5, 11, 0.35);
                b.ruin(1, 4, 1, 11, 4, 11, 0.15);
                b.set(2, 1, 10, Blocks.SOUL_CAMPFIRE); b.set(10, 1, 3, Blocks.SOUL_CAMPFIRE);
                b.set(9, 1, 9, b.m("lore_desk")); b.chest(3, 1, 9, "common");
                for (int i = 0; i < 12; i++) b.set(3 + b.r.nextInt(7), 1, 3 + b.r.nextInt(7), Blocks.BLACK_CARPET);
                signs(b, t, 4);
            }
            case "HOUSE_09" -> { // nursery: pastel, tiny beds, a jukebox with no disc
                std(b, Blocks.PINK_WOOL, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, 4, Blocks.BIRCH_DOOR);
                b.fill(2, 1, 2, 10, 1, 10, Blocks.PINK_CARPET);
                for (int x = 2; x <= 8; x += 3) bed(b, x, 1, 5, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
                b.set(10, 1, 9, Blocks.JUKEBOX); b.set(2, 1, 9, b.m("lore_desk")); b.chest(6, 1, 9, "common");
                signs(b, t, 4);
            }
            case "HOUSE_10" -> { // radio: receivers everywhere and an antenna mast
                std(b, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE, Blocks.IRON_BLOCK, 4, Blocks.BIRCH_DOOR);
                for (int x = 2; x <= 10; x += 2) b.set(x, 1, 10, b.m("radio_receiver"));
                b.set(2, 1, 5, b.m("radio_receiver")); b.set(10, 1, 5, b.m("radio_receiver")); b.set(6, 1, 6, b.m("corrupted_relay"));
                for (int y = 6; y <= 16; y++) b.set(6, y, 6, Blocks.IRON_BARS);
                b.set(6, 17, 6, Blocks.LIGHTNING_ROD);
                b.set(2, 1, 2, b.m("lore_desk")); b.chest(10, 1, 2, "common");
                signs(b, t, 4);
            }
            case "HOUSE_11" -> { // stilts: the whole house floats 7 blocks up, reached by a stair
                for (int x : new int[]{1, 6, 11}) for (int z : new int[]{1, 6, 11}) for (int y = -3; y <= 6; y++) b.set(x, y, z, Blocks.SPRUCE_LOG);
                B u = new B(b.l, b.o.above(7));
                u.room(1, 1, 11, 11, 4, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB);
                u.air(6, 1, 1, 6, 2, 1); Houses.door(u, 6, 1, 1, Blocks.SPRUCE_DOOR); u.set(6, 0, 0, Blocks.SPRUCE_PLANKS);
                for (int i = 0; i <= 7; i++) { for (int y = 0; y < i; y++) b.set(6, y, i - 8, Blocks.SPRUCE_PLANKS); b.set(6, i, i - 8, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false)); }
                u.set(2, 1, 10, b.m("lore_desk")); u.chest(10, 1, 10, "common");
                signs(u, t, 4);
            }
            case "HOUSE_12" -> { // lantern: a pillar of light and a ring of hanging lanterns
                std(b, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_SLAB, 5, Blocks.DARK_OAK_DOOR);
                b.fill(6, 1, 6, 6, 4, 6, Blocks.IRON_BARS); b.set(6, 5, 6, Blocks.SEA_LANTERN);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; b.set(6 + (int) Math.round(Math.cos(a) * 3.5), 5, 6 + (int) Math.round(Math.sin(a) * 3.5), hanging(Blocks.LANTERN)); }
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 5);
            }
            case "HOUSE_13" -> { // library: shelves to the ceiling, one lectern
                std(b, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_SLAB, 5, Blocks.DARK_OAK_DOOR);
                for (int x = 4; x <= 8; x += 4) { b.fill(x, 1, 4, x, 4, 5, Blocks.BOOKSHELF); b.fill(x, 1, 8, x, 4, 9, Blocks.BOOKSHELF); }
                b.fill(2, 1, 4, 2, 3, 9, Blocks.BOOKSHELF); b.fill(10, 1, 4, 10, 3, 9, Blocks.BOOKSHELF);
                b.set(6, 1, 6, Blocks.LECTERN); b.set(6, 1, 7, b.m("memory_log"));
                b.chest(5, 1, 10, "archive");
                signs(b, t, 5);
            }
            case "HOUSE_14" -> { // seven chairs around a table laid for seven
                std(b, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_SLAB, 4, Blocks.SPRUCE_DOOR);
                for (int x = 3; x <= 9; x++) b.set(x, 1, 6, Blocks.SPRUCE_SLAB);
                for (int x : new int[]{3, 5, 7, 9}) { b.set(x, 1, 5, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false)); b.set(x, 2, 6, Blocks.CANDLE); }
                for (int x : new int[]{4, 6, 8}) { b.set(x, 1, 7, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false)); b.set(x, 2, 6, Blocks.CANDLE); }
                b.set(6, 2, 6, Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 4);
            }
            case "HOUSE_15" -> { // five doors in a row; four closets lead nowhere
                std(b, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_SLAB, 4, Blocks.OAK_DOOR);
                b.fill(2, 1, 8, 10, 3, 8, Blocks.OAK_PLANKS);
                for (int x = 2; x <= 10; x += 2) { b.air(x, 1, 8, x, 2, 8); Houses.door(b, x, 1, 8, Blocks.SPRUCE_DOOR); }
                for (int x : new int[]{3, 5, 7, 9}) b.fill(x, 1, 9, x, 3, 10, Blocks.OAK_PLANKS);
                b.chest(10, 1, 10, "common"); b.set(2, 1, 10, b.m("lore_desk"));
                b.set(6, 1, 10, Blocks.WHITE_CARPET);
                signs(b, t, 4);
            }
            case "HOUSE_16" -> { // cold hearth: a lit soul campfire behind bars, a cauldron
                std(b, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, 4, Blocks.SPRUCE_DOOR);
                b.fill(5, 1, 9, 7, 3, 10, Blocks.STONE_BRICKS); b.air(6, 1, 9, 6, 2, 10);
                b.set(6, 1, 10, Blocks.SOUL_CAMPFIRE); b.set(5, 1, 8, Blocks.IRON_BARS); b.set(6, 1, 8, Blocks.IRON_BARS); b.set(7, 1, 8, Blocks.IRON_BARS);
                b.set(3, 1, 9, Blocks.CAULDRON); b.set(9, 1, 9, Blocks.CRAFTING_TABLE);
                b.set(4, 1, 5, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, false)); b.set(8, 1, 5, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false)); b.set(6, 1, 5, Blocks.SPRUCE_SLAB);
                b.set(2, 1, 2, b.m("lore_desk")); b.chest(10, 1, 2, "common");
                signs(b, t, 4);
            }
            case "HOUSE_17" -> { // moss: the building is losing to the plants
                std(b, Blocks.MOSSY_COBBLESTONE, Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, 4, Blocks.OAK_DOOR);
                b.fill(2, 1, 2, 10, 1, 10, Blocks.MOSS_CARPET);
                for (int i = 0; i < 18; i++) { int x = 2 + b.r.nextInt(9), z = 2 + b.r.nextInt(9); b.set(x, 1, z, i % 3 == 0 ? Blocks.AZALEA : Blocks.FERN); }
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                b.ruin(1, 5, 1, 11, 5, 11, 0.25);
                signs(b, t, 4);
            }
            case "HOUSE_18" -> { // frozen
                std(b, Blocks.SPRUCE_PLANKS, Blocks.SNOW_BLOCK, Blocks.SPRUCE_SLAB, 4, Blocks.SPRUCE_DOOR);
                b.fill(2, 1, 2, 10, 1, 10, Blocks.SNOW);
                for (int i = 0; i < 14; i++) b.set(2 + b.r.nextInt(9), 4, 2 + b.r.nextInt(9), Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
                for (int z = 2; z <= 9; z++) { b.set(6, 1, z, Blocks.AIR); b.set(6, 0, z, Blocks.PACKED_ICE); }
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 4);
            }
            case "HOUSE_19" -> { // sunken: the roof is level with the ground; a hatch leads down
                B u = new B(b.l, b.o.below(5));
                u.foundation(0, 0, 12, 12);
                u.room(1, 1, 11, 11, 4, Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.STONE_BRICK_SLAB);
                for (int y = 1; y <= 4; y++) { u.set(6, y, 7, Blocks.SPRUCE_PLANKS); u.set(6, y, 6, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH)); }
                u.set(6, 5, 6, Blocks.SPRUCE_TRAPDOOR);
                u.set(2, 1, 10, b.m("lore_desk")); u.chest(10, 1, 10, "common"); u.set(6, 4, 9, hanging(Blocks.SOUL_LANTERN));
                Houses.standSign(u, 5, 6, 5, 8, t.get(0));
                Houses.wallSign(u, 3, 3, 2, Direction.SOUTH, t.get(1)); Houses.wallSign(u, 9, 3, 2, Direction.SOUTH, t.get(2)); Houses.wallSign(u, 6, 3, 10, Direction.NORTH, t.get(3));
            }
            case "HOUSE_20" -> { // split: a roofless trench cuts the house in two
                std(b, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB, 4, Blocks.BIRCH_DOOR);
                b.air(8, -4, 2, 9, 5, 10);
                b.fill(8, -5, 2, 9, -5, 10, Blocks.BASALT);
                for (int y = -4; y <= 0; y++) { b.set(7, y, 6, Blocks.STONE_BRICKS); b.set(8, y, 6, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST)); }
                b.chest(9, -4, 9, "common"); b.set(2, 1, 10, b.m("lore_desk")); b.set(5, 1, 6, Blocks.SOUL_LANTERN);
                signs(b, t, 4);
            }
            case "HOUSE_21" -> { // clock: a great white dial on the east wall stopped at 11:55
                std(b, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_SLAB, 6, Blocks.DARK_OAK_DOOR);
                for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dy, dz) <= 2.3) b.set(10, 3 + dy, 6 + dz, Blocks.WHITE_CONCRETE);
                b.set(10, 3, 6, Blocks.BLACK_CONCRETE); b.set(10, 4, 6, Blocks.BLACK_CONCRETE); b.set(10, 5, 6, Blocks.BLACK_CONCRETE); b.set(10, 4, 5, Blocks.BLACK_CONCRETE);
                for (int y = 6; y >= 4; y--) b.set(4, y, 6, Blocks.CHAIN);
                b.set(4, 3, 6, Blocks.IRON_BLOCK);
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(2, 1, 2, "common");
                signs(b, t, 6);
            }
            case "HOUSE_22" -> { // dead garden: fenced beds, a scarecrow, a small hut at the back
                b.foundation(0, 0, 12, 12);
                for (int i = 0; i <= 12; i++) { b.set(i, 1, 0, Blocks.OAK_FENCE); if (i > 0 && i < 12) b.set(i, 1, 6, i == 6 ? Blocks.AIR : Blocks.OAK_FENCE); }
                for (int z = 0; z <= 6; z++) { b.set(0, 1, z, Blocks.OAK_FENCE); b.set(12, 1, z, Blocks.OAK_FENCE); }
                for (int i = 0; i < 20; i++) b.set(2 + b.r.nextInt(9), 1, 1 + b.r.nextInt(5), Blocks.DEAD_BUSH);
                b.fill(6, 1, 3, 6, 2, 3, Blocks.HAY_BLOCK);
                b.set(6, 3, 3, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH));
                b.set(5, 2, 3, Blocks.OAK_FENCE); b.set(7, 2, 3, Blocks.OAK_FENCE);
                b.room(4, 7, 8, 11, 3, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_SLAB);
                b.air(6, 1, 7, 6, 2, 7); Houses.door(b, 6, 1, 7, Blocks.OAK_DOOR);
                b.set(5, 1, 10, b.m("lore_desk")); b.chest(7, 1, 10, "common");
                Houses.wallSign(b, 5, 2, 6, Direction.NORTH, t.get(0)); Houses.wallSign(b, 7, 2, 6, Direction.NORTH, t.get(1));
                Houses.wallSign(b, 5, 3, 8, Direction.SOUTH, t.get(2)); Houses.wallSign(b, 6, 3, 10, Direction.NORTH, t.get(3));
            }
            case "HOUSE_23" -> { // red room
                std(b, Blocks.RED_CONCRETE, Blocks.RED_NETHER_BRICKS, Blocks.RED_NETHER_BRICK_SLAB, 4, Blocks.CRIMSON_DOOR);
                b.fill(2, 1, 2, 10, 1, 10, Blocks.RED_CARPET);
                for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}, {6, 6}}) b.set(c[0], 1, c[1], Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
                b.set(6, 1, 8, stair(Blocks.CRIMSON_STAIRS, Direction.NORTH, false)); b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 4);
            }
            case "HOUSE_24" -> { // echo: calcite hall, note blocks in the walls
                std(b, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.CALCITE, 5, Blocks.BIRCH_DOOR);
                for (int z = 3; z <= 9; z += 2) { b.set(2, 2, z, Blocks.NOTE_BLOCK); b.set(10, 2, z, Blocks.NOTE_BLOCK); }
                for (int z = 4; z <= 8; z += 2) for (int y = 1; y <= 2; y++) { b.set(4, y, z, Blocks.QUARTZ_PILLAR); b.set(8, y, z, Blocks.QUARTZ_PILLAR); }
                b.set(6, 1, 9, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 5);
            }
            case "HOUSE_25" -> { // sleepers: seven beds, all made, all slept in
                std(b, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICK_SLAB, 4, Blocks.DARK_OAK_DOOR);
                for (int i = 0; i < 4; i++) bed(b, 2 + i * 2, 1, 3, i % 2 == 0 ? Blocks.WHITE_BED : Blocks.GRAY_BED, Direction.SOUTH);
                for (int i = 0; i < 3; i++) bed(b, 3 + i * 3, 1, 8, Blocks.BLACK_BED, Direction.SOUTH);
                for (int x = 3; x <= 9; x += 3) b.set(x, 4, 6, hanging(Blocks.SOUL_LANTERN));
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 4);
            }
            case "HOUSE_26" -> { // black witness: one great eye
                std(b, Blocks.BLACK_CONCRETE, Blocks.OBSIDIAN, Blocks.BLACK_CONCRETE, 6, Blocks.DARK_OAK_DOOR);
                for (int x = 5; x <= 7; x++) for (int y = 4; y <= 5; y++) b.set(x, y, 11, Blocks.BLACK_STAINED_GLASS);
                b.set(6, 4, 11, Blocks.RED_STAINED_GLASS);
                b.set(6, 1, 4, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH, false));
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 6);
            }
            case "HOUSE_27" -> { // white presence: bright, gentle, open to the light
                std(b, Blocks.WHITE_CONCRETE, Blocks.QUARTZ_BLOCK, Blocks.WHITE_STAINED_GLASS, 5, Blocks.BIRCH_DOOR);
                for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}}) b.fill(c[0], 1, c[1], c[0], 4, c[1], Blocks.QUARTZ_PILLAR);
                b.fill(5, 1, 5, 7, 1, 7, Blocks.WHITE_WOOL); b.set(6, 2, 6, Blocks.WHITE_CANDLE);
                b.set(2, 1, 10, b.m("lore_desk")); b.chest(10, 1, 10, "common");
                signs(b, t, 5);
            }
            case "HOUSE_28" -> { // signal: a crystal hums in a cellar under the floor
                std(b, Blocks.GRAY_CONCRETE, Blocks.POLISHED_DEEPSLATE, Blocks.GRAY_CONCRETE, 4, Blocks.BIRCH_DOOR);
                b.fill(3, -6, 4, 9, -6, 9, Blocks.DEEPSLATE_BRICKS);
                b.air(3, -5, 5, 9, -1, 9);
                for (int y = -5; y <= -1; y++) { b.set(6, y, 4, Blocks.DEEPSLATE_BRICKS); b.set(6, y, 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)); }
                b.set(6, 0, 5, Blocks.SPRUCE_TRAPDOOR);
                b.set(6, -5, 8, b.m("signal_crystal")); b.set(4, -5, 8, b.m("corrupted_relay")); b.set(8, -5, 8, b.m("corrupted_relay"));
                b.chest(4, -5, 6, "archive"); b.set(8, -5, 6, b.m("memory_log"));
                b.set(2, 1, 10, b.m("lore_desk"));
                signs(b, t, 4);
            }
            case "HOUSE_29" -> { // last camp: seven bedrolls round a cold fire
                b.foundation(0, 0, 12, 12);
                b.set(6, 0, 6, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                Block[] bedc = {Blocks.RED_BED, Blocks.BLUE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED};
                for (int i = 0; i < 7; i++) {
                    double a = i * Math.PI * 2 / 7;
                    int x = 6 + (int) Math.round(Math.cos(a) * 4), z = 6 + (int) Math.round(Math.sin(a) * 4);
                    Direction f = Math.abs(x - 6) > Math.abs(z - 6) ? (x > 6 ? Direction.WEST : Direction.EAST) : (z > 6 ? Direction.NORTH : Direction.SOUTH);
                    if (i < 4) bed(b, x, 0, z, bedc[i], f); else b.set(x, 0, z, Blocks.GRAY_CARPET);
                }
                b.set(6, 0, 3, Blocks.SPRUCE_LOG); b.set(6, 0, 9, Blocks.SPRUCE_LOG); b.chest(11, 0, 11, "common"); b.set(1, 0, 11, b.m("lore_desk"));
                Houses.standSign(b, 6, 0, 1, 8, t.get(0)); Houses.standSign(b, 1, 0, 6, 12, t.get(1));
                Houses.standSign(b, 11, 0, 6, 4, t.get(2)); Houses.standSign(b, 6, 0, 11, 0, t.get(3));
            }
            case "HOUSE_30" -> { // the final house: the core of the signal
                b.foundation(0, 0, 12, 12);
                b.room(0, 0, 12, 12, 6, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE, Blocks.OBSIDIAN);
                b.air(6, 1, 0, 6, 2, 0); Houses.door(b, 6, 1, 0, Blocks.DARK_OAK_DOOR);
                b.set(6, 0, -1, Blocks.POLISHED_BLACKSTONE);
                for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}}) for (int y = 1; y <= 5; y++) b.set(c[0], y, c[1], b.m("strange_pillar"));
                b.set(6, 1, 6, b.m("signal_crystal"));
                b.set(6, 1, 9, b.m("memory_log")); b.set(2, 1, 6, b.m("lore_desk")); b.chest(10, 1, 6, "endgame");
                Houses.wallSign(b, 3, 3, 1, Direction.SOUTH, t.get(0)); Houses.wallSign(b, 9, 3, 1, Direction.SOUTH, t.get(1));
                Houses.wallSign(b, 1, 3, 6, Direction.EAST, t.get(2)); Houses.wallSign(b, 6, 3, 11, Direction.NORTH, t.get(3));
            }
            default -> { return false; }
        }
        return true;
    }

    private HousesB() {}
}
