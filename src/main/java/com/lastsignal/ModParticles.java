package com.lastsignal;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LastSignalMod.MODID);
    public static final RegistryObject<SimpleParticleType> STATIC =
            PARTICLES.register("static", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
