package com.lastsignal.item;

import com.lastsignal.entity.HorrorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Signal Detector / Distorted Compass: reports distance and bearing of the nearest horror entity. */
public class DetectorItem extends LoreItem {
    private final boolean compass;

    public DetectorItem(Properties p, boolean compass) {
        super(p, compass ? "distorted_compass" : "signal_detector");
        this.compass = compass;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            HorrorEntity best = null;
            double bd = Double.MAX_VALUE;
            for (HorrorEntity h : sl.getEntitiesOfClass(HorrorEntity.class, new AABB(player.blockPosition()).inflate(96))) {
                double d = h.distanceToSqr(player);
                if (d < bd) { bd = d; best = h; }
            }
            if (best == null) {
                player.displayClientMessage(Component.literal(compass ? "The needle spins." : "…"), true);
            } else {
                double dist = Math.sqrt(bd);
                String bars = "▮".repeat(Math.max(1, 10 - (int) (dist / 10)));
                player.displayClientMessage(Component.literal(compass
                        ? "The needle trembles toward the " + dir(player, best)
                        : "Signal " + bars), true);
            }
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.success(stack);
    }

    private static String dir(Player p, HorrorEntity e) {
        double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
        double a = Math.toDegrees(Math.atan2(dz, dx));
        String[] n = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }
}
