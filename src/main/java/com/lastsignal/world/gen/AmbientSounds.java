package com.lastsignal.world.gen;

import com.lastsignal.LastSignalMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Registers the bed + two accents of every Phase-1 dimension as sound events (ids match sounds.json). */
public final class AmbientSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LastSignalMod.MODID);

    static {
        for (Theme t : ThemeData.ALL) {
            for (String id : new String[]{t.bed, t.acc1, t.acc2})
                SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LastSignalMod.MODID, id)));
        }
    }

    public static SoundEvent get(String id) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(LastSignalMod.MODID, id));
    }

    private AmbientSounds() {}
}
