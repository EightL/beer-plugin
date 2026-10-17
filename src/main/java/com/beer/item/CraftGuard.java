package com.beer.item;

import com.beer.Keys;
import org.bukkit.Keyed;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/** Beer items sit on a vanilla base (paper); this keeps them out of vanilla recipes such as books. */
public final class CraftGuard implements Listener {

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (blocked(event.getRecipe(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    /** A Crafter never fires PrepareItemCraftEvent, so it is checked as it crafts. */
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter && blocked(event.getRecipe(), crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    /** A vanilla recipe with a Beer item among its ingredients. */
    private static boolean blocked(Recipe recipe, ItemStack[] matrix) {
        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.NAMESPACE)) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (Items.idOf(item) != null) {
                return true;
            }
        }
        return false;
    }
}
