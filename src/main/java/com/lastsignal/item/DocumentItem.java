package com.lastsignal.item;

import com.lastsignal.story.Notes;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Consumable document: reveals the next unread note and files it into the notebook. */
public class DocumentItem extends LoreItem {
    public DocumentItem(Properties p, String id) {
        super(p, id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        PlayerStory st = StoryManager.get(sp);
        Notes.Note n = Notes.nextUnread(st, sp.getRandom().nextInt());
        if (n == null) {
            sp.displayClientMessage(Component.literal("There is nothing more to learn from this."), true);
            return InteractionResultHolder.fail(stack);
        }
        st.notes.add(n.id());
        StoryManager.onNoteFound(sp, st, n);
        sp.sendSystemMessage(Component.literal("§e" + n.title() + "\n§7" + n.text()));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }
}
