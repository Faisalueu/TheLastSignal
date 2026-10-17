package com.lastsignal.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level persistent story store (saved on the overworld's data storage). Survives restarts. */
public class StoryData extends SavedData {
    public static final String NAME = "lastsignal_story";
    public PlayerStory shared = new PlayerStory();
    public final Map<UUID, PlayerStory> players = new HashMap<>();

    public PlayerStory forPlayer(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerStory());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("shared", shared.save());
        ListTag l = new ListTag();
        for (var e : players.entrySet()) {
            CompoundTag c = e.getValue().save();
            c.putUUID("uuid", e.getKey());
            l.add(c);
        }
        tag.put("players", l);
        return tag;
    }

    public static StoryData load(CompoundTag tag) {
        StoryData d = new StoryData();
        d.shared = PlayerStory.load(tag.getCompound("shared"));
        ListTag l = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            d.players.put(c.getUUID("uuid"), PlayerStory.load(c));
        }
        return d;
    }
}
