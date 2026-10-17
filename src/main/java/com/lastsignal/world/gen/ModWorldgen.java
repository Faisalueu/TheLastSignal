package com.lastsignal.world.gen;

import com.lastsignal.LastSignalMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModWorldgen {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, LastSignalMod.MODID);
    public static final RegistryObject<Codec<ThemedChunkGenerator>> THEMED = GENERATORS.register("themed", () -> ThemedChunkGenerator.CODEC);

    private ModWorldgen() {}
}
