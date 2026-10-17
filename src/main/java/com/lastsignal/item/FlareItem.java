package com.lastsignal.item;

import com.lastsignal.entity.HorrorEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Emergency Flare: nearby horror entities that respect light are forced into Retreating. */
public class FlareItem extends LoreItem {
    public FlareItem(Properties p) {
        super(p, "emergency_flare");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            AABB box = player.getBoundingBox().inflate(24);
            for (HorrorEntity h : sl.getEntitiesOfClass(HorrorEntity.class, box)) h.scareFromLight(200);
            sl.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.05);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 100);
        }
        return InteractionResultHolder.success(stack);
    }
}
