package com.beer.brew;

import com.beer.Keys;
import com.beer.item.ItemId;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

import static com.beer.item.Items.plain;

/** Buckets of Wort, mugs of beer and Fermenting Barrel items: making them and reading them back. */
public final class BrewItems {

    /** Mugs of the same beer stack this high. */
    public static final int MUG_STACK = 16;

    private BrewItems() {
    }

    /** What {@code /beer give} and recipe books show: a pale wort, a Pale Ale. */
    public static ItemStack example(ItemId id, int amount, int quality) {
        return id == ItemId.WORT_BUCKET ? wort(new Wort(0, 4, 0, 1, null))
                : beer(Beer.plain(Style.PALE_ALE), Math.max(BrewRules.FRESH_STARS, quality), amount);
    }

    // ------------------------------------------------------------------------------------------ wort

    public static ItemStack wort(Wort wort) {
        Colour colour = wort.colour();
        List<Component> lore = new ArrayList<>();
        lore.add(plain(colour.displayName() + " wort", TextColor.color(colour.color())).append(details(wort)));
        lore.add(plain("Warm: ", NamedTextColor.GRAY).append(plain(BrewRules.name(BrewRules.pour(wort, false, false, Wood.PALE, 2)), NamedTextColor.WHITE)));
        lore.add(plain("Cold: ", NamedTextColor.GRAY).append(plain(BrewRules.name(BrewRules.pour(wort, true, false, Wood.PALE, 2)), NamedTextColor.AQUA)));
        lore.add(plain(ItemId.WORT_BUCKET.hint(), NamedTextColor.GRAY));
        ItemStack stack = Items.tagged(ItemId.WORT_BUCKET, 1, Component.text(ItemId.WORT_BUCKET.displayName()), lore);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(colour.look()).build());
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.BREW, PersistentDataType.STRING, wort.encode()));
        return stack;
    }

    /** The wort in a Bucket of Wort, or null. */
    public static Wort wortOf(ItemStack stack) {
        return Items.is(stack, ItemId.WORT_BUCKET)
                ? Wort.decode(stack.getPersistentDataContainer().get(Keys.BREW, PersistentDataType.STRING)) : null;
    }

    /** " · Hoppy · Cocoa Beans" */
    private static Component details(Wort wort) {
        String text = (wort.hoppy() ? " · Hoppy" : "") + (wort.extra() == null ? "" : " · " + wort.extra().displayName());
        return plain(text, NamedTextColor.DARK_GRAY);
    }

    // ------------------------------------------------------------------------------------------ beer

    public static ItemStack beer(Beer beer, int stars, int amount) {
        int clamped = Math.clamp(stars, 1, Items.MAX_QUALITY);
        Colour colour = beer.style().colour();
        Component name = Component.text(BrewRules.name(beer));
        List<Component> lore = new ArrayList<>();
        if (beer.legend() != null) {
            lore.add(plain("✦ Tavern Legend ", Items.LEGEND_COLOR).append(plain("(" + beer.style().displayName() + ")", NamedTextColor.DARK_GRAY)));
        }
        lore.add(plain(beer.style().displayName(), TextColor.color(colour.color()))
                .append(plain(" · " + (beer.style().lager() ? "Lager" : "Ale"), NamedTextColor.DARK_GRAY)));
        lore.add(Items.stars(clamped));
        lore.addAll(effectLines(beer));
        lore.add(plain(ItemId.BEER.hint(), NamedTextColor.GRAY));
        ItemStack stack = Items.tagged(ItemId.BEER, amount, beer.legend() == null ? name : name.color(Items.LEGEND_COLOR), lore);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(beer.style().look()).build());
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, MUG_STACK);
        if (beer.legend() != null) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(1.6f)
                .animation(ItemUseAnimation.DRINK)
                .sound(Key.key("minecraft", "entity.generic.drink"))
                .hasConsumeParticles(false)
                .build());
        // "liquid bread": a little hunger, and it can be drunk when full
        stack.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(2).saturation(0.4f).canAlwaysEat(true).build());
        stack.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(Items.create(ItemId.EMPTY_MUG, 1)));
        stack.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.BREW, PersistentDataType.STRING, beer.encode());
            pdc.set(Keys.QUALITY, PersistentDataType.INTEGER, clamped);
        });
        return stack;
    }

    /** The beer in a mug, or null. */
    public static Beer beerOf(ItemStack stack) {
        return Items.is(stack, ItemId.BEER)
                ? Beer.decode(stack.getPersistentDataContainer().get(Keys.BREW, PersistentDataType.STRING)) : null;
    }

    /** "▸ Haste", "▸ Strength", and a legend's bonus marked ✦. */
    public static List<Component> effectLines(Beer beer) {
        List<Component> lines = new ArrayList<>();
        List<Trait> bonus = beer.legend() == null ? List.of() : beer.legend().bonus();
        for (Trait trait : BrewRules.effects(beer)) {
            boolean legendary = bonus.contains(trait);
            lines.add(plain(legendary ? " ✦ " : " ▸ ", legendary ? Items.LEGEND_COLOR : NamedTextColor.DARK_GRAY)
                    .append(plain(trait.effect().displayName() + (trait.level() > 0 ? " II" : ""), TextColor.color(trait.effect().color()))));
        }
        return lines;
    }

    // ------------------------------------------------------------------------------------------ barrel

    public static ItemStack barrel(Wood wood, int amount) {
        List<Component> lore = new ArrayList<>();
        lore.add(plain(wood.displayName() + " wood", wood == Wood.PALE ? NamedTextColor.YELLOW : NamedTextColor.GOLD));
        lore.add(plain(wood == Wood.PALE ? "+1★ for Wheat and Pale beers" : "+1★ for Amber and Dark beers", NamedTextColor.DARK_GREEN));
        lore.add(plain(ItemId.FERMENTING_BARREL.hint(), NamedTextColor.GRAY));
        ItemStack stack = Items.tagged(ItemId.FERMENTING_BARREL, amount,
                Component.text(wood.displayName() + " " + ItemId.FERMENTING_BARREL.displayName()), lore);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(wood.id()).build());
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.WOOD, PersistentDataType.STRING, wood.id()));
        return stack;
    }

    public static Wood woodOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Wood.PALE
                : Wood.byId(stack.getPersistentDataContainer().get(Keys.WOOD, PersistentDataType.STRING));
    }
}
