package com.lastsignal.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** The single custom generator behind all 48 Phase-1 dimensions; behaviour is selected by the "theme" slug. */
public class ThemedChunkGenerator extends ChunkGenerator {
    public static final Codec<ThemedChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.STRING.fieldOf("theme").forGetter(g -> g.themeSlug)
    ).apply(i, i.stable(ThemedChunkGenerator::new)));

    private final String themeSlug;
    private final Theme theme;

    public ThemedChunkGenerator(BiomeSource source, String themeSlug) {
        super(source);
        this.themeSlug = themeSlug;
        Theme t = Themes.get(themeSlug);
        this.theme = t != null ? t : ThemeData.ALL[0];
    }

    public Theme theme() { return theme; }

    @Override
    protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk, GenerationStep.Carving step) { }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk) { }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override
    public int getGenDepth() { return 256; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor ex, Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) {
        new Gen(theme, chunk).run();
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() { return (int) theme.p[Theme.P_SEA] > 0 ? (int) theme.p[Theme.P_SEA] : 63; }

    @Override
    public int getMinY() { return 0; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs) {
        return switch (theme.kind) {
            case "open" -> Math.max(Gen.openHeight(theme, x, z), (int) theme.p[Theme.P_SEA]) + 1;
            case "deep" -> Gen.deepFloor(theme, x, z) + 1;
            case "sea" -> (int) theme.p[Theme.P_SEA] + 1;
            case "rooms" -> Gen.Y0;
            case "stairs" -> 25;
            case "clouds" -> SkyRealm.height(theme, x, z) + 1;
            case "volcano" -> Math.max(Volcano.height(theme, x, z), 62) + 1;
            default -> (int) theme.p[Theme.P_BASE] + 1;
        };
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState rs) {
        return new NoiseColumn(0, new BlockState[]{Blocks.AIR.defaultBlockState()});
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState rs, BlockPos pos) {
        info.add("LastSignal theme: " + theme.slug + " (" + theme.kind + ")");
    }
}
