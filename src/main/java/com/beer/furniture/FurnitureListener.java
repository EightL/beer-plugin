package com.beer.furniture;

import com.beer.BeerPlugin;
import com.beer.item.ItemId;
import com.beer.item.Items;
import com.beer.world.Dispensers;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/** Placing, using and breaking furniture. */
public final class FurnitureListener implements Listener {

    private final BeerPlugin plugin;
    private final FurnitureManager furniture;
    /** Set while this listener fires its own BlockBreakEvent as a protection check, so it does not handle it. */
    private boolean checking;

    private final Mining mining;

    public FurnitureListener(BeerPlugin plugin, FurnitureManager furniture) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.mining = new Mining(this::breakBy);
        plugin.getServer().getPluginManager().registerEvents(mining, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, mining::tick, 1L, 1L);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        furniture.loaded(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        furniture.unloaded(event.getEntities());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Furniture used = furniture.at(clicked);
        ItemStack held = event.getItem();
        // sneaking with an item in hand places blocks against furniture, except pouring a barrel into every mug held
        boolean pourAll = used != null && used.type() == FurnitureType.FERMENTING_BARREL && Items.is(held, ItemId.EMPTY_MUG);
        if (used != null && (pourAll || !(player.isSneaking() && held != null && !held.isEmpty()))) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            switch (used.type()) {
                case BREW_KETTLE -> furniture.useKettle(player, used);
                case FERMENTING_BARREL -> furniture.useBarrel(player, used);
            }
            player.swingHand(EquipmentSlot.HAND);
            return;
        }
        FurnitureType type = FurnitureType.byItem(Items.idOf(held));
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Block target = clicked.isReplaceable() && !clicked.isLiquid() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!(target.isEmpty() || (target.isReplaceable() && !target.isLiquid())) || furniture.displayAt(target) != null
                || !target.getWorld().getNearbyEntities(BoundingBox.of(target), e -> e instanceof LivingEntity).isEmpty()) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, held, player, true, event.getHand());
        if (!check.callEvent() || !check.canBuild()) {
            return;
        }
        furniture.place(target, type, player.getYaw(), held);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().getItem(event.getHand()).subtract();
        }
        player.swingHand(event.getHand());
    }

    /** A dispenser with a Water Bucket facing a Brew Kettle fills it. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getType() != Material.DISPENSER || !(dispenser.getBlockData() instanceof Directional facing)
                || !plugin.settings().dispensers() || !Dispensers.plain(event.getItem(), Material.WATER_BUCKET)) {
            return;
        }
        Furniture kettle = furniture.at(dispenser.getRelative(facing.getFacing()));
        if (kettle != null && kettle.type() == FurnitureType.BREW_KETTLE) {
            event.setCancelled(true);
            if (furniture.fillKettle(kettle)) {
                Dispensers.swap(plugin, dispenser, Material.WATER_BUCKET, Material.BUCKET);
            }
        }
    }

    /** Barriers cannot be broken in survival, so furniture is mined here (see {@link Mining}). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Furniture used = furniture.at(event.getClickedBlock());
        if (used == null) {
            return;
        }
        event.setCancelled(true);
        mining.start(event.getPlayer(), used);
    }

    /** Breaks furniture for a player, after asking protection plugins with a BlockBreakEvent. */
    private void breakBy(Player player, Furniture used) {
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        BlockBreakEvent check = new BlockBreakEvent(used.block(), player);
        check.setDropItems(false);
        checking = true;
        try {
            if (!check.callEvent()) {
                return;
            }
        } finally {
            checking = false;
        }
        furniture.remove(used, player.getGameMode() != GameMode.CREATIVE);
    }

    /** Any other way the barrier gets broken (creative, commands that fire the event): drop the contents. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (checking) {
            return;
        }
        Furniture used = furniture.at(event.getBlock());
        if (used != null) {
            furniture.remove(used, event.getPlayer().getGameMode() != GameMode.CREATIVE);
        }
    }
}
