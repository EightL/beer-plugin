package com.beer.furniture;

import com.beer.Keys;
import com.beer.BeerPlugin;
import com.beer.Settings;
import com.beer.brew.Beer;
import com.beer.brew.BrewItems;
import com.beer.brew.BrewRules;
import com.beer.brew.Legend;
import com.beer.brew.Wood;
import com.beer.brew.Wort;
import com.beer.item.ItemId;
import com.beer.item.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Fermenting Barrel: pour a Bucket of Wort in and it ferments into a batch of mugs, an ale when warm or a lager
 * when cold (touching ice or snow, or in a snowy biome). Ready after a while; the longer the beer waits, the more stars
 * it pours with, and the barrel's wood can add one. It never goes off.
 */
final class FermentingBarrel {

    private static final Set<Material> COLD = EnumSet.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.SNOW_BLOCK, Material.POWDER_SNOW);
    private static final BlockFace[] SIDES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    /** Snow falls in biomes colder than this. */
    private static final double SNOWY = 0.15;

    /** One barrel's contents. {@code wort} is null when it is empty. */
    record State(Wood wood, Wort wort, boolean cold, boolean blueIce, long filledAt, int mugsLeft) {
        boolean empty() {
            return wort == null || mugsLeft <= 0;
        }
    }

    private final BeerPlugin plugin;
    private final FurnitureManager manager;

    FermentingBarrel(BeerPlugin plugin, FurnitureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    static State read(PersistentDataContainer pdc) {
        Wood wood = Wood.byId(pdc.get(Keys.WOOD, PersistentDataType.STRING));
        Wort wort = Wort.decode(pdc.get(Keys.BREW, PersistentDataType.STRING));
        return new State(wood, wort, pdc.getOrDefault(Keys.COLD, PersistentDataType.BOOLEAN, false),
                pdc.getOrDefault(Keys.BLUE_ICE, PersistentDataType.BOOLEAN, false),
                pdc.getOrDefault(Keys.FILLED_AT, PersistentDataType.LONG, 0L),
                pdc.getOrDefault(Keys.MUGS_LEFT, PersistentDataType.INTEGER, 0));
    }

    static void write(PersistentDataContainer pdc, State state) {
        pdc.set(Keys.WOOD, PersistentDataType.STRING, state.wood().id());
        if (state.empty()) {
            for (var key : new org.bukkit.NamespacedKey[] {Keys.BREW, Keys.COLD, Keys.BLUE_ICE, Keys.FILLED_AT, Keys.MUGS_LEFT}) {
                pdc.remove(key);
            }
            return;
        }
        pdc.set(Keys.BREW, PersistentDataType.STRING, state.wort().encode());
        pdc.set(Keys.COLD, PersistentDataType.BOOLEAN, state.cold());
        pdc.set(Keys.BLUE_ICE, PersistentDataType.BOOLEAN, state.blueIce());
        pdc.set(Keys.FILLED_AT, PersistentDataType.LONG, state.filledAt());
        pdc.set(Keys.MUGS_LEFT, PersistentDataType.INTEGER, state.mugsLeft());
    }

    /** Model state: [wood, fermenting | ready | empty]. */
    static String[] look(PersistentDataContainer pdc, long now, Settings brewing) {
        State state = read(pdc);
        String fill = state.empty() ? "empty" : ready(state, now, brewing) ? "ready" : "fermenting";
        return new String[] {state.wood().id(), fill};
    }

    static boolean ready(State state, long now, Settings brewing) {
        return !state.empty() && now - state.filledAt() >= BrewRules.fermentMs(state.cold(), brewing.aleMs(), brewing.lagerMs());
    }

    private static int stars(State state, long now, Settings brewing) {
        return BrewRules.stars(now - state.filledAt(), brewing.agedMs(), brewing.longAgedMs(), state.wood(), state.wort().colour());
    }

    /** The beer the barrel pours now, and its stars. */
    private static Beer beer(State state, int stars) {
        return BrewRules.pour(state.wort(), state.cold(), state.blueIce(), state.wood(), stars);
    }

    // ------------------------------------------------------------------------------------------ filling

    /**
     * Fills an empty barrel with a batch of wort; whether it ferments cold is decided now. Tells {@code player} (if
     * any) what it will be. Returns false when the barrel is not empty.
     */
    boolean fill(Furniture furniture, Wort wort, Player player) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        if (!state.empty()) {
            return false;
        }
        Block block = furniture.block();
        boolean blueIce = false;
        boolean cold = block.getTemperature() < SNOWY;
        for (BlockFace face : SIDES) {
            Material next = block.getRelative(face).getType();
            cold |= COLD.contains(next);
            blueIce |= next == Material.BLUE_ICE;
        }
        Settings brewing = plugin.settings();
        State filled = new State(state.wood(), wort, cold, blueIce, System.currentTimeMillis(), brewing.mugsPerBatch());
        write(pdc, filled);
        manager.refresh(furniture);
        FurnitureManager.sound(furniture, Sound.BLOCK_BARREL_CLOSE);
        if (player != null) {
            String what = BrewRules.name(beer(filled, BrewRules.FRESH_STARS));
            long time = BrewRules.fermentMs(cold, brewing.aleMs(), brewing.lagerMs());
            player.sendActionBar(Component.text((cold ? "Cold barrel: a lager. " : "Warm barrel: an ale. ")
                    + what + ", ready in " + FurnitureManager.duration(time) + ".", cold ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ player use

    void use(Player player, Furniture furniture) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        Settings brewing = plugin.settings();
        long now = System.currentTimeMillis();
        ItemId heldId = Items.idOf(held);
        if (heldId == ItemId.WORT_BUCKET) {
            Wort wort = BrewItems.wortOf(held);
            if (wort == null) {
                return;
            }
            if (!state.empty()) {
                FurnitureManager.status(player, "The barrel holds a batch already: " + status(state, now, brewing));
                return;
            }
            fill(furniture, wort, player);
            if (!creative) {
                player.getInventory().setItemInMainHand(ItemStack.of(Material.BUCKET));
            }
            FurnitureManager.sound(furniture, Sound.ITEM_BUCKET_EMPTY);
        } else if (heldId == ItemId.EMPTY_MUG) {
            if (state.empty()) {
                FurnitureManager.status(player, "The barrel is empty. Pour a Bucket of Wort in.");
                return;
            }
            if (!ready(state, now, brewing)) {
                FurnitureManager.status(player, status(state, now, brewing));
                return;
            }
            int count = player.isSneaking() ? Math.min(held.getAmount(), state.mugsLeft()) : 1;
            int stars = stars(state, now, brewing);
            Beer beer = beer(state, stars);
            if (!creative) {
                held.subtract(count);
            }
            FurnitureManager.give(player, BrewItems.beer(beer, stars, count));
            State after = new State(state.wood(), state.wort(), state.cold(), state.blueIce(), state.filledAt(), state.mugsLeft() - count);
            write(pdc, after);
            manager.refresh(furniture);
            FurnitureManager.sound(furniture, Sound.ITEM_BOTTLE_FILL);
            discover(player, beer);
            if (beer.legend() == null) {
                FurnitureManager.status(player, BrewRules.name(beer) + " " + "★".repeat(stars) + ". "
                        + (after.empty() ? "The barrel is empty." : after.mugsLeft() + " mugs left."));
            }
        } else {
            FurnitureManager.status(player, state.empty() ? state.wood().displayName() + " barrel, empty. Pour a Bucket of Wort in."
                    : status(state, now, brewing));
        }
    }

    /** "Stout, ale: ready in 12:30" or "Pilsner: 6 mugs, 3★ now (4★ in 1 h 20 min)". */
    private String status(State state, long now, Settings brewing) {
        String name = BrewRules.name(beer(state, BrewRules.FRESH_STARS));
        if (!ready(state, now, brewing)) {
            long left = state.filledAt() + BrewRules.fermentMs(state.cold(), brewing.aleMs(), brewing.lagerMs()) - now;
            return name + (state.cold() ? " (lager)" : " (ale)") + " is fermenting: ready in " + FurnitureManager.duration(left) + ".";
        }
        int stars = stars(state, now, brewing);
        long waited = now - state.filledAt();
        String next = waited < brewing.agedMs() ? " (" + (stars + 1) + "★ in " + FurnitureManager.duration(brewing.agedMs() - waited) + ")"
                : waited < brewing.longAgedMs() ? " (" + (stars + 1) + "★ in " + FurnitureManager.duration(brewing.longAgedMs() - waited) + ")" : "";
        if (stars >= 5) {
            next = "";
        }
        return name + " is ready: " + state.mugsLeft() + " mugs, " + stars + "★ now" + next + ". Pour with an Empty Mug.";
    }

    /** Records the style, extra and legend in the pourer's journal; a legend's first pour gets a title. */
    private void discover(Player player, Beer beer) {
        plugin.journal().found(player, beer.style());
        if (beer.extra() != null) {
            plugin.journal().found(player, beer.extra());
        }
        Legend legend = beer.legend();
        if (legend == null) {
            return;
        }
        boolean first = plugin.journal().found(player, legend);
        player.showTitle(Title.title(
                Component.text("✦ " + legend.displayName(), Items.LEGEND_COLOR),
                Component.text(first ? "A Tavern Legend! Recorded in your journal." : "Tavern Legend", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(600))));
        player.playSound(player.getLocation(), first ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------------------------------ ticks and hoppers

    /** Run every few ticks: the airlock blips bubbles while it ferments. */
    void tick(Furniture furniture, long now) {
        State state = read(furniture.display().getPersistentDataContainer());
        if (state.empty() || ready(state, now, plugin.settings()) || ThreadLocalRandom.current().nextInt(4) != 0) {
            return;
        }
        Location airlock = furniture.block().getLocation().add(0.5, 1.15, 0.5);
        airlock.getWorld().spawnParticle(Particle.BUBBLE_POP, airlock, 1, 0.02, 0.02, 0.02, 0.0);
        if (ThreadLocalRandom.current().nextInt(6) == 0) {
            airlock.getWorld().playSound(airlock, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.3f, 1.6f);
        }
    }

    /**
     * Hoppers: once the beer has had the long wait, an Empty Mug from a feeding hopper is filled straight into the
     * hopper below. Returns the filled mug, or null when nothing should be poured.
     */
    ItemStack pourForHopper(Furniture furniture, long now) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        Settings brewing = plugin.settings();
        if (!ready(state, now, brewing) || now - state.filledAt() < brewing.longAgedMs()) {
            return null;
        }
        int stars = stars(state, now, brewing);
        return BrewItems.beer(beer(state, stars), stars, 1);
    }

    /** One mug was poured (by a hopper). */
    void poured(Furniture furniture) {
        PersistentDataContainer pdc = furniture.display().getPersistentDataContainer();
        State state = read(pdc);
        if (state.empty()) {
            return;
        }
        write(pdc, new State(state.wood(), state.wort(), state.cold(), state.blueIce(), state.filledAt(), state.mugsLeft() - 1));
        manager.refresh(furniture);
    }

    static Wood wood(Furniture furniture) {
        return read(furniture.display().getPersistentDataContainer()).wood();
    }
}
