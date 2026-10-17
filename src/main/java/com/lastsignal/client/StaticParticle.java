package com.lastsignal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Small grey static speck. Client-only. */
public class StaticParticle extends TextureSheetParticle {
    protected StaticParticle(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(l, x, y, z, vx, vy, vz);
        this.lifetime = 12 + this.random.nextInt(10);
        this.quadSize = 0.06f + this.random.nextFloat() * 0.08f;
        this.gravity = 0f;
        this.xd = (this.random.nextFloat() - 0.5f) * 0.05;
        this.yd = (this.random.nextFloat() - 0.5f) * 0.05;
        this.zd = (this.random.nextFloat() - 0.5f) * 0.05;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new StaticParticle(l, x, y, z, vx, vy, vz, sprites);
        }
    }
}
