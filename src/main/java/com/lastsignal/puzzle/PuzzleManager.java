package com.lastsignal.puzzle;

import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative puzzle state, persisted per-dimension (SavedData). Two mechanisms:
 *  - POWER: Archive Terminals need a Power Cell; once powered the whole terminal stays powered.
 *  - SEQUENCE: symbol blocks (circle=0, triangle=1) pressed within 10 s must match the fixed
 *    pattern 0,1,1,0 (printed on note n11/n17 style clues). Success reveals a hint note and
 *    marks puzzle "sequence" complete for the player's story. State is world-wide so co-op works.
 */
public class PuzzleManager extends SavedData {
    public static final String NAME = "lastsignal_puzzles";
    private static final int[] PATTERN = {0, 1, 1, 0};

    private final Set<Long> powered = new HashSet<>();
    private final List<Integer> progress = new ArrayList<>();
    private long lastPress = -1000;

    public static PuzzleManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PuzzleManager::load, PuzzleManager::new, NAME);
    }

    public boolean isPowered(BlockPos pos) {
        return powered.contains(pos.asLong());
    }

    public boolean restorePower(ServerLevel level, BlockPos pos, ServerPlayer by) {
        if (!powered.add(pos.asLong())) return false;
        setDirty();
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.6f);
        PlayerStory st = StoryManager.get(by);
        st.puzzles.add("power");
        StoryManager.markDirty(by);
        by.displayClientMessage(Component.literal("The terminal hums to life."), true);
        return true;
    }

    public void readArchive(ServerLevel level, BlockPos pos, ServerPlayer by) {
        PlayerStory st = StoryManager.get(by);
        st.evidence.add("archive_read");
        StoryManager.markDirty(by);
        by.sendSystemMessage(Component.literal("§bARCHIVE§7: Recordings 1-412 indexed. 17 entries unavailable. A hint is stored in the sequence: circle, triangle, triangle, circle."));
    }

    public void pressSymbol(ServerLevel level, BlockPos pos, ServerPlayer by, int symbol) {
        long now = level.getGameTime();
        if (now - lastPress > 200) progress.clear();
        lastPress = now;
        progress.add(symbol);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8f, symbol == 0 ? 0.8f : 1.2f);
        int i = progress.size() - 1;
        if (progress.get(i) != PATTERN[i]) {
            progress.clear();
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1f, 0.5f);
            if (com.lastsignal.config.LastSignalConfig.COMMON.puzzleHints.get())
                by.displayClientMessage(Component.literal("The blocks go cold. Something about the order was wrong."), true);
            setDirty();
            return;
        }
        if (progress.size() == PATTERN.length) {
            progress.clear();
            PlayerStory st = StoryManager.get(by);
            if (st.puzzles.add("sequence")) {
                st.evidence.add("sequence_solved");
                by.sendSystemMessage(Component.literal("§aA hidden mechanism clicks open somewhere nearby."));
                level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, 0.5f);
                StoryManager.markDirty(by);
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (long p : powered) l.add(LongTag.valueOf(p));
        tag.put("powered", l);
        return tag;
    }

    public static PuzzleManager load(CompoundTag tag) {
        PuzzleManager m = new PuzzleManager();
        ListTag l = tag.getList("powered", Tag.TAG_LONG);
        for (int i = 0; i < l.size(); i++) m.powered.add(((LongTag) l.get(i)).getAsLong());
        return m;
    }
}
