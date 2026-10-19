package com.lastsignal.block;

import com.lastsignal.GeneratedContent;
import com.lastsignal.LastSignalMod;
import com.lastsignal.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LastSignalMod.MODID);
    public static final Map<String, RegistryObject<Block>> BY_ID = new LinkedHashMap<>();

    static {
        for (String[] def : GeneratedContent.BLOCKS) {
            String id = def[0];
            int light = Integer.parseInt(def[1]);
            String special = def[2];
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(special.equals("sealed") ? -1f : 2.0f, special.equals("sealed") ? 3600000f : 6.0f)
                    .lightLevel(s -> light);
            RegistryObject<Block> rb = BLOCKS.register(id, () -> create(special, props));
            BY_ID.put(id, rb);
            ModItems.ITEMS.register(id, () -> new BlockItem(rb.get(), new Item.Properties()));
        }
    }

    static {
        for (String[] def : com.lastsignal.world.gen.ThemeData.BLOCKS) {
            String id = def[0];
            int light = Integer.parseInt(def[1]);
            boolean glass = def[2].equals("glass");
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).lightLevel(s -> light);
            if (glass) props = props.noOcclusion().isValidSpawn((a, b, c, d) -> false).isRedstoneConductor((a, b, c) -> false)
                    .isSuffocating((a, b, c) -> false).isViewBlocking((a, b, c) -> false);
            final BlockBehaviour.Properties fp = props;
            RegistryObject<Block> rb = BLOCKS.register(id, () -> glass ? new net.minecraft.world.level.block.GlassBlock(fp) : new Block(fp));
            BY_ID.put(id, rb);
            ModItems.ITEMS.register(id, () -> new BlockItem(rb.get(), new Item.Properties()));
        }
    }

    static {
        for (String id : new String[]{"black_fracture", "white_fracture"}) {
            boolean black = id.startsWith("black");
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(black ? MapColor.COLOR_BLACK : MapColor.SNOW)
                    .noCollission().strength(1.5f, 3600000f).lightLevel(s -> black ? 0 : 6).noLootTable().isValidSpawn((a, b, c, d) -> false);
            RegistryObject<Block> rb = BLOCKS.register(id, () -> new FractureBlock(props, black));
            BY_ID.put(id, rb);
            ModItems.ITEMS.register(id, () -> new BlockItem(rb.get(), new Item.Properties()));
        }
    }

    static {
        for (String[] def : com.lastsignal.world.gen.ExtraThemes.BLOCKS) {
            String id = def[0];
            int light = Integer.parseInt(def[1]);
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).lightLevel(s -> light);
            RegistryObject<Block> rb = BLOCKS.register(id, () -> new Block(props));
            BY_ID.put(id, rb);
            ModItems.ITEMS.register(id, () -> new BlockItem(rb.get(), new Item.Properties()));
        }
    }

    private static Block create(String special, BlockBehaviour.Properties p) {
        return switch (special) {
            case "radio" -> new RadioReceiverBlock(p);
            case "terminal" -> new ArchiveTerminalBlock(p);
            case "checkpoint" -> new CheckpointBeaconBlock(p);
            case "sealed" -> new SealedDoorBlock(p);
            case "lore" -> new LoreBlock(p);
            case "gate" -> new DimensionGateBlock(p);
            case "puzzle" -> new PuzzleBlock(p);
            default -> new Block(p);
        };
    }

    private ModBlocks() {}
}
