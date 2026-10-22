package com.lastsignal.world.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lookup for the 48 Phase-1 themes (data lives in the generated ThemeData). */
public final class Themes {
    private static final Map<String, Theme> BY_SLUG = new LinkedHashMap<>();

    static {
        for (Theme t : ThemeData.ALL) BY_SLUG.put(t.slug, t);
        for (Theme t : ExtraThemes.ALL) BY_SLUG.put(t.slug, t);
    }

    /** the 48 horror dimensions followed by the two lake-entry dimensions */
    public static List<Theme> all() { return new ArrayList<>(BY_SLUG.values()); }

    /** the 48 horror dimensions only (black fracture destinations, original behaviour) */
    public static List<String> horrorSlugs() {
        List<String> l = new ArrayList<>();
        for (Theme t : ThemeData.ALL) l.add(t.slug);
        return l;
    }

    public static Profile profile(Theme t) {
        Profile base = t.index <= ThemeData.ALL.length ? ProfileData.ALL[t.index - 1] : ExtraThemes.PROFILES[t.index - ThemeData.ALL.length - 1];
        return Wonder.profile(t, base);
    }

    public static Theme get(String slug) { return BY_SLUG.get(slug); }

    public static boolean isPhase1(String slug) { return BY_SLUG.containsKey(slug); }

    public static List<String> slugs() { return new ArrayList<>(BY_SLUG.keySet()); }

    public static Theme byIndex(int i) {
        if (i >= 1 && i <= ThemeData.ALL.length) return ThemeData.ALL[i - 1];
        int k = i - ThemeData.ALL.length - 1;
        return k >= 0 && k < ExtraThemes.ALL.length ? ExtraThemes.ALL[k] : null;
    }

    private Themes() {}
}
