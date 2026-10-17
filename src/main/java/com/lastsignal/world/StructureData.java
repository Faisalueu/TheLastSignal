package com.lastsignal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Registry of every structure this mod has placed (used by /lastsignal locate and by story events). */
public class StructureData extends SavedData {
    public static final String NAME = "lastsignal_structures";

    public record Entry(String type, long pos) {
        public BlockPos blockPos() { return BlockPos.of(pos); }
    }

    public final List<Entry> entries = new ArrayList<>();

    public void add(String type, BlockPos pos) {
        entries.add(new Entry(type, pos.asLong()));
        setDirty();
    }

    public Entry nearest(String typeOrNull, BlockPos from, double maxDist) {
        Entry best = null;
        double bd = maxDist * maxDist;
        for (Entry e : entries) {
            if (typeOrNull != null && !e.type().equals(typeOrNull)) continue;
            double d = e.blockPos().distSqr(from);
            if (d <= bd) { bd = d; best = e; }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString("t", e.type());
            c.putLong("p", e.pos());
            l.add(c);
        }
        tag.put("list", l);
        return tag;
    }

    public static StructureData load(CompoundTag tag) {
        StructureData d = new StructureData();
        ListTag l = tag.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) d.entries.add(new Entry(l.getCompound(i).getString("t"), l.getCompound(i).getLong("p")));
        return d;
    }
}
