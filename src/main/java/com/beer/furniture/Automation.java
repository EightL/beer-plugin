package com.beer.furniture;

import com.beer.BeerPlugin;
import com.beer.item.ItemId;
import com.beer.item.Items;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hoppers, run every 8 game ticks (a hopper's pace), and the brewing tick: kettles boil and barrels bubble. A hopper
 * pointing into a kettle or barrel from above or the side feeds it; a hopper below a barrel takes out mugs of
 * well-aged beer. Powered (locked) hoppers do nothing, as in vanilla.
 */
public final class Automation implements Runnable {

    private static final BlockFace[] INPUTS = {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final BeerPlugin plugin;
    private final FurnitureManager manager;

    public Automation(BeerPlugin plugin, FurnitureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.brewTick();
        if (!plugin.settings().hoppers()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Furniture furniture : manager.loaded()) {
            switch (furniture.type()) {
                case BREW_KETTLE -> kettle(furniture);
                case FERMENTING_BARREL -> barrel(furniture, now);
            }
        }
    }

    /** Each feeding hopper puts in one grain, hop or extra that fits (the kettle must have water). */
    private void kettle(Furniture furniture) {
        BrewKettle kettles = manager.kettles();
        for (Inventory input : inputs(furniture)) {
            int slot = first(input, item -> kettles.accepts(furniture, item));
            if (slot >= 0 && kettles.feed(furniture, input.getItem(slot).asOne())) {
                take(input, slot, 1);
            }
        }
    }

    /** Once the beer has had the long wait, an Empty Mug from a feeding hopper goes into the hopper below, filled. */
    private void barrel(Furniture furniture, long now) {
        Inventory output = output(furniture);
        if (output == null) {
            return;
        }
        for (Inventory input : inputs(furniture)) {
            int slot = first(input, item -> Items.is(item, ItemId.EMPTY_MUG));
            if (slot < 0) {
                continue;
            }
            ItemStack beer = manager.barrels().pourForHopper(furniture, now);
            if (beer == null || !hasRoom(output, beer)) {
                return;
            }
            take(input, slot, 1);
            output.addItem(beer);
            manager.barrels().poured(furniture);
            return;
        }
    }

    // ------------------------------------------------------------------------------------------ hoppers

    /** Unlocked hoppers above or beside the furniture that point into it. */
    private static List<Inventory> inputs(Furniture furniture) {
        List<Inventory> out = new ArrayList<>(1);
        Block block = furniture.block();
        for (BlockFace face : INPUTS) {
            Inventory inventory = hopper(block.getRelative(face), face.getOppositeFace());
            if (inventory != null) {
                out.add(inventory);
            }
        }
        return out;
    }

    /** The unlocked hopper below the furniture, whichever way it points, or null. */
    private static Inventory output(Furniture furniture) {
        return hopper(furniture.block().getRelative(BlockFace.DOWN), null);
    }

    /** The inventory of an unlocked hopper at {@code block} (pointing {@code facing}, if given), or null. */
    private static Inventory hopper(Block block, BlockFace facing) {
        if (block.getType() != Material.HOPPER || !(block.getBlockData() instanceof org.bukkit.block.data.type.Hopper data)
                || !data.isEnabled() || (facing != null && data.getFacing() != facing)) {
            return null;
        }
        return block.getState(false) instanceof Hopper hopper ? hopper.getInventory() : null;
    }

    /**
     * Puts something the furniture made (a kettle's empty bucket or bottle) into the hopper below it, or drops it on top
     * of the furniture when there is none or it is full.
     */
    public static void output(Furniture furniture, ItemStack item) {
        Inventory below = output(furniture);
        var rest = below == null ? List.of(item) : below.addItem(item).values();
        for (ItemStack left : rest) {
            furniture.block().getWorld().dropItem(furniture.block().getLocation().add(0.5, 1.05, 0.5), left);
        }
    }

    private static boolean hasRoom(Inventory inventory, ItemStack item) {
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.isEmpty() || (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private static int first(Inventory inventory, java.util.function.Predicate<ItemStack> test) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.isEmpty() && test.test(item)) {
                return i;
            }
        }
        return -1;
    }

    private static void take(Inventory inventory, int slot, int amount) {
        ItemStack item = inventory.getItem(slot);
        inventory.setItem(slot, item.getAmount() <= amount ? null : item.asQuantity(item.getAmount() - amount));
    }
}
