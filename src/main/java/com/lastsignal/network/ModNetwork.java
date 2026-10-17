package com.lastsignal.network;

import com.lastsignal.LastSignalMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;

/**
 * Single S2C channel for presentation-only effects. The server decides WHEN; the client only renders.
 * There is deliberately no C2S packet that can change world state, so a client can never ask the server
 * to modify the world, spawn entities or complete puzzles.
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LastSignalMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, EffectPacket.class, EffectPacket::encode, EffectPacket::decode, EffectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendTo(ServerPlayer p, String effect, int ticks, float intensity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EffectPacket(effect, ticks, intensity));
    }

    private ModNetwork() {}
}
