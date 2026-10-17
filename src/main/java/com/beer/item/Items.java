package com.beer.item;

import com.beer.Keys;
import com.beer.brew.BrewItems;
import com.beer.brew.Wood;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Creates and recognises Beer items. */
public final class Items {

    public static final int MAX_QUALITY = 5;
    /** Gold, for Tavern Legend names. */
    public static final TextColor LEGEND_COLOR = TextColor.color(0xF0C040);

    private Items() {
    }

    public static ItemStack create(ItemId id, int amount) {
        return create(id, amount, 1);
    }

    /** A Beer item; {@code quality} only matters for beer. Brews and barrels get an example (a pale wort, a Pale Ale, a pale barrel). */
    public static ItemStack create(ItemId id, int amount, int quality) {
        if (id.kind() == ItemId.Kind.BREW) {
            return BrewItems.example(id, amount, quality);
        }
        if (id == ItemId.FERMENTING_BARREL) {
            return BrewItems.barrel(Wood.PALE, amount);
        }
        ItemStack stack = tagged(id, amount, Component.text(id.displayName()), List.of(plain(id.hint(), NamedTextColor.GRAY)));
        switch (id) {
            case BEER_JOURNAL -> stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            case EMPTY_MUG -> stack.setData(DataComponentTypes.MAX_STACK_SIZE, BrewItems.MUG_STACK);
            default -> {
            }
        }
        return stack;
    }

    /** A bare Beer item: its base, model, name, lore and id tag, nothing else; the caller adds the rest. */
    public static ItemStack tagged(ItemId id, int amount, Component name, List<Component> lore) {
        ItemStack stack = ItemStack.of(id.base(), Math.max(1, amount));
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Keys.NAMESPACE, id.model()));
        stack.setData(DataComponentTypes.ITEM_NAME, name);
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, id.id()));
        return stack;
    }

    /** The Beer item this stack is, or null for anything else (including null and air). */
    public static ItemId idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = stack.getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
        return id == null ? null : ItemId.byId(id);
    }

    public static boolean is(ItemStack stack, ItemId id) {
        return idOf(stack) == id;
    }

    public static int qualityOf(ItemStack stack) {
        Integer quality = stack.getPersistentDataContainer().get(Keys.QUALITY, PersistentDataType.INTEGER);
        return quality == null ? 1 : quality;
    }

    /** "★★☆☆☆" in gold and grey. */
    public static Component stars(int stars) {
        return Component.text()
                .append(plain("★".repeat(stars), NamedTextColor.GOLD))
                .append(plain("☆".repeat(MAX_QUALITY - stars), NamedTextColor.DARK_GRAY))
                .build();
    }

    public static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
