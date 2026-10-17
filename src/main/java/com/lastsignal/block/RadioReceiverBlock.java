package com.lastsignal.block;

import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.story.Transmissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Static radio placed in structures. Plays the next unheard transmission that suits its position. */
public class RadioReceiverBlock extends Block {
    public RadioReceiverBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerStory st = StoryManager.get(sp);
            Transmissions.Entry e = Transmissions.pickAtReceiver(sp, st);
            if (e == null) {
                sp.displayClientMessage(Component.translatable("lastsignal.msg.nothing"), true);
            } else {
                sp.sendSystemMessage(Component.literal("§8[receiver] §7" + e.text()));
                if (st.recordings.add(e.id())) StoryManager.onRecordingFound(sp, st, e);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
