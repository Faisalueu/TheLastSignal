package com.lastsignal.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 30 original documents. Chronology: Year 0 founding -> Year 3 first anomaly -> Year 5 abandonment -> now. */
public final class Notes {
    public record Note(String id, String title, String text, String evidence) {}

    public static final List<Note> ALL = new ArrayList<>();
    private static final Map<String, Note> BY_ID = new HashMap<>();

    private static void n(String id, String title, String text, String evidence) {
        Note x = new Note(id, title, text, evidence);
        ALL.add(x);
        BY_ID.put(id, x);
    }

    static {
        n("n01", "Welcome Memo", "Station Meridian opens today. Forty-one staff, one hill, one mandate: understand the carrier wave. Please sign the confidentiality form. Please do not name the wave. Names are a kind of invitation. — D. Ashby", null);
        n("n02", "Shift Roster, Week 12", "Quill: radio room. Marrow: relay maintenance. Venn: archive. Voss: lab. Someone has written a seventh name on the roster in pencil. Nobody will admit to it. It is not in anyone's handwriting.", null);
        n("n03", "Marrow's Repair Log", "Replaced the relay coil again. The old one was warm in a way a dead coil should not be. It also hummed back at me when I whistled. I did not whistle a second time.", null);
        n("n04", "Cafeteria Notice", "Please stop leaving a place set for the extra person. We have counted. There is no extra person. The chair is not for anyone. Stop pulling it out.", null);
        n("n05", "Quill's Radio Diary", "Day 40. It uses my name now. Day 41. It knows how I take my tea. Day 42. It said my sister's name, which I have never said on the air. I turned the set off. The set kept talking.", null);
        n("n06", "Incident Report 7A", "Subject observed a figure at the end of Hall B for eleven minutes. Figure was recorded by no camera. Subject describes it as 'tall, patient, and polite'. Subject has stopped sleeping.", null);
        n("n07", "Venn's Archive Index", "The archive holds 412 recordings. I have stopped listing 17 of them because the entries change when I look away. The index is now a record of my own doubt.", "archive_location");
        n("n08", "Voss's Notebook, p.3", "If the Signal moves through adjacent places, then rooms are not fixed. Hall B and the valley field are neighbors. Hall B and my childhood kitchen are neighbors. I cannot prove it. I can only stand in the kitchen.", "voss_logs");
        n("n09", "Letter Never Sent", "Mama, the work is going well. They give us a lot of tea. The nights are very quiet and the quiet has a shape. If you hear me on the radio, it isn't me. Love, T.", null);
        n("n10", "Safety Bulletin 12", "Staff must not respond to voices that call from outside the building. Staff must not follow footprints that begin at doors and end in the middle of rooms. Staff must not count the people in the control room.", null);
        n("n11", "Ines's Sketch", "A drawing of three towers on three hills connected by a line. Below it: 'cut all three or none, or it learns the cut'. In the corner, small: 'void lens clears the glass'.", "containment_blueprint");
        n("n12", "Doorman Field Note", "The door in the forest is not a door. It is a thing that learned what doors are for. It will not let anyone through who does not carry a key. It will let anyone out. That is how it eats.", "door_hint");
        n("n13", "Archivist Handbook", "The Archivist trades. Bring it something that was true once: a coin, a stamp, a page. It will tell you something that is true now. It does not lie. It is simply very selective.", null);
        n("n14", "Memo: Remove the Mirror", "Facilities will remove the mirror in Hall C. Please stop reporting that your reflection is late. It is not late. Everything else is early.", "facet_hint");
        n("n15", "Ashby's Private Ledger", "Funding is tied to results. The results are impossible. If the board reads the true reports they will cut us. If they cut us, the Signal loses its only audience. Is that a reason to lie? I am told it is.", "truth_ledger");
        n("n16", "The Seventh Name", "I found the pencil handwriting. It is mine. I do not remember writing it. I think I am the seventh. I think I have always been the seventh. — Anonymous, found in the cafeteria", null);
        n("n17", "Maintenance Request", "Hall B corridor is longer on Tuesdays. Please measure it. We have measured it. It is longer on Tuesdays.", null);
        n("n18", "Burrower Survey", "Seismic sensors recorded a regular pulse below the hill, one beat every nineteen seconds. It is not geological. It is not mechanical. It is slow, and it is large, and it is waiting to be asked.", null);
        n("n19", "Lantern Widow Sighting", "Forest staff reported lights on the path home. They followed them. The lights were warm and kind. The path turned back on itself until it was somewhere else. Three did not return.", null);
        n("n20", "Hollow Autopsy Fragment", "Subject was human. Subject has been dead for years. Subject's throat contains a transmitter. It is not metal. It is not tissue. It hums at 115.5. Subject's last words were a count.", null);
        n("n21", "Echo Transcript", "Voice 1: Are you there? Voice 2: Are you there? Voice 1: That was me. Voice 2: That was me. (The tape continues for forty minutes.)", null);
        n("n22", "Pale Herd Report", "Eleven figures in the east field. Twelve the next morning. One of the staff did not come to breakfast. We have stopped asking which.", null);
        n("n23", "Evacuation Order", "All staff to the surface. The tunnel is clear. Do not use the lower corridor. Repeat: do not use the lower corridor. If you hear the order repeated from behind you, do not turn around.", null);
        n("n24", "Voss's Last Page", "I understand now. It is not hungry. It is not hostile. It was a lighthouse and we are the ship that answered. We cannot silence it. We can only decide what it is allowed to learn.", "truth_ledger");
        n("n25", "The Three Options", "Staff vote results: Seal (11), Let it speak (9), Leave and hope (21). Nobody voted for the option that would end it, because nobody believes there is one. — M. Quill", "transmission_choice");
        n("n26", "Marrow's Final Entry", "I cut the hill relay and the hall relay. I cannot reach the hollow relay. If you find this, the hollow is below the archive, and it is not a place a person should stand for long.", "containment_blueprint");
        n("n27", "Archivist's Receipt", "Received: one stamped page. Given: one true thing. Remaining balance: seven rooms. Do not ask which rooms. Ask what they were for.", "hidden_seal");
        n("n28", "Seal One", "A small room with a single chair and a note: 'I walked away. I chose to forget. I would choose it again.' The note is signed by someone who is standing behind you.", "hidden_seal");
        n("n29", "Core Schematic", "The Signal Core is not a machine. The schematic shows an organ that grows toward whatever listens. Weak points: the relay-spine (cut with a signal blade) and the false-eye (reveal with a void lens).", "signal_core_unlocked");
        n("n30", "Final Page", "If you have read this far, you have been listening for a long time. Whatever you decide, decide it gently. It has never been spoken to by anyone who wanted nothing from it.", null);
    }

    public static Note byId(String id) {
        return BY_ID.get(id);
    }

    public static Note forPosition(long posHash) {
        return ALL.get((int) Math.floorMod(posHash * 31L, (long) ALL.size()));
    }

    public static Note nextUnread(PlayerStory st, int salt) {
        List<Note> unread = new ArrayList<>();
        for (Note n : ALL) if (!st.notes.contains(n.id())) unread.add(n);
        if (unread.isEmpty()) return null;
        return unread.get(Math.floorMod(salt, unread.size()));
    }

    private Notes() {}
}
