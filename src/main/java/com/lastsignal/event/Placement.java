package com.lastsignal.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where an event puts things. Each rule is a different real search (not a re-labelled random point),
 * and returns null when nothing suitable exists - the event then simply does not happen (a false alarm).
 */
public enum Placement {
    ANY_GROUND, FOREST_EDGE, HIGH_GROUND, MOUNTAIN, DARK_CAVE, OUTSIDE_WINDOW, CORRIDOR_END,
    BEHIND_WATER, ROOFTOP, OPEN_FIELD, BEHIND_PLAYER, DEEP_UNDERGROUND;

    /** Placements that scatter around a point (the others are tied to the player's view, windows, caves, water...). */
    private boolean scatters() {
        return this == ANY_GROUND || this == FOREST_EDGE || this == HIGH_GROUND || this == MOUNTAIN || this == ROOFTOP || this == OPEN_FIELD;
    }

    /**
     * Spawn-centred search: most scattering events look around the player's spawn anchor (within the configured radius),
     * as long as the player is near enough for the result to be seen. Falls back to the plain player-centred search,
     * and never forces a spot - null still means "no suitable place, skip the event".
     */
    public BlockPos find(ServerLevel l, ServerPlayer p, double min, double max) {
        if (scatters()) {
            BlockPos anchor = SpawnAnchor.of(l, p);
            if (anchor != null && p.getRandom().nextDouble() < com.lastsignal.config.LastSignalConfig.COMMON.spawnAnchorBias.get()) {
                double rad = com.lastsignal.config.LastSignalConfig.COMMON.spawnAnchorRadius.get();
                for (int i = 0; i < 3; i++) {
                    BlockPos g = search(l, p, anchor, 8, rad, min, max);
                    if (g != null && g.distSqr(p.blockPosition()) >= 100 && Math.sqrt(g.distSqr(anchor)) <= rad + 6) return g;
                }
            }
        }
        return search(l, p, p.blockPosition(), min, max, min, max);
    }

    private BlockPos search(ServerLevel l, ServerPlayer p, BlockPos pp, double min, double max, double pmin, double pmax) {
        RandomSource r = p.getRandom();
        switch (this) {
            case ANY_GROUND: {
                for (int i = 0; i < 12; i++) {
                    BlockPos c = ring(pp, r, min, max);
                    BlockPos g = surface(l, c);
                    if (g != null && standable(l, g)) return g;
                }
                return null;
            }
            case FOREST_EDGE: {
                for (int i = 0; i < 24; i++) {
                    BlockPos g = surface(l, ring(pp, r, min, max));
                    if (g == null || !standable(l, g)) continue;
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++)
                            if (l.getBlockState(g.offset(dx, 1, dz)).is(BlockTags.LOGS)) return g;
                }
                return null;
            }
            case HIGH_GROUND:
            case MOUNTAIN: {
                int rise = this == MOUNTAIN ? 10 : 3;
                BlockPos best = null;
                for (int i = 0; i < 32; i++) {
                    BlockPos g = surface(l, ring(pp, r, min, max));
                    if (g == null || !standable(l, g) || g.getY() < pp.getY() + rise) continue;
                    if (best == null || g.getY() > best.getY()) best = g;
                }
                return best;
            }
            case DARK_CAVE:
            case DEEP_UNDERGROUND: {
                int maxY = this == DEEP_UNDERGROUND ? 30 : 200;
                for (int i = 0; i < 40; i++) {
                    BlockPos c = ring(pp, r, min, max).offset(0, r.nextInt(9) - 4, 0);
                    if (c.getY() > maxY) continue;
                    BlockState here = l.getBlockState(c), above = l.getBlockState(c.above()), below = l.getBlockState(c.below());
                    if (here.isAir() && above.isAir() && below.isSolid()
                            && l.getBrightness(LightLayer.BLOCK, c) <= 3 && !l.canSeeSky(c)) return c;
                }
                return null;
            }
            case OUTSIDE_WINDOW: {
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (BlockPos b : BlockPos.betweenClosed(pp.offset(-12, -3, -12), pp.offset(12, 4, 12))) {
                    BlockState s = l.getBlockState(b);
                    if (!(s.is(Blocks.GLASS_PANE) || s.is(Blocks.GLASS) || s.is(BlockTags.IMPERMEABLE))) continue;
                    for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos out = b.relative(dir), in = b.relative(dir.getOpposite());
                        if (l.getBlockState(out).isAir() && l.getBlockState(out.above()).isAir() && l.getBlockState(out.below()).isSolid()
                                && l.getBlockState(in).isAir()) {
                            double d = out.distSqr(pp);
                            if (d > 9 && d < bd) { bd = d; best = out.immutable(); }
                        }
                    }
                }
                return best;
            }
            case CORRIDOR_END: {
                Vec3 look = p.getLookAngle();
                Vec3 dir = new Vec3(look.x, 0, look.z);
                if (dir.lengthSqr() < 1e-4) return null;
                dir = dir.normalize();
                BlockPos last = null;
                for (int i = 4; i <= max; i++) {
                    BlockPos c = BlockPos.containing(p.getX() + dir.x * i, p.getY(), p.getZ() + dir.z * i);
                    if (l.getBlockState(c).isAir() && l.getBlockState(c.above()).isAir() && l.getBlockState(c.below()).isSolid()) last = c;
                    else if (!l.getBlockState(c).isAir()) break;
                }
                return last != null && last.distSqr(pp) >= min * min ? last : null;
            }
            case BEHIND_WATER: {
                for (BlockPos b : BlockPos.betweenClosed(pp.offset(-24, -6, -24), pp.offset(24, 10, 24))) {
                    if (l.getFluidState(b).isSource() || !l.getFluidState(b).isEmpty()) {
                        if (!l.getFluidState(b.above()).isEmpty() && l.getFluidState(b.above()).getType().isSame(l.getFluidState(b).getType())) {
                            Vec3 away = new Vec3(b.getX() - p.getX(), 0, b.getZ() - p.getZ());
                            if (away.lengthSqr() < 36) continue;
                            away = away.normalize().scale(3);
                            BlockPos g = surface(l, BlockPos.containing(b.getX() + away.x, b.getY(), b.getZ() + away.z));
                            if (g != null && standable(l, g)) return g;
                        }
                    }
                }
                return null;
            }
            case ROOFTOP: {
                for (int i = 0; i < 40; i++) {
                    BlockPos c = ring(pp, r, min, max);
                    BlockPos g = surface(l, c);
                    if (g == null) continue;
                    BlockState below = l.getBlockState(g.below());
                    boolean built = below.is(BlockTags.PLANKS) || below.is(BlockTags.STAIRS) || below.is(BlockTags.SLABS) || below.is(BlockTags.WOOL)
                            || below.is(Blocks.STONE_BRICKS) || below.is(Blocks.BRICKS);
                    if (built && standable(l, g) && g.getY() > pp.getY() + 2) return g;
                }
                return null;
            }
            case OPEN_FIELD: {
                for (int i = 0; i < 24; i++) {
                    BlockPos g = surface(l, ring(pp, r, min, max));
                    if (g == null || !standable(l, g) || !l.getBlockState(g.below()).is(Blocks.GRASS_BLOCK)) continue;
                    boolean clear = true;
                    for (int dx = -3; dx <= 3 && clear; dx += 3)
                        for (int dz = -3; dz <= 3 && clear; dz += 3)
                            if (!l.getBlockState(g.offset(dx, 1, dz)).isAir() || Math.abs(surfaceY(l, g.offset(dx, 0, dz)) - g.getY()) > 1) clear = false;
                    if (clear) return g;
                }
                return null;
            }
            case BEHIND_PLAYER: {
                Vec3 look = p.getLookAngle();
                Vec3 back = new Vec3(-look.x, 0, -look.z);
                if (back.lengthSqr() < 1e-4) return null;
                back = back.normalize().scale(min + r.nextDouble() * (max - min));
                BlockPos c = BlockPos.containing(p.getX() + back.x, p.getY(), p.getZ() + back.z);
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos t = c.offset(0, dy, 0);
                    if (standable(l, t)) return t;
                }
                return null;
            }
            default:
                return null;
        }
    }

    private static BlockPos ring(BlockPos c, RandomSource r, double min, double max) {
        double a = r.nextDouble() * Math.PI * 2;
        double d = min + r.nextDouble() * (max - min);
        return new BlockPos((int) (c.getX() + Math.cos(a) * d), c.getY(), (int) (c.getZ() + Math.sin(a) * d));
    }

    static BlockPos surface(ServerLevel l, BlockPos c) {
        if (!l.hasChunkAt(c)) return null;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        return new BlockPos(c.getX(), y, c.getZ());
    }

    static int surfaceY(ServerLevel l, BlockPos c) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
    }

    static boolean standable(ServerLevel l, BlockPos g) {
        if (!l.hasChunkAt(g)) return false;
        return l.getBlockState(g).isAir() && l.getBlockState(g.above()).isAir()
                && l.getBlockState(g.below()).isSolid() && l.getFluidState(g.below()).isEmpty();
    }
}
