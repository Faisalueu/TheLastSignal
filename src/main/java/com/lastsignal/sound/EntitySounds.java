package com.lastsignal.sound;

import com.lastsignal.LastSignalMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sound events of entities added after the original 17 (ids match sounds.json; files under sounds/entity/). */
public final class EntitySounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LastSignalMod.MODID);

    private static RegistryObject<SoundEvent> reg(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LastSignalMod.MODID, id)));
    }

    public static final RegistryObject<SoundEvent> WP_AMBIENT = reg("entity.white_presence.ambient"), WP_HURT = reg("entity.white_presence.hurt"),
            WP_DEATH = reg("entity.white_presence.death"), WP_WINGS = reg("entity.white_presence.wings"), WP_STEP = reg("entity.white_presence.step"),
            WP_TAKEOFF = reg("entity.white_presence.takeoff");

    private EntitySounds() {}
}
