package com.lastsignal.entity.client;

import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.HorrorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Client-only. One GeckoLib renderer for all 17 creatures; emissive parts come from the <id>_glowmask.png texture. */
public class HorrorRenderer extends GeoEntityRenderer<HorrorEntity> {
    private final boolean ghostly;

    public HorrorRenderer(EntityRendererProvider.Context ctx, EntityProfiles.Profile p) {
        super(ctx, new HorrorGeoModel());
        this.shadowRadius = Math.max(0.3f, p.hbW() * 0.5f);
        this.ghostly = p.id().equals("echo");
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(HorrorEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return ghostly ? RenderType.entityTranslucent(texture) : super.getRenderType(e, texture, buffers, partialTick);
    }

    @Override
    protected float getDeathMaxRotation(HorrorEntity e) {
        return 0f; // the death animation handles the collapse
    }
}
