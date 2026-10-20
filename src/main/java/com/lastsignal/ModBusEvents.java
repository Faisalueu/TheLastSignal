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

    @SubscribeEvent
    public static void whiteAttributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.WHITE_PRESENCE.get(), com.lastsignal.entity.WhitePresenceEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void spawnRules(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) {
        e.register(ModEntities.WHITE_PRESENCE.get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.lastsignal.entity.WhitePresenceEntity::checkSpawn,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void volcanoSpawnRules(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) {
        for (String id : new String[]{"hollow", "crawler"})
            e.register(ModEntities.BY_ID.get(id).get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.lastsignal.entity.VolcanoSpawns::check,
                    net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private ModBusEvents() {}
}
