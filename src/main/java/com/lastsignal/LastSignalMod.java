package com.lastsignal;

import com.lastsignal.block.ModBlocks;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.entity.ModEntities;
import com.lastsignal.item.ModItems;
import com.lastsignal.network.ModNetwork;
import com.lastsignal.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LastSignalMod.MODID)
public class LastSignalMod {
    public static final String MODID = "lastsignal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LastSignalMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModTabs.TABS.register(bus);
        com.lastsignal.world.gen.ModWorldgen.GENERATORS.register(bus);
        com.lastsignal.world.gen.AmbientSounds.SOUNDS.register(bus);
        com.lastsignal.sound.EntitySounds.SOUNDS.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LastSignalConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LastSignalConfig.CLIENT_SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
