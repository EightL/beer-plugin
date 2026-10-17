package com.beer.brew;


import java.util.Locale;

/** One optional extra per batch, put in the kettle: a word in the name and something more in the mug. */
public enum Extra {
    HONEY("HONEY_BOTTLE", "Honey Bottle", "Honey", Effect.REGENERATION, "Adds Regeneration."),
    SUGAR("SUGAR", "Sugar", "Strong", null, "Base effect level II. +1 Tipsy."),
    COCOA("COCOA_BEANS", "Cocoa Beans", "Chocolate", Effect.NIGHT_VISION, "Adds Night Vision."),
    BERRIES("SWEET_BERRIES", "Sweet Berries", "Berry", Effect.LUCK, "Adds Luck."),
    PUMPKIN("PUMPKIN", "Pumpkin", "Pumpkin", Effect.HEALTH_BOOST, "Adds Health Boost.");

    private final String material;
    private final String displayName;
    private final String prefix;
    private final Effect adds;
    private final String describe;

    Extra(String material, String displayName, String prefix, Effect adds, String describe) {
        this.material = material;
        this.displayName = displayName;
        this.prefix = prefix;
        this.adds = adds;
        this.describe = describe;
    }

    /** The vanilla item's Material name. */
    public String material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    /** The word it puts in front of the style: "Honey", "Strong"... */
    public String prefix() {
        return prefix;
    }

    /** The effect it adds, or null (Sugar raises the base effect instead). */
    public Effect adds() {
        return adds;
    }

    public String describe() {
        return describe;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Extra byMaterial(String material) {
        for (Extra extra : values()) {
            if (material.equals(extra.material)) {
                return extra;
            }
        }
        return null;
    }

    public static Extra byId(String id) {
        for (Extra extra : values()) {
            if (extra.id().equals(id)) {
                return extra;
            }
        }
        return null;
    }
}
