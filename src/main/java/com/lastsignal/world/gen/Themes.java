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
    }

    public static Theme get(String slug) { return BY_SLUG.get(slug); }

    public static boolean isPhase1(String slug) { return BY_SLUG.containsKey(slug); }

    public static List<String> slugs() { return new ArrayList<>(BY_SLUG.keySet()); }

    public static Theme byIndex(int i) { return i >= 1 && i <= ThemeData.ALL.length ? ThemeData.ALL[i - 1] : null; }

    private Themes() {}
}
