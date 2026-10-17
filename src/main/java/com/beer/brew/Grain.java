package com.beer.brew;

import java.util.Locale;

/** What goes into the kettle as the body of the beer: four of them, in any mix. */
public enum Grain {
    WHEAT("Wheat"),
    MALT("Malt"),
    DARK_MALT("Dark Malt");

    private final String displayName;

    Grain(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Grain byId(String id) {
        for (Grain grain : values()) {
            if (grain.id().equals(id)) {
                return grain;
            }
        }
        return null;
    }
}
