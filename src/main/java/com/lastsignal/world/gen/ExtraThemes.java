package com.lastsignal.world.gen;

/**
 * The two dimensions added after the 48 horror dimensions (indices 49 and 50). They are hand-written (not part of the generated
 * ThemeData) so the 48 existing dimensions stay byte-for-byte untouched. Entered by diving in an Overworld lake (see world/LakeEntry).
 */
public final class ExtraThemes {
    public static final String CLOUD = "golden_cloud_realm", VOLCANO = "infernal_volcano";

    /** id, light level, kind (plain|glass) - registered by ModBlocks, textures/models by tools/extra_dims.py */
    public static final String[][] BLOCKS = {
        {"d49_cloud_bank", "0", "plain"}, {"d49_skystone", "0", "plain"}, {"d49_gilded_marble", "0", "plain"}, {"d49_aurelian_lamp", "15", "plain"},
        {"d50_ashrock", "0", "plain"}, {"d50_cinder_gravel", "0", "plain"}, {"d50_scorched_basalt", "0", "plain"},
        {"d50_ember_vent", "11", "plain"}, {"d50_cooled_rim", "5", "plain"},
    };

    private static final double[] CLOUD_P = {5, .5, 0, 0, .5, 94, 6, 34, 0, 62, 0, 0, 0};
    private static final double[] VOLCANO_P = {5, .5, 0, 0, .5, 72, 14, 70, 0, 62, 0, 0, 0};
    private static final String[][] NO_GLYPH = {{}};

    public static final Theme[] ALL = {
        new Theme(49, CLOUD, "The Golden Cloud Realm", "clouds",
            new String[]{"minecraft:grass_block", "minecraft:dirt", "d49_skystone", "d49_gilded_marble", "d49_gilded_marble", "d49_gilded_marble", "d49_gilded_marble", "d49_aurelian_lamp", "d49_aurelian_lamp", ""},
            new String[]{}, NO_GLYPH, CLOUD_P, "", 0xF3E6C2, 420f, "amb_" + CLOUD, "acc_" + CLOUD + "_1", "acc_" + CLOUD + "_2", 60f),
        new Theme(50, VOLCANO, "The Infernal Volcano", "volcano",
            new String[]{"d50_ashrock", "d50_cinder_gravel", "d50_scorched_basalt", "d50_scorched_basalt", "d50_ashrock", "d50_scorched_basalt", "d50_cooled_rim", "d50_ember_vent", "d50_ember_vent", ""},
            new String[]{}, NO_GLYPH, VOLCANO_P, "", 0x4A1408, 260f, "amb_" + VOLCANO, "acc_" + VOLCANO + "_1", "acc_" + VOLCANO + "_2", 60f),
    };

    /** No chests, no set pieces, no loot in either dimension (balance requirement). */
    public static final Profile[] PROFILES = {
        new Profile(1, new double[]{1, 0, 0, 0, 0, 0, 0}, "none", 10, new String[]{}, 0.0, "", 24, 0.0, 0.0, "", 420, 0.7f),
        new Profile(1, new double[]{1, 0, 0, 0, 0, 0, 0}, "none", 10, new String[]{}, 0.0, "", 24, 0.0, 0.0, "", 260, 1.0f),
    };

    public static boolean isExtra(String slug) { return CLOUD.equals(slug) || VOLCANO.equals(slug); }

    private ExtraThemes() {}
}
