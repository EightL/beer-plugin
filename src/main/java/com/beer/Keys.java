package com.beer;

import org.bukkit.NamespacedKey;

/** Every PersistentDataContainer key the plugin uses. The namespace is fixed so data survives a plugin rename. */
public final class Keys {

    public static final String NAMESPACE = "beer";

    /** Item: which Beer item this is (see {@link com.beer.item.ItemId}). */
    public static final NamespacedKey ITEM_ID = key("id");
    /** Item: quality in stars, 1 to 5 (beers only). */
    public static final NamespacedKey QUALITY = key("quality");
    /** Chunk: the crops stored in this chunk. */
    public static final NamespacedKey PLANTS = key("plants");
    /** Entity: marks the display and interaction entities of a crop. */
    public static final NamespacedKey PLANT_ENTITY = key("plant_entity");

    /** Entity: the furniture type of a furniture display (see {@link com.beer.furniture.FurnitureType}). */
    public static final NamespacedKey FURNITURE = key("furniture");

    /** Item: a Bucket of Wort's or a beer's brew (see {@link com.beer.brew.Wort#encode}, {@link com.beer.brew.Beer#encode}). */
    public static final NamespacedKey BREW = key("brew");
    /** Item and barrel: the barrel's wood, pale or dark. */
    public static final NamespacedKey WOOD = key("wood");
    /** Brew Kettle: water in it, what was added in order, the extra item, and the boil's progress. */
    public static final NamespacedKey KETTLE_WATER = key("kettle_water");
    public static final NamespacedKey KETTLE_ADDED = key("kettle_added");
    public static final NamespacedKey KETTLE_EXTRA = key("kettle_extra");
    public static final NamespacedKey BOILED = key("boiled");
    public static final NamespacedKey HEAT_SINCE = key("heat_since");
    /** Fermenting Barrel: whether it ferments cold (and beside blue ice), when it was filled, mugs left to pour. */
    public static final NamespacedKey COLD = key("cold");
    public static final NamespacedKey BLUE_ICE = key("blue_ice");
    public static final NamespacedKey FILLED_AT = key("filled_at");
    public static final NamespacedKey MUGS_LEFT = key("mugs_left");
    /** Player: the Tipsy level and when it last changed. */
    public static final NamespacedKey TIPSY = key("tipsy");
    public static final NamespacedKey TIPSY_SINCE = key("tipsy_since");
    /** Player: beer styles, extras and legends the journal has recorded, and who they have clinked mugs with. */
    public static final NamespacedKey FOUND_STYLES = key("found_styles");
    public static final NamespacedKey FOUND_EXTRAS = key("found_extras");
    public static final NamespacedKey FOUND_LEGENDS = key("found_legends");
    public static final NamespacedKey DRINKING_BUDDIES = key("drinking_buddies");
    /** Chunk: mugs standing on blocks; one entry per block, each with its four slots. */
    public static final NamespacedKey TABLE_MUGS = key("table_mugs");
    public static final NamespacedKey MUGS = key("mugs");
    /** Entity: marks the display and interaction entities of mugs on a table. */
    public static final NamespacedKey TABLE_ENTITY = key("table_entity");

    public static final NamespacedKey X = key("x");
    public static final NamespacedKey Y = key("y");
    public static final NamespacedKey Z = key("z");
    public static final NamespacedKey STAGE = key("stage");
    /** Plant: barley or hops. */
    public static final NamespacedKey SPECIES = key("species");

    private Keys() {
    }

    public static NamespacedKey key(String value) {
        return new NamespacedKey(NAMESPACE, value);
    }
}
