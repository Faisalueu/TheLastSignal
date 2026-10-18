package com.lastsignal.world.gen;

import com.lastsignal.block.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** One Phase-1 dimension: terrain kind, material roles, feature list, glyph bitmaps, parameters, atmosphere and sound ids. */
public final class Theme {
    // role indices (same order as tools/dim_spec.ROLE_ORDER)
    public static final int GROUND = 0, SUB = 1, STONE = 2, WALL = 3, FLOOR = 4, CEIL = 5, ACCENT = 6, MARK = 7, LIGHT = 8, GLASS = 9;
    // numeric parameter indices (same order as tools/dim_gen.PARAMS)
    public static final int P_H = 0, P_LIGHT = 1, P_MARK = 2, P_MURAL = 3, P_FEAT = 4, P_BASE = 5, P_AMP = 6, P_SCALE = 7, P_SEA = 8,
            P_OPEN = 9, P_ROOF = 10, P_VA = 11, P_VB = 12;

    public final int index;
    public final String slug, name, kind;
    public final String[] roleIds, feats;
    public final String[][] glyph;
    public final double[] p;
    public final Set<String> flags = new HashSet<>();
    public final int fogColor;
    public final float fogFar;
    public final String bed, acc1, acc2;
    public final float bedSecs;
    public final long seed;
    private volatile BlockState[] roles;

    public Theme(int index, String slug, String name, String kind, String[] roleIds, String[] feats, String[][] glyph, double[] p,
                 String flags, int fogColor, float fogFar, String bed, String acc1, String acc2, float bedSecs) {
        this.index = index; this.slug = slug; this.name = name; this.kind = kind; this.roleIds = roleIds; this.feats = feats;
        this.glyph = glyph; this.p = p; this.fogColor = fogColor; this.fogFar = fogFar; this.bed = bed; this.acc1 = acc1; this.acc2 = acc2;
        this.bedSecs = bedSecs;
        if (!flags.isEmpty()) this.flags.addAll(Arrays.asList(flags.split(",")));
        this.seed = 0x5EEDL * index + slug.hashCode();
    }

    public BlockState role(int r) {
        BlockState[] a = roles;
        if (a == null) {
            a = new BlockState[roleIds.length];
            for (int i = 0; i < a.length; i++) a[i] = resolve(roleIds[i], i);
            roles = a;
        }
        return a[r];
    }

    private static BlockState resolve(String id, int role) {
        if (id.isEmpty()) return role == GLASS ? Blocks.GLASS.defaultBlockState() : Blocks.STONE.defaultBlockState();
        Block b;
        if (id.indexOf(':') >= 0) b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        else b = ModBlocks.BY_ID.get(id) != null ? ModBlocks.BY_ID.get(id).get() : null;
        return b == null ? Blocks.STONE.defaultBlockState() : b.defaultBlockState();
    }

    public String acc(int i) { return "acc_" + slug + "_" + i; }

    public boolean has(String flag) { return flags.contains(flag); }

    public double param(int i) { return p[i]; }

    public boolean hasRole(int r) { return !roleIds[r].isEmpty(); }
}
