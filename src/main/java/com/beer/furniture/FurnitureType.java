package com.beer.furniture;

import com.beer.item.ItemId;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Placeable brewing furniture. Each is one persistent ItemDisplay (holding its state) plus a barrier block for
 * collision and clicks. Each mines like the vanilla block it is made of ({@link #like()}): its hardness, the right
 * tool, its sounds.
 */
public enum FurnitureType {
    BREW_KETTLE(ItemId.BREW_KETTLE, Material.BARRIER, Material.COPPER_BLOCK),
    FERMENTING_BARREL(ItemId.FERMENTING_BARREL, Material.BARRIER, Material.BARREL);

    private final ItemId item;
    private final Material block;
    private final Material like;

    FurnitureType(ItemId item, Material block, Material like) {
        this.item = item;
        this.block = block;
        this.like = like;
    }

    /** The vanilla block it mines like: the barrel wants an axe, the copper kettle a pickaxe. */
    public Material like() {
        return like;
    }

    public ItemId item() {
        return item;
    }

    /** The block it occupies. */
    public Material block() {
        return block;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FurnitureType byId(String id) {
        for (FurnitureType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static FurnitureType byItem(ItemId item) {
        for (FurnitureType type : values()) {
            if (type.item == item) {
                return type;
            }
        }
        return null;
    }
}
