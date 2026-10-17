package com.lastsignal.entity;

import com.lastsignal.LastSignalMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LastSignalMod.MODID);
    public static final Map<String, RegistryObject<EntityType<HorrorEntity>>> BY_ID = new LinkedHashMap<>();

    static {
        for (EntityProfiles.Profile pr : EntityProfiles.ALL) {
            boolean boss = HorrorEntity.archOf(pr.id()) == HorrorEntity.Arch.BOSS;
            BY_ID.put(pr.id(), ENTITIES.register(pr.id(), () ->
                    EntityType.Builder.<HorrorEntity>of((t, l) -> boss ? new BossHorrorEntity(t, l) : new HorrorEntity(t, l), MobCategory.MONSTER)
                            .sized(pr.hbW(), pr.hbH())
                            .clientTrackingRange(boss ? 16 : 10)
                            .fireImmune()
                            .build(LastSignalMod.MODID + ":" + pr.id())));
        }
    }

    private ModEntities() {}
}
