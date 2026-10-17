package com.beer.brew;


import java.util.Locale;

/**
 * The colour of a beer, decided by its grain: no Dark Malt makes it pale (or wheat, with 2 or more Wheat), 1 Dark
 * Malt amber, 2 or more dark. Each colour has a base effect and a barrel wood it likes.
 */
public enum Colour {
    WHEAT("Wheat", "straw", Effect.SPEED, Wood.PALE, 0xE8D890),
    PALE("Pale", "gold", Effect.HASTE, Wood.PALE, 0xE8B840),
    AMBER("Amber", "copper", Effect.ABSORPTION, Wood.DARK, 0xC8642C),
    DARK("Dark", "dark", Effect.RESISTANCE, Wood.DARK, 0x6C4430);

    private final String displayName;
    private final String look;
    private final Effect base;
    private final Wood wood;
    private final int color;

    Colour(String displayName, String look, Effect base, Wood wood, int color) {
        this.displayName = displayName;
        this.look = look;
        this.base = base;
        this.wood = wood;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** Resource pack name of the liquid: straw, gold, copper or dark. */
    public String look() {
        return look;
    }

    public Effect base() {
        return base;
    }

    /** The barrel wood that adds a star. */
    public Wood wood() {
        return wood;
    }

    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The colour four grains make. */
    public static Colour of(int wheat, int darkMalt) {
        if (darkMalt >= 2) {
            return DARK;
        }
        if (darkMalt == 1) {
            return AMBER;
        }
        return wheat >= 2 ? WHEAT : PALE;
    }
}
