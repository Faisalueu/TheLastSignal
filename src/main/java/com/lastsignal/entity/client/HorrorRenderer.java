package com.lastsignal.entity.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.HorrorEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Client-only. One renderer class; the profile selects model layer and texture. */
public class HorrorRenderer extends MobRenderer<HorrorEntity, ProfileModel> {
    private final ResourceLocation texture;

    public static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(new ResourceLocation(LastSignalMod.MODID, id), "main");
    }

    public HorrorRenderer(EntityRendererProvider.Context ctx, EntityProfiles.Profile p) {
        super(ctx, makeModel(ctx, p), Math.max(0.3f, p.hbW() * 0.5f));
        this.texture = new ResourceLocation(LastSignalMod.MODID, "textures/entity/" + p.id() + ".png");
    }

    private static ProfileModel makeModel(EntityRendererProvider.Context ctx, EntityProfiles.Profile p) {
        return new ProfileModel(ctx.bakeLayer(layer(p.id())), p.id(), p.quad(), p.body()[1], ProfileModel.bodyTop(p));
    }

    @Override
    public ResourceLocation getTextureLocation(HorrorEntity e) {
        return texture;
    }

    @Override
    protected void scale(HorrorEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float partial) {
        // model is built at final size (16 px = 1 block), so no extra scaling
    }

    @Override
    protected float getFlipDegrees(HorrorEntity e) {
        return 0f; // creatures do not tip over on death; they simply fall apart
    }
}
