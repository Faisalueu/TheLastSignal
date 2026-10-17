package com.lastsignal.story;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 30 radio transmissions. band = which band carries it; fine >= 0 means the radio's fine-tune must match
 * (tuning puzzle). minStage gates by hidden threat stage. kind: "story", "false" (false lead), "rare".
 * The Meridian Project cast: Dr. Halloran Voss (lead), Ines Marrow (technician), Teodor Quill (radio operator),
 * Dr. Yara Venn (archivist), Director Corvin Ashby (funding), and the unnamed voice that answers.
 */
public final class Transmissions {
    public static final int BANDS = 5;

    public record Entry(String id, int band, int fine, int minStage, String kind, String evidence, String text) {}

    public static final List<Entry> ALL = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();

    private static void t(String id, int band, int fine, int minStage, String kind, String evidence, String text) {
        Entry e = new Entry(id, band, fine, minStage, kind, evidence, text);
        ALL.add(e);
        BY_ID.put(id, e);
    }

    static {
        // Band 0 - public noise and early warnings
        t("t01_first", 0, -1, 0, "story", null, "...is anyone receiving? This is Station Meridian. We have not been... we have not been shut down. Please. Say anything.");
        t("t02_static_words", 0, -1, 0, "story", null, "krshh... do not answer it... krshh... it only learns your voice when you answer...");
        t("t03_weather", 0, -1, 0, "false", null, "Clear skies over the valley tonight, light winds from the east. Nothing unusual to report. Nothing unusual. Nothing—");
        t("t04_count", 0, 3, 1, "story", null, "Four. Five. Six. Seven. Seven people in the control room. There were six of us on shift. Count again.");
        t("t05_ines", 0, -1, 1, "story", null, "Ines here. If you can hear this, the relay on the hill is still sending. I never turned it off. I swear I never turned it off.");
        t("t06_empty_house", 0, -1, 1, "false", null, "Go to the cabin with the green door. Someone is waiting. (the voice sounds pleased with itself)");
        // Band 1 - research recordings
        t("t07_voss_log1", 1, -1, 1, "story", null, "Voss, log one. We have a carrier wave that originates from no transmitter. It arrives before it is sent. I am choosing to call this progress.");
        t("t08_voss_log2", 1, -1, 2, "story", "voss_logs", "Voss, log nine. The signal does not travel through space. It travels through the places that are adjacent to each other in a way we do not have a word for. A door in Hall B opens onto a field I have never visited. I remember the field.");
        t("t09_quill", 1, 5, 2, "story", null, "Quill, radio room. Tuned to 115.5 for the ninth time. The voice on it says my name in my mother's tone. I have not told anyone my mother's tone.");
        t("t10_venn", 1, -1, 2, "story", "archive_location", "Venn. I moved the original recordings below the station. Level minus three, behind the sealed door. The terminal needs a power cell. Do not let Ashby find the key.");
        t("t11_ashby", 1, -1, 2, "story", null, "Ashby. Funding continues. I do not want to hear about the figures in the hall. They are sensor artifacts. They are sensor artifacts. They are sensor artifacts.");
        t("t12_false_exit", 1, -1, 2, "false", null, "Exit is north through the tunnel. Run. Do not look back. (The tunnel leads nowhere. Someone wanted you to run.)");
        // Band 2 - location hints
        t("t13_hint_tower", 2, -1, 1, "story", "tower_hint", "Relay stations were built on the highest ground. If you can see a tower lit in the dark, that is where the signal is strongest. Bring something to listen with.");
        t("t14_hint_cabin", 2, -1, 1, "story", null, "The first staff cabins were built in the forest. People did not leave them. The things inside learned to wear their routines.");
        t("t15_hint_well", 2, -1, 3, "story", null, "There is a well that was never dug. It is older than the station. The water makes no sound when you drop something in. Do not lean over it.");
        t("t16_hint_doorway", 2, 7, 3, "story", "door_hint", "A door appears where the signal is thin. Three knocks, then wait. If it knocks back, you are not alone in the doorway.");
        t("t17_hint_archive", 2, -1, 3, "story", "archive_location", "The archive is not a place. It is a decision somebody made about where to keep what could not be forgotten.");
        t("t18_false_safe", 2, -1, 2, "false", null, "Safe zone ahead. No entities detected. Come closer. Come closer. Closer.");
        // Band 3 - entities
        t("t19_observer", 3, -1, 2, "story", null, "Field note: it does not move while you look. It does not have to. It has all the time that was taken from the others.");
        t("t20_hollow", 3, -1, 2, "story", null, "The thin ones are what remains of people who stayed on the frequency too long. They hunt by sound because the sound is all they kept.");
        t("t21_mimic", 3, 9, 3, "story", null, "If you hear someone you know calling from the dark, count their footsteps. The real ones have a limp.");
        t("t22_echo", 3, -1, 3, "story", null, "I walked the corridor yesterday. Today I met myself in it, walking the corridor. We nodded. Neither of us was the first one there.");
        t("t23_facet", 3, -1, 4, "story", "facet_hint", "The mirror people lie to your eyes, not your ears. If a lens clears the glass, the false doors vanish. Void lenses were Ines's idea.");
        t("t24_pale_herd", 3, -1, 4, "story", null, "They gather in fields because fields have no corners to hide in. They only move when you blink. You will blink.");
        // Band 4 - revelations and rare
        t("t25_truth1", 4, -1, 3, "story", "truth_ledger", "Voss, last log. The Signal is not a monster. It is a request. The research did not find it. It found us, and we answered, and every answer taught it a word.");
        t("t26_containment", 4, 2, 4, "story", "containment_blueprint", "To seal the source, three relays must be cut in sequence: the hill, the hall, the hollow. Cutting them destroys every recording. Ines drew the diagram on the back of a keycard.");
        t("t27_transmission", 4, 8, 4, "story", "transmission_choice", "You could answer it. Not with words. With the whole archive. It has been asking for a very long time. The station was only the first place that said yes.");
        t("t28_hidden", 4, 6, 5, "rare", "hidden_seal", "Seven seals. Seven rooms that never existed. One for each person who tuned to this exact frequency and walked away. Find the rooms. Learn what they decided.");
        t("t29_core", 4, -1, 6, "story", "signal_core_unlocked", "The core is waiting under the void. It knows you have the evidence. It is not angry. It is lonely. That is the most dangerous thing it could be.");
        t("t30_final", 4, 0, 6, "rare", null, "This is Station Meridian, signing on. We are still here. We were always still here. Thank you for listening.");
    }

    public static Entry byId(String id) {
        return BY_ID.get(id);
    }

    /** Radio use: only entries on the current band that match fine tuning (if required) and stage. */
    public static Entry pick(ServerPlayer sp, PlayerStory st) {
        int stage = st.stage();
        List<Entry> pool = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.band() != st.band || e.minStage() > stage) continue;
            if (e.fine() >= 0 && e.fine() != st.fine) continue;
            pool.add(e);
        }
        if (pool.isEmpty()) return null;
        // prefer unheard; occasional static so the radio never feels like a menu
        List<Entry> fresh = new ArrayList<>();
        for (Entry e : pool) if (!st.recordings.contains(e.id())) fresh.add(e);
        if (fresh.isEmpty()) return sp.getRandom().nextInt(3) == 0 ? pool.get(sp.getRandom().nextInt(pool.size())) : null;
        if (sp.getRandom().nextInt(5) == 0) return null;
        return fresh.get(sp.getRandom().nextInt(fresh.size()));
    }

    /** Static radio receiver blocks ignore band/fine and give the next unheard story/false entry. */
    public static Entry pickAtReceiver(ServerPlayer sp, PlayerStory st) {
        int stage = st.stage();
        for (Entry e : ALL) {
            if (e.fine() >= 0) continue;
            if (e.minStage() > stage || st.recordings.contains(e.id())) continue;
            return e;
        }
        return null;
    }

    private Transmissions() {}
}
