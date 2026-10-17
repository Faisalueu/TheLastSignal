package com.lastsignal.item;

import com.lastsignal.GeneratedContent;
import com.lastsignal.LastSignalMod;
import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.ModEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LastSignalMod.MODID);
    public static final Map<String, RegistryObject<Item>> ITEMS_BY_ID = new LinkedHashMap<>();

    static {
        for (String id : GeneratedContent.ITEMS) {
            Item.Properties p = new Item.Properties().stacksTo(isKeyOrTool(id) ? 1 : 64);
            if (id.equals("buried_crown_shard") || id.equals("unfinished_effigy")) p.rarity(Rarity.EPIC);
            ITEMS_BY_ID.put(id, ITEMS.register(id, () -> create(id, p)));
        }
    }

    private static boolean isKeyOrTool(String id) {
        return id.endsWith("_key") || id.equals("broken_radio") || id.equals("signal_detector")
                || id.equals("distorted_compass") || id.equals("research_notebook") || id.equals("signal_blade");
    }

    private static Item create(String id, Item.Properties p) {
        return switch (id) {
            case "broken_radio" -> new RadioItem(p);
            case "research_notebook" -> new NotebookItem(p);
            case "sealed_envelope", "field_journal_page", "strange_photograph" -> new DocumentItem(p, id);
            case "protective_charm" -> new CharmItem(p);
            case "emergency_flare" -> new FlareItem(p);
            case "signal_detector", "distorted_compass" -> new DetectorItem(p, id.equals("distorted_compass"));
            default -> new LoreItem(p, id);
        };
    }

    /** Spawn eggs for every entity (creative testing). Registered after entities exist. */
    static {
        for (EntityProfiles.Profile pr : EntityProfiles.ALL) {
            String egg = pr.id() + "_spawn_egg";
            ITEMS.register(egg, () -> new ForgeSpawnEggItem(ModEntities.BY_ID.get(pr.id()), pr.base(), pr.accent(), new Item.Properties()));
        }
    }

    private ModItems() {}
}
