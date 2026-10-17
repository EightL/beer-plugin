package com.beer.world;

import com.beer.item.Items;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Using up what a dispenser held when the plugin cancelled its dispense and did the work itself. A cancelled dispense
 * keeps its item, so this runs once the event is over.
 */
public final class Dispensers {

    private Dispensers() {
    }

    /** Takes one plain (non-Beer) {@code material} out of the dispenser. */
    public static void consume(Plugin plugin, Block dispenser, Material material) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = inventory(dispenser);
            int slot = slot(inventory, material);
            if (slot >= 0) {
                ItemStack item = inventory.getItem(slot);
                inventory.setItem(slot, item.getAmount() <= 1 ? null : item.asQuantity(item.getAmount() - 1));
            }
        });
    }

    /** Turns one plain {@code from} in the dispenser into {@code to} (a Water Bucket into a Bucket). */
    public static void swap(Plugin plugin, Block dispenser, Material from, Material to) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = inventory(dispenser);
            int slot = slot(inventory, from);
            if (slot < 0) {
                return;
            }
            ItemStack item = inventory.getItem(slot);
            if (item.getAmount() <= 1) {
                inventory.setItem(slot, ItemStack.of(to));
            } else {
                inventory.setItem(slot, item.asQuantity(item.getAmount() - 1));
                inventory.addItem(ItemStack.of(to)).values()
                        .forEach(rest -> dispenser.getWorld().dropItemNaturally(dispenser.getLocation().add(0.5, 1, 0.5), rest));
            }
        });
    }

    /** Wears the dispenser's plain {@code material} tool by {@code uses} (Unbreaking applies); it breaks at 0. */
    public static void wear(Plugin plugin, Block dispenser, Material material, int uses) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = inventory(dispenser);
            int slot = slot(inventory, material);
            if (slot < 0) {
                return;
            }
            ItemStack tool = inventory.getItem(slot);
            if (!(tool.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) {
                return;
            }
            int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
            int damage = 0;
            for (int i = 0; i < uses; i++) {
                if (ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) {
                    damage++;
                }
            }
            int max = meta.hasMaxDamage() ? meta.getMaxDamage() : tool.getType().getMaxDurability();
            if (meta.getDamage() + damage >= max) {
                inventory.setItem(slot, null);
                dispenser.getWorld().playSound(dispenser.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return;
            }
            meta.setDamage(meta.getDamage() + damage);
            tool.setItemMeta(meta);
            inventory.setItem(slot, tool);
        });
    }

    /** Whether the dispensed item is a plain (non-Beer) {@code material}. */
    public static boolean plain(ItemStack item, Material material) {
        return item.getType() == material && Items.idOf(item) == null;
    }

    private static Inventory inventory(Block block) {
        return block.getState(false) instanceof Dispenser state ? state.getInventory() : null;
    }

    private static int slot(Inventory inventory, Material material) {
        if (inventory == null) {
            return -1;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && plain(item, material)) {
                return i;
            }
        }
        return -1;
    }
}
