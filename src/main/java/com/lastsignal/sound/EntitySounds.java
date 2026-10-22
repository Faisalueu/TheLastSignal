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

    /** Roster-pass creatures with their own ambient/hurt/death sets (tools/roster_sounds.py). */
    public static final String[] ROSTER = {"black_witness", "tall_black", "borrowed_skin", "drowned_choir", "hanging_man", "salt_mother", "radio_shepherd",
            "glass_walker", "candle_bearer", "paper_man", "stair_thing", "moon_watcher",
            "observer", "hollow", "mimic", "signalman", "witness", "crawler", "lost_child", "doorman", "pale_herd", "echo", "burrower", "lantern_widow", "facet",
            "archivist", "signal_core", "buried_king", "unfinished"};
    private static final java.util.Map<String, RegistryObject<SoundEvent>> ROSTER_SOUNDS = new java.util.HashMap<>();

    static {
        for (String id : ROSTER) for (String g : new String[]{"ambient", "hurt", "death"}) ROSTER_SOUNDS.put(id + "." + g, reg("entity." + id + "." + g));
    }

    /** the creature's own sound, or null when it uses the shared set */
    public static SoundEvent of(String entityId, String kind) {
        RegistryObject<SoundEvent> r = ROSTER_SOUNDS.get(entityId + "." + kind);
        return r == null ? null : r.get();
    }

    private EntitySounds() {}
}
