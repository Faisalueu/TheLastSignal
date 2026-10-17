package com.lastsignal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Presentation-only client effect: "static", "shadow", "fog", "distort", "shake", "tension", "flash", "glitch". */
public class EffectPacket {
    public final String effect;
    public final int ticks;
    public final float intensity;

    public EffectPacket(String effect, int ticks, float intensity) {
        this.effect = effect;
        this.ticks = ticks;
        this.intensity = intensity;
    }

    public static void encode(EffectPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.effect, 32);
        buf.writeVarInt(p.ticks);
        buf.writeFloat(p.intensity);
    }

    public static EffectPacket decode(FriendlyByteBuf buf) {
        return new EffectPacket(buf.readUtf(32), Math.min(buf.readVarInt(), 20 * 60), Math.max(0f, Math.min(buf.readFloat(), 1f)));
    }

    public static void handle(EffectPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lastsignal.client.ClientEffects.apply(p.effect, p.ticks, p.intensity)));
        ctx.get().setPacketHandled(true);
    }
}
