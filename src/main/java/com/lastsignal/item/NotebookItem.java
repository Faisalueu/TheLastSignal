package com.lastsignal.item;

import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.story.Transmissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Prints the player's journal (collected recordings and notes) to chat. Server-authoritative. */
public class NotebookItem extends LoreItem {
    public NotebookItem(Properties p) {
        super(p, "research_notebook");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerStory st = StoryManager.get(sp);
            sp.sendSystemMessage(Component.literal("§6— Research Notebook —"));
            sp.sendSystemMessage(Component.literal("§7Recordings: " + st.recordings.size() + "/" + Transmissions.ALL.size()
                    + "   Notes: " + st.notes.size() + "/" + com.lastsignal.story.Notes.ALL.size()));
            int shown = 0;
            for (String id : st.recordings) {
                Transmissions.Entry e = Transmissions.byId(id);
                if (e != null && shown++ < 8) sp.sendSystemMessage(Component.literal("§8• §7" + e.text()));
            }
            for (String id : st.notes) {
                var n = com.lastsignal.story.Notes.byId(id);
                if (n != null && shown++ < 12) sp.sendSystemMessage(Component.literal("§8• §e" + n.title() + "§7: " + n.text()));
            }
        }
        return InteractionResultHolder.success(stack);
    }
}
