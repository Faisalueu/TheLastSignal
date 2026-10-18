package com.lastsignal;

import com.lastsignal.world.Transit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fall protection for the fracture blocks: ONLY while the per-player landing tag is set, the final fall damage is clamped so that the player
 * keeps at least 2 health. The tag is removed after the landing (or in water / flying / timeout / logout / dimension change). No global effect.
 */
@Mod.EventBusSubscriber(modid = LastSignalMod.MODID)
public final class TransitEvents {
    @SubscribeEvent
    public static void damage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!e.getSource().is(DamageTypeTags.IS_FALL)) return;
        CompoundTag pd = sp.getPersistentData();
        if (!pd.contains(Transit.LAND_UNTIL)) return;
        if (sp.level().getGameTime() > pd.getLong(Transit.LAND_UNTIL)) { Transit.clear(sp); return; }
        float max = Math.max(0f, sp.getHealth() + sp.getAbsorptionAmount() - 2f);
        if (e.getAmount() > max) e.setAmount(max);
        Transit.clear(sp);                           // one landing = one clamp
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        CompoundTag pd = sp.getPersistentData();
        if (!pd.contains(Transit.LAND_UNTIL)) return;
        long now = sp.level().getGameTime();
        long start = pd.getLong(Transit.LAND_START);
        if (now > pd.getLong(Transit.LAND_UNTIL)) { Transit.clear(sp); return; }
        if (now - start > 10 && (sp.onGround() || sp.isInWater() || sp.isInLava() || sp.getAbilities().flying || sp.isPassenger() || sp.onClimbable())) Transit.clear(sp);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Transit.clear(sp);
    }

    @SubscribeEvent
    public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.level().getGameTime() - sp.getPersistentData().getLong(Transit.LAND_START) > 10) Transit.clear(sp);
    }

    private TransitEvents() {}
}
