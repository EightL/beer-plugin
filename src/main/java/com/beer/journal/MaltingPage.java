package com.beer.journal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;

/** One malting step: what goes in, the three ways to cook it with their times, and what comes out. */
final class MaltingPage extends Page {

    private final CookingRecipe<?> recipe;

    MaltingPage(Journal journal, Player player, CookingRecipe<?> recipe) {
        super(journal, player, "Beer Journal › Recipe Book");
        this.recipe = recipe;
    }

    @Override
    protected void render() {
        ItemStack input = recipe.getInputChoice().getItemStack().clone();
        set(20, shown(input, null));
        String base = recipe.getKey().getKey().replace("_smoking", "");
        set(22, cooker(Material.SMOKER, "Smoker", seconds(base + "_smoking")));
        set(31, cooker(Material.FURNACE, "Furnace", seconds(base + "_smelting")));
        set(13, cooker(Material.CAMPFIRE, "Campfire", seconds(base + "_campfire")));
        set(23, icon(Material.SPECTRAL_ARROW, Component.text("Cooking Output", NamedTextColor.YELLOW), List.of()));
        set(24, shown(recipe.getResult().clone(), null));
        set(4, icon(Material.BOOK, recipe.getResult().effectiveName().color(NamedTextColor.GOLD),
                wrap("Cook it in any of the three.", NamedTextColor.GRAY)));
        set(BACK, icon(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of()),
                () -> turnTo(new BrewRecipesPage(journal, player)));
    }

    private ItemStack cooker(Material material, String name, long seconds) {
        return icon(material, Component.text(name, NamedTextColor.GOLD),
                List.of(field("Time", seconds > 0 ? seconds + " s each" : "?", NamedTextColor.WHITE)));
    }

    /** Cooking time in seconds of the recipe with this key, or 0. */
    private long seconds(String key) {
        Recipe found = journal.plugin().getServer().getRecipe(new NamespacedKey(recipe.getKey().getNamespace(), key));
        return found instanceof CookingRecipe<?> cooking ? cooking.getCookingTime() / 20 : 0;
    }
}
