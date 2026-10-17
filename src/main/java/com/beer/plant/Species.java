package com.beer.plant;

import java.util.Locale;

/**
 * What grows in a crop spot: Barley (like wheat, one block tall) or Hops (a perennial on a 3-block pole that is picked
 * again and again). Stages are indexes into the species' own list.
 */
public enum Species {
    BARLEY(new String[] {"planted", "sprout", "seedling", "tillering", "jointing", "heading", "ripening", "ripe"},
            new String[] {"Planted", "Sprout", "Seedling", "Tillering", "Jointing", "Heading", "Ripening", "Ripe"},
            new float[] {0.2f, 0.3f, 0.4f, 0.55f, 0.7f, 0.85f, 0.9f, 0.9f}),
    HOPS(new String[] {"planted", "shoots", "climbing", "leafy", "burrs", "ripe"},
            new String[] {"Planted", "Shoots", "Climbing", "Leafy", "Burrs", "Ripe"},
            new float[] {3.0f, 3.0f, 3.0f, 3.0f, 3.0f, 3.0f});

    /** Picked hops go back to this stage and regrow from there. */
    public static final int HOPS_PICKED = 3;
    /** Hops models are written 15 units lower (an item model only reaches y = 32); the display is raised to match. */
    public static final float HOPS_RAISE = 15 / 16f;

    private final String[] ids;
    private final String[] names;
    private final float[] heights;

    Species(String[] ids, String[] names, float[] heights) {
        this.ids = ids;
        this.names = names;
        this.heights = heights;
    }

    public int stages() {
        return ids.length;
    }

    public int ripe() {
        return ids.length - 1;
    }

    /** Model and command id of a stage, for example {@code tillering}. */
    public String stageId(int stage) {
        return ids[clamp(stage)];
    }

    public String stageName(int stage) {
        return names[clamp(stage)];
    }

    /** Height of the click box in blocks, matching the model. */
    public float height(int stage) {
        return heights[clamp(stage)];
    }

    public int clamp(int stage) {
        return Math.clamp(stage, 0, ids.length - 1);
    }

    /** The stage with this id, or -1. */
    public int stageOf(String id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equalsIgnoreCase(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Blocks above the crop that must stay free: none for barley, two for the hop pole. */
    public int headroom() {
        return this == HOPS ? 2 : 0;
    }

    public String displayName() {
        return this == HOPS ? "Hops" : "Barley";
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** By id, or null. */
    public static Species byId(String id) {
        for (Species species : values()) {
            if (species.id().equals(id)) {
                return species;
            }
        }
        return null;
    }
}
