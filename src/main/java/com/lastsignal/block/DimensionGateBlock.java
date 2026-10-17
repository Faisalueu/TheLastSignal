package com.lastsignal.block;

import com.lastsignal.world.Dimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** TARGET 0 = exit back to where you came from; 1..6 = the six mod dimensions (see Dimensions.IDS). */
public class DimensionGateBlock extends Block {
    public static final IntegerProperty TARGET = IntegerProperty.create("target", 0, 6);

    public DimensionGateBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(TARGET, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TARGET);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) Dimensions.useGate(sp, s.getValue(TARGET));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
