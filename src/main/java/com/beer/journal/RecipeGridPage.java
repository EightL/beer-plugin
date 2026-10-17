package com.beer.journal;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One recipe as a 3x3 crafting grid, an arrow and the result, laid out like the crafting table. */
final class RecipeGridPage extends Page {

    private static final int[] GRID = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };
    private static final int ARROW = 23;
    private static final int RESULT = 25;
    /** Choices with more options than this list the first few and a count. */
    private static final int LISTED = 6;

    private final Recipe recipe;
    /** The recipe list this grid was opened from. */
    private final java.util.function.Supplier<Page> back;

    RecipeGridPage(Journal journal, Player player, Recipe recipe, java.util.function.Supplier<Page> back) {
        super(journal, player, "Journal › Recipes");
        this.recipe = recipe;
        this.back = back;
    }

    @Override
    protected Material accent() {
        return Material.YELLOW_STAINED_GLASS_PANE;
    }

    @Override
    protected void render() {
        RecipeChoice[] cells = layout(recipe);
        for (int i = 0; i < GRID.length; i++) {
            if (cells[i] != null) {
                set(GRID[i], ingredient(cells[i]));
            }
        }
        ItemStack result = recipe.getResult();
        set(ARROW, icon(Material.SPECTRAL_ARROW, Component.text("Crafting Output", NamedTextColor.YELLOW), List.of()));
        set(RESULT, shown(result.clone(), null));

        List<Component> info = new ArrayList<>();
        info.add(line(recipe instanceof ShapedRecipe ? "Shaped: place it exactly like this." : "Shapeless: any arrangement works.",
                NamedTextColor.GRAY));
        set(4, icon(Material.BOOK, result.effectiveName().color(NamedTextColor.GOLD), info));
        set(BACK, icon(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of()),
                () -> turnTo(back.get()));
    }

    /** The ingredient in each of the nine cells, row by row (null = empty). */
    private static RecipeChoice[] layout(Recipe recipe) {
        RecipeChoice[] cells = new RecipeChoice[9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    cells[row * 3 + col] = choices.get(shape[row].charAt(col));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            for (int i = 0; i < choices.size() && i < cells.length; i++) {
                cells[i] = choices.get(i);
            }
        }
        return cells;
    }

    /** The choice's example item; when several materials fit (planks, slabs), the first one and a list of the rest. */
    private static ItemStack ingredient(RecipeChoice choice) {
        ItemStack stack = choice.getItemStack().clone();
        stack.setAmount(1);
        if (choice instanceof RecipeChoice.MaterialChoice materials && materials.getChoices().size() > 1) {
            List<Material> options = materials.getChoices();
            List<Component> lore = new ArrayList<>();
            lore.add(line("Any of:", NamedTextColor.GRAY));
            for (int i = 0; i < options.size() && i < LISTED; i++) {
                lore.add(Component.translatable(options.get(i).translationKey(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (options.size() > LISTED) {
                lore.add(line("…and " + (options.size() - LISTED) + " more", NamedTextColor.DARK_GRAY));
            }
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        return shown(stack, null);
    }
}
