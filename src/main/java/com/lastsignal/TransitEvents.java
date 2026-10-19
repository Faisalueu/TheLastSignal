package com.lastsignal;

import com.lastsignal.world.LakeEntry;
import com.lastsignal.world.Transit;
import com.lastsignal.world.gen.ExtraThemes;
import com.lastsignal.world.gen.Themes;
import com.lastsignal.world.gen.Volcano;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fall protection for the fracture blocks: ONLY while the per-player landing tag is set, the final fall damage is clamped so that the player
 * takes no damage. The tag is removed after the landing (or in water / flying / timeout / logout / dimension change). No global effect.
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID)
public final class TransitEvents {
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!e.getSource().is(DamageTypeTags.IS_FALL)) return;
        CompoundTag pd = sp.getPersistentData();
        if (!pd.contains(Transit.LAND_UNTIL)) return;
        if (sp.level().getGameTime() > pd.getLong(Transit.LAND_UNTIL)) { Transit.clear(sp); return; }
        e.setCanceled(true);                         // the transition fall deals no damage...
        Transit.clear(sp);                           // ...and protection ends with this first landing (no permanent immunity)
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        LakeEntry.tick(sp);                                              // cheap: acts every 10th tick, only in the Overworld
        if (sp.tickCount % 5 == 0) {
            var loc = sp.level().dimension().location();
            if (loc.getNamespace().equals(LastSignalMod.MODID) && loc.getPath().equals(ExtraThemes.VOLCANO)
                    && Volcano.inExit(Themes.get(ExtraThemes.VOLCANO), sp.getX(), sp.getY(), sp.getZ())) Transit.exitVolcano(sp);
        }
        CompoundTag pd = sp.getPersistentData();
        if (!pd.contains(Transit.LAND_UNTIL)) return;
        long now = sp.level().getGameTime();
        long start = pd.getLong(Transit.LAND_START);
        if (now > pd.getLong(Transit.LAND_UNTIL)) { Transit.clear(sp); return; }
        if (now - start > 10 && (sp.onGround() || sp.isInWater() || sp.isInLava() || sp.getAbilities().flying || sp.isPassenger() || sp.onClimbable())) Transit.clear(sp);
    }

    @SubscribeEvent
    public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { LakeEntry.reset(sp.getUUID()); Transit.clear(sp); }
    }

    @SubscribeEvent
    public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent e) { LakeEntry.resetAll(); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { LakeEntry.reset(sp.getUUID()); Transit.clear(sp); }
    }

    @SubscribeEvent
    public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) LakeEntry.reset(sp.getUUID());
        if (e.getEntity() instanceof ServerPlayer sp && sp.level().getGameTime() - sp.getPersistentData().getLong(Transit.LAND_START) > 10) Transit.clear(sp);
    }

    private TransitEvents() {}
}
