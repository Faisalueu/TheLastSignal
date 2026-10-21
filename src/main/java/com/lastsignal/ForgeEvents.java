package com.lastsignal;

import com.lastsignal.command.ModCommands;
import com.lastsignal.entity.NoiseBus;
import com.lastsignal.event.EventManager;
import com.lastsignal.event.Tasks;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.world.Dimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus game events. Everything here runs on the logical server; the client never reaches this code. */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID)
public final class ForgeEvents {
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        ModCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Tasks.tick();
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        long now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                perPlayerSecond(p, now);
            } catch (RuntimeException ex) {
                LastSignalMod.LOGGER.error("Per-player tick failed for {}", p.getName().getString(), ex);
            }
        }
    }

    private static void perPlayerSecond(ServerPlayer p, long now) {
        if (p.isSpectator()) return;
        if (p.isSprinting()) NoiseBus.emit(p.level().dimension().location().toString(), p.position(), p.level().getGameTime(), 1.0f);
        boolean mod = Dimensions.isModDimension(p.level());
        // Void-fall rescue: nobody stays trapped under a floating dimension.
        if (mod && p.getY() < p.level().getMinBuildHeight() - 5 && com.lastsignal.config.LastSignalConfig.COMMON.recoveryEnabled.get()) {
            Dimensions.recover(p);
            return;
        }
        // Passive exposure: slow, capped, so time alone can never push anyone past stage 2.
        if (now % 600 == 0) {
            PlayerStory st = StoryManager.get(p);
            if (st.exposure < 40) { st.exposure += 0.5; StoryManager.markDirty(p); }
        }
        // The Red Silence is a hazard: hunger pulses unless standing near a beacon-lit area (platform is safe by design).
        if (mod && p.level().dimension().location().getPath().equals("red_silence") && now % 200 == 0 && !p.isCreative()) {
            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
        }
        EventManager.tickPlayer(p);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent e) {
        if (e.getLevel().isClientSide() || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        NoiseBus.emit(sl.dimension().location().toString(), net.minecraft.world.phys.Vec3.atCenterOf(e.getPos()), sl.getGameTime(), 1.5f);
    }

    @SubscribeEvent
    public static void wake(PlayerWakeUpEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getEntity() instanceof ServerPlayer p) || e.wakeImmediately()) return;
        PlayerStory st = StoryManager.get(p);
        st.nights++;
        StoryManager.markDirty(p);
        if (st.stage() >= 1 && p.getRandom().nextInt(4) == 0) EventManager.trigger(p, "plr_sleep_clue", true);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) StoryManager.onDeath(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        EventManager.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        EventManager.shutdown();
        NoiseBus.clear();
    }

    /** Full Lantern Warden set: 15% less damage from the signal's creatures. */
    @SubscribeEvent
    public static void wardenDamage(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player p && e.getSource().getEntity() instanceof com.lastsignal.entity.HorrorEntity
                && com.lastsignal.item.GearItems.fullSet(p)) e.setAmount(e.getAmount() * 0.85f);
    }

    /** Full Lantern Warden set: Darkness cannot be applied (counters the Tall Black's step and the Witness's stare). */
    @SubscribeEvent
    public static void wardenDarkness(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player p && e.getEffectInstance().getEffect() == MobEffects.DARKNESS
                && com.lastsignal.item.GearItems.fullSet(p)) e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    private ForgeEvents() {}
}
