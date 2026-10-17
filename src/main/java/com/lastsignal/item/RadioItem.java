package com.lastsignal.item;

import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.story.Transmissions;
import com.lastsignal.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Broken Radio. Use = listen on the current band (server picks a transmission valid for
 * location + story stage). Sneak+use = cycle band. Having a Frequency Tuner in the offhand
 * allows fine tuning (+1/-1 step) via sneak+use instead of band cycling.
 */
public class RadioItem extends LoreItem {
    public RadioItem(Properties p) {
        super(p, "broken_radio");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        PlayerStory st = StoryManager.get(sp);
        if (player.isShiftKeyDown()) {
            boolean tuner = player.getOffhandItem().getItem() == ModItems.ITEMS_BY_ID.get("frequency_tuner").get();
            if (tuner) st.fine = (st.fine + 1) % 10;
            else st.band = (st.band + 1) % Transmissions.BANDS;
            level.playSound(null, player.blockPosition(), ModSounds.RADIO_TUNE.get(), SoundSource.PLAYERS, 0.6f, 1f);
            sp.displayClientMessage(Component.literal(String.format("%d.%d MHz", 80 + st.band * 7, st.fine)), true);
            StoryManager.markDirty(sp);
            return InteractionResultHolder.success(stack);
        }
        Transmissions.Entry e = Transmissions.pick(sp, st);
        level.playSound(null, player.blockPosition(), e == null ? ModSounds.STATIC_LOOP.get() : ModSounds.RADIO_BLIP.get(),
                SoundSource.PLAYERS, 0.7f, 1f);
        if (e == null) {
            sp.displayClientMessage(Component.translatable("lastsignal.msg.nothing"), true);
        } else {
            sp.sendSystemMessage(Component.literal("§8[" + String.format("%d.%d", 80 + st.band * 7, st.fine) + "] §7" + e.text()));
            if (st.recordings.add(e.id())) {
                StoryManager.onRecordingFound(sp, st, e);
                sp.displayClientMessage(Component.translatable("lastsignal.msg.recorded"), true);
            }
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(stack);
    }
}
