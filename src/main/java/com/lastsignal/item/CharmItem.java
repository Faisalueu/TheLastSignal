package com.lastsignal.item;

import com.lastsignal.story.StoryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Protective Charm: grants ~5 minutes of Signal Ward (threat director skips hostile selection). */
public class CharmItem extends LoreItem {
    public CharmItem(Properties p) {
        super(p, "protective_charm");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            StoryManager.get(sp).wardUntil = level.getGameTime() + 20L * 60 * 5;
            StoryManager.markDirty(sp);
            sp.displayClientMessage(Component.literal("The static recedes."), true);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }
}
