package com.lastsignal.world.gen;

import com.lastsignal.config.LastSignalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Golden Palace: a quartz-and-gold palace on its own plateau island (centre PX,PZ; floor block Y = PY) in the Golden Cloud Realm.
 * Pure function of the world position (re-derived by every chunk that touches it, clipped by Gen.set), like every other generator here.
 *
 * Layout (u = x-PX, v = z-PZ; entrance faces north = -v, towards the spawn island):
 *   terrace u -24..24, v -33..26 with gilded balustrade, four obelisks and a gap in the north edge;
 *   great hall u -10..10, v -8..22: columns, gold-ribbed dome, throne dais, windows, entrance arch, rear balcony;
 *   two spiral-stair towers (u = +-17) with balconies; the east one holds a treasure chest on top;
 *   two hidden rooms behind subtly different wall panels (west vault with a chest, east archive with a chest).
 * Exactly three chests exist (west vault, east archive, east tower top), all with the same modest loot table. Architectural gold is real
 * vanilla gold blocks (minable by design: the palace is made of the real thing).
 */
final class Palace {
    static final int F = SkyRealm.PY;                 // terrace floor block Y; walkable surface is F+1
    static final int TOWER_FLOOR = F + 31;            // top-room floor block Y of the towers

    // the hidden panels (hall wall cells, two high) - the smoke test treats them as passable ("break the wall")
    static final int[][] PANELS = {{-10, 10}, {10, 10}};
    // chest cells (u, y, v)
    static final int[][] CHESTS = {{-16, F + 1, 10}, {16, F + 1, 10}, {18, TOWER_FLOOR + 1, -3}};

    private static final BlockState Q = Blocks.QUARTZ_BLOCK.defaultBlockState(), SQ = Blocks.SMOOTH_QUARTZ.defaultBlockState(),
            CQ = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), QP = Blocks.QUARTZ_PILLAR.defaultBlockState(),
            G = Blocks.GOLD_BLOCK.defaultBlockState(), L = Blocks.SEA_LANTERN.defaultBlockState(), QS = Blocks.QUARTZ_SLAB.defaultBlockState(),
            AIR = Blocks.AIR.defaultBlockState(), SHELF = Blocks.BOOKSHELF.defaultBlockState();

    private final Gen g;
    private final int ou, ov;          // local = world-relative palace coords + (ou, ov)

    private Palace(Gen g) { this.g = g; this.ou = SkyRealm.PX - g.bx; this.ov = SkyRealm.PZ - g.bz; }

    static void run(Gen g) {
        int u0 = g.bx - SkyRealm.PX, v0 = g.bz - SkyRealm.PZ;
        if (u0 > 30 || u0 + 15 < -30 || v0 > 40 || v0 + 15 < -40) return;
        new Palace(g).build();
    }

    // ------------------------------------------------------------------------------------------------ primitives
    private void blk(int u, int y, int v, BlockState s) { g.set(u + ou, y, v + ov, s); }

    private void box(int u0, int u1, int y0, int y1, int v0, int v1, BlockState s) {
        int a = Math.max(u0, -ou), b = Math.min(u1, 15 - ou), c = Math.max(v0, -ov), d = Math.min(v1, 15 - ov);
        for (int u = a; u <= b; u++) for (int v = c; v <= d; v++) for (int y = y0; y <= y1; y++) g.set(u + ou, y, v + ov, s);
    }

    private void build() {
        terrace();
        balustrade();
        obelisks();
        hall();
        tower(-17, false);
        tower(17, true);
        hiddenRooms();
    }

    // ------------------------------------------------------------------------------------------------ terrace
    private void terrace() {
        BlockState rock = g.S(Theme.STONE);
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int u = g.bx + lx - SkyRealm.PX, v = g.bz + lz - SkyRealm.PZ;
            if (Math.abs(u) > 24 || v < -33 || v > 26) continue;
            boolean border = Math.abs(u) == 24 || v == -33 || v == 26;
            BlockState s;
            if (border || (u == 0 && v < -8)) s = CQ;
            else if (Math.floorMod(u, 8) == 4 && Math.floorMod(v, 8) == 4) s = L;
            else s = ((u + v) & 1) == 0 ? Q : SQ;
            g.set(lx, F, lz, s);
            for (int y = F + 1; y <= F + 3; y++) g.set(lx, y, lz, AIR);                  // flowers etc.
            for (int y = F - 1; y > F - 45 && g.get(lx, y, lz).isAir(); y--) g.set(lx, y, lz, rock);   // never a floating edge
        }
    }

    private void balustrade() {
        for (int u = -24; u <= 24; u++) {
            if (Math.abs(u) > 3) blk(u, F + 1, -33, QS);
            blk(u, F + 1, 26, QS);
        }
        for (int v = -32; v <= 25; v++) { blk(-24, F + 1, v, QS); blk(24, F + 1, v, QS); }
        for (int u = -24; u <= 24; u += 6) {
            if (Math.abs(u) > 3) post(u, -33);
            post(u, 26);
        }
        for (int v = -27; v <= 20; v += 6) { post(-24, v); post(24, v); }
    }

    private void post(int u, int v) {
        blk(u, F + 1, v, G);
        blk(u, F + 2, v, L);
    }

    private void obelisks() {
        for (int[] c : new int[][]{{-20, -30}, {20, -30}, {-20, -12}, {20, -12}}) {
            blk(c[0], F + 1, c[1], G);
            for (int y = F + 2; y <= F + 8; y++) blk(c[0], y, c[1], QP);
            blk(c[0], F + 9, c[1], G);
            blk(c[0], F + 10, c[1], L);
        }
    }

    // ------------------------------------------------------------------------------------------------ great hall
    private void hall() {
        box(-10, 10, F + 1, F + 14, -8, 22, Q);                                    // solid mass
        box(-9, 9, F + 1, F + 13, -7, 21, AIR);                                    // interior
        // entrance arch (north)
        box(-3, 3, F + 1, F + 9, -8, -8, AIR);
        box(-4, -4, F + 1, F + 9, -8, -8, G);
        box(4, 4, F + 1, F + 9, -8, -8, G);
        box(-4, 4, F + 10, F + 10, -8, -8, G);
        blk(0, F + 12, -8, CQ);
        // windows
        for (int v : new int[]{-2, 3, 17}) {
            box(-10, -10, F + 3, F + 9, v - 1, v + 1, AIR);
            box(10, 10, F + 3, F + 9, v - 1, v + 1, AIR);
        }
        for (int u : new int[]{-6, 6}) box(u - 1, u + 1, F + 3, F + 9, 22, 22, AIR);
        // columns
        for (int su : new int[]{-6, 6}) for (int v : new int[]{-3, 2, 7, 12, 17}) {
            blk(su, F + 1, v, G);
            for (int y = F + 2; y <= F + 12; y++) blk(su, y, v, QP);
            blk(su, F + 13, v, G);
        }
        // dome: carve the roof inside r<9 about (0, 7); shell 9.0..10.4 from the centre (0, F+14, 7); gold ribs along the axes and diagonals
        for (int u = -9; u <= 9; u++) for (int v = -2; v <= 16; v++) if (u * u + (v - 7) * (v - 7) < 81) blk(u, F + 14, v, AIR);
        for (int u = -10; u <= 10; u++) for (int v = -3; v <= 17; v++) for (int y = F + 14; y <= F + 25; y++) {
            double d = Math.sqrt(u * u + (double) (y - (F + 14)) * (y - (F + 14)) + (v - 7.0) * (v - 7.0));
            if (d < 9.0 || d > 10.4) continue;
            boolean rib = u == 0 || v == 7 || Math.abs(u) == Math.abs(v - 7);
            blk(u, y, v, rib ? G : Q);
        }
        for (int y = F + 25; y <= F + 28; y++) blk(0, y, 7, G);
        blk(0, F + 29, 7, L);
        // cornice
        for (int u = -10; u <= 10; u++) { cornice(u, -8); cornice(u, 22); }
        for (int v = -7; v <= 21; v++) { cornice(-10, v); cornice(10, v); }
        // throne dais
        box(-7, 7, F + 1, F + 1, 15, 21, Q);
        box(-5, 5, F + 1, F + 2, 17, 21, SQ);
        box(-3, 3, F + 1, F + 3, 19, 21, SQ);
        blk(0, F + 4, 20, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        blk(-1, F + 4, 20, QS);
        blk(1, F + 4, 20, QS);
        box(-1, 1, F + 4, F + 6, 21, 21, G);
        // columns on the dais overwrite its steps: redo the two nearest bases so they stay gold
        for (int su : new int[]{-6, 6}) blk(su, F + 1, 17, G);
        // rear balcony railing is part of the balustrade (v = 26)
    }

    private void cornice(int u, int v) {
        blk(u, F + 15, v, ((u + v) % 5 == 0) ? G : QS);
    }

    // ------------------------------------------------------------------------------------------------ towers
    /** ring cell (iu, iv) of the 5x5 interior -> index 0..15 around the ring (starting at the north-west corner), or -1 for the core. */
    static int ring(int iu, int iv) {
        if (iv == 0) return iu;                          // 0..4
        if (iu == 4 && iv <= 3) return 4 + iv;           // 5..7
        if (iv == 4) return 12 - iu;                     // 8..12
        if (iu == 0) return 16 - iv;                     // 13..15  (iv = 3, 2, 1)
        return -1;
    }

    private void tower(int cu, boolean withChest) {
        int u0 = cu - 3, u1 = cu + 3, ft = TOWER_FLOOR;
        box(u0, u1, F + 1, F + 36, -5, 1, Q);
        box(cu - 2, cu + 2, F + 1, ft + 4, -4, 0, AIR);
        // spiral: loop 0 starts on the floor, loop 1 ends level with the top-room floor
        for (int loop = 0; loop < 2; loop++) for (int iu = 0; iu < 5; iu++) for (int iv = 0; iv < 5; iv++) {
            int i = ring(iu, iv);
            if (i < 0) continue;
            if (loop == 0 && i == 0) continue;
            blk(cu - 2 + iu, F + i + 16 * loop, -4 + iv, SQ);
        }
        // top-room floor: everywhere except the two cells of the last approach (ring 13, 14)
        for (int iu = 0; iu < 5; iu++) for (int iv = 0; iv < 5; iv++) {
            int i = ring(iu, iv);
            if (i == 13 || i == 14) continue;
            blk(cu - 2 + iu, ft, -4 + iv, SQ);
        }
        // door (north, next to the first step), lintel
        box(cu - 2, cu - 2, F + 1, F + 2, -5, -5, AIR);
        blk(cu - 2, F + 3, -5, G);
        // arrow slits (1 high) and lights on the four wall centres
        int[][] centres = {{cu, -5}, {cu, 1}, {cu - 3, -2}, {cu + 3, -2}};
        for (int[] c : centres) {
            for (int y : new int[]{F + 6, F + 16, F + 24}) blk(c[0], y, c[1], AIR);
            for (int y : new int[]{F + 10, F + 20, F + 28}) blk(c[0], y, c[1], L);
        }
        // top room: windows on three sides, a full opening onto the south balcony
        box(cu, cu, ft + 2, ft + 3, -5, -5, AIR);
        box(cu - 3, cu - 3, ft + 2, ft + 3, -2, -2, AIR);
        box(cu + 3, cu + 3, ft + 2, ft + 3, -2, -2, AIR);
        box(cu, cu, ft + 1, ft + 3, 1, 1, AIR);
        box(cu - 2, cu + 2, ft, ft, 2, 3, SQ);                                           // balcony floor
        for (int u = cu - 2; u <= cu + 2; u++) blk(u, ft + 1, 3, QS);
        blk(cu - 2, ft + 1, 2, QS);
        blk(cu + 2, ft + 1, 2, QS);
        // one gold band and a stepped gold roof with a lantern crown
        for (int u = u0; u <= u1; u++) { blk(u, F + 12, -5, G); blk(u, F + 12, 1, G); }
        for (int v = -4; v <= 0; v++) { blk(u0, F + 12, v, G); blk(u1, F + 12, v, G); }
        box(cu - 2, cu + 2, F + 37, F + 37, -4, 0, G);
        box(cu - 1, cu + 1, F + 38, F + 38, -3, -1, G);
        blk(cu, F + 39, -2, G);
        blk(cu, F + 40, -2, L);
        // the top-room centrepiece: a chest (east tower) or a lone relic (west tower)
        if (withChest) chest(cu + 1, ft + 1, -3, Direction.SOUTH);
        else { blk(cu + 1, ft + 1, -3, QP); blk(cu + 1, ft + 2, -3, G); }
    }

    // ------------------------------------------------------------------------------------------------ hidden rooms
    private void hiddenRooms() {
        // west vault
        box(-18, -10, F + 1, F + 5, 7, 13, Q);
        box(-17, -11, F + 1, F + 4, 8, 12, AIR);
        box(-10, -10, F + 1, F + 2, 10, 10, SQ);                                         // the panel (smooth instead of plain quartz)
        blk(-14, F + 5, 10, L);
        for (int[] c : new int[][]{{-17, 8}, {-17, 12}, {-11, 8}, {-11, 12}}) for (int y = F + 1; y <= F + 4; y++) blk(c[0], y, c[1], QP);
        chest(-16, F + 1, 10, Direction.EAST);
        // east archive
        box(10, 18, F + 1, F + 5, 7, 13, Q);
        box(11, 17, F + 1, F + 4, 8, 12, AIR);
        box(10, 10, F + 1, F + 2, 10, 10, CQ);                                           // the panel (chiseled instead of plain quartz)
        blk(14, F + 5, 10, L);
        for (int u = 11; u <= 17; u++) { box(u, u, F + 1, F + 3, 8, 8, SHELF); box(u, u, F + 1, F + 3, 12, 12, SHELF); }
        box(17, 17, F + 1, F + 3, 9, 11, SHELF);
        chest(16, F + 1, 10, Direction.WEST);
    }

    // ------------------------------------------------------------------------------------------------ chests
    private void chest(int u, int y, int v, Direction f) {
        try { if (LastSignalConfig.COMMON.chestDensity.get() <= 0.0) return; } catch (IllegalStateException e) { /* config not loaded (data gen) */ }
        int lx = u + ou, lz = v + ov;
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
        BlockPos wp = new BlockPos(g.bx + lx, y, g.bz + lz);
        BlockState cs = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f);
        g.set(lx, y, lz, cs);
        ChestBlockEntity be = new ChestBlockEntity(wp, cs);
        be.setLootTable(new ResourceLocation("lastsignal", "chests/dim/sky_palace"), Noise.hash(g.t.seed + 777, wp.getX(), wp.getZ()) * 31L + wp.getY());
        g.c.setBlockEntity(be);
    }

}
