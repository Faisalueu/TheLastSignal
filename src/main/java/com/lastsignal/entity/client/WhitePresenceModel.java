package com.lastsignal.entity.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.entity.WhitePresenceEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Client-only. */
public class WhitePresenceModel extends GeoModel<WhitePresenceEntity> {
    @Override
    public ResourceLocation getModelResource(WhitePresenceEntity e) { return new ResourceLocation(LastSignalMod.MODID, "geo/white_presence.geo.json"); }

    @Override
    public ResourceLocation getTextureResource(WhitePresenceEntity e) { return new ResourceLocation(LastSignalMod.MODID, "textures/entity/white_presence.png"); }

    @Override
    public ResourceLocation getAnimationResource(WhitePresenceEntity e) { return new ResourceLocation(LastSignalMod.MODID, "animations/white_presence.animation.json"); }
}
