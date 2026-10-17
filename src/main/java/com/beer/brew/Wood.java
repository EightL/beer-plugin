package com.beer.brew;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** The two kinds of Fermenting Barrel, by the planks they are made of. */
public enum Wood {
    PALE("Pale", EnumSet.of(Material.OAK_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS,
            Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS, Material.PALE_OAK_PLANKS)),
    DARK("Dark", EnumSet.of(Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS));

    private final String displayName;
    private final Set<Material> planks;

    Wood(String displayName, Set<Material> planks) {
        this.displayName = displayName;
        this.planks = planks;
    }

    public String displayName() {
        return displayName;
    }

    public Set<Material> planks() {
        return planks;
    }

    /** Model state and stored id: pale or dark. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** By id; anything unknown is pale. */
    public static Wood byId(String id) {
        return DARK.id().equals(id) ? DARK : PALE;
    }
}
