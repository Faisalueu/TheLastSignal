package com.lastsignal.entity.client;

import com.lastsignal.entity.WhitePresenceEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Client-only. */
public class WhitePresenceRenderer extends GeoEntityRenderer<WhitePresenceEntity> {
    public WhitePresenceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WhitePresenceModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    protected float getDeathMaxRotation(WhitePresenceEntity e) { return 0f; }      // the death animation handles the collapse
}
