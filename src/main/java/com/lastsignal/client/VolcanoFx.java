package com.lastsignal.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.sound.ModSounds;
import com.lastsignal.world.gen.ExtraThemes;
import com.lastsignal.world.gen.Themes;
import com.lastsignal.world.gen.Volcano;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Client-only Infernal Volcano layer: lavafall particles and nearby lava sound, plus a sparse schedule of layered, spatial sound events
 * (distant eruption, rocks breaking, heavy impact, far creature call, cave noise, steps behind you, sudden silence) and a soft bell when the real exit is near.
 * One event per 25-70 s (scaled by the ambience-frequency setting), each at its own distance; nothing plays continuously. Volume follows the audio settings.
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, value = Dist.CLIENT)
public final class VolcanoFx {
    private static final Random RNG = new Random();
    private static int ticks, nextEvent = 400, silence, bellCd;
    private static boolean inVolcano, announcedExit;

    public static boolean silent() { return silence > 0; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { inVolcano = false; return; }
        boolean here = mc.level.dimension().location().equals(new net.minecraft.resources.ResourceLocation(LastSignalMod.MODID, ExtraThemes.VOLCANO));
        if (!here) { inVolcano = false; silence = 0; announcedExit = false; return; }
        if (mc.isPaused()) return;
        float vol = (float) (double) LastSignalConfig.CLIENT.audioIntensity.get() * (float) (double) LastSignalConfig.CLIENT.ambienceVolume.get();
        var t = Themes.get(ExtraThemes.VOLCANO);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        if (!inVolcano) {                                                  // entering: one deep rumble
            inVolcano = true; nextEvent = 300 + RNG.nextInt(300); ticks = 0;
            if (vol > .01f) mc.level.playLocalSound(px, py, pz, ModSounds.RUMBLE.get(), SoundSource.AMBIENT, 0.7f * vol, 0.6f, false);
        }
        ticks++;
        if (silence > 0) silence--;
        boolean particles = mc.options.particles().get() != ParticleStatus.MINIMAL;
        // lavafalls: particles along the column (every 4 ticks), lava sound at the nearest (every 30 ticks)
        if (ticks % 4 == 0 || ticks % 30 == 0) {
            double nearest = 1e9; int[] near = null;
            for (int[] a : Volcano.lavafallsNear(t, px, pz, 56)) {
                double d = Math.hypot(a[0] - px, a[1] - pz);
                if (d < nearest) { nearest = d; near = a; }
                if (particles && ticks % 4 == 0 && d < 48) {
                    int span = Math.max(1, a[2] - 4 - (a[3] + 1));
                    for (int i = 0; i < 2; i++)
                        mc.level.addParticle(ParticleTypes.FALLING_LAVA, a[0] + .5 + (RNG.nextDouble() - .5) * .6, a[3] + 1 + RNG.nextInt(span), a[1] + .5 + (RNG.nextDouble() - .5) * .6, 0, -.1, 0);
                    if (RNG.nextInt(2) == 0) mc.level.addParticle(ParticleTypes.LAVA, a[0] + .5 + (RNG.nextDouble() - .5) * 2, a[3] + 1.2, a[1] + .5 + (RNG.nextDouble() - .5) * 2, 0, 0, 0);
                    if (RNG.nextInt(6) == 0) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, a[0] + .5, a[2] + 1.5, a[1] + .5, 0, .05, 0);
                }
            }
            if (ticks % 30 == 0 && near != null && nearest < 40 && vol > .01f)
                mc.level.playLocalSound(near[0] + .5, near[3] + 2, near[1] + .5, SoundEvents.LAVA_AMBIENT, SoundSource.AMBIENT, (float) (0.9 * (1 - nearest / 40.0)) * vol, 0.55f, false);
        }
        // the real exit: a soft, low bell when within 70 blocks (heard at most every 25 s), and one deeper note the first time you are within 22
        double de = Math.hypot(px - Volcano.exitX(t), pz - Volcano.exitZ(t));
        if (bellCd > 0) bellCd--;
        if (vol > .01f && bellCd == 0 && de < 70) {
            bellCd = 500;
            boolean close = de < 22 && !announcedExit;
            if (close) announcedExit = true;
            Vec3 dir = new Vec3(Volcano.exitX(t) - px, 0, Volcano.exitZ(t) - pz).normalize();
            double dist = Math.min(de, 12);
            mc.level.playLocalSound(px + dir.x * dist, py, pz + dir.z * dist, ModSounds.BELL_DISTANT.get(), SoundSource.AMBIENT, (close ? 0.8f : 0.45f) * vol, close ? 0.5f : 0.75f, false);
        }
        if (vol <= .01f || --nextEvent > 0) return;
        double freq = Math.max(.15, LastSignalConfig.CLIENT.ambienceFrequency.get());
        nextEvent = (int) ((500 + RNG.nextInt(900)) / freq);
        double a = RNG.nextDouble() * Math.PI * 2;
        int ev = RNG.nextInt(100);
        if (ev < 22) at(mc, ModSounds.RUMBLE.get(), px, py, pz, a, 70 + RNG.nextInt(20), 0.9f * vol, 0.45f);                // distant eruption
        else if (ev < 38) { at(mc, SoundEvents.STONE_BREAK, px, py, pz, a, 10 + RNG.nextInt(15), 0.8f * vol, 0.7f); at(mc, SoundEvents.GRAVEL_BREAK, px, py, pz, a + .2, 12 + RNG.nextInt(15), 0.7f * vol, 0.6f); }   // rocks breaking
        else if (ev < 50) at(mc, SoundEvents.GENERIC_EXPLODE, px, py, pz, a, 45 + RNG.nextInt(20), 0.35f * vol, 0.4f);        // heavy impact
        else if (ev < 63) at(mc, ModSounds.DISTANT_CALL.get(), px, py, pz, a, 55 + RNG.nextInt(25), 0.6f * vol, 0.6f);         // far creature
        else if (ev < 75) at(mc, ModSounds.CREATURE_BREATH.get(), px, py - 2, pz, a, 14 + RNG.nextInt(10), 0.5f * vol, 0.5f);  // something deep in a cave
        else if (ev < 88) {                                                                                                    // steps behind you
            Vec3 back = mc.player.getLookAngle().multiply(-1, 0, -1).normalize();
            mc.level.playLocalSound(px + back.x * 5, py, pz + back.z * 5, ModSounds.FOOTSTEP_FAR.get(), SoundSource.AMBIENT, 0.6f * vol, 0.8f, false);
        } else silence = 200 + RNG.nextInt(200);                                                                              // sudden silence (10-20 s)
    }

    private static void at(Minecraft mc, SoundEvent s, double px, double py, double pz, double ang, double dist, float v, float pitch) {
        double d = Math.min(dist, 24);        // sound range limits how far a local sound can be placed; "far" is expressed by volume/pitch instead
        mc.level.playLocalSound(px + Math.cos(ang) * d, py, pz + Math.sin(ang) * d, s, SoundSource.AMBIENT, v * (float) Math.max(.35, 1 - dist / 120.0), pitch, false);
    }

    private VolcanoFx() {}
}
