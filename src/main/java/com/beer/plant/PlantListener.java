package com.beer.plant;

import com.beer.BeerPlugin;
import com.beer.item.ItemId;
import com.beer.item.Items;
import com.beer.world.Dispensers;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Planting, breaking, bone meal, and keeping crops consistent with the blocks around them. */
public final class PlantListener implements Listener {

    private final BeerPlugin plugin;
    private final PlantManager plants;

    public PlantListener(BeerPlugin plugin, PlantManager plants) {
        this.plugin = plugin;
        this.plants = plants;
    }

    // ------------------------------------------------------------------------------------------ chunks

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plants.removeStrays(event.getEntities());
        plants.load(event.getChunk());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plants.unload(event.getChunk());
    }

    // ------------------------------------------------------------------------------------------ player actions

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlant(PlayerInteractEvent event) {
        Species species = species(Items.idOf(event.getItem()));
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || species == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() != BlockFace.UP || !plants.isSoil(clicked)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Block target = clicked.getRelative(BlockFace.UP);
        if (!plants.canPlant(target, species)) {
            if (species == Species.HOPS && target.isEmpty() && plants.at(target) == null) {
                player.sendActionBar(Component.text("Hops need the two blocks above free for their pole.", NamedTextColor.GRAY));
            }
            return;
        }
        ItemStack item = event.getItem();
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, item, player, true, event.getHand());
        if (!check.callEvent() || !check.canBuild()) {
            return;
        }
        plants.plant(target, species);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().getItem(event.getHand()).subtract();
        }
        player.swingHand(event.getHand());
        target.getWorld().playSound(target.getLocation().add(0.5, 0, 0.5), Sound.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** What an item plants: Barley and Hops are their own seed. Null for anything else. */
    public static Species species(ItemId item) {
        if (item == null) {
            return null;
        }
        return switch (item) {
            case BARLEY -> Species.BARLEY;
            case HOPS -> Species.HOPS;
            default -> null;
        };
    }

    /** Left-clicking the crop breaks it: instantly, with any tool. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(PrePlayerAttackEntityEvent event) {
        Plant plant = plants.byEntity(event.getAttacked());
        if (plant == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        BlockBreakEvent check = new BlockBreakEvent(plant.block(), player);
        check.setDropItems(false);
        if (!check.callEvent()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        plants.remove(plant, player.getGameMode() != GameMode.CREATIVE, tool.getEnchantmentLevel(Enchantment.FORTUNE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        Plant plant = plants.byEntity(event.getRightClicked());
        if (plant == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType() == Material.BONE_MEAL && Items.idOf(item) == null) {
            if (boneMeal(plant)) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    item.subtract();
                }
                player.swingHand(event.getHand());
            } else {
                player.sendActionBar(PlantInfo.describe(plugin, plant));
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plant.isRipe()) {
            harvestAndReplant(player, plant);
        } else {
            player.sendActionBar(PlantInfo.describe(plugin, plant));
        }
    }

    /** Right-clicking a ripe crop: drop its harvest and put one grain straight back (hops are picked). */
    private void harvestAndReplant(Player player, Plant plant) {
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        BlockBreakEvent check = new BlockBreakEvent(plant.block(), player);
        check.setDropItems(false);
        if (!check.callEvent()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        plants.harvestAndReplant(plant, tool.getEnchantmentLevel(Enchantment.FORTUNE), player.getGameMode() != GameMode.CREATIVE);
        player.swingHand(EquipmentSlot.HAND);
    }

    /** A dispenser facing a crop: bone meal grows it; shears harvest and replant barley, or pick hops, once it is ripe. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() != Material.DISPENSER || !(event.getBlock().getBlockData() instanceof Directional facing)) {
            return;
        }
        Plant plant = plants.at(event.getBlock().getRelative(facing.getFacing()));
        if (plant == null) {
            return;
        }
        ItemStack item = event.getItem();
        Block dispenser = event.getBlock();
        if (Dispensers.plain(item, Material.BONE_MEAL)) {
            event.setCancelled(true);
            if (boneMeal(plant)) {
                Dispensers.consume(plugin, dispenser, Material.BONE_MEAL);
            }
        } else if (Dispensers.plain(item, Material.SHEARS) && plugin.settings().dispensers()) {
            event.setCancelled(true);
            if (plant.isRipe()) {
                plants.harvestAndReplant(plant, 0, true);
                Dispensers.wear(plugin, dispenser, Material.SHEARS, 1);
                dispenser.getWorld().playSound(plant.base(), Sound.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    /** Grows the crop: barley 2-5 stages like wheat, hops 1-2. Returns false, using no bone meal, when it is ripe. */
    private boolean boneMeal(Plant plant) {
        if (plant.isRipe()) {
            return false;
        }
        int steps = plant.species() == Species.BARLEY
                ? ThreadLocalRandom.current().nextInt(2, 6) : ThreadLocalRandom.current().nextInt(1, 3);
        plants.setStage(plant, plant.stage() + steps);
        Location at = plant.base().add(0, 0.5, 0);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.3, 0.4, 0.3);
        at.getWorld().playSound(at, Sound.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    // ------------------------------------------------------------------------------------------ world changes

    /** Nothing may be placed into a crop or its hop pole (two blocks above hops). Barley is one block tall. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plants.occupying(event.getBlockPlaced()) != null) {
            event.setCancelled(true);
        }
    }

    /** Snow layers would otherwise form in the crop's (air) blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (plants.occupying(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (plants.occupying(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Dry farmland keeps a crop on it in vanilla; the crop's block is air, so stop the farmland reverting to dirt. */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && plants.at(event.getBlock().getRelative(BlockFace.UP)) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        plants.revalidateLater(event.getBlock().getRelative(BlockFace.UP));
    }

    /** Trampling (farmland to dirt) and falling blocks landing in the crop. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        plants.revalidateLater(event.getBlock());
        plants.revalidateLater(event.getBlock().getRelative(BlockFace.UP));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFlow(BlockFromToEvent event) {
        plants.revalidateLater(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBucket(PlayerBucketEmptyEvent event) {
        plants.revalidateLater(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        piston(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        piston(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    private void piston(Block piston, List<Block> moved, BlockFace direction) {
        plants.revalidateLater(piston.getRelative(direction));
        for (Block block : moved) {
            plants.revalidateLater(block.getRelative(BlockFace.UP));
            plants.revalidateLater(block.getRelative(direction));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.getLocation(), event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.getBlock().getLocation().add(0.5, 0.5, 0.5), event.blockList());
    }

    /** Crops near the blast pop; crops whose farmland is destroyed pop a tick later. */
    private void explode(Location center, List<Block> destroyed) {
        for (Block block : destroyed) {
            plants.revalidateLater(block.getRelative(BlockFace.UP));
        }
        Block origin = center.getBlock();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Plant plant = plants.at(origin.getRelative(dx, dy, dz));
                    if (plant != null && plant.base().add(0, 0.5, 0).distanceSquared(center) <= 2.5 * 2.5) {
                        plants.pop(plant);
                    }
                }
            }
        }
    }
}
