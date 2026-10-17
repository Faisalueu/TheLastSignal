package com.lastsignal.entity;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Tiny server-side bus of "sounds" (sprinting, block breaking) that hunters can investigate.
 * Bounded size, entries expire quickly; touched only from the server thread.
 */
public final class NoiseBus {
    public record Noise(String dim, Vec3 pos, long tick, float loudness) {}

    private static final ArrayDeque<Noise> NOISES = new ArrayDeque<>();
    private static final int MAX = 48;

    public static void emit(String dim, Vec3 pos, long tick, float loudness) {
        if (NOISES.size() >= MAX) NOISES.pollFirst();
        NOISES.addLast(new Noise(dim, pos, tick, loudness));
    }

    /** Loudest recent noise within range*loudness of pos in this dimension, or null. */
    public static Noise nearest(String dim, Vec3 from, long now, double range) {
        Noise best = null;
        double bd = Double.MAX_VALUE;
        for (Iterator<Noise> it = NOISES.iterator(); it.hasNext(); ) {
            Noise n = it.next();
            if (now - n.tick() > 200) { it.remove(); continue; }
            if (!n.dim().equals(dim)) continue;
            double d = n.pos().distanceTo(from);
            if (d <= range * n.loudness() && d < bd) { bd = d; best = n; }
        }
        return best;
    }

    public static void clear() { NOISES.clear(); }

    private NoiseBus() {}
}
