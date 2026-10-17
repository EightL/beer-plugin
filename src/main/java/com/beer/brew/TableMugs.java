package com.beer.brew;

import com.beer.Keys;
import com.beer.BeerPlugin;
import com.beer.item.ItemId;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mugs put down on blocks: sneak and right-click the top of a block with a mug, up to four per block, one per corner
 * like sea pickles. Right-click a full one to drink it there (the empty mug stays); right-click an empty one, or hit
 * any, to pick it up. Stored in the chunk's PersistentDataContainer like plants, and shown by a non-persistent display
 * and click box, so there is no barrier: you can walk past a bar counter.
 */
public final class TableMugs implements Listener {

    static final int SLOTS = 4;
    /** Corner centres of the slots in block space, as the table_mug models draw them. */
    private static final double[][] CORNERS = {{0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}};

    private static final class Table {
        final World world;
        final int x;
        final int y;
        final int z;
        final ItemStack[] mugs = new ItemStack[SLOTS];
        ItemDisplay display;
        Interaction hitbox;

        Table(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Block block() {
            return world.getBlockAt(x, y, z);
        }

        long key() {
            return com.beer.plant.Plant.blockKey(x, y, z);
        }

        long chunkKey() {
            return Chunk.getChunkKey(x >> 4, z >> 4);
        }

        boolean empty() {
            for (ItemStack mug : mugs) {
                if (mug != null) {
                    return false;
                }
            }
            return true;
        }
    }

    private final BeerPlugin plugin;
    /** World id, then block key. */
    private final Map<UUID, Map<Long, Table>> tables = new HashMap<>();
    private final Map<UUID, Table> byEntity = new HashMap<>();

    public TableMugs(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------------ player actions

    /** Sneak and right-click the top of a block with a mug: put it down. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPutDown(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || event.getBlockFace() != BlockFace.UP || event.getClickedBlock() == null || !isMug(event.getItem())) {
            return;
        }
        Block support = event.getClickedBlock();
        Block target = support.getRelative(BlockFace.UP);
        if (!canHold(support) || !target.isEmpty()) {
            return;
        }
        event.setCancelled(true);                         // no drinking (or vanilla use) while putting a mug down
        Table table = at(target);
        Vector hit = event.getInteractionPoint() == null ? null : event.getInteractionPoint().toVector();
        put(player, table == null ? new Table(target.getWorld(), target.getX(), target.getY(), target.getZ()) : table, hit, event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        Table table = byEntity.get(event.getRightClicked().getUniqueId());
        if (table == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Vector hit = aim(player, table);
        if (player.isSneaking() && isMug(held)) {
            put(player, table, hit, held);
            return;
        }
        int slot = nearest(table, hit, true);
        if (slot < 0 || !allowed(player, table)) {
            return;
        }
        ItemStack mug = table.mugs[slot];
        Beer beer = BrewItems.beerOf(mug);
        if (beer != null) {
            plugin.brewing().drink(player, beer, Items.qualityOf(mug));
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + 2));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 0.8f));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            table.mugs[slot] = Items.create(ItemId.EMPTY_MUG, 1);
            changed(table);
        } else {
            take(player, table, slot);
        }
        player.swingHand(EquipmentSlot.HAND);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(PrePlayerAttackEntityEvent event) {
        Table table = byEntity.get(event.getAttacked().getUniqueId());
        if (table == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        int slot = nearest(table, aim(player, table), true);
        if (slot >= 0 && player.getGameMode() != GameMode.ADVENTURE && player.getGameMode() != GameMode.SPECTATOR && allowed(player, table)) {
            take(player, table, slot);
        }
    }

    private void put(Player player, Table table, Vector hit, ItemStack held) {
        int slot = nearest(table, hit, false);
        if (slot < 0) {
            return;
        }
        Block target = table.block();
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), target.getRelative(BlockFace.DOWN), held, player, true, EquipmentSlot.HAND);
        if (!check.callEvent() || !check.canBuild()) {
            return;
        }
        table.mugs[slot] = held.asOne();
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        if (at(target) == null) {
            index(table);
        }
        changed(table);
        player.swingHand(EquipmentSlot.HAND);
        target.getWorld().playSound(target.getLocation().add(0.5, 0.1, 0.5), Sound.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.7f, 1.4f);
    }

    private void take(Player player, Table table, int slot) {
        ItemStack mug = table.mugs[slot];
        table.mugs[slot] = null;
        player.getInventory().addItem(mug).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        changed(table);
        table.world.playSound(table.block().getLocation().add(0.5, 0.1, 0.5), Sound.BLOCK_GLASS_HIT, SoundCategory.BLOCKS, 0.7f, 1.4f);
    }

    /** Protection plugins decide who may take or drink mugs, as they would for breaking a block there. */
    private static boolean allowed(Player player, Table table) {
        BlockBreakEvent check = new BlockBreakEvent(table.block(), player);
        check.setDropItems(false);
        return check.callEvent();
    }

    private static boolean isMug(ItemStack item) {
        ItemId id = Items.idOf(item);
        return id == ItemId.EMPTY_MUG || id == ItemId.BEER;
    }

    /** Blocks mugs can stand on: anything solid that is not furniture. */
    private boolean canHold(Block support) {
        return support.getType().isSolid() && support.getType() != Material.BARRIER && plugin.plants().occupying(support.getRelative(BlockFace.UP)) == null;
    }

    /** Where the player's look meets the table's click box, or null. */
    private static Vector aim(Player player, Table table) {
        Entity box = table.hitbox;
        if (box == null) {
            return null;
        }
        BoundingBox bounds = box.getBoundingBox();
        RayTraceResult hit = bounds.rayTrace(player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), 6.0);
        return hit == null ? null : hit.getHitPosition();
    }

    /** The slot nearest {@code hit}: an occupied one, or a free one. -1 when there is none. */
    private static int nearest(Table table, Vector hit, boolean occupied) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            if ((table.mugs[i] != null) != occupied) {
                continue;
            }
            double distance = hit == null ? i : Math.pow(table.x + CORNERS[i][0] - hit.getX(), 2) + Math.pow(table.z + CORNERS[i][1] - hit.getZ(), 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------ world changes

    /** Nothing may be placed into a block holding mugs. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (at(event.getBlockPlaced()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Table table = at(event.getBlock().getRelative(BlockFace.UP));
        if (table != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> validate(table));
        }
    }

    /** Every couple of seconds: mugs whose block filled up (water, a piston) or whose support went drop as items. */
    public void tick() {
        for (Map<Long, Table> world : tables.values()) {
            for (Table table : new ArrayList<>(world.values())) {
                validate(table);
            }
        }
    }

    private void validate(Table table) {
        if (at(table.block()) != table) {
            return;
        }
        if (table.block().isEmpty() && canHold(table.block().getRelative(BlockFace.DOWN))) {
            return;
        }
        Location at = table.block().getLocation().add(0.5, 0.2, 0.5);
        for (int i = 0; i < SLOTS; i++) {
            if (table.mugs[i] != null) {
                table.world.dropItemNaturally(at, table.mugs[i]);
                table.mugs[i] = null;
            }
        }
        changed(table);
    }

    // ------------------------------------------------------------------------------------------ chunks

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(Keys.TABLE_ENTITY) && !byEntity.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
        load(event.getChunk());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Map<Long, Table> world = tables.get(event.getChunk().getWorld().getUID());
        if (world == null) {
            return;
        }
        long chunk = event.getChunk().getChunkKey();
        for (Table table : new ArrayList<>(world.values())) {
            if (table.chunkKey() == chunk) {
                despawn(table);
                world.remove(table.key());
            }
        }
    }

    private void load(Chunk chunk) {
        List<PersistentDataContainer> list = chunk.getPersistentDataContainer().get(Keys.TABLE_MUGS, PersistentDataType.LIST.dataContainers());
        if (list == null) {
            return;
        }
        for (PersistentDataContainer entry : list) {
            Integer x = entry.get(Keys.X, PersistentDataType.INTEGER);
            Integer y = entry.get(Keys.Y, PersistentDataType.INTEGER);
            Integer z = entry.get(Keys.Z, PersistentDataType.INTEGER);
            if (x == null || y == null || z == null || at(chunk.getWorld().getBlockAt(x, y, z)) != null) {
                continue;
            }
            Table table = new Table(chunk.getWorld(), x, y, z);
            List<byte[]> mugs = entry.getOrDefault(Keys.MUGS, PersistentDataType.LIST.byteArrays(), List.of());
            for (int i = 0; i < Math.min(SLOTS, mugs.size()); i++) {
                table.mugs[i] = mugs.get(i).length == 0 ? null : ItemStack.deserializeBytes(mugs.get(i));
            }
            if (!table.empty()) {
                tables.computeIfAbsent(table.world.getUID(), id -> new HashMap<>()).put(table.key(), table);
                spawn(table);
            }
        }
    }

    public void loadAll() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isEntitiesLoaded()) {
                    load(chunk);
                }
            }
        }
    }

    public void unloadAll() {
        for (Map<Long, Table> world : tables.values()) {
            world.values().forEach(this::despawn);
        }
        tables.clear();
        byEntity.clear();
    }

    // ------------------------------------------------------------------------------------------ internals

    private Table at(Block block) {
        Map<Long, Table> world = tables.get(block.getWorld().getUID());
        return world == null ? null : world.get(com.beer.plant.Plant.blockKey(block.getX(), block.getY(), block.getZ()));
    }

    private void index(Table table) {
        tables.computeIfAbsent(table.world.getUID(), id -> new HashMap<>()).put(table.key(), table);
    }

    /** After the mugs changed: drop an empty table, update the look and save the chunk. */
    private void changed(Table table) {
        Map<Long, Table> world = tables.get(table.world.getUID());
        if (table.empty()) {
            despawn(table);
            if (world != null) {
                world.remove(table.key());
            }
        } else if (table.display == null || !table.display.isValid()) {
            spawn(table);
        } else {
            table.display.setItemStack(look(table));
        }
        save(table.world, table.chunkKey(), table.x >> 4, table.z >> 4);
    }

    private void save(World world, long chunkKey, int cx, int cz) {
        if (!world.isChunkLoaded(cx, cz)) {
            return;
        }
        Chunk chunk = world.getChunkAt(cx, cz);
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<PersistentDataContainer> list = new ArrayList<>();
        Map<Long, Table> all = tables.getOrDefault(world.getUID(), Map.of());
        for (Table table : all.values()) {
            if (table.chunkKey() != chunkKey || table.empty()) {
                continue;
            }
            PersistentDataContainer entry = pdc.getAdapterContext().newPersistentDataContainer();
            entry.set(Keys.X, PersistentDataType.INTEGER, table.x);
            entry.set(Keys.Y, PersistentDataType.INTEGER, table.y);
            entry.set(Keys.Z, PersistentDataType.INTEGER, table.z);
            List<byte[]> mugs = new ArrayList<>(SLOTS);
            for (ItemStack mug : table.mugs) {
                mugs.add(mug == null ? new byte[0] : mug.serializeAsBytes());
            }
            entry.set(Keys.MUGS, PersistentDataType.LIST.byteArrays(), mugs);
            list.add(entry);
        }
        if (list.isEmpty()) {
            pdc.remove(Keys.TABLE_MUGS);
        } else {
            pdc.set(Keys.TABLE_MUGS, PersistentDataType.LIST.dataContainers(), list);
        }
    }

    private void spawn(Table table) {
        despawn(table);
        Block block = table.block();
        Block support = block.getRelative(BlockFace.DOWN);
        // stand on the support's top: a slab or a carpet-high block is lower than a full block
        double top = support.getBoundingBox().getHeight() > 0 ? support.getBoundingBox().getMaxY() - block.getY() : 0;
        double lift = Math.clamp(top, -1.0, 0.0);
        // yaw 180 undoes the item display's half turn, so the model's corners are the block's corners
        Location center = new Location(table.world, table.x + 0.5, table.y + 0.5 + lift, table.z + 0.5, 180f, 0f);
        table.display = table.world.spawn(center, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(look(table));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setViewRange(0.5f);
            display.setShadowRadius(0);
            display.getPersistentDataContainer().set(Keys.TABLE_ENTITY, PersistentDataType.BOOLEAN, true);
        });
        table.hitbox = table.world.spawn(new Location(table.world, table.x + 0.5, table.y + lift, table.z + 0.5), Interaction.class, box -> {
            box.setPersistent(false);
            box.setInteractionWidth(0.95f);
            box.setInteractionHeight(0.45f);
            box.setResponsive(true);
            box.getPersistentDataContainer().set(Keys.TABLE_ENTITY, PersistentDataType.BOOLEAN, true);
        });
        byEntity.put(table.display.getUniqueId(), table);
        byEntity.put(table.hitbox.getUniqueId(), table);
    }

    private void despawn(Table table) {
        for (Entity entity : new Entity[] {table.display, table.hitbox}) {
            if (entity != null) {
                byEntity.remove(entity.getUniqueId());
                entity.remove();
            }
        }
        table.display = null;
        table.hitbox = null;
    }

    /** strings[slot]: empty, a beer colour, or nothing. */
    private static ItemStack look(Table table) {
        List<String> strings = new ArrayList<>(SLOTS);
        for (ItemStack mug : table.mugs) {
            Beer beer = BrewItems.beerOf(mug);
            strings.add(mug == null ? "none" : beer == null ? "empty" : beer.style().colour().look());
        }
        ItemStack look = ItemStack.of(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key(Keys.NAMESPACE, "table_mugs"));
        look.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addStrings(strings).build());
        return look;
    }

    /** How many blocks hold mugs (for /beer info). */
    public int count() {
        return tables.values().stream().mapToInt(Map::size).sum();
    }
}
