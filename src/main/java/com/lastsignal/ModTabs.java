package com.lastsignal;

import com.lastsignal.block.ModBlocks;
import com.lastsignal.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LastSignalMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lastsignal"))
            .icon(() -> new ItemStack(ModItems.ITEMS_BY_ID.get("broken_radio").get()))
            .displayItems((params, out) -> {
                ModItems.ITEMS.getEntries().forEach(e -> out.accept(e.get()));
            })
            .build());

    private ModTabs() {}
}
