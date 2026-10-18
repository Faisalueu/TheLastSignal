package com.lastsignal.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.world.gen.AmbientSounds;
import com.lastsignal.world.gen.Profile;
import com.lastsignal.world.gen.Theme;
import com.lastsignal.world.gen.Themes;
import com.lastsignal.world.gen.ProfileData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Client-only dimension presentation:
 *  - one streamed ambience bed at a time (60 s, two variants), cross-faded in/out, random silent gaps between beds;
 *  - spatial accents with weighted selection (open vs enclosed, rare far event), a per-dimension frequency, and a hard budget of one pending accent;
 *  - subtle, configurable fog distance (root cause of the old "2 chunks" fog was a hard-coded 22-40 block far plane here).
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, value = Dist.CLIENT)
public final class DimensionAmbience {
    private static final Random RNG = new Random();
    private static String current = "";
    private static Bed bed;
    private static int bedGap = 60, accGap = 400, bedAge;
    private static float fogNow = -1;      // smoothed fog far distance (avoids a pop when entering / leaving)

    /** A bed whose volume follows a target (fade in on start, fade out on dimension change / disconnect). */
    private static final class Bed extends AbstractSoundInstance implements TickableSoundInstance {
        float target, cur;
        boolean fadingOut, done;
        final float base;

        Bed(SoundEvent ev, float base) {
            super(ev, SoundSource.AMBIENT, RandomSource.create());
            this.base = base; this.cur = 0; this.volume = 0.001f; this.relative = true; this.attenuation = SoundInstance.Attenuation.NONE; this.looping = false;
            this.target = base;
        }

        @Override public boolean isStopped() { return done; }

        @Override
        public void tick() {
            float step = 1f / 60f;       // 3 s fades at 20 tps
            if (fadingOut) target = 0;
            cur += Math.signum(target - cur) * Math.min(step * Math.max(base, .1f), Math.abs(target - cur));
            this.volume = Math.max(0.0001f, cur);
            if (fadingOut && cur <= 0.002f) done = true;
        }
    }

    private static Theme themeNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var loc = mc.level.dimension().location();
        if (!loc.getNamespace().equals(LastSignalMod.MODID)) return null;
        return Themes.get(loc.getPath());
    }

    private static void fadeOutBed() {
        if (bed != null) { bed.fadingOut = true; }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Theme t = themeNow();
        String slug = t == null ? "" : t.slug;
        if (!slug.equals(current)) {
            fadeOutBed();
            if (bed != null && bed.done) bed = null;
            current = slug; fogNow = -1;
            bedGap = 40 + RNG.nextInt(120); accGap = 300 + RNG.nextInt(500);
        }
        if (bed != null) {
            bedAge++;
            boolean ended = bed.done || (bedAge > 60 && !mc.getSoundManager().isActive(bed));
            if (ended) { mc.getSoundManager().stop(bed); bed = null; bedGap = 200 + RNG.nextInt(500); }
        }
        if (t == null || mc.player == null || mc.level == null || mc.isPaused()) return;
        Profile pr = ProfileData.ALL[t.index - 1];
        float vol = (float) (double) LastSignalConfig.CLIENT.audioIntensity.get() * (float) (double) LastSignalConfig.CLIENT.ambienceVolume.get();
        if (vol <= 0.01f) return;
        if (bed == null && --bedGap <= 0) {
            SoundEvent ev = AmbientSounds.get(t.bed);
            if (ev != null) {
                bed = new Bed(ev, 0.6f * vol); bedAge = 0;
                mc.getSoundManager().play(bed);
            }
        }
        if (bed != null) bed.target = 0.6f * vol;
        double freq = LastSignalConfig.CLIENT.ambienceFrequency.get() * pr.amb;
        if (--accGap <= 0) {
            accGap = (int) ((500 + RNG.nextInt(1000)) / Math.max(.15, freq));
            if (RNG.nextInt(100) < 22) return;                               // deliberate near-silence
            BlockPos pp = mc.player.blockPosition();
            boolean open = mc.level.canSeeSky(pp);
            int roll = RNG.nextInt(100);
            int idx;
            float dist = 0, v2 = 0.7f;
            if (roll < 8) { idx = 4; dist = 20 + RNG.nextInt(14); v2 = 0.55f; }          // rare, far, unidentifiable
            else if (open) idx = RNG.nextBoolean() ? 1 : 2;
            else idx = RNG.nextBoolean() ? 3 : (RNG.nextBoolean() ? 1 : 2);
            if (idx != 4) dist = 7 + RNG.nextInt(11);
            SoundEvent ev = AmbientSounds.get(t.acc(idx));
            if (ev != null) {
                double a = RNG.nextDouble() * Math.PI * 2;
                mc.level.playLocalSound(mc.player.getX() + Math.cos(a) * dist, mc.player.getY() + RNG.nextInt(3), mc.player.getZ() + Math.sin(a) * dist,
                        ev, SoundSource.AMBIENT, v2 * vol, 0.9f + RNG.nextFloat() * 0.2f, false);
            }
        }
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        if (bed != null) { Minecraft.getInstance().getSoundManager().stop(bed); bed = null; }
        current = ""; fogNow = -1;
    }

    /** Fog: subtle and long. Far plane = profile.fogFar * config multiplier (blocks), starting at 45% of that distance. */
    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        Theme t = themeNow();
        if (t == null || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        if (e.getCamera().getFluidInCamera() != FogType.NONE) return;
        float target = (float) (ProfileData.ALL[t.index - 1].fogFar * LastSignalConfig.CLIENT.fogMultiplier.get());
        fogNow = fogNow < 0 ? Math.max(target, e.getFarPlaneDistance()) : fogNow + (target - fogNow) * 0.02f;     // eases in after entering
        float far = Math.min(fogNow, Math.max(e.getFarPlaneDistance(), 64f));
        e.setNearPlaneDistance(far * 0.45f);
        e.setFarPlaneDistance(far);
        e.setCanceled(true);
    }

    private DimensionAmbience() {}
}
