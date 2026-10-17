package com.beer.brew;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns what went into the kettle and the barrel into a beer, and a beer into effects. Free of Bukkit. */
public final class BrewRules {

    /** Grains in one batch. */
    public static final int GRAINS = 4;
    /** Hops in one batch at most; two make it Hoppy. */
    public static final int MAX_HOPS = 2;
    /** Stars of a beer poured as soon as it is ready. */
    public static final int FRESH_STARS = 2;
    /** Lager effects last this much longer. */
    public static final double LAGER = 1.5;
    /** Clinking mugs with someone makes the beer's effects last this much longer. */
    public static final double CHEERS = 1.25;
    /** Level II effects run this much shorter, like a potent potion. */
    public static final double POTENT = 0.75;

    private BrewRules() {
    }

    /** Whether a kettle with this in it boils (given heat): water, four grains and a hop. */
    public static boolean canBoil(boolean water, int grains, int hops) {
        return water && grains >= GRAINS && hops >= 1;
    }

    /** How long a barrel ferments: ale, or twice as long for lager. */
    public static long fermentMs(boolean cold, long aleMs, long lagerMs) {
        return cold ? lagerMs : aleMs;
    }

    /**
     * Stars of a beer poured {@code waitedMs} after the barrel was filled: 2 when fresh, +1 after {@code agedMs}, +2
     * after {@code longAgedMs}, and +1 in the barrel wood the colour likes.
     */
    public static int stars(long waitedMs, long agedMs, long longAgedMs, Wood wood, Colour colour) {
        int stars = FRESH_STARS + (waitedMs >= longAgedMs ? 2 : waitedMs >= agedMs ? 1 : 0) + (wood == colour.wood() ? 1 : 0);
        return Math.min(5, stars);
    }

    /** The beer a barrel pours: the wort's style for its temperature, and the legend it makes, if any. */
    public static Beer pour(Wort wort, boolean cold, boolean blueIce, Wood wood, int stars) {
        Style style = Style.of(wort.colour(), cold);
        Beer beer = new Beer(style, wort.hoppy(), wort.extra(), null);
        Legend legend = legend(beer, blueIce, wood, stars);
        return legend == null ? beer : new Beer(style, beer.hoppy(), beer.extra(), legend);
    }

    static Legend legend(Beer beer, boolean blueIce, Wood wood, int stars) {
        Style style = beer.style();
        Extra extra = beer.extra();
        if (style == Style.STOUT && beer.hoppy() && extra == Extra.COCOA && wood == Wood.DARK) {
            return Legend.MINERS_STOUT;
        }
        if (style == Style.MARZEN && extra == Extra.PUMPKIN && stars >= 5) {
            return Legend.FESTBIER;
        }
        if (style == Style.PILSNER && beer.hoppy() && blueIce) {
            return Legend.GLACIER_PILS;
        }
        if (style == Style.WHEAT_BEER && extra == Extra.HONEY && wood == Wood.PALE) {
            return Legend.BEES_KNEES;
        }
        return null;
    }

    /** "Hoppy Chocolate Stout", "Strong Pilsner", "IPA" (a Hoppy Pale Ale), or the legend's name. */
    public static String name(Beer beer) {
        if (beer.legend() != null) {
            return beer.legend().displayName();
        }
        boolean ipa = beer.hoppy() && beer.style() == Style.PALE_ALE;
        return (beer.hoppy() && !ipa ? "Hoppy " : "") + (beer.extra() == null ? "" : beer.extra().prefix() + " ")
                + (ipa ? "IPA" : beer.style().displayName());
    }

    /** The name a wort will have as an ale: "Hoppy Chocolate Stout". */
    public static String name(Wort wort) {
        return name(new Beer(Style.of(wort.colour(), false), wort.hoppy(), wort.extra(), null));
    }

    /** Tipsy one mug adds: 1, or 2 for a Strong beer. */
    public static int tipsy(Beer beer) {
        return beer.extra() == Extra.SUGAR ? 2 : 1;
    }

    /** Seconds of a plain effect for one mug at this many stars: 120 to 210 for 2 to 5 stars. */
    public static double baseSeconds(int stars) {
        return 60 + 30 * stars;
    }

    /** The beer's effects in order, each once at its highest level: the colour's base, Hoppy, the extra, the legend. */
    public static List<Trait> effects(Beer beer) {
        Map<Effect, Integer> levels = new EnumMap<>(Effect.class);
        List<Effect> order = new ArrayList<>();
        for (Trait trait : sources(beer)) {
            if (!levels.containsKey(trait.effect())) {
                order.add(trait.effect());
            }
            levels.merge(trait.effect(), trait.level(), Math::max);
        }
        List<Trait> out = new ArrayList<>();
        for (Effect effect : order) {
            out.add(new Trait(effect, levels.get(effect)));
        }
        return out;
    }

    /**
     * Every effect of one mug: {@link #baseSeconds} times the effect's weight, shorter at level II, x1.5 for a lager,
     * times {@code factor} (the config strength and Cheers). When an effect comes from two places, the higher level wins.
     */
    public static List<Dose> doses(Beer beer, int stars, double factor) {
        double base = baseSeconds(stars) * (beer.style().lager() ? LAGER : 1.0);
        List<Dose> out = new ArrayList<>();
        for (Trait trait : effects(beer)) {
            double seconds = base * trait.effect().weight() * (trait.level() > 0 ? POTENT : 1.0);
            out.add(new Dose(trait.effect(), trait.level(), (int) Math.max(20, Math.round(seconds * 20 * factor))));
        }
        return out;
    }

    /** Where a beer's effects come from, in order (an effect may come twice). */
    private static List<Trait> sources(Beer beer) {
        List<Trait> out = new ArrayList<>();
        out.add(new Trait(beer.style().colour().base(), beer.extra() == Extra.SUGAR ? 1 : 0));
        if (beer.hoppy()) {
            out.add(new Trait(Effect.STRENGTH, 0));
        }
        if (beer.extra() != null && beer.extra().adds() != null) {
            out.add(new Trait(beer.extra().adds(), 0));
        }
        if (beer.legend() != null) {
            out.addAll(beer.legend().bonus());
        }
        return out;
    }
}
