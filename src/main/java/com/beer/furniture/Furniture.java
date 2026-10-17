package com.beer.furniture;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;

/** One placed piece of furniture: its display entity (which stores the state) and the barrier block it occupies. */
public record Furniture(ItemDisplay display, FurnitureType type) {

    public Block block() {
        return display.getLocation().getBlock();
    }

    /** Centre of the block, for sounds and drops. */
    public Location center() {
        return block().getLocation().add(0.5, 0.5, 0.5);
    }
}
