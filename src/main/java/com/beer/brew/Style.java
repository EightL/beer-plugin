package com.beer.brew;

import java.util.Locale;

/** The eight beers: a colour, fermented warm (ale) or cold (lager). */
public enum Style {
    WHEAT_BEER("Wheat Beer", Colour.WHEAT, false),
    CRYSTAL_WHEAT("Crystal Wheat", Colour.WHEAT, true),
    PALE_ALE("Pale Ale", Colour.PALE, false),
    PILSNER("Pilsner", Colour.PALE, true),
    RED_ALE("Red Ale", Colour.AMBER, false),
    MARZEN("Märzen", Colour.AMBER, true),
    STOUT("Stout", Colour.DARK, false),
    DARK_LAGER("Dark Lager", Colour.DARK, true);

    private final String displayName;
    private final Colour colour;
    private final boolean lager;

    Style(String displayName, Colour colour, boolean lager) {
        this.displayName = displayName;
        this.colour = colour;
        this.lager = lager;
    }

    public String displayName() {
        return displayName;
    }

    public Colour colour() {
        return colour;
    }

    public boolean lager() {
        return lager;
    }

    /** The mug model: the colour, with {@code _clear} for a lager. */
    public String look() {
        return colour.look() + (lager ? "_clear" : "");
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Style of(Colour colour, boolean cold) {
        for (Style style : values()) {
            if (style.colour == colour && style.lager == cold) {
                return style;
            }
        }
        throw new IllegalArgumentException(colour + " " + cold);
    }

    public static Style byId(String id) {
        for (Style style : values()) {
            if (style.id().equalsIgnoreCase(id)) {
                return style;
            }
        }
        return null;
    }
}
