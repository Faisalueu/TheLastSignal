package com.lastsignal.world.gen;

/** Per-dimension generation / presentation profile (data in the generated ProfileData). Separate from Theme so balancing is easy. */
public final class Profile {
    /** room archetype indices (weights array order) */
    public static final int CELL = 0, HALL = 1, PIT = 2, SHAFT = 3, CRAWL = 4, ORGANIC = 5, COLUMNS = 6;

    public final int sc;               // rooms: supercell size in chunks (halls span sc x sc chunks)
    public final double[] arch;        // rooms: archetype weights CELL,HALL,PIT,SHAFT,CRAWL,ORGANIC,COLUMNS
    public final String hallStyle;     // rooms: how halls are furnished
    public final int hallH;            // rooms: hall ceiling height
    public final String[] lm;          // big landmark types (implicit, may span chunks)
    public final double lmDens;        // probability per 8x8-chunk supercell
    public final String center;        // fixed centerpiece landmark near the origin ("" = none)
    public final int cell;             // scatter grid cell size (blocks) for outdoor set pieces
    public final double scat;          // scatter density
    public final double chest;         // chest density multiplier (1 = default)
    public final String loot;          // loot table slug (data/lastsignal/loot_tables/chests/dim/<loot>.json)
    public final int fogFar;           // fog far distance in blocks (client)
    public final float amb;            // ambience event frequency multiplier

    public Profile(int sc, double[] arch, String hallStyle, int hallH, String[] lm, double lmDens, String center, int cell, double scat,
                   double chest, String loot, int fogFar, float amb) {
        this.sc = sc; this.arch = arch; this.hallStyle = hallStyle; this.hallH = hallH; this.lm = lm; this.lmDens = lmDens;
        this.center = center; this.cell = cell; this.scat = scat; this.chest = chest; this.loot = loot; this.fogFar = fogFar; this.amb = amb;
    }
}
