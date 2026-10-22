package com.lastsignal.world.gen;

import java.util.Map;
import java.util.Random;

/**
 * Tile rules for the "rooms" dimensions chosen for the liminal-tile rework (hand-written, so the generated ThemeData/ProfileData stay untouched).
 *  period  : the layout of every chunk repeats every `period` chunks in x and z (walk straight on and you come back to the same room);
 *  lattice : long straight corridors run along every `lattice`-th chunk row/column; other chunks are sealed off except for rare side doors;
 *  mono    : floor, walls and ceiling use one material (the WALL role);
 *  anomaly : percentage of chunks that ignore the repetition and get a different, unrepeated room ("the wrong one"); never within 2 chunks of the origin.
 * Everything is a pure function of (theme, chunk x/z), like the rest of the generator, so neighbours always agree.
 */
final class Wonder {
    static final class Rule {
        final int period, lattice, anomaly; final boolean mono;
        Rule(int period, int lattice, boolean mono, int anomaly) { this.period = period; this.lattice = lattice; this.mono = mono; this.anomaly = anomaly; }
        int fold(int c) { return Math.floorMod(c, period); }
    }

    static final Map<String, Rule> RULES = Map.of(
            "endless_archive", new Rule(6, 0, false, 3),
            "yellow_silence", new Rule(8, 4, true, 2),
            "forgotten_childhood", new Rule(6, 0, false, 4),
            "nameless_catacombs", new Rule(6, 3, false, 4),
            "living_walls", new Rule(4, 0, true, 5),
            "sealed_bunker", new Rule(6, 3, true, 3));

    static Rule of(Theme t) { return "rooms".equals(t.kind) ? RULES.get(t.slug) : null; }

    static boolean anomaly(Theme t, Rule w, int cx, int cz) {
        if (Math.abs(cx) <= 2 && Math.abs(cz) <= 2) return false;
        return Noise.hash(t.seed + 777, cx, cz) % 100 < w.anomaly;
    }

    /** Gives the generator the folded coordinates (identical tiles) unless this chunk is an anomaly. */
    static void apply(Gen g, Rule w) {
        if (anomaly(g.t, w, g.cx, g.cz)) return;
        g.fcx = w.fold(g.cx); g.fcz = w.fold(g.cz);
        g.fbx = g.fcx << 4; g.fbz = g.fcz << 4;
        g.r = new Random(Noise.hash(g.t.seed, g.fcx, g.fcz));
    }

    static boolean onLattice(Rule w, int cx, int cz) {
        return w.lattice > 0 && (Math.floorMod(cx, w.lattice) == 0 || Math.floorMod(cz, w.lattice) == 0);
    }

    private Wonder() {}
}
