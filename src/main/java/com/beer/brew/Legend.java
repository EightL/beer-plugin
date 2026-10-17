package com.beer.brew;

import java.util.List;
import java.util.Locale;

/** Tavern Legends: one exact brew per colour, with a gold name and a bonus no extra gives. */
public enum Legend {
    MINERS_STOUT("Miner's Stout", Colour.DARK, List.of(new Trait(Effect.HASTE, 1)),
            "Hoppy Chocolate Stout from a dark barrel",
            "Black as a mineshaft, bitter, with something sweet for the dark."),
    FESTBIER("Festbier", Colour.AMBER, List.of(new Trait(Effect.HERO_OF_THE_VILLAGE, 0)),
            "Pumpkin Märzen at 5 stars",
            "An autumn lager, poured at its very best."),
    GLACIER_PILS("Glacier Pils", Colour.PALE, List.of(new Trait(Effect.SPEED, 1)),
            "Hoppy Pilsner fermented beside Blue Ice",
            "The palest, hoppiest lager, kept colder than snow."),
    BEES_KNEES("Bee's Knees", Colour.WHEAT, List.of(new Trait(Effect.JUMP_BOOST, 1)),
            "Honey Wheat Beer from a pale barrel",
            "Cloudy and golden as a hive, kept in the palest wood. Puts a spring in your step.");

    private final String displayName;
    private final Colour colour;
    private final List<Trait> bonus;
    private final String recipe;
    private final String hint;

    Legend(String displayName, Colour colour, List<Trait> bonus, String recipe, String hint) {
        this.displayName = displayName;
        this.colour = colour;
        this.bonus = bonus;
        this.recipe = recipe;
        this.hint = hint;
    }

    public String displayName() {
        return displayName;
    }

    public Colour colour() {
        return colour;
    }

    /** Extra effects. */
    public List<Trait> bonus() {
        return bonus;
    }

    /** What makes it, for the journal once it is brewed. */
    public String recipe() {
        return recipe;
    }

    /** A riddle for the journal until it is brewed. */
    public String hint() {
        return hint;
    }

    /** The bonus in words, for lore and the journal. */
    public String bonusText() {
        return bonus.stream().map(t -> t.effect().displayName() + (t.level() > 0 ? " II" : "")).reduce((a, b) -> a + ", " + b).orElse("");
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Legend byId(String id) {
        for (Legend legend : values()) {
            if (legend.id().equalsIgnoreCase(id)) {
                return legend;
            }
        }
        return null;
    }
}
