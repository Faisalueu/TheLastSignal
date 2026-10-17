package com.lastsignal.block;

import com.lastsignal.item.ModItems;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sealed Door: unbreakable until opened with an Archive Key, Long Hall Key, Red Key or Rusted Keycard.
 * Opening consumes one key use and removes a small 1x2x1 column (so it re-seals for nobody; it is permanent).
 */
public class SealedDoorBlock extends Block {
    public SealedDoorBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level.isClientSide);
        ItemStack held = player.getItemInHand(hand);
        String[] keys = {"archive_key", "long_hall_key", "red_key", "rusted_keycard"};
        boolean ok = false;
        for (String k : keys) {
            if (held.getItem() == ModItems.ITEMS_BY_ID.get(k).get()) { ok = true; break; }
        }
        if (!ok) {
            sp.displayClientMessage(Component.translatable("lastsignal.msg.locked"), true);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 0.6f);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (level.getBlockState(pos.above()).getBlock() == this) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.5f);
        PlayerStory st = StoryManager.get(sp);
        st.doorsOpened++;
        StoryManager.markDirty(sp);
        if (!player.getAbilities().instabuild && held.getItem() == ModItems.ITEMS_BY_ID.get("rusted_keycard").get()) held.shrink(1);
        return InteractionResult.CONSUME;
    }
}
