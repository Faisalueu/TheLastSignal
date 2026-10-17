package com.lastsignal.block;

import com.lastsignal.config.LastSignalConfig;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
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

/**
 * Safe checkpoint / recovery: use once to attune. Using it again (or /lastsignal recover) from anywhere
 * returns the player to the attuned beacon. Hostile mod entities will not target players next to a beacon
 * (checked in HorrorEntity#canTargetPlayer). Also lowers exposure a little.
 */
public class CheckpointBeaconBlock extends Block {
    public CheckpointBeaconBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerStory st = StoryManager.get(sp);
            st.checkpointDim = level.dimension().location().toString();
            st.checkpointPos = pos.above().asLong();
            st.exposure = Math.max(0, st.exposure - 20);
            StoryManager.markDirty(sp);
            sp.displayClientMessage(Component.literal("Checkpoint attuned."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean enabled() {
        return LastSignalConfig.COMMON.recoveryEnabled.get();
    }
}
