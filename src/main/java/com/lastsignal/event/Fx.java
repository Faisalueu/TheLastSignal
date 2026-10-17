package com.lastsignal.event;

import com.lastsignal.network.ModNetwork;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Presentation helpers. "private" methods send only to one player so a whisper is never heard by the party. */
public final class Fx {
    /** Sound only this player hears, positioned in the world. */
    public static void privateSound(ServerPlayer p, SoundEvent s, Vec3 at, float vol, float pitch) {
        p.connection.send(new ClientboundSoundPacket(Holder.direct(s), SoundSource.AMBIENT, at.x, at.y, at.z, vol, pitch, p.getRandom().nextLong()));
    }

    /** Sound everyone nearby hears (shared event). */
    public static void sharedSound(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.AMBIENT, vol, pitch);
    }

    public static void stopSounds(ServerPlayer p, SoundSource src) {
        p.connection.send(new ClientboundStopSoundPacket(null, src));
    }

    public static void message(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    public static void effect(ServerPlayer p, String id, int ticks, float intensity) {
        ModNetwork.sendTo(p, id, ticks, intensity);
    }

    public static void particles(ServerLevel l, ParticleOptions o, Vec3 at, int n, double spread) {
        l.sendParticles(o, at.x, at.y, at.z, n, spread, spread, spread, 0.01);
    }

    private Fx() {}
}
