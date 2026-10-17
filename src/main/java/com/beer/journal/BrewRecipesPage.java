package com.beer.journal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/** The Beer Journal's recipe book: every brewing crafting recipe (click for its grid) and the malting steps. */
final class BrewRecipesPage extends Page {

    private static final int[] CRAFTING = {20, 21, 22, 23, 24};
    private static final int[] MALTING = {30, 32};

    BrewRecipesPage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Recipe Book");
    }

    @Override
    protected void render() {
        List<Component> about = new ArrayList<>(wrap("Click an item to see how it is made.", NamedTextColor.GRAY));
        about.add(blank());
        about.addAll(wrap("A barrel's planks set its wood, pale or dark.", NamedTextColor.GRAY));
        set(4, icon(Material.CRAFTING_TABLE, Component.text("Recipe Book", NamedTextColor.GOLD), about));

        List<Recipe> crafting = journal.plugin().recipes().craftingKeys().stream()
                .map(key -> journal.plugin().getServer().getRecipe(key)).filter(r -> r != null).toList();
        for (int i = 0; i < crafting.size() && i < CRAFTING.length; i++) {
            Recipe recipe = crafting.get(i);
            List<Component> lore = List.of(
                    line(recipe instanceof ShapedRecipe ? "Shaped" : "Shapeless", NamedTextColor.GRAY),
                    blank(),
                    hint("Click to see the recipe"));
            set(CRAFTING[i], shown(recipe.getResult().clone(), lore),
                    () -> turnTo(new RecipeGridPage(journal, player, recipe, () -> new BrewRecipesPage(journal, player))));
        }
        List<Recipe> malting = journal.plugin().recipes().maltingKeys().stream()
                .map(key -> journal.plugin().getServer().getRecipe(key)).filter(r -> r != null).toList();
        for (int i = 0; i < malting.size() && i < MALTING.length; i++) {
            if (malting.get(i) instanceof CookingRecipe<?> recipe) {
                List<Component> lore = List.of(line("Smoker, furnace or campfire", NamedTextColor.GRAY), blank(),
                        hint("Click to see the recipe"));
                set(MALTING[i], shown(recipe.getResult().clone(), lore),
                        () -> turnTo(new MaltingPage(journal, player, recipe)));
            }
        }
        set(31, icon(Material.SMOKER, Component.text("Malting", NamedTextColor.GOLD),
                wrap("Barley cooks into Malt, and Malt into Dark Malt.", NamedTextColor.GRAY)));
        backButton();
    }
}
