package com.lastsignal.story;

import com.lastsignal.config.LastSignalConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Entry point for all story state. Server-side only. */
public final class StoryManager {
    public static StoryData data(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(StoryData::load, StoryData::new, StoryData.NAME);
    }

    /** The story record that applies to this player under the current progression mode. */
    public static PlayerStory get(ServerPlayer p) {
        StoryData d = data(p.getServer());
        return LastSignalConfig.COMMON.sharedStory.get() ? d.shared : d.forPlayer(p.getUUID());
    }

    /** Private record regardless of mode (for per-player-only flags). */
    public static PlayerStory personal(ServerPlayer p) {
        return data(p.getServer()).forPlayer(p.getUUID());
    }

    public static void markDirty(ServerPlayer p) {
        data(p.getServer()).setDirty();
    }

    public static void onRecordingFound(ServerPlayer p, PlayerStory st, Transmissions.Entry e) {
        st.exposure += 2;
        if (e.evidence() != null) st.evidence.add(e.evidence());
        data(p.getServer()).setDirty();
    }

    public static void onNoteFound(ServerPlayer p, PlayerStory st, Notes.Note n) {
        st.exposure += 1.5;
        if (n.evidence() != null) st.evidence.add(n.evidence());
        data(p.getServer()).setDirty();
    }

    public static void onDeath(ServerPlayer p) {
        PlayerStory st = get(p);
        st.exposure *= 1.0 - LastSignalConfig.COMMON.deathThreatRelief.get();
        data(p.getServer()).setDirty();
    }

    public static void resetPlayer(ServerPlayer p) {
        StoryData d = data(p.getServer());
        d.players.remove(p.getUUID());
        if (LastSignalConfig.COMMON.sharedStory.get()) d.shared = new PlayerStory();
        d.setDirty();
    }

    private StoryManager() {}
}
