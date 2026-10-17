package com.beer.item;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Every Beer item. Each is a vanilla base item carrying {@code item_model = beer:<id>} and a {@code beer:id} tag, so
 * no vanilla item is re-textured. The base only matters without the resource pack and for vanilla behaviour, so it is
 * a material that does nothing on its own (vanilla recipes are blocked by {@link CraftGuard}).
 */
public enum ItemId {
    BARLEY(Material.PAPER, "Barley", Kind.PLAIN, "Plant it on farmland, or smoke it into Malt."),
    HOPS(Material.PAPER, "Hops", Kind.PLAIN, "Plant it on farmland: it climbs a pole. 1 or 2 go in a brew."),
    MALT(Material.PAPER, "Malt", Kind.PLAIN, "Brew with it, or smoke it again into Dark Malt."),
    DARK_MALT(Material.PAPER, "Dark Malt", Kind.PLAIN, "A little colours a whole beer: 1 makes it amber, 2 dark."),
    WORT_BUCKET(Material.PAPER, "Bucket of Wort", Kind.BREW, "Pour it into a Fermenting Barrel."),
    EMPTY_MUG(Material.PAPER, "Empty Mug", Kind.PLAIN, "Fill it at a Fermenting Barrel. Sneak to put it on a table."),
    BEER(Material.PAPER, "Beer", Kind.BREW, "Drink it. Sneak and right-click a block top to put it down."),
    BREW_KETTLE(Material.PAPER, "Brew Kettle", Kind.PLAIN, "Place it over a campfire, then fill it with water, grain and hops."),
    FERMENTING_BARREL(Material.PAPER, "Fermenting Barrel", Kind.PLAIN, "Pour wort in and wait. Beside ice it makes lager."),
    BEER_JOURNAL(Material.PAPER, "Beer Journal", Kind.PLAIN, "Use it to read about brewing: styles, extras, legends and recipes.");

    /** What an item carries besides its id. */
    public enum Kind {
        /** Nothing else. */
        PLAIN,
        /** A brew: a Bucket of Wort, or a beer (which also has stars). See {@link com.beer.brew.BrewItems}. */
        BREW
    }

    private final Material base;
    private final String displayName;
    private final Kind kind;
    private final String hint;

    ItemId(Material base, String displayName, Kind kind, String hint) {
        this.base = base;
        this.displayName = displayName;
        this.kind = kind;
        this.hint = hint;
    }

    /** The id in item tags, commands and the resource pack, for example {@code dark_malt}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Material base() {
        return base;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    public boolean hasQuality() {
        return this == BEER;
    }

    /** The item model ({@code beer:<model>}): its id, except mugs, which share one. */
    public String model() {
        return this == EMPTY_MUG || this == BEER ? "mug" : id();
    }

    public String hint() {
        return hint;
    }

    public static ItemId byId(String id) {
        for (ItemId item : values()) {
            if (item.id().equalsIgnoreCase(id)) {
                return item;
            }
        }
        return null;
    }
}
