package com.beer.item;

import com.beer.Keys;
import com.beer.brew.BrewItems;
import com.beer.brew.Wood;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;

import java.util.ArrayList;
import java.util.List;

/** Crafting recipes for the brewing furniture, mugs and journal, and the malting steps, shown in players' recipe books. */
public final class Recipes implements Listener {

    private final Server server;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public Recipes(Server server) {
        this.server = server;
    }

    public void register() {
        add(shapeless("beer_journal", Items.create(ItemId.BEER_JOURNAL, 1), new RecipeChoice.MaterialChoice(Material.BOOK), exact(ItemId.HOPS)));
        // a copper pot round a cauldron; a barrel with a copper tap and an iron hoop, its planks set the wood
        add(shaped(ItemId.BREW_KETTLE, "C C", "CKC", "CCC")
                .setIngredient('C', Material.COPPER_INGOT).setIngredient('K', Material.CAULDRON));
        for (Wood wood : Wood.values()) {
            ShapedRecipe barrel = new ShapedRecipe(Keys.key("fermenting_barrel_" + wood.id()), BrewItems.barrel(wood, 1));
            barrel.shape(" I ", "PBP", " C ");
            barrel.setIngredient('I', Material.IRON_INGOT).setIngredient('C', Material.COPPER_INGOT)
                    .setIngredient('B', Material.BARREL).setIngredient('P', new RecipeChoice.MaterialChoice(List.copyOf(wood.planks())));
            add(barrel);
        }
        ShapedRecipe mugs = new ShapedRecipe(Keys.key("empty_mug"), Items.create(ItemId.EMPTY_MUG, 2));
        mugs.shape("P P", "PPP");
        mugs.setIngredient('P', Material.GLASS_PANE);
        add(mugs);

        // malting: barley in a smoker (or furnace, or on a campfire) becomes malt, malt becomes dark malt
        cook("malt", ItemId.BARLEY, ItemId.MALT);
        cook("dark_malt", ItemId.MALT, ItemId.DARK_MALT);
    }

    /** One cooking step in a smoker, a furnace and on a campfire, at vanilla food times. */
    private void cook(String key, ItemId from, ItemId to) {
        // grain has no stars, so every copy of it is identical and an exact match is safe
        RecipeChoice input = RecipeChoice.exactChoice(Items.create(from, 1));
        ItemStack result = Items.create(to, 1);
        add(new SmokingRecipe(Keys.key(key + "_smoking"), result, input, 0.1f, 100));
        add(new FurnaceRecipe(Keys.key(key + "_smelting"), result, input, 0.1f, 200));
        add(new CampfireRecipe(Keys.key(key + "_campfire"), result, input, 0.1f, 600));
    }

    /** Matches any copy of a Beer item, whatever its lore; the example is what recipe books show. */
    private static RecipeChoice exact(ItemId id) {
        return RecipeChoice.predicateChoice(item -> Items.idOf(item) == id, Items.create(id, 1));
    }

    private static ShapelessRecipe shapeless(String key, ItemStack result, RecipeChoice... ingredients) {
        ShapelessRecipe recipe = new ShapelessRecipe(Keys.key(key), result);
        for (RecipeChoice ingredient : ingredients) {
            recipe.addIngredient(ingredient);
        }
        return recipe;
    }

    public int count() {
        return keys.size();
    }

    /** The keys of every registered recipe, in registration order. */
    public List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }

    /** The crafting recipes (no cooking), in registration order (for the journal's recipe book). */
    public List<NamespacedKey> craftingKeys() {
        return keys.stream().filter(key -> server.getRecipe(key) instanceof CraftingRecipe).toList();
    }

    /** The smoker recipes (one per malting step; the furnace and campfire ones match them). */
    public List<NamespacedKey> maltingKeys() {
        return keys.stream().filter(key -> server.getRecipe(key) instanceof SmokingRecipe).toList();
    }

    public void unregister() {
        keys.forEach(server::removeRecipe);
        keys.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(keys);
    }

    private ShapedRecipe shaped(ItemId result, String... shape) {
        ShapedRecipe recipe = new ShapedRecipe(Keys.key(result.id()), Items.create(result, 1));
        recipe.shape(shape);
        return recipe;
    }

    private void add(org.bukkit.inventory.Recipe recipe) {
        server.addRecipe(recipe);
        keys.add(((org.bukkit.Keyed) recipe).getKey());
    }
}
