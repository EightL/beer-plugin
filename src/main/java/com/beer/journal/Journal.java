package com.beer.journal;

import com.beer.Keys;
import com.beer.BeerPlugin;
import com.beer.brew.Extra;
import com.beer.brew.Legend;
import com.beer.brew.Style;
import com.beer.item.ItemId;
import com.beer.item.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Beer Journal: what each player has brewed (beer styles, extras and legends) and who they have drunk with, kept
 * on the player, and the menu that shows it. Opened with the journal item or {@code /beer journal}.
 */
public final class Journal implements Listener {

    private final BeerPlugin plugin;

    public Journal(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    public BeerPlugin plugin() {
        return plugin;
    }

    // ------------------------------------------------------------------------------------------ discoveries

    // styles and legends are recorded when poured, extras when they go in a kettle

    public boolean found(Player player, Style style) {
        return add(player, Keys.FOUND_STYLES, style.id());
    }

    public boolean found(Player player, Extra extra) {
        return add(player, Keys.FOUND_EXTRAS, extra.id());
    }

    public boolean found(Player player, Legend legend) {
        return add(player, Keys.FOUND_LEGENDS, legend.id());
    }

    /** Records someone this player clinked mugs with; returns true the first time. */
    public boolean clinked(Player player, Player other) {
        return add(player, Keys.DRINKING_BUDDIES, other.getUniqueId().toString());
    }

    public boolean has(Player player, Style style) {
        return list(player, Keys.FOUND_STYLES).contains(style.id());
    }

    public boolean has(Player player, Extra extra) {
        return list(player, Keys.FOUND_EXTRAS).contains(extra.id());
    }

    public boolean has(Player player, Legend legend) {
        return list(player, Keys.FOUND_LEGENDS).contains(legend.id());
    }

    public int count(Player player, NamespacedKey key) {
        return list(player, key).size();
    }

    private static List<String> list(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LIST.strings(), List.of());
    }

    private static boolean add(Player player, NamespacedKey key, String id) {
        List<String> found = new ArrayList<>(list(player, key));
        if (found.contains(id)) {
            return false;
        }
        found.add(id);
        player.getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), found);
        return true;
    }

    // ------------------------------------------------------------------------------------------ menu

    /** The Beer Journal: brewing, styles, extras, legends and the brewing recipes. */
    public void open(Player player) {
        new BeerHomePage(this, player).open();
    }

    // Right-clicking air arrives as a "cancelled" event, so cancellation is checked by hand.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        ItemId id = Items.idOf(event.getItem());
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getHand() != EquipmentSlot.HAND || id != ItemId.BEER_JOURNAL
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page page) {
            event.setCancelled(true);
            if (event.getView().getTopInventory().equals(event.getClickedInventory())) {
                page.click(event.getSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) {
            event.setCancelled(true);
        }
    }
}
