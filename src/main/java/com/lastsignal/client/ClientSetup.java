package com.lastsignal.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.ModParticles;
import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.ModEntities;
import com.lastsignal.entity.client.HorrorRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only mod-bus registrations. The Dist.CLIENT filter keeps this class off dedicated servers entirely. */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (EntityProfiles.Profile p : EntityProfiles.ALL)
            e.registerEntityRenderer(ModEntities.BY_ID.get(p.id()).get(), ctx -> new HorrorRenderer(ctx, p));
    }

    @SubscribeEvent
    public static void whiteRenderer(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.WHITE_PRESENCE.get(), com.lastsignal.entity.client.WhitePresenceRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.STATIC.get(), StaticParticle.Provider::new);
    }

    private ClientSetup() {}
}
