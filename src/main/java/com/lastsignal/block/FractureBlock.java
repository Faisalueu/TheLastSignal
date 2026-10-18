package com.lastsignal.block;

import com.lastsignal.world.Transit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Broken-looking transition block. Walk into it (it has no collision): BLACK = another dimension (~40 block fall), WHITE = Overworld (~120 block fall). */
public class FractureBlock extends Block {
    private final boolean black;

    public FractureBlock(Properties p, boolean black) {
        super(p);
        this.black = black;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp)) return;
        Transit.touch(sp, pos, black);
    }
}
