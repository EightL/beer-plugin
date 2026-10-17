package com.beer.brew;

import java.util.Locale;

/** Every effect a beer can give: each colour's base effect, Hoppy's Strength, the extras' and the legends' bonuses. */
public enum Effect {
    REGENERATION("Regeneration", true, 0.25, 0xF07CB0),
    NIGHT_VISION("Night Vision", false, 1.0, 0x5C7CF0),
    HASTE("Haste", true, 1.0, 0xE8C440),
    SPEED("Speed", true, 1.0, 0x6CC8E8),
    JUMP_BOOST("Jump Boost", true, 1.0, 0x8CE07C),
    ABSORPTION("Absorption", true, 0.5, 0xF0C860),
    LUCK("Luck", true, 1.0, 0x6CC060),
    RESISTANCE("Resistance", true, 1.0, 0xA89CB8),
    STRENGTH("Strength", true, 1.0, 0xD05048),
    HEALTH_BOOST("Health Boost", true, 0.5, 0xF07848),
    HERO_OF_THE_VILLAGE("Hero of the Village", false, 2.0, 0x60D860);

    private final String displayName;
    private final boolean leveled;
    private final double weight;
    private final int color;

    Effect(String displayName, boolean leveled, double weight, int color) {
        this.displayName = displayName;
        this.leveled = leveled;
        this.weight = weight;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether level II means anything. */
    public boolean leveled() {
        return leveled;
    }

    /** Duration share: strong effects such as Regeneration last a quarter as long as the rest. */
    public double weight() {
        return weight;
    }

    /** Text colour in lore, as 0xRRGGBB. */
    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Effect byId(String id) {
        for (Effect effect : values()) {
            if (effect.id().equals(id)) {
                return effect;
            }
        }
        return null;
    }
}
