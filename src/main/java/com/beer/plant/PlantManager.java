package com.beer.plant;

import com.beer.BeerPlugin;
import com.beer.Keys;
import com.beer.Settings;
import com.beer.item.ItemId;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the crops of every chunk whose entities are loaded: indexes them, shows them (a non-persistent ItemDisplay
 * plus an Interaction for clicks) and writes every change straight to the chunk's PersistentDataContainer.
 */
public final class PlantManager {

    /** Plants of one world: by block key, and by chunk key in insertion order. */
    private static final class WorldPlants {
        final Map<Long, Plant> byBlock = new HashMap<>();
        final Map<Long, Map<Long, Plant>> byChunk = new HashMap<>();
    }

    private final BeerPlugin plugin;
    private final Map<UUID, WorldPlants> worlds = new HashMap<>();
    private final Map<UUID, Plant> byEntity = new HashMap<>();
    private final Map<String, ItemStack> looks = new HashMap<>();

    public PlantManager(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------------ lookup

    public Plant at(Block block) {
        WorldPlants plants = worlds.get(block.getWorld().getUID());
        return plants == null ? null : plants.byBlock.get(Plant.blockKey(block.getX(), block.getY(), block.getZ()));
    }

    public Plant at(World world, int x, int y, int z) {
        WorldPlants plants = worlds.get(world.getUID());
        return plants == null ? null : plants.byBlock.get(Plant.blockKey(x, y, z));
    }

    /** The plant whose display or interaction entity this is, or null. */
    public Plant byEntity(Entity entity) {
        return byEntity.get(entity.getUniqueId());
    }

    /** A snapshot, safe to iterate while plants are added or removed. */
    public List<Plant> all() {
        List<Plant> out = new ArrayList<>();
        for (WorldPlants plants : worlds.values()) {
            out.addAll(plants.byBlock.values());
        }
        return out;
    }

    public int count() {
        int count = 0;
        for (WorldPlants plants : worlds.values()) {
            count += plants.byBlock.size();
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------ rules

    /**
     * Whether {@code species} can be planted in {@code target}: an empty block on farmland, with the blocks above it
     * the species needs free (two for the hop pole).
     */
    public boolean canPlant(Block target, Species species) {
        return target.isEmpty() && isSoil(target.getRelative(0, -1, 0)) && free(target, species.headroom()) && at(target) == null;
    }

    /**
     * Whether the crop can stay: its block is still empty and the farmland below is still there (and, for hops, the
     * pole's two blocks are still free).
     */
    public boolean canStay(Plant plant) {
        Block block = plant.block();
        return block.isEmpty() && isSoil(block.getRelative(0, -1, 0))
                && (plant.species() != Species.HOPS || free(block, Species.HOPS.headroom()));
    }

    /** Whether the {@code count} blocks above {@code block} are empty. */
    private static boolean free(Block block, int count) {
        for (int dy = 1; dy <= count; dy++) {
            if (!block.getRelative(0, dy, 0).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The plant whose spot or hop pole takes up {@code block}, or null. */
    public Plant occupying(Block block) {
        Plant plant = at(block);
        if (plant != null) {
            return plant;
        }
        for (int dy = 1; dy <= Species.HOPS.headroom(); dy++) {
            Plant below = at(block.getRelative(0, -dy, 0));
            if (below != null && below.species().headroom() >= dy) {
                return below;
            }
        }
        return null;
    }

    /** What crops grow on. */
    public boolean isSoil(Block block) {
        return block.getType() == Material.FARMLAND;
    }

    // ------------------------------------------------------------------------------------------ changes

    public Plant plant(Block target, Species species) {
        if (target.getType() != Material.AIR) {
            target.setType(Material.AIR);                   // cave air is the ripe signal (see setStage)
        }
        Plant plant = new Plant(target.getWorld(), target.getX(), target.getY(), target.getZ(), species, 0);
        index(plant);
        spawn(plant);
        save(plant);
        return plant;
    }

    public void setStage(Plant plant, int stage) {
        if (plant.stage() == plant.species().clamp(stage)) {
            return;
        }
        plant.stage(stage);
        if (plant.display != null && plant.display.isValid()) {
            plant.display.setItemStack(look(plant));
        }
        if (plant.hitbox != null && plant.hitbox.isValid()) {
            plant.hitbox.setInteractionHeight(plant.species().height(plant.stage()));
        }
        signal(plant, true);
        save(plant);
    }

    /**
     * The crop's block is cave air while it is ripe and plain air otherwise. Both are air to everything (water,
     * pistons, mobs), but the change is a block update, so an observer facing the plant fires when it ripens and when
     * it is harvested, as with wheat.
     */
    private void signal(Plant plant, boolean updates) {
        Block block = plant.block();
        Material want = plant.isRipe() ? Material.CAVE_AIR : Material.AIR;
        Material now = block.getType();
        if (now != want && (now == Material.AIR || now == Material.CAVE_AIR)) {
            block.setType(want, updates);
        }
    }

    /**
     * Harvests ripe barley and plants one grain again (if one dropped); ripe hops are picked instead and go back to
     * leafy. With {@code dropItems} false (a creative player) the rest of the harvest is not dropped.
     */
    public void harvestAndReplant(Plant plant, int fortune, boolean dropItems) {
        if (plant.species() == Species.HOPS) {
            pick(plant, dropItems);
            return;
        }
        Block block = plant.block();
        List<ItemStack> drops = drops(plant, fortune);
        boolean replant = false;
        for (ItemStack drop : drops) {
            if (Items.is(drop, ItemId.BARLEY)) {
                drop.subtract();
                replant = true;
                break;
            }
        }
        remove(plant, false, 0);
        if (dropItems) {
            drop(plant, drops);
        }
        if (replant && canPlant(block, plant.species())) {
            plant(block, plant.species());
            block.getWorld().playSound(block.getLocation().add(0.5, 0, 0.5), Sound.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    /**
     * Removes the crop. With {@code drop}, drops its harvest at the crop ({@code fortune} adds barley when ripe) and
     * plays the crop break effect.
     */
    public void remove(Plant plant, boolean drop, int fortune) {
        despawn(plant);
        unindex(plant);
        save(plant);
        if (plant.block().getType() == Material.CAVE_AIR) {
            plant.block().setType(Material.AIR);
        }
        Location center = plant.base().add(0, 0.4, 0);
        World world = plant.world();
        world.playSound(center, Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.spawnParticle(Particle.BLOCK, center, 20, 0.25, 0.3, 0.25, Material.SHORT_GRASS.createBlockData());
        if (drop) {
            drop(plant, drops(plant, fortune));
        }
    }

    /** Drops items at the crop. */
    public void drop(Plant plant, List<ItemStack> items) {
        Location center = plant.base().add(0, 0.4, 0);
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                plant.world().dropItemNaturally(center, item);
            }
        }
    }

    /** Picks ripe hops: their cones drop and the bine goes back to leafy, to flower again. */
    public void pick(Plant plant, boolean dropItems) {
        if (plant.species() != Species.HOPS || !plant.isRipe()) {
            return;
        }
        int cones = plugin.settings().hopCones().roll(ThreadLocalRandom.current());
        setStage(plant, Species.HOPS_PICKED);
        if (dropItems && cones > 0) {
            drop(plant, List.of(Items.create(ItemId.HOPS, cones)));
        }
        plant.world().playSound(plant.base().add(0, 1, 0), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** Drops the crop as if broken, when the world under it changed (trampled, flooded, pushed, blown up...). */
    public void pop(Plant plant) {
        remove(plant, true, 0);
    }

    public List<ItemStack> drops(Plant plant, int fortune) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Settings settings = plugin.settings();
        if (plant.species() == Species.BARLEY) {
            return List.of(Items.create(ItemId.BARLEY, plant.isRipe() ? settings.barleyDrops().roll(random) + Math.max(0, fortune) : 1));
        }
        return List.of(Items.create(ItemId.HOPS, 1 + (plant.isRipe() ? settings.hopCones().roll(random) : 0)));
    }

    /** Pops the plant at {@code block} (or whose hop pole is there) if it can no longer stay. */
    public void revalidate(Block block) {
        Plant plant = occupying(block);
        if (plant != null && !canStay(plant)) {
            pop(plant);
        }
    }

    /** {@link #revalidate} one tick later, once a block change the event announced has happened. */
    public void revalidateLater(Block block) {
        if (occupying(block) != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> revalidate(block));
        }
    }

    // ------------------------------------------------------------------------------------------ chunks

    /** Reads the chunk's crops and shows them. Crops whose spot changed while unloaded drop as items. */
    public void load(Chunk chunk) {
        if (chunkPlants(chunk.getWorld(), chunk.getChunkKey(), false) != null) {
            return;
        }
        List<Plant> invalid = new ArrayList<>();
        for (PlantStore.Stored stored : PlantStore.read(chunk)) {
            Plant plant = new Plant(chunk.getWorld(), stored.x(), stored.y(), stored.z(), stored.species(), stored.stage());
            if (at(plant.world(), plant.x(), plant.y(), plant.z()) != null) {
                continue;
            }
            index(plant);
            if (canStay(plant)) {
                spawn(plant);
                signal(plant, false);
            } else {
                invalid.add(plant);
            }
        }
        for (Plant plant : invalid) {
            pop(plant);
        }
    }

    /** Hides the chunk's plants and forgets them (the data is already saved). */
    public void unload(Chunk chunk) {
        Map<Long, Plant> plants = chunkPlants(chunk.getWorld(), chunk.getChunkKey(), false);
        if (plants == null) {
            return;
        }
        for (Plant plant : new ArrayList<>(plants.values())) {
            despawn(plant);
            unindex(plant);
        }
        worlds.get(chunk.getWorld().getUID()).byChunk.remove(chunk.getChunkKey());
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
        for (Plant plant : all()) {
            despawn(plant);
        }
        worlds.clear();
        byEntity.clear();
    }

    /** Removes plant entities left behind (they are not persistent, so this only matters after a crash mid-save). */
    public void removeStrays(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(Keys.PLANT_ENTITY) && !byEntity.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------------------------------ internals

    private Map<Long, Plant> chunkPlants(World world, long chunkKey, boolean create) {
        WorldPlants plants = create ? worlds.computeIfAbsent(world.getUID(), id -> new WorldPlants()) : worlds.get(world.getUID());
        if (plants == null) {
            return null;
        }
        return create ? plants.byChunk.computeIfAbsent(chunkKey, key -> new LinkedHashMap<>()) : plants.byChunk.get(chunkKey);
    }

    private void index(Plant plant) {
        WorldPlants plants = worlds.computeIfAbsent(plant.world().getUID(), id -> new WorldPlants());
        plants.byBlock.put(plant.blockKey(), plant);
        chunkPlants(plant.world(), plant.chunkKey(), true).put(plant.blockKey(), plant);
    }

    private void unindex(Plant plant) {
        WorldPlants plants = worlds.get(plant.world().getUID());
        if (plants == null) {
            return;
        }
        plants.byBlock.remove(plant.blockKey());
        Map<Long, Plant> chunk = plants.byChunk.get(plant.chunkKey());
        if (chunk != null) {
            chunk.remove(plant.blockKey());
            // keep the (possibly empty) chunk entry while the chunk stays loaded; dropped on unload
        }
    }

    /** Writes the plant's chunk to its PersistentDataContainer. */
    private void save(Plant plant) {
        World world = plant.world();
        int cx = plant.x() >> 4;
        int cz = plant.z() >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            return;
        }
        Map<Long, Plant> plants = chunkPlants(world, plant.chunkKey(), false);
        PlantStore.write(world.getChunkAt(cx, cz), plants == null ? List.of() : plants.values());
    }

    private void spawn(Plant plant) {
        despawn(plant);
        World world = plant.world();
        Species species = plant.species();
        // Item models are centred on the display, so the block-space model (0..16) lines up with the plant's block.
        float raise = species == Species.HOPS ? Species.HOPS_RAISE : 0f;
        Location center = new Location(world, plant.x() + 0.5, plant.y() + 0.5 + raise, plant.z() + 0.5, yaw(plant), 0);
        float viewRange = plugin.settings().viewDistance() / 64.0f;
        plant.display = world.spawn(center, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(look(plant));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setViewRange(viewRange);
            display.setShadowRadius(0);
            display.getPersistentDataContainer().set(Keys.PLANT_ENTITY, PersistentDataType.BOOLEAN, true);
        });
        plant.hitbox = world.spawn(plant.base(), Interaction.class, hitbox -> {
            hitbox.setPersistent(false);
            hitbox.setInteractionWidth(species == Species.HOPS ? 0.6f : 0.8f);
            hitbox.setInteractionHeight(species.height(plant.stage()));
            hitbox.setResponsive(true);
            hitbox.getPersistentDataContainer().set(Keys.PLANT_ENTITY, PersistentDataType.BOOLEAN, true);
        });
        byEntity.put(plant.display.getUniqueId(), plant);
        byEntity.put(plant.hitbox.getUniqueId(), plant);
    }

    private void despawn(Plant plant) {
        for (Entity entity : new Entity[] {plant.display, plant.hitbox}) {
            if (entity != null) {
                byEntity.remove(entity.getUniqueId());
                entity.remove();
            }
        }
        plant.display = null;
        plant.hitbox = null;
    }

    /**
     * A fixed, random-looking turn per position, so rows of crops do not look copy-pasted. Barley and hops are drawn
     * like vanilla crops (planes in a # pattern), so they only turn by quarters.
     */
    private static float yaw(Plant plant) {
        long h = plant.blockKey() * 0x9E3779B97F4A7C15L;
        return 90f * ((h >>> 40) % 4);
    }

    private ItemStack look(Plant plant) {
        String when = plant.species().id() + "/" + plant.stageId();
        return looks.computeIfAbsent(when, key -> {
            ItemStack look = ItemStack.of(Material.PAPER);
            look.setData(DataComponentTypes.ITEM_MODEL, Key.key(Keys.NAMESPACE, "plant"));
            look.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(key).build());
            return look;
        }).clone();
    }
}
