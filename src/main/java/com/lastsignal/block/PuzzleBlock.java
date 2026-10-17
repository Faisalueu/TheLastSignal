package com.lastsignal.block;

import com.lastsignal.puzzle.PuzzleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Symbol blocks for the sequence puzzle: pressing reports the symbol to the PuzzleManager (server-side). */
public class PuzzleBlock extends Block {
    public PuzzleBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            boolean circle = s.getBlock() == ModBlocks.BY_ID.get("puzzle_block_a").get();
            PuzzleManager.get(sl).pressSymbol(sl, pos, sp, circle ? 0 : 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
