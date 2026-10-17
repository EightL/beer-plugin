package com.beer.journal;

import com.beer.Keys;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One page of the Beer Journal: a 6-row chest menu with a coloured top bar, a dark bottom bar holding the
 * navigation, and clickable icons in between. Pages are rebuilt on every open, so they always show fresh progress.
 */
abstract class Page implements InventoryHolder {

    static final int ROWS = 6;
    static final int BACK = 45;
    static final int CLOSE = 49;
    /** Width of the wrapped lore text, in characters. */
    private static final int WRAP = 34;

    protected final Journal journal;
    protected final Player player;
    private final Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    Page(Journal journal, Player player, String title) {
        this.journal = journal;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, ROWS * 9, Component.text(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Draws the page's own icons; the frame is drawn first. */
    protected abstract void render();

    /** The colour of the top bar. */
    protected Material accent() {
        return Material.ORANGE_STAINED_GLASS_PANE;
    }

    void open() {
        inventory.clear();
        actions.clear();
        ItemStack fill = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot < 9 ? pane(accent()) : slot >= 45 ? pane(Material.BLACK_STAINED_GLASS_PANE) : fill);
        }
        render();
        set(CLOSE, icon(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()), player::closeInventory);
        player.openInventory(inventory);
    }

    void click(int slot) {
        Runnable action = actions.get(slot);
        if (action != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.0f);
            action.run();
        }
    }

    protected void set(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    protected void set(int slot, ItemStack icon, Runnable action) {
        inventory.setItem(slot, icon);
        actions.put(slot, action);
    }

    /** A "Back" arrow to the journal's front page. */
    protected void backButton() {
        set(BACK, icon(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of()), () -> turnTo(home()));
    }

    /** The journal's front page. */
    protected Page home() {
        return new BeerHomePage(journal, player);
    }

    protected void turnTo(Page page) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.7f, 1.0f);
        page.open();
    }

    // ------------------------------------------------------------------------------------------ icons

    static ItemStack pane(Material material) {
        ItemStack pane = ItemStack.of(material);
        pane.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return pane;
    }

    /** A vanilla item as an icon, with only our name and lore in its tooltip. */
    static ItemStack icon(Material material, Component name, List<Component> lore) {
        return dress(ItemStack.of(material), name, lore);
    }

    /** A Beer model as an icon, for example {@code mug} with variant {@code gold}. */
    static ItemStack model(String model, String variant, Component name, List<Component> lore) {
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Keys.NAMESPACE, model));
        if (variant != null) {
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(variant).build());
        }
        return dress(stack, name, lore);
    }

    private static ItemStack dress(ItemStack stack, Component name, List<Component> lore) {
        if (name != null) {
            stack.setData(DataComponentTypes.ITEM_NAME, name.decoration(TextDecoration.ITALIC, false));
        }
        if (lore != null) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.POTION_CONTENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        DataComponentTypes.ENCHANTMENTS, DataComponentTypes.FOOD)
                .build());
        return stack;
    }

    /** A real item (amount and model kept) as a menu icon: our lore if given, and no stat lines in the tooltip. */
    static ItemStack shown(ItemStack stack, List<Component> lore) {
        if (lore != null) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.FOOD, DataComponentTypes.CONSUMABLE)
                .build());
        return stack;
    }

    static ItemStack glint(ItemStack stack) {
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    // ------------------------------------------------------------------------------------------ text

    static Component line(String text, TextColor color) {
        return Items.plain(text, color);
    }

    /** Text wrapped to the tooltip width, every line in one colour. */
    static List<Component> wrap(String text, TextColor color) {
        List<Component> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!current.isEmpty() && current.length() + 1 + word.length() > WRAP) {
                out.add(line(current.toString(), color));
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            out.add(line(current.toString(), color));
        }
        return out;
    }

    /** "Label: value" with a grey label. */
    static Component field(String label, String value, TextColor color) {
        return line(label + ": ", NamedTextColor.GRAY).append(line(value, color));
    }

    /** A 10-cell progress bar, "▰▰▰▱▱▱▱▱▱▱ 3/10". */
    static Component bar(String label, int have, int total) {
        int cells = total == 0 ? 0 : Math.round(10f * have / total);
        return line(label + "  ", NamedTextColor.GRAY)
                .append(line("▰".repeat(cells), have >= total ? NamedTextColor.GOLD : NamedTextColor.GREEN))
                .append(line("▱".repeat(10 - cells), NamedTextColor.DARK_GRAY))
                .append(line("  " + have + "/" + total, NamedTextColor.WHITE));
    }

    static Component blank() {
        return Component.empty();
    }

    static Component hint(String text) {
        return line(text, NamedTextColor.YELLOW);
    }

    /** "20 min", "1 hour", "3 hours". */
    static String minutes(long ms) {
        long minutes = Math.round(ms / 60_000.0);
        if (minutes >= 60 && minutes % 60 == 0) {
            return minutes == 60 ? "1 hour" : minutes / 60 + " hours";
        }
        return minutes + " min";
    }
}
