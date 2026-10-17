package com.lastsignal.block;

import com.lastsignal.story.Notes;
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

/** Memory Log / Abandoned Desk: the note is chosen deterministically from the block position (stable per block). */
public class LoreBlock extends Block {
    public LoreBlock(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerStory st = StoryManager.get(sp);
            Notes.Note n = Notes.forPosition(pos.asLong());
            sp.sendSystemMessage(Component.literal("§e" + n.title() + "\n§7" + n.text()));
            if (st.notes.add(n.id())) StoryManager.onNoteFound(sp, st, n);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
