package com.lastsignal.world.gen;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The liminal-tile rework of all 48 horror dimensions (hand-written, so the generated ThemeData/ProfileData stay untouched).
 * Every horror dimension becomes a bedrock-enclosed world built from repeating 16x16 chunk tiles (the "rooms" generator), each with its own rule set:
 *  period    : the layout repeats every `period` chunks in x and z (walk straight on and you come back to the same room);
 *  lattice   : long straight corridors run along every `lattice`-th chunk row/column; other chunks are sealed off except for rare side doors;
 *  mono      : floor, walls and ceiling use one material (the WALL role);
 *  anomaly   : percentage of chunks that ignore the repetition and get a different, unrepeated room ("the wrong one"); never within 2 chunks of the origin;
 *  openPlan  : pillared open space with no walls between chunks (fields, plains, voids);
 *  h / sc / arch / style / hallH : ceiling height, hall supercell size, archetype weights and hall furnishing.
 * Everything is a pure function of (theme, chunk x/z), like the rest of the generator, so neighbours always agree.
 */
final class Wonder {
    static final class Rule {
        final int period, lattice, anomaly, h, sc, hallH; final boolean mono, openPlan; final double[] arch; final String style;
        Rule(int period, int lattice, boolean mono, int anomaly, int h, int sc, double[] arch, String style, int hallH, boolean openPlan) {
            this.period = period; this.lattice = lattice; this.mono = mono; this.anomaly = anomaly; this.h = h; this.sc = sc;
            this.arch = arch; this.style = style; this.hallH = hallH; this.openPlan = openPlan;
        }
        int fold(int c) { return Math.floorMod(c, period); }
    }

    // ---- presets (arch order: CELL, HALL, PIT, SHAFT, CRAWL, ORGANIC, COLUMNS)
    private static Rule hallway(int l, boolean mono, int h) { return new Rule(l * 2, l, mono, 2, h, 1, new double[]{.7, 0, 0, 0, .3, 0, 0}, "none", 8, false); }
    private static Rule houses() { return new Rule(4, 0, false, 3, 7, 1, new double[]{.8, 0, 0, 0, .1, 0, .1}, "none", 8, false); }
    private static Rule library(String style) { return new Rule(6, 0, false, 3, 10, 3, new double[]{.35, .45, 0, .1, 0, 0, .1}, style, 16, false); }
    private static Rule halls(String style, int hallH) { return new Rule(6, 0, false, 3, 10, 3, new double[]{.2, .6, .05, .05, 0, 0, .1}, style, hallH, false); }
    private static Rule caves(int lat, boolean mono) { return new Rule(6, lat, mono, 4, 6, 2, new double[]{.2, 0, .1, .05, .3, .35, 0}, "none", 8, false); }
    private static Rule vertical() { return new Rule(4, 0, false, 4, 9, 1, new double[]{.2, 0, .3, .4, .1, 0, 0}, "none", 8, false); }
    private static Rule field(boolean mono) { return new Rule(4, 0, mono, 4, 14, 1, new double[]{0, 0, 0, 0, 0, 0, 1}, "none", 8, true); }

    static final Map<String, Rule> RULES = new HashMap<>();
    private static final Map<String, Profile> PROFILES = new HashMap<>();

    static {
        RULES.put("last_signal", field(false));          RULES.put("forgotten_village", houses());
        RULES.put("hollow_cathedral", halls("pews", 26)); RULES.put("endless_archive", library("shelves"));
        RULES.put("silent_ward", hallway(3, false, 5));  RULES.put("last_station", halls("tracks", 14));
        RULES.put("starless_expanse", field(true));      RULES.put("watching_deep", caves(3, false));
        RULES.put("impossible_geometry", vertical());    RULES.put("red_monolith", field(false));
        RULES.put("pale_moon", field(true));             RULES.put("fractured_beyond", vertical());
        RULES.put("living_walls", caves(0, true));       RULES.put("rotting_garden", field(false));
        RULES.put("rootbound_abyss", vertical());        RULES.put("nest_below", caves(3, false));
        RULES.put("drowned_marsh", field(false));        RULES.put("petrified_forest", field(false));
        RULES.put("rusted_engine", halls("machines", 20)); RULES.put("black_laboratory", hallway(4, false, 5));
        RULES.put("dead_grid", field(false));            RULES.put("sealed_bunker", hallway(3, true, 4));
        RULES.put("dead_server", hallway(4, true, 5));   RULES.put("unfinished_mine", caves(3, false));
        RULES.put("yellow_silence", hallway(4, true, 4)); RULES.put("broken_hour", houses());
        RULES.put("mirrorless_hall", halls("mirrors", 14)); RULES.put("forgotten_childhood", houses());
        RULES.put("empty_theater", halls("seats", 16));  RULES.put("staircase_below", vertical());
        RULES.put("bloodless_desert", field(false));     RULES.put("frozen_grave", field(false));
        RULES.put("ashen_sun", field(false));            RULES.put("black_tide", field(false));
        RULES.put("storm_that_waits", vertical());       RULES.put("grey_forest", field(false));
        RULES.put("red_chapel", halls("pews", 20));      RULES.put("nameless_catacombs", caves(3, false));
        RULES.put("buried_kingdom", halls("colonnade", 18)); RULES.put("unfinished_ritual", halls("monoliths", 16));
        RULES.put("silent_monastery", houses());         RULES.put("endless_cemetery", field(false));
        RULES.put("corrupted_chunk", vertical());        RULES.put("white_void", hallway(3, true, 5));
        RULES.put("blind_observatory", field(false));    RULES.put("forbidden_index", library("shelves"));
        RULES.put("signal_core", halls("machines", 22)); RULES.put("unwritten_end", hallway(3, true, 6));
    }

    /** All 48 horror dimensions are generated by the rooms generator. Called from the Theme constructor. */
    static String kindFor(String slug, String kind) { return RULES.containsKey(slug) ? "rooms" : kind; }

    static Rule of(Theme t) { return "rooms".equals(t.kind) ? RULES.get(t.slug) : null; }

    static synchronized Profile profile(Theme t, Profile base) {
        Rule w = of(t);
        if (w == null) return base;
        return PROFILES.computeIfAbsent(t.slug, k -> new Profile(w.sc, w.arch, w.style, w.hallH, new String[]{}, 0.0, "", base.cell, base.scat, base.chest, base.loot, base.fogFar, base.amb));
    }

    static int height(Theme t) {
        Rule w = of(t);
        return w != null ? w.h : (int) t.p[Theme.P_H];
    }

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
