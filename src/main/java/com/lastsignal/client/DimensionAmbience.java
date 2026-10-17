package com.lastsignal.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.world.gen.AmbientSounds;
import com.lastsignal.world.gen.Theme;
import com.lastsignal.world.gen.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;

import java.util.Random;

/** Client-only: one streamed ambience bed per Phase-1 dimension (non-looping, random gaps), spatial accents, and themed fog distance. */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, value = Dist.CLIENT)
public final class DimensionAmbience {
    private static final Random RNG = new Random();
    private static String current = "";
    private static SimpleSoundInstance bed;
    private static int bedGap = 40, accGap = 300;

    private static Theme themeNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var loc = mc.level.dimension().location();
        if (!loc.getNamespace().equals(LastSignalMod.MODID)) return null;
        return Themes.get(loc.getPath());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Theme t = themeNow();
        String slug = t == null ? "" : t.slug;
        if (!slug.equals(current)) {
            if (bed != null) { mc.getSoundManager().stop(bed); bed = null; }
            current = slug;
            bedGap = 40; accGap = 200 + RNG.nextInt(300);
        }
        if (t == null || mc.player == null || mc.isPaused()) return;
        float vol = (float) (double) LastSignalConfig.CLIENT.audioIntensity.get();
        if (vol <= 0.01f) return;
        if (bed != null && !mc.getSoundManager().isActive(bed)) { bed = null; bedGap = 100 + RNG.nextInt(300); }
        if (bed == null && --bedGap <= 0) {
            SoundEvent ev = AmbientSounds.get(t.bed);
            if (ev != null) {
                bed = SimpleSoundInstance.forUI(ev, 1.0f, 0.55f * vol);
                mc.getSoundManager().play(bed);
            }
        }
        if (--accGap <= 0) {
            accGap = 500 + RNG.nextInt(1100);
            SoundEvent ev = AmbientSounds.get(RNG.nextBoolean() ? t.acc1 : t.acc2);
            if (ev != null) {
                double a = RNG.nextDouble() * Math.PI * 2, d = 6 + RNG.nextDouble() * 10;
                mc.level.playLocalSound(mc.player.getX() + Math.cos(a) * d, mc.player.getY() + RNG.nextInt(3), mc.player.getZ() + Math.sin(a) * d,
                        ev, SoundSource.AMBIENT, 0.7f * vol, 0.9f + RNG.nextFloat() * 0.2f, false);
            }
        }
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        Theme t = themeNow();
        if (t == null || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        if (e.getCamera().getFluidInCamera() != FogType.NONE) return;
        float far = Math.min(t.fogFar, e.getFarPlaneDistance());
        e.setNearPlaneDistance(Math.min(far * 0.05f, 4f));
        e.setFarPlaneDistance(far);
        e.setCanceled(true);
    }

    private DimensionAmbience() {}
}
