package com.lastsignal.world.gen;

/** Tiny deterministic value noise (no world seed: generation depends only on theme + coordinates). */
public final class Noise {
    public static int hash(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (int) (h & 0x7fffffff);
    }

    public static double rnd(long seed, int x, int z) {
        return hash(seed, x, z) / (double) 0x7fffffff;
    }

    private static double sm(double t) { return t * t * (3 - 2 * t); }

    public static double value(long seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = sm(x - x0), fz = sm(z - z0);
        double a = rnd(seed, x0, z0), b = rnd(seed, x0 + 1, z0), c = rnd(seed, x0, z0 + 1), d = rnd(seed, x0 + 1, z0 + 1);
        double top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    public static double fbm(long seed, double x, double z, int oct) {
        double s = 0, a = 1, tot = 0, f = 1;
        for (int i = 0; i < oct; i++) {
            s += value(seed + i * 131L, x * f, z * f) * a;
            tot += a; a *= .5; f *= 2;
        }
        return s / tot;
    }

    private Noise() {}
}
