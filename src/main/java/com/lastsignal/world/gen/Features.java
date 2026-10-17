package com.lastsignal.world.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small chunk-local set pieces. Every builder only writes inside the current chunk (Gen.set clips), so structures at
 * chunk borders are simply cut off: a deliberate trade-off for deterministic, async-safe generation.
 */
final class Features {
    static void build(String name, Gen g, int base, int H, boolean indoor) {
        int x = g.px(), z = g.pz();
        int y;
        if (indoor) y = base;
        else {
            int gr = g.groundAt(x, z);
            if (gr < 0) return;
            y = gr + 1;
        }
        int hh = indoor ? Math.max(2, H - 1) : 12;
        BlockState wall = g.S(Theme.WALL), stone = g.S(Theme.STONE), acc = g.S(Theme.ACCENT), mark = g.S(Theme.MARK),
                glass = g.S(Theme.GLASS), floor = g.S(Theme.FLOOR), ceil = g.S(Theme.CEIL), ground = g.S(Theme.GROUND);
        switch (name) {
            case "pillars" -> { int n = g.rnd(2, 4); for (int i = 0; i < n; i++) pillar(g, g.px(), y, g.pz(), Math.min(hh, g.rnd(4, 9)), wall, acc); }
            case "ruinpillars" -> { int n = g.rnd(2, 4); for (int i = 0; i < n; i++) pillar(g, g.px(), y, g.pz(), g.rnd(2, 7), stone, acc); }
            case "altar" -> { found(g, x - 1, z - 1, 3, 3, y, stone); g.box(x - 1, y, z - 1, 3, 1, 3, stone); g.set(x, y + 1, z, mark); g.set(x - 1, y + 1, z, acc); g.set(x + 1, y + 1, z, acc); g.lampAt(x, y + 3, z); }
            case "rubble" -> { int n = g.rnd(8, 16); for (int i = 0; i < n; i++) { int rx = g.px(), rz = g.pz(); int ry = indoor ? y : Math.max(y, g.groundAt(rx, rz) + 1); if (ry <= 0) continue; g.set(rx, ry, rz, g.chance(.5) ? stone : wall); if (g.chance(.3)) g.set(rx, ry + 1, rz, stone); } }
            case "chains" -> { if (g.ceilY > 0) { int n = g.rnd(3, 6); for (int i = 0; i < n; i++) { int cx = g.px(), cz = g.pz(), len = g.rnd(3, Math.max(3, hh - 2)); for (int k = 1; k <= len; k++) g.set(cx, g.ceilY - k, cz, Blocks.CHAIN.defaultBlockState()); } } else { g.col(x, y, z, 5, stone); g.set(x + 1, y + 4, z, stone); for (int k = 1; k <= 3; k++) g.set(x + 1, y + 4 - k, z, Blocks.CHAIN.defaultBlockState()); } }
            case "house" -> house(g, x - 2, z - 2, y, wall, ceil, floor, acc);
            case "well" -> { found(g, x - 1, z - 1, 3, 3, y, stone); g.hollow(x - 1, y, z - 1, 3, 1, 3, stone); g.set(x, y, z, Blocks.WATER.defaultBlockState()); g.col(x - 1, y + 1, z - 1, 2, g.S(Theme.WALL)); g.col(x + 1, y + 1, z + 1, 2, g.S(Theme.WALL)); g.box(x - 1, y + 3, z - 1, 3, 1, 3, ceil); }
            case "fence" -> { int len = g.rnd(5, 10); for (int i = 0; i < len; i++) if (i % 4 != 3) { int gy = g.groundAt(Math.min(15, x + i), z); if (gy < 0) continue; g.set(x + i, gy + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState()); } }
            case "deadtree" -> { int h = g.rnd(5, 8); BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState(); g.col(x, y, z, h, log); g.set(x + 1, y + h - 2, z, log); g.set(x + 2, y + h - 1, z, log); g.set(x - 1, y + h - 3, z, log); g.set(x - 2, y + h - 2, z, log); }
            case "pews" -> { BlockState p = Blocks.DARK_OAK_SLAB.defaultBlockState(); for (int row = 0; row < 3; row++) for (int i = 0; i < 6; i++) if (i != 2 && i != 3 || row == 1 && false) g.set(x - 3 + i, y, z - 3 + row * 3, p); }
            case "arch", "arches" -> { int n = name.equals("arches") ? 3 : 1; for (int k = 0; k < n; k++) { int ax = x - 3 + k * 3, h = Math.min(hh, 6); g.col(ax, y, z - 1, h, wall); g.col(ax, y, z + 2, h, wall); for (int zz = z - 1; zz <= z + 2; zz++) g.set(ax, y + h, zz, acc); } }
            case "shelves" -> { BlockState b = Blocks.BOOKSHELF.defaultBlockState(); int h = Math.min(3, hh); for (int i = 0; i < 7; i += 1) if (i % 4 != 3) g.col(x - 3 + i, y, z, h, b); for (int i = 0; i < 7; i++) if (i % 4 != 3) g.col(x - 3 + i, y, z + 3, h, b); }
            case "desks" -> { for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) { g.set(x - 3 + i * 2, y, z - 2 + j * 3, acc); g.set(x - 3 + i * 2, y, z - 1 + j * 3, Blocks.DARK_OAK_SLAB.defaultBlockState()); } }
            case "seats" -> { for (int row = 0; row < 3; row++) for (int i = 0; i < 6; i++) g.set(x - 3 + i, y + row / 1 * 0, z - 3 + row * 2, row == 1 ? floor : acc); }
            case "cubicles" -> { for (int k = 0; k < 2; k++) { g.hollow(x - 4 + k * 4, y, z - 1, 3, 2, 3, glass); g.set(x - 3 + k * 4, y, z - 1, Blocks.AIR.defaultBlockState()); g.set(x - 3 + k * 4, y, z + 1, acc); } }
            case "beds" -> { for (int k = 0; k < 3; k++) { g.box(x - 4 + k * 3, y, z - 1, 2, 1, 1, floor); g.box(x - 4 + k * 3, y + 1, z - 1, 2, 1, 1, acc); g.set(x - 4 + k * 3, y, z - 2, wall); } }
            case "cases" -> { for (int k = 0; k < 2; k++) { g.hollow(x - 3 + k * 4, y, z - 1, 2, 3, 2, glass); g.box(x - 3 + k * 4, y + 3, z - 1, 2, 1, 2, glass); g.set(x - 3 + k * 4, y, z - 1, mark); } }
            case "machines" -> { g.box(x - 2, y, z - 1, 4, 2, 2, stone); g.box(x - 2, y + 2, z, 4, 1, 1, acc); g.set(x - 1, y + 2, z - 1, g.S(Theme.LIGHT)); g.set(x + 1, y + 1, z - 2, mark); }
            case "racks" -> { for (int i = 0; i < 4; i++) g.col(x - 3 + i * 2, y, z, Math.min(4, hh), Blocks.IRON_BARS.defaultBlockState()); for (int i = 0; i < 7; i++) g.set(x - 3 + i, y + Math.min(4, hh), z, stone); }
            case "tracks" -> { int len = g.rnd(8, 14); for (int i = 0; i < len; i++) { int zz = Math.min(15, z - 2 + i); int gy = indoor ? y - 1 : g.groundAt(x, zz); if (gy < 0) continue; g.set(x, gy + 1, zz, Blocks.RAIL.defaultBlockState()); } }
            case "conduits" -> { for (int i = 0; i < 9; i++) { g.set(x - 4 + i, y, z, acc); if (i % 3 == 1) g.set(x - 4 + i, y + 1, z, g.S(Theme.LIGHT).getBlock() instanceof net.minecraft.world.level.block.LanternBlock ? acc : g.S(Theme.LIGHT)); } }
            case "pipes" -> { int py = indoor ? Math.max(y + 1, g.ceilY - 1) : y; for (int i = 0; i < 10; i++) g.set(x - 4 + i, py, z, ceil); for (int k = 0; k < 3; k++) g.set(x - 4 + k * 4, py - 1, z, ceil); }
            case "mirrors" -> { for (int k = 0; k < 3; k++) for (int j = 0; j < 3; j++) for (int i = 0; i < 2; i++) g.set(x - 4 + k * 3 + i, y + j, z, glass); for (int k = 0; k < 3; k++) { g.set(x - 4 + k * 3 - 1, y, z, acc); g.set(x - 4 + k * 3 + 2, y, z, acc); } }
            case "niches", "ruinwall" -> { int len = g.rnd(6, 9), h = g.rnd(3, 5); for (int i = 0; i < len; i++) for (int j = 0; j < h; j++) { if (name.equals("ruinwall") && g.chance(.15 + j * .12)) continue; int gy = indoor ? y - 1 : g.groundAt(Math.min(15, x - 3 + i), z); if (gy < 0) continue; boolean n = name.equals("niches") && i % 3 == 1 && j == 1; g.set(x - 3 + i, (indoor ? y : gy + 1) + j, z, n ? g.S(Theme.LIGHT).getBlock() instanceof net.minecraft.world.level.block.LanternBlock ? mark : g.S(Theme.LIGHT) : (g.chance(.1) ? acc : wall)); } }
            case "tombs" -> { for (int k = 0; k < 3; k++) { int tx = x - 3 + k * 3; g.box(tx, y, z, 1, 1, 2, stone); g.set(tx, y + 1, z + 2, k == 1 ? mark : stone); g.set(tx, y + 2, z + 2, stone); } }
            case "cloister" -> { for (int i = 0; i < 9; i += 2) { pillar(g, x - 4 + i, y, z - 4, Math.min(hh, 4), wall, acc); pillar(g, x - 4 + i, y, z + 4, Math.min(hh, 4), wall, acc); pillar(g, x - 4, y, z - 4 + i, Math.min(hh, 4), wall, acc); pillar(g, x + 4, y, z - 4 + i, Math.min(hh, 4), wall, acc); } g.set(x, y, z, mark); }
            case "basalt" -> { int n = g.rnd(5, 9); for (int i = 0; i < n; i++) { int bx = g.px(), bz = g.pz(); int gy = g.groundAt(bx, bz); if (gy < 0) continue; g.col(bx, gy + 1, bz, g.rnd(2, 8), stone); } }
            case "coralspire", "icespire" -> { int h = g.rnd(7, 13); BlockState b = name.equals("icespire") && g.chance(.5) ? glass : acc; for (int k = 0; k < h; k++) { int rad = k < h / 3 ? 1 : 0; g.set(x, y + k, z, b); if (rad > 0) { g.set(x + 1, y + k, z, b); g.set(x - 1, y + k, z, b); g.set(x, y + k, z + 1, b); g.set(x, y + k, z - 1, b); } } }
            case "eyepillar" -> { g.col(x, y, z, 7, stone); g.set(x, y + 7, z, mark); g.set(x, y + 8, z, g.S(Theme.LIGHT).getBlock() instanceof net.minecraft.world.level.block.LanternBlock ? acc : g.S(Theme.LIGHT)); }
            case "lightningrod" -> { g.col(x, y, z, 10, Blocks.LIGHTNING_ROD.defaultBlockState()); }
            case "mound" -> { for (int i = -4; i <= 4; i++) for (int k = -4; k <= 4; k++) { int d2 = i * i + k * k; if (d2 > 16) continue; int h = 3 - (int) Math.sqrt(d2) * 3 / 4; for (int j = 0; j < h; j++) g.set(x + i, y + j, z + k, j == h - 1 ? ground : g.S(Theme.SUB)); } }
            case "monolith" -> { found(g, x - 1, z - 1, 3, 2, y, stone); g.box(x - 1, y, z, 3, 9, 1, stone); g.set(x, y + 4, z, mark); g.set(x, y + 5, z, mark); g.set(x, y + 3, z, acc); }
            case "monument" -> { for (int k = 0; k < 4; k++) { int r = 4 - k; g.box(x - r, y + k, z - r, r * 2 + 1, 1, r * 2 + 1, k == 3 ? mark : (k % 2 == 0 ? stone : wall)); } found(g, x - 4, z - 4, 9, 9, y, stone); g.col(x, y + 4, z, 3, stone); }
            case "mushroom" -> { int h = g.rnd(4, 7); g.col(x, y, z, h, wall); g.disc(x, y + h, z, 3, acc); g.disc(x, y + h + 1, z, 1, acc); }
            case "reeds" -> { int n = g.rnd(6, 12); for (int i = 0; i < n; i++) { int rx = g.px(), rz = g.pz(); int gy = g.groundAt(rx, rz); if (gy < 0) continue; int h = g.rnd(2, 4); for (int k = 1; k <= h; k++) g.set(rx, gy + k, rz, Blocks.SUGAR_CANE.defaultBlockState()); } }
            case "ringstones" -> { for (int a = 0; a < 8; a++) { double ang = a * Math.PI / 4; int sx = x + (int) Math.round(Math.cos(ang) * 5), sz = z + (int) Math.round(Math.sin(ang) * 5); int gy = g.groundAt(sx, sz); if (gy < 0) continue; g.col(sx, gy + 1, sz, g.rnd(3, 5), stone); } g.set(x, y, z, mark); }
            case "rootcol" -> { int top = Math.min(g.ceilY > 0 ? g.ceilY : 90, 90); int h = Math.max(4, Math.min(30, top - y)); g.col(x, y, z, h, wall); for (int k = 0; k < 6; k++) g.set(x + (k % 2 == 0 ? 1 : -1), y + k * 2, z + (k % 3 - 1), acc); }
            case "saltpillar" -> { int h = g.rnd(6, 11); g.box(x, y, z, 2, h, 2, ground); g.set(x, y + h, z, ground); }
            case "stalagmites" -> { int n = g.rnd(3, 5); for (int i = 0; i < n; i++) { int sx = g.px(), sz = g.pz(); int gy = g.groundAt(sx, sz); if (gy < 0) continue; int h = g.rnd(2, 6); for (int k = 0; k < h; k++) { g.set(sx, gy + 1 + k, sz, stone); if (k < h / 3) { g.set(sx + 1, gy + 1 + k, sz, stone); g.set(sx, gy + 1 + k, sz + 1, stone); } } } }
            case "stonetree" -> { g.col(x, y, z, 7, stone); g.disc(x, y + 7, z, 3, acc); g.disc(x, y + 8, z, 2, acc); g.set(x, y + 9, z, mark); }
            case "towerruin" -> { found(g, x - 3, z - 3, 6, 6, y, stone); for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) { if (i != 0 && j != 0 && i != 5 && j != 5) continue; int h = 4 + (Noise.hash(g.t.seed + 5, i * 9 + x, j * 5 + z) % 7); for (int k = 0; k < h; k++) if (!(g.chance(.12))) g.set(x - 3 + i, y + k, z - 3 + j, k % 4 == 3 ? acc : wall); } g.set(x - 3, y, z - 1, Blocks.AIR.defaultBlockState()); g.set(x - 3, y + 1, z - 1, Blocks.AIR.defaultBlockState()); }
            case "toyblocks" -> { int n = g.rnd(3, 5); BlockState[] pal = {acc, mark, wall, floor}; for (int i = 0; i < n; i++) { int tx = g.px(), tz = g.pz(); int gy = indoor ? y - 1 : g.groundAt(tx, tz); if (gy < 0) continue; int h = g.rnd(1, 3); for (int k = 1; k <= h; k++) g.set(tx, gy + k, tz, pal[(i + k) % pal.length]); } }
            case "wreck" -> { found(g, x - 4, z - 1, 9, 3, y, stone); g.hollow(x - 4, y, z - 1, 9, 2, 3, wall); g.box(x - 4, y + 2, z - 1, 3, 1, 3, wall); g.col(x, y + 2, z, 6, Blocks.DARK_OAK_FENCE.defaultBlockState()); g.set(x + 4, y, z, Blocks.AIR.defaultBlockState()); g.set(x + 4, y + 1, z, Blocks.AIR.defaultBlockState()); }
            case "dome" -> { for (int i = -5; i <= 5; i++) for (int k = -5; k <= 5; k++) for (int j = 0; j <= 5; j++) { double d = Math.sqrt(i * i + k * k + j * j); if (d <= 5.3 && d >= 4.2) g.set(x + i, y + j, z + k, j == 0 ? stone : glass); } found(g, x - 5, z - 5, 11, 11, y, stone); g.set(x - 5, y, z, Blocks.AIR.defaultBlockState()); g.set(x - 5, y + 1, z, Blocks.AIR.defaultBlockState()); }
            case "platform" -> { found(g, x - 3, z - 3, 7, 7, y + 1, stone); g.box(x - 3, y, z - 3, 7, 2, 7, stone); g.box(x - 2, y + 2, z - 2, 5, 1, 5, acc); g.set(x, y + 3, z, mark); }
            case "pylon" -> { found(g, x - 1, z - 1, 3, 3, y, stone); g.col(x, y, z, 13, stone); for (int k = 2; k < 13; k += 3) { g.set(x + 1, y + k, z, acc); g.set(x - 1, y + k, z, acc); } g.set(x, y + 13, z, g.S(Theme.LIGHT).getBlock() instanceof net.minecraft.world.level.block.LanternBlock ? acc : g.S(Theme.LIGHT)); }
            case "stairs_up", "stairsnowhere" -> { int n = name.equals("stairs_up") ? 8 : 10; for (int k = 0; k < n; k++) { int sx = x - 4 + k; for (int j = 0; j <= k && j < 9; j++) if (j == k || !indoor) g.set(sx, y + j, z, wall); } if (name.equals("stairsnowhere")) g.box(x + 5, y + 10, z - 1, 3, 1, 3, acc); }
            case "throne" -> { found(g, x - 1, z - 1, 3, 3, y, stone); g.box(x - 2, y, z - 2, 5, 1, 5, stone); g.box(x - 1, y + 1, z + 1, 3, 5, 1, wall); g.set(x, y + 1, z, acc); g.set(x, y + 3, z + 1, mark); g.set(x - 1, y + 6, z + 1, g.S(Theme.LIGHT).getBlock() instanceof net.minecraft.world.level.block.LanternBlock ? acc : g.S(Theme.LIGHT)); }
            case "bridge" -> { for (int i = 0; i < 9; i++) { g.set(x - 4 + i, y - 1, z, i % 3 == 1 ? acc : stone); if (i % 4 == 0) g.set(x - 4 + i, y, z + 1, Blocks.CHAIN.defaultBlockState()); } }
            default -> { g.set(x, y, z, mark); }
        }
    }

    private static void pillar(Gen g, int x, int y, int z, int h, BlockState body, BlockState cap) {
        g.col(x, y, z, h, body);
        g.set(x, y + h - 1, z, cap);
        g.set(x, y, z, cap);
    }

    /** fill air/water below a footprint down to the ground (max 10 deep) so set pieces do not float on slopes. */
    private static void found(Gen g, int x0, int z0, int w, int d, int y, BlockState s) {
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            for (int yy = y - 1; yy > y - 11 && yy > 1; yy--) {
                BlockState b = g.get(x, yy, z);
                if (b.isAir() || b.getFluidState().isSource()) g.set(x, yy, z, s); else break;
            }
        }
    }

    private static void house(Gen g, int x0, int z0, int y, BlockState wall, BlockState ceil, BlockState floor, BlockState acc) {
        found(g, x0, z0, 5, 5, y, wall);
        g.box(x0, y - 1, z0, 5, 1, 5, floor);
        g.hollow(x0, y, z0, 5, 3, 5, wall);
        g.set(x0 + 2, y, z0, Blocks.AIR.defaultBlockState()); g.set(x0 + 2, y + 1, z0, Blocks.AIR.defaultBlockState());
        g.set(x0, y + 1, z0 + 2, Blocks.AIR.defaultBlockState());
        g.box(x0 - 1, y + 3, z0 - 1, 7, 1, 7, ceil);
        g.box(x0, y + 4, z0, 5, 1, 5, ceil);
        g.set(x0 + 4, y + 1, z0 + 2, acc);
    }

    private Features() {}
}
