package com.lastsignal.story;

import com.lastsignal.item.ModItems;
import com.lastsignal.world.Dimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Five endings with different requirements and different, persistent consequences. */
public final class Endings {
    public static final List<String> IDS = List.of("escape", "containment", "transmission", "truth", "hidden");

    /** @return null when available, otherwise a human-readable list of what is missing. */
    public static String missing(PlayerStory st, String id) {
        if (!st.evidence.contains("core_defeated")) return "The Signal Core still stands.";
        StringBuilder m = new StringBuilder();
        switch (id) {
            case "escape" -> { }
            case "containment" -> {
                if (!st.evidence.contains("containment_blueprint")) m.append("the containment blueprint; ");
                if (!st.puzzles.contains("power")) m.append("restore the Archive's power; ");
            }
            case "transmission" -> {
                if (!st.evidence.contains("transmission_choice")) m.append("hear what the Signal offers (band 4); ");
            }
            case "truth" -> {
                if (!st.evidence.contains("truth_ledger")) m.append("the truth ledger; ");
                if (!st.evidence.contains("voss_logs")) m.append("Voss's logs; ");
                if (!st.evidence.contains("archive_read")) m.append("read the Archive; ");
                if (st.notes.size() < 20) m.append("20 notes (" + st.notes.size() + "); ");
                if (st.recordings.size() < 15) m.append("15 recordings (" + st.recordings.size() + "); ");
            }
            case "hidden" -> {
                if (!st.evidence.contains("hidden_seal")) m.append("the seven seals; ");
                if (!st.evidence.contains("sequence_solved")) m.append("the symbol sequence; ");
                if (!st.bossesDefeated.contains("buried_king")) m.append("the Buried King; ");
                if (!st.bossesDefeated.contains("unfinished")) m.append("the Unfinished; ");
            }
            default -> { return "Unknown ending."; }
        }
        return m.length() == 0 ? null : "Missing: " + m;
    }

    public static boolean apply(ServerPlayer sp, String id) {
        PlayerStory st = StoryManager.get(sp);
        if (missing(st, id) != null) return false;
        switch (id) {
            case "escape" -> {
                st.exposure = 0;
                st.nights = 0;
                say(sp, "§aESCAPE§7 — You turn the radio off and walk until the static is only wind. Somewhere behind you something keeps listening, but it no longer knows your name.");
                Dimensions.returnHome(sp, true);
            }
            case "containment" -> {
                st.exposure = 0;
                st.recordings.clear();
                st.notes.clear();
                st.evidence.removeIf(e -> !e.equals("core_defeated"));
                say(sp, "§bCONTAINMENT§7 — Three relays fall silent in sequence. The archive burns without flame. You keep your life and lose everything you learned. Perhaps that was the price all along.");
                Dimensions.returnHome(sp, true);
            }
            case "transmission" -> {
                st.exposure = 400;
                give(sp, "signal_fragment", 8);
                say(sp, "§dTRANSMISSION§7 — You answer. Not with words, with everything. The world does not end. It simply becomes slightly more connected than it was. You can hear every radio now. They can hear you.");
            }
            case "truth" -> {
                give(sp, "memory_shard", 3);
                give(sp, "archivist_stamp", 1);
                say(sp, "§eTRUTH§7 — Voss, Ines, Quill, Venn, Ashby: all of them were asking the same thing in different voices. You know what the Signal is, what it wants, and what each choice would have cost. You choose with open eyes, and you choose to remember them.");
                Dimensions.returnHome(sp, true);
            }
            case "hidden" -> {
                give(sp, "echo_whistle", 1);
                give(sp, "buried_crown_shard", 1);
                say(sp, "§5HIDDEN§7 — Seven rooms that never existed open at once. In each, a person you never met nods and leaves. The dimensions were not places: they were decisions. You walk out through the seventh door and it closes behind you like a held breath.");
                Dimensions.returnHome(sp, true);
            }
            default -> { return false; }
        }
        st.endings.add(id);
        st.evidence.add("ended");
        StoryManager.markDirty(sp);
        return true;
    }

    /** Replay: wipes progress but keeps which endings have been seen. The Minecraft world is untouched. */
    public static void replay(ServerPlayer sp) {
        PlayerStory old = StoryManager.get(sp);
        var seen = new java.util.LinkedHashSet<>(old.endings);
        StoryManager.resetPlayer(sp);
        StoryManager.get(sp).endings.addAll(seen);
        StoryManager.markDirty(sp);
    }

    private static void give(ServerPlayer sp, String item, int n) {
        ItemStack s = new ItemStack(ModItems.ITEMS_BY_ID.get(item).get(), n);
        if (!sp.getInventory().add(s)) sp.drop(s, false);
    }

    private static void say(ServerPlayer sp, String msg) {
        sp.sendSystemMessage(Component.literal(msg));
    }

    private Endings() {}
}
