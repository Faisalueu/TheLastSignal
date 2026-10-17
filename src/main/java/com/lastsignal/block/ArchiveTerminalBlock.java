package com.lastsignal.block;

import com.lastsignal.item.ModItems;
import com.lastsignal.puzzle.PuzzleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Archive Terminal: Power Cell restores power for the area (puzzle "power"); Archive Key reads archives. */
public class ArchiveTerminalBlock extends Block {
    public ArchiveTerminalBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            var held = player.getItemInHand(hand);
            if (held.getItem() == ModItems.ITEMS_BY_ID.get("power_cell").get()) {
                if (PuzzleManager.get(sl).restorePower(sl, pos, sp)) {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                }
            } else if (!PuzzleManager.get(sl).isPowered(pos)) {
                sp.displayClientMessage(Component.literal("No power. The screen shows a single line: INSERT CELL."), true);
            } else {
                PuzzleManager.get(sl).readArchive(sl, pos, sp);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
