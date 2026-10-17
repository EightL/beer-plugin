package com.beer.world;

import com.beer.BeerPlugin;
import com.beer.Settings;
import com.beer.item.ItemId;
import com.beer.item.Items;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Where players find the crops, which are their own seed: Barley from grass in plains, savannas and meadows and from
 * plains and savanna village houses, Hops from grass in taigas and forests, from taiga houses and wandering traders.
 */
public final class SeedSources implements Listener {

    private static final Set<Material> GRASS = Set.of(Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN);
    /** Grass here drops Barley. */
    private static final Set<NamespacedKey> BARLEY_BIOMES = biomes("plains", "sunflower_plains", "savanna", "savanna_plateau",
            "windswept_savanna", "meadow");
    /** Grass here drops Hops. */
    private static final Set<NamespacedKey> HOPS_BIOMES = biomes("taiga", "snowy_taiga", "old_growth_pine_taiga",
            "old_growth_spruce_taiga", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest");
    /** Village house chests that hold Barley or Hops now and then. */
    private static final Map<NamespacedKey, ItemId> CROP_CHESTS = Map.of(
            NamespacedKey.minecraft("chests/village/village_plains_house"), ItemId.BARLEY,
            NamespacedKey.minecraft("chests/village/village_savanna_house"), ItemId.BARLEY,
            NamespacedKey.minecraft("chests/village/village_taiga_house"), ItemId.HOPS);

    private static Set<NamespacedKey> biomes(String... ids) {
        return Arrays.stream(ids).map(NamespacedKey::minecraft).collect(Collectors.toUnmodifiableSet());
    }

    private final BeerPlugin plugin;

    public SeedSources(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onGrassBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!GRASS.contains(block.getType()) || !event.isDropItems() || event.getPlayer().getGameMode() == GameMode.CREATIVE
                || event.getPlayer().getInventory().getItemInMainHand().getType() == Material.SHEARS) {
            return;
        }
        Settings settings = plugin.settings();
        NamespacedKey biome = block.getBiome().getKey();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location drop = block.getLocation().add(0.5, 0.25, 0.5);
        if (BARLEY_BIOMES.contains(biome) && random.nextDouble() < settings.barleyGrassChance()) {
            block.getWorld().dropItemNaturally(drop, Items.create(ItemId.BARLEY, 1));
        }
        if (HOPS_BIOMES.contains(biome) && random.nextDouble() < settings.hopsGrassChance()) {
            block.getWorld().dropItemNaturally(drop, Items.create(ItemId.HOPS, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        ItemId crop = CROP_CHESTS.get(event.getLootTable().getKey());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (crop == null || random.nextDouble() >= plugin.settings().villageChestChance()) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        loot.add(Items.create(crop, random.nextInt(1, 4)));
        event.setLoot(loot);
    }

    /** Now and then a wandering trader sells 2 Hops for an emerald, 4 times. */
    @EventHandler(ignoreCancelled = true)
    public void onTraderSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)
                || ThreadLocalRandom.current().nextDouble() >= plugin.settings().traderChance()) {
            return;
        }
        // offers are generated on first access, so add ours once the trader exists
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!trader.isValid()) {
                return;
            }
            List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
            MerchantRecipe hops = new MerchantRecipe(Items.create(ItemId.HOPS, 2), 4);
            hops.addIngredient(ItemStack.of(Material.EMERALD, 1));
            hops.setExperienceReward(true);
            recipes.add(hops);
            trader.setRecipes(recipes);
        });
    }
}
