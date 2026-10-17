package com.beer.furniture;

import com.beer.Keys;
import com.beer.BeerPlugin;
import com.beer.brew.BrewItems;
import com.beer.brew.BrewRules;
import com.beer.brew.Colour;
import com.beer.brew.Extra;
import com.beer.brew.Grain;
import com.beer.brew.Wort;
import com.beer.item.ItemId;
import com.beer.item.Items;
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
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Brew Kettle: fill it with water, 4 grain, 1 or 2 hops and an optional extra, and with heat below it boils for a
 * few minutes into a batch of wort that waits until it is taken out with a bucket. The boil's progress is wall-clock
 * time while there was heat, so it carries on while the chunk is unloaded.
 */
final class BrewKettle {

    private static final Set<Material> HEAT = Set.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.LAVA);
    private static final String HOP = "hops";
    private static final String EXTRA = "extra";

    /** What is in one kettle, read from and written to its display. */
    static final class State {
        boolean water;
        /** Grain ids, "hops" and "extra", in the order they went in (for taking the last one back). */
        final List<String> added = new ArrayList<>();
        ItemStack extra;
        /** Milliseconds boiled so far, and since when it has been boiling (0 = not boiling right now). */
        long boiled;
        long heatSince;

        int count(String what) {
            return (int) added.stream().filter(what::equals).count();
        }

        int grains() {
            return added.size() - count(HOP) - count(EXTRA);
        }

        int hops() {
            return count(HOP);
        }

        Extra extraKind() {
            return extra == null ? null : extraOf(extra);
        }

        boolean canBoil() {
            return BrewRules.canBoil(water, grains(), hops());
        }

        /** Whether it has started boiling (nothing can be taken back any more). */
        boolean started() {
            return boiled > 0 || heatSince > 0;
        }

        long boiledAt(long now) {
            return boiled + (heatSince > 0 ? Math.max(0, now - heatSince) : 0);
        }

        boolean done(long boilMs) {
            return canBoil() && boiled >= boilMs;
        }

        Colour colour() {
            return Colour.of(count(Grain.WHEAT.id()), count(Grain.DARK_MALT.id()));
        }

        Wort wort() {
            return new Wort(count(Grain.WHEAT.id()), count(Grain.MALT.id()), count(Grain.DARK_MALT.id()), hops(), extraKind());
        }

        void clear() {
            water = false;
            added.clear();
            extra = null;
            boiled = 0;
            heatSince = 0;
        }
    }

    private final BeerPlugin plugin;
    private final FurnitureManager manager;

    BrewKettle(BeerPlugin plugin, FurnitureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    static State read(PersistentDataContainer pdc) {
        State state = new State();
        state.water = pdc.getOrDefault(Keys.KETTLE_WATER, PersistentDataType.BOOLEAN, false);
        state.added.addAll(pdc.getOrDefault(Keys.KETTLE_ADDED, PersistentDataType.LIST.strings(), List.of()));
        byte[] extra = pdc.get(Keys.KETTLE_EXTRA, PersistentDataType.BYTE_ARRAY);
        state.extra = extra == null ? null : ItemStack.deserializeBytes(extra);
        if (state.extra == null) {
            state.added.remove(EXTRA);
        }
        state.boiled = pdc.getOrDefault(Keys.BOILED, PersistentDataType.LONG, 0L);
        state.heatSince = pdc.getOrDefault(Keys.HEAT_SINCE, PersistentDataType.LONG, 0L);
        return state;
    }

    static void write(PersistentDataContainer pdc, State state) {
        pdc.set(Keys.KETTLE_WATER, PersistentDataType.BOOLEAN, state.water);
        pdc.set(Keys.KETTLE_ADDED, PersistentDataType.LIST.strings(), List.copyOf(state.added));
        if (state.extra == null) {
            pdc.remove(Keys.KETTLE_EXTRA);
        } else {
            pdc.set(Keys.KETTLE_EXTRA, PersistentDataType.BYTE_ARRAY, state.extra.serializeAsBytes());
        }
        pdc.set(Keys.BOILED, PersistentDataType.LONG, state.boiled);
        pdc.set(Keys.HEAT_SINCE, PersistentDataType.LONG, state.heatSince);
    }

    /** Model state: empty, water, or the wort's colour once it boils. */
    static String look(PersistentDataContainer pdc) {
        State state = read(pdc);
        if (!state.water) {
            return "empty";
        }
        return state.started() ? "wort_" + state.colour().look() : "water";
    }

    // ------------------------------------------------------------------------------------------ what goes in

    /** The grain an item is: vanilla Wheat, Malt or Dark Malt. */
    static Grain grainOf(ItemStack item) {
        ItemId id = Items.idOf(item);
        if (id == null) {
            return item != null && item.getType() == Material.WHEAT ? Grain.WHEAT : null;
        }
        return id == ItemId.MALT ? Grain.MALT : id == ItemId.DARK_MALT ? Grain.DARK_MALT : null;
    }

    /** The extra a plain vanilla item is (Honey Bottle, Sugar...), or null. */
    static Extra extraOf(ItemStack item) {
        if (item == null || item.isEmpty() || Items.idOf(item) != null) {
            return null;
        }
        return Extra.byMaterial(item.getType().name());
    }

    /** Why {@code item} cannot go in now, or null when it can. */
    String refusal(State state, ItemStack item) {
        Grain grain = grainOf(item);
        boolean hop = Items.is(item, ItemId.HOPS);
        Extra extra = extraOf(item);
        if (grain == null && !hop && extra == null) {
            return Items.is(item, ItemId.BARLEY)
                    ? "Smoke it into malt first: that wakes the enzymes that turn starch into sugar."
                    : "That does not go in a brew.";
        }
        if (!state.water) {
            return "Fill the kettle with a Water Bucket first.";
        }
        if (state.done(plugin.settings().boilMs())) {
            return "The wort is ready: take it out with an empty Bucket.";
        }
        if (grain != null && state.grains() >= BrewRules.GRAINS) {
            return "It holds " + BrewRules.GRAINS + " grain already.";
        }
        if (hop && state.hops() >= BrewRules.MAX_HOPS) {
            return "Two hops is as hoppy as it gets.";
        }
        if (extra != null && state.extra != null) {
            return "One extra per batch: it already has " + state.extraKind().displayName() + ".";
        }
        return null;
    }

    /** Puts one of {@code item} in (it must be allowed); returns what is left of a container, such as a glass bottle. */
    static ItemStack add(State state, ItemStack item) {
        Grain grain = grainOf(item);
        if (grain != null) {
            state.added.add(grain.id());
            return null;
        }
        if (Items.is(item, ItemId.HOPS)) {
            state.added.add(HOP);
            return null;
        }
        state.extra = item.asOne();
        state.added.add(EXTRA);
        return extraOf(item) == Extra.HONEY ? ItemStack.of(Material.GLASS_BOTTLE) : null;
    }

    // ------------------------------------------------------------------------------------------ player use

    void use(Player player, Furniture furniture) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        long boilMs = plugin.settings().boilMs();
        boolean plain = Items.idOf(held) == null;
        if (plain && held.getType() == Material.WATER_BUCKET) {
            if (state.water) {
                FurnitureManager.status(player, "It has water already.");
                return;
            }
            state.water = true;
            if (!creative) {
                player.getInventory().setItemInMainHand(ItemStack.of(Material.BUCKET));
            }
            FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_EMPTY);
        } else if (plain && held.getType() == Material.BUCKET) {
            if (state.done(boilMs)) {
                Wort wort = state.wort();
                if (!creative) {
                    held.subtract();
                }
                FurnitureManager.give(player, BrewItems.wort(wort));
                state.clear();
                FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_FILL);
                FurnitureManager.status(player, "A Bucket of Wort: pour it into a Fermenting Barrel.");
            } else if (state.water && state.added.isEmpty()) {
                if (!creative) {
                    held.subtract();
                }
                FurnitureManager.give(player, ItemStack.of(Material.WATER_BUCKET));
                state.water = false;
                FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_FILL);
            } else {
                FurnitureManager.status(player, describe(furniture, state));
                return;
            }
        } else if (held.isEmpty()) {
            if (player.isSneaking() && !state.added.isEmpty() && !state.started()) {
                FurnitureManager.give(player, takeLast(state));
                FurnitureManager.sound(furniture, Sound.ITEM_BUNDLE_REMOVE_ONE);
            } else {
                FurnitureManager.status(player, describe(furniture, state)
                        + (!state.added.isEmpty() && !state.started() ? " Sneak to take the last one back." : ""));
                return;
            }
        } else {
            String refusal = refusal(state, held);
            if (refusal != null) {
                FurnitureManager.status(player, refusal);
                return;
            }
            Extra extra = extraOf(held);
            ItemStack left = add(state, held);
            if (!creative) {
                held.subtract();
                if (left != null) {
                    FurnitureManager.give(player, left);
                }
            }
            if (extra != null) {
                plugin.journal().found(player, extra);
            }
            FurnitureManager.sound(furniture, Sound.BLOCK_BREWING_STAND_BREW);
        }
        write(pdc, state);
        manager.refresh(furniture);
        player.sendActionBar(Component.text(describe(furniture, state), NamedTextColor.GRAY));
    }

    /** Takes the last thing added back out, as an item. */
    private static ItemStack takeLast(State state) {
        String last = state.added.removeLast();
        if (last.equals(EXTRA)) {
            ItemStack extra = state.extra;
            state.extra = null;
            return extraOf(extra) == Extra.HONEY ? ItemStack.of(Material.HONEY_BOTTLE) : extra;
        }
        if (last.equals(HOP)) {
            return Items.create(ItemId.HOPS, 1);
        }
        return switch (Grain.byId(last)) {
            case WHEAT -> ItemStack.of(Material.WHEAT);
            case MALT -> Items.create(ItemId.MALT, 1);
            case DARK_MALT -> Items.create(ItemId.DARK_MALT, 1);
            case null -> ItemStack.of(Material.AIR);
        };
    }

    /** "Water · 3/4 grain · 1 hop · Sugar. Needs a 4th grain." */
    String describe(Furniture furniture, State state) {
        if (!state.water) {
            return "Fill it with a Water Bucket, then add 4 grain and a hop. It needs heat below.";
        }
        long now = System.currentTimeMillis();
        long boilMs = plugin.settings().boilMs();
        StringBuilder text = new StringBuilder("Water · " + state.grains() + "/" + BrewRules.GRAINS + " grain · "
                + state.hops() + (state.hops() == 1 ? " hop" : " hops"));
        if (state.extra != null) {
            text.append(" · ").append(state.extraKind().displayName());
        }
        text.append(". ");
        if (state.done(boilMs)) {
            text.append(BrewRules.name(state.wort())).append(" wort is ready: take it with an empty Bucket.");
        } else if (!state.canBoil()) {
            text.append(state.grains() < BrewRules.GRAINS ? "Add grain: Wheat, Malt or Dark Malt." : "Add 1 or 2 Hops.");
        } else if (!heated(furniture)) {
            text.append("Needs heat below: a campfire, fire, magma or lava.");
        } else {
            text.append("Boiling: ").append(FurnitureManager.duration(boilMs - state.boiledAt(now))).append(" left.");
        }
        return text.toString();
    }

    /** What breaking the kettle drops: what went in, unless it has already become wort. The water is lost. */
    static List<ItemStack> drops(PersistentDataContainer pdc, long boilMs) {
        State state = read(pdc);
        List<ItemStack> out = new ArrayList<>();
        if (state.done(boilMs)) {
            return out;
        }
        while (!state.added.isEmpty()) {
            ItemStack item = takeLast(state);
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ boiling

    static boolean heated(Furniture furniture) {
        Block below = furniture.block().getRelative(BlockFace.DOWN);
        if (!HEAT.contains(below.getType())) {
            return false;
        }
        return !(below.getBlockData() instanceof Lightable lit) || lit.isLit();
    }

    /**
     * Run every few ticks for each loaded kettle: counts boiling time while there is heat, finishes the batch, pours
     * finished wort through the tap into an empty barrel in front of it, and shows bubbles and steam.
     */
    void tick(Furniture furniture, long now) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        long boilMs = plugin.settings().boilMs();
        boolean changed = false;
        if (state.canBoil() && !state.done(boilMs)) {
            boolean heat = heated(furniture);
            if (heat && state.heatSince == 0) {
                state.heatSince = now;
                changed = true;
            } else if (state.heatSince > 0) {
                state.boiled += Math.max(0, now - state.heatSince);
                state.heatSince = heat ? now : 0;
                changed = true;
                if (state.boiled >= boilMs) {
                    state.heatSince = 0;
                    FurnitureManager.sound(furniture, Sound.BLOCK_BREWING_STAND_BREW);
                }
            }
            if (heat) {
                bubble(furniture);
            }
        }
        if (state.done(boilMs) && plugin.settings().hoppers()) {
            Furniture barrel = manager.at(furniture.block().getRelative(tap(furniture)));
            if (barrel != null && barrel.type() == FurnitureType.FERMENTING_BARREL && manager.barrels().fill(barrel, state.wort(), null)) {
                state.clear();
                changed = true;
                FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_EMPTY);
            }
        }
        if (changed) {
            write(pdc, state);
            manager.refresh(furniture);
        }
    }

    private static void bubble(Furniture furniture) {
        Location surface = furniture.block().getLocation().add(0.5, 0.82, 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        surface.getWorld().spawnParticle(Particle.BUBBLE_POP, surface, 2, 0.2, 0.0, 0.2, 0.0);
        if (random.nextInt(3) == 0) {
            surface.getWorld().spawnParticle(Particle.WHITE_SMOKE, surface.clone().add(0, 0.15, 0), 1, 0.15, 0.05, 0.15, 0.01);
        }
        if (random.nextInt(12) == 0) {
            surface.getWorld().playSound(surface, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
    }

    /** Hoppers: one grain, hop or extra from a feeding hopper, if it fits. Returns whether anything went in. */
    boolean feed(Furniture furniture, ItemStack item) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        if (refusal(state, item) != null) {
            return false;
        }
        ItemStack left = add(state, item);
        if (left != null) {
            Automation.output(furniture, left);
        }
        write(pdc, state);
        manager.refresh(furniture);
        return true;
    }

    /** Whether a hopper could put this in now. */
    boolean accepts(Furniture furniture, ItemStack item) {
        return refusal(read(furniture.display().getPersistentDataContainer()), item) == null;
    }

    /** A dispenser with a Water Bucket: fills an empty kettle. Returns whether it did. */
    boolean fillWater(Furniture furniture) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        if (state.water) {
            return false;
        }
        state.water = true;
        write(pdc, state);
        manager.refresh(furniture);
        FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_EMPTY);
        return true;
    }

    /**
     * The side the tap is on. The model's front is its south side, and an item display shows the front to a viewer
     * looking along its yaw, so the tap faces back towards whoever placed it.
     */
    static BlockFace tap(Furniture furniture) {
        int quarter = Math.floorMod(Math.round(furniture.display().getLocation().getYaw() / 90f), 4);
        return switch (quarter) {
            case 0 -> BlockFace.NORTH;      // placed looking south
            case 1 -> BlockFace.EAST;       // looking west
            case 2 -> BlockFace.SOUTH;      // looking north
            default -> BlockFace.WEST;      // looking east
        };
    }
}
