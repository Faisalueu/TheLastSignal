package com.lastsignal.client;

import com.lastsignal.LastSignalMod;
import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Presentation-only effects triggered by the server (EffectPacket). Fully reversible: every effect has a tick
 * budget and simply expires; nothing touches game state, files or the window. Respects the accessibility
 * options (disableScreenEffects / disableFlashing / disableJumpscares / audioIntensity).
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID, value = Dist.CLIENT)
public final class ClientEffects {
    private static final class Fx { int left, total; float intensity; }
    private static final Map<String, Fx> ACTIVE = new HashMap<>();
    private static final Random RNG = new Random();

    public static void apply(String effect, int ticks, float intensity) {
        if (ticks <= 0) return;
        boolean flashy = effect.equals("glitch") || effect.equals("flash") || effect.equals("static");
        if (LastSignalConfig.CLIENT.disableScreenEffects.get() && !effect.equals("tension")) return;
        if (flashy && LastSignalConfig.CLIENT.disableFlashing.get()) { intensity *= 0.35f; }
        if (effect.equals("glitch") && LastSignalConfig.CLIENT.disableJumpscares.get()) return;
        Fx f = ACTIVE.computeIfAbsent(effect, k -> new Fx());
        f.left = Math.min(ticks, 20 * 30);
        f.total = f.left;
        f.intensity = Math.max(0f, Math.min(1f, intensity));
        if (effect.equals("tension") || effect.equals("static") || effect.equals("glitch")) {
            float vol = (float) (f.intensity * LastSignalConfig.CLIENT.audioIntensity.get());
            if (vol > 0.01f) {
                var snd = effect.equals("tension") ? ModSounds.CHASE_TENSION.get() : effect.equals("glitch") ? ModSounds.GLITCH.get() : ModSounds.STATIC_LOOP.get();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(snd, 1.0f, vol));
            }
        }
    }

    private static float level(String effect) {
        Fx f = ACTIVE.get(effect);
        if (f == null || f.left <= 0) return 0f;
        float t = f.left / (float) f.total;                 // 1 -> 0
        float fade = Math.min(1f, Math.min(t * 4f, (1f - t) * 8f + 0.2f));
        return f.intensity * Math.max(0f, fade);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        ACTIVE.values().forEach(f -> { if (f.left > 0) f.left--; });
        ACTIVE.values().removeIf(f -> f.left <= 0);
    }

    @SubscribeEvent
    public static void gui(RenderGuiEvent.Post e) {
        if (ACTIVE.isEmpty()) return;
        var g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        boolean calm = LastSignalConfig.CLIENT.disableFlashing.get();

        float st = Math.max(level("static"), level("glitch"));
        if (st > 0.01f) {
            if (calm) {
                g.fill(0, 0, w, h, ((int) (st * 70) << 24) | 0x808080);
            } else {
                int specks = (int) (st * 260);
                for (int i = 0; i < specks; i++) {
                    int x = RNG.nextInt(w), y = RNG.nextInt(h), s = 1 + RNG.nextInt(3), c = 90 + RNG.nextInt(150);
                    g.fill(x, y, x + s * 2, y + s, ((int) (st * 160) << 24) | (c << 16) | (c << 8) | c);
                }
            }
        }
        float sh = level("shadow");
        if (sh > 0.01f) {
            Fx f = ACTIVE.get("shadow");
            float p = 1f - f.left / (float) f.total;
            int x = (int) (p * (w + 200)) - 200;
            g.fill(Mth.clamp(x, 0, w), 0, Mth.clamp(x + 200, 0, w), h, ((int) (sh * 200) << 24));
            g.fill(0, 0, w, h, ((int) (sh * 60) << 24));
        }
        float fg = level("fog");
        if (fg > 0.01f) g.fill(0, 0, w, h, ((int) (fg * 40) << 24) | 0xA0A0A0);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        float f = level("fog");
        if (f <= 0.01f) return;
        e.setNearPlaneDistance(0f);
        e.setFarPlaneDistance(Mth.lerp(f, e.getFarPlaneDistance(), 14f));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float s = Math.max(level("shake"), level("distort") * 0.5f);
        if (s <= 0.01f) return;
        float t = (float) (e.getPartialTick() + Minecraft.getInstance().player.tickCount);
        e.setRoll(e.getRoll() + Mth.sin(t * 0.9f) * s * 1.5f);
        e.setPitch(e.getPitch() + Mth.sin(t * 1.7f) * s * 0.8f);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        float d = level("distort");
        if (d <= 0.01f) return;
        float t = (float) (e.getPartialTick() + Minecraft.getInstance().player.tickCount);
        e.setFOV(e.getFOV() + Mth.sin(t * 0.25f) * d * 7f);
    }

    private ClientEffects() {}
}
