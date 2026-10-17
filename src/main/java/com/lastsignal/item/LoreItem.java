package com.lastsignal.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Base for items whose purpose is a tooltip + story/crafting role. */
public class LoreItem extends Item {
    protected final String id;

    public LoreItem(Properties p, String id) {
        super(p);
        this.id = id;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.lastsignal." + id + ".tooltip").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
    }
}
