package com.beer.furniture;

import com.beer.BeerPlugin;
import com.beer.Keys;
import com.beer.Settings;
import com.beer.brew.BrewItems;
import com.beer.brew.BrewRules;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Places, finds, uses and breaks furniture. A piece of furniture is a persistent ItemDisplay at the centre of a
 * barrier block; the display's PersistentDataContainer holds its type and contents, and its item model shows them.
 */
public final class FurnitureManager {

    private final BeerPlugin plugin;
    /** Every loaded piece of furniture, for hoppers and the brewing tick. */
    private final Map<UUID, ItemDisplay> loaded = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();
    private final BrewKettle kettles;
    private final FermentingBarrel barrels;

    public FurnitureManager(BeerPlugin plugin) {
        this.plugin = plugin;
        this.kettles = new BrewKettle(plugin, this);
        this.barrels = new FermentingBarrel(plugin, this);
    }

    BrewKettle kettles() {
        return kettles;
    }

    FermentingBarrel barrels() {
        return barrels;
    }

    public void useKettle(Player player, Furniture furniture) {
        kettles.use(player, furniture);
    }

    public void useBarrel(Player player, Furniture furniture) {
        barrels.use(player, furniture);
    }

    /** A dispenser with a Water Bucket facing a kettle: fills it. Returns whether it did. */
    public boolean fillKettle(Furniture furniture) {
        return kettles.fillWater(furniture);
    }

    /**
     * For admins and tests: finishes a kettle's boil, or makes a barrel ready and {@code ageMs} older (for more stars).
     * Returns what happened, for the command to report.
     */
    public String skip(Furniture furniture, long ageMs) {
        var pdc = furniture.display().getPersistentDataContainer();
        long now = System.currentTimeMillis();
        Settings settings = plugin.settings();
        if (furniture.type() == FurnitureType.BREW_KETTLE) {
            BrewKettle.State state = BrewKettle.read(pdc);
            if (!state.canBoil()) {
                return "The kettle needs water, 4 grain and a hop before it can boil.";
            }
            state.boiled = settings.boilMs();
            state.heatSince = 0;
            BrewKettle.write(pdc, state);
            refresh(furniture);
            return "The kettle's wort is ready.";
        }
        FermentingBarrel.State state = FermentingBarrel.read(pdc);
        if (state.empty()) {
            return "The barrel is empty.";
        }
        long ferment = BrewRules.fermentMs(state.cold(), settings.aleMs(), settings.lagerMs());
        FermentingBarrel.write(pdc, new FermentingBarrel.State(state.wood(), state.wort(), state.cold(), state.blueIce(),
                Math.min(state.filledAt(), now - ferment - Math.max(0, ageMs)), state.mugsLeft()));
        refresh(furniture);
        return "The barrel is ready" + (ageMs > 0 ? " and " + duration(ageMs) + " older" : "") + ".";
    }

    /** Every few ticks: kettles boil and pour through their taps, barrels bubble. */
    public void brewTick() {
        long now = System.currentTimeMillis();
        for (Furniture furniture : loaded()) {
            switch (furniture.type()) {
                case BREW_KETTLE -> kettles.tick(furniture, now);
                case FERMENTING_BARREL -> barrels.tick(furniture, now);
            }
        }
    }

    /** A snapshot of the loaded furniture. */
    List<Furniture> loaded() {
        List<Furniture> out = new ArrayList<>(loaded.size());
        for (ItemDisplay display : loaded.values()) {
            FurnitureType type = typeOf(display);
            if (display.isValid() && type != null) {
                out.add(new Furniture(display, type));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ lookup

    public static FurnitureType typeOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.FURNITURE, PersistentDataType.STRING);
        return id == null ? null : FurnitureType.byId(id);
    }

    /** The furniture occupying this block, or null. */
    public Furniture at(Block block) {
        Material material = block.getType();
        if (material != Material.BARRIER) {
            return null;
        }
        Furniture furniture = displayAt(block);
        return furniture != null && furniture.type().block() == material ? furniture : null;
    }

    /** The furniture whose display sits in this block, whatever the block is now (for place events). */
    public Furniture displayAt(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.3, 0.3, 0.3)) {
            if (entity instanceof ItemDisplay display && typeOf(display) != null) {
                return new Furniture(display, typeOf(display));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ place and break

    /** Puts furniture into {@code target}, turned to face a player looking along {@code viewerYaw}. No build checks. */
    public Furniture place(Block target, FurnitureType type, float viewerYaw) {
        return place(target, type, viewerYaw, null);
    }

    /** The same, from a furniture item that may carry a variant (a barrel's wood). */
    public Furniture place(Block target, FurnitureType type, float viewerYaw, ItemStack source) {
        target.setType(type.block());
        // an item display already shows the model's front (south side) to a viewer looking along its yaw
        float yaw = Math.round(viewerYaw / 90f) * 90f;
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        center.setYaw(yaw);
        ItemDisplay display = target.getWorld().spawn(center, ItemDisplay.class, entity -> {
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setViewRange(0.75f);
            entity.setShadowRadius(0);
            entity.getPersistentDataContainer().set(Keys.FURNITURE, PersistentDataType.STRING, type.id());
            if (type == FurnitureType.FERMENTING_BARREL) {
                entity.getPersistentDataContainer().set(Keys.WOOD, PersistentDataType.STRING, BrewItems.woodOf(source).id());
            }
            entity.setItemStack(look(type, new String[] {"empty"}));
        });
        Furniture furniture = new Furniture(display, type);
        track(display);
        refresh(furniture);
        target.getWorld().playSound(center, type == FurnitureType.BREW_KETTLE ? Sound.BLOCK_COPPER_PLACE : Sound.BLOCK_WOOD_PLACE,
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        return furniture;
    }

    /** Removes the furniture and drops its contents; with {@code dropItem}, the furniture item too. */
    public void remove(Furniture furniture, boolean dropItem) {
        Location center = furniture.center();
        World world = center.getWorld();
        List<ItemStack> drops = new ArrayList<>();
        if (furniture.type() == FurnitureType.BREW_KETTLE) {
            drops.addAll(BrewKettle.drops(furniture.display().getPersistentDataContainer(), plugin.settings().boilMs()));
        }
        if (dropItem) {
            drops.add(furniture.type() == FurnitureType.FERMENTING_BARREL
                    ? BrewItems.barrel(FermentingBarrel.wood(furniture), 1) : Items.create(furniture.type().item(), 1));
        }
        untrack(furniture.display());
        furniture.display().remove();
        if (furniture.block().getType() == furniture.type().block()) {
            furniture.block().setType(Material.AIR);
        }
        world.playSound(center, furniture.type() == FurnitureType.BREW_KETTLE ? Sound.BLOCK_COPPER_BREAK : Sound.BLOCK_WOOD_BREAK,
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        for (ItemStack drop : drops) {
            world.dropItemNaturally(center, drop);
        }
    }

    // ------------------------------------------------------------------------------------------ looks and loading

    /** Updates the display's model state from its contents (only when it changed). */
    public void refresh(Furniture furniture) {
        if (!furniture.display().isValid()) {
            return;
        }
        var pdc = furniture.display().getPersistentDataContainer();
        String[] strings = switch (furniture.type()) {
            case BREW_KETTLE -> new String[] {BrewKettle.look(pdc)};
            case FERMENTING_BARREL -> FermentingBarrel.look(pdc, System.currentTimeMillis(), plugin.settings());
        };
        String key = String.join(",", strings);
        if (!key.equals(shown.put(furniture.display().getUniqueId(), key))) {
            furniture.display().setItemStack(look(furniture.type(), strings));
        }
    }

    /** Every few seconds: barrels show when they are ready. */
    public void tick() {
        for (Furniture furniture : loaded()) {
            if (furniture.type() == FurnitureType.FERMENTING_BARREL) {
                refresh(furniture);
            }
        }
    }

    /** Called with entities that just loaded: tracks furniture and removes displays whose barrier is gone. */
    public void loaded(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            FurnitureType type = typeOf(entity);
            if (type == null || !(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (display.getLocation().getBlock().getType() != type.block()) {
                plugin.getLogger().info("Removing a " + type.id() + " whose block was replaced at " + display.getLocation().toVector());
                display.remove();
                continue;
            }
            track(display);
            refresh(new Furniture(display, type));
        }
    }

    public void unloaded(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            untrack(entity);
        }
    }

    public void loadAll() {
        for (World world : plugin.getServer().getWorlds()) {
            loaded(world.getEntitiesByClass(ItemDisplay.class));
        }
    }

    private void track(ItemDisplay display) {
        loaded.put(display.getUniqueId(), display);
    }

    private void untrack(Entity entity) {
        loaded.remove(entity.getUniqueId());
        shown.remove(entity.getUniqueId());
    }

    static ItemStack look(FurnitureType type, String[] strings) {
        ItemStack look = ItemStack.of(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key(Keys.NAMESPACE, type.id()));
        look.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addStrings(Arrays.asList(strings)).build());
        return look;
    }

    // ------------------------------------------------------------------------------------------ helpers

    static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    static void status(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.GRAY));
    }

    static void sound(Furniture furniture, Sound sound) {
        furniture.center().getWorld().playSound(furniture.center(), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** "3:05" or "1 h 12 min". */
    static String duration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        if (seconds >= 3600) {
            return seconds / 3600 + " h " + (seconds % 3600) / 60 + " min";
        }
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
