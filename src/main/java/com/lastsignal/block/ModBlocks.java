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
