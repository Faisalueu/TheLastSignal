package com.lastsignal;

import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.entity.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus (lifecycle) events that apply to both physical sides. */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        for (EntityProfiles.Profile p : EntityProfiles.ALL) {
            e.put(ModEntities.BY_ID.get(p.id()).get(), HorrorEntity.createAttributes(p.id()).build());
        }
    }

    private ModBusEvents() {}
}
