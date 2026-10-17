package com.lastsignal.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/** Per-player (or shared-world) story record. Persisted in StoryData. */
public class PlayerStory {
    public int band = 0, fine = 0;
    public double exposure = 0;
    public int nights = 0;
    public int doorsOpened = 0;
    public int encounters = 0;
    public int dimensionsEscaped = 0;
    public long wardUntil = 0;
    public String checkpointDim = "";
    public String returnDim = "";
    public long returnPos = 0;
    public long checkpointPos = 0;
    public final Set<String> recordings = new LinkedHashSet<>();
    public final Set<String> notes = new LinkedHashSet<>();
    /** Evidence flags used for endings, e.g. "truth_ledger", "containment_blueprint", "hidden_seal". */
    public final Set<String> evidence = new LinkedHashSet<>();
    public final Set<String> bossesDefeated = new LinkedHashSet<>();
    public final Set<String> endings = new LinkedHashSet<>();
    public final Set<String> puzzles = new LinkedHashSet<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("band", band); t.putInt("fine", fine);
        t.putDouble("exposure", exposure);
        t.putInt("nights", nights); t.putInt("doors", doorsOpened);
        t.putInt("enc", encounters); t.putInt("esc", dimensionsEscaped);
        t.putLong("ward", wardUntil);
        t.putString("rdim", returnDim); t.putLong("rpos", returnPos);
        t.putString("cpDim", checkpointDim); t.putLong("cpPos", checkpointPos);
        t.put("rec", list(recordings)); t.put("notes", list(notes)); t.put("ev", list(evidence));
        t.put("boss", list(bossesDefeated)); t.put("end", list(endings)); t.put("puz", list(puzzles));
        return t;
    }

    public static PlayerStory load(CompoundTag t) {
        PlayerStory s = new PlayerStory();
        s.band = t.getInt("band"); s.fine = t.getInt("fine");
        s.exposure = t.getDouble("exposure");
        s.nights = t.getInt("nights"); s.doorsOpened = t.getInt("doors");
        s.encounters = t.getInt("enc"); s.dimensionsEscaped = t.getInt("esc");
        s.wardUntil = t.getLong("ward");
        s.returnDim = t.getString("rdim"); s.returnPos = t.getLong("rpos");
        s.checkpointDim = t.getString("cpDim"); s.checkpointPos = t.getLong("cpPos");
        read(t.getList("rec", Tag.TAG_STRING), s.recordings);
        read(t.getList("notes", Tag.TAG_STRING), s.notes);
        read(t.getList("ev", Tag.TAG_STRING), s.evidence);
        read(t.getList("boss", Tag.TAG_STRING), s.bossesDefeated);
        read(t.getList("end", Tag.TAG_STRING), s.endings);
        read(t.getList("puz", Tag.TAG_STRING), s.puzzles);
        return s;
    }

    private static ListTag list(Set<String> set) {
        ListTag l = new ListTag();
        for (String x : set) l.add(StringTag.valueOf(x));
        return l;
    }

    private static void read(ListTag l, Set<String> out) {
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
    }

    /** Hidden threat stage 0-6 derived from exposure + investigation progress. */
    public int stage() {
        double score = exposure + recordings.size() * 4 + notes.size() * 3 + nights * 2 + encounters * 3 + doorsOpened * 2;
        int s = score < 8 ? 0 : score < 30 ? 1 : score < 65 ? 2 : score < 110 ? 3 : score < 170 ? 4 : score < 240 ? 5 : 6;
        if (s >= 6 && !(evidence.contains("signal_core_unlocked") || bossesDefeated.size() >= 1)) s = 5;
        return s;
    }
}
