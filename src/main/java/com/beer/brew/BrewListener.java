package com.beer.brew;

import com.beer.Keys;
import com.beer.BeerPlugin;
import com.beer.Settings;
import com.beer.item.ItemId;
import com.beer.item.Items;
import com.beer.brew.Effects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drinking beer: its effects, the Tipsy meter (a wobble now and then, passing out and a hangover), bread and milk
 * sobering you up, and Cheers when two players drink close together.
 */
public final class BrewListener implements Listener {

    /** Players who drink this close together, in time and space, clink mugs. */
    private static final long CHEERS_MS = 2_000;
    private static final double CHEERS_DISTANCE = 4.0;
    private static final int CHEERS_LUCK_TICKS = 2 * 60 * 20;
    private static final int WOBBLE_TICKS = 5 * 20;
    /** The first wobble comes this soon after turning tipsy, then every 15-30 s. */
    private static final long FIRST_WOBBLE_MIN = 3_000;
    private static final long FIRST_WOBBLE_MAX = 8_000;
    private static final long WOBBLE_MIN = 15_000;
    private static final long WOBBLE_MAX = 30_000;
    private static final int BLACKOUT_TICKS = 5 * 20;
    private static final int HANGOVER_TICKS = 2 * 60 * 20;

    /** A mug just drunk, for Cheers. */
    private record Drink(UUID who, long at, Location where, Beer beer, int stars) {
    }

    private final BeerPlugin plugin;
    private final Deque<Drink> recent = new ArrayDeque<>();
    /** When each tipsy player wobbles next. */
    private final Map<UUID, Long> nextWobble = new HashMap<>();

    public BrewListener(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        ItemId id = Items.idOf(item);
        if (id == ItemId.BEER) {
            Beer beer = BrewItems.beerOf(item);
            if (beer != null) {
                drink(player, beer, Items.qualityOf(item));
            }
        } else if (id == null && item.getType() == Material.BREAD) {
            soberUp(player, 1);
        } else if (id == null && item.getType() == Material.MILK_BUCKET) {
            soberUp(player, 2);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nextWobble.remove(event.getPlayer().getUniqueId());
    }

    /** One mug of {@code beer} drunk (from the hand or off a table): its effects, Tipsy, a burp, maybe Cheers. */
    public void drink(Player player, Beer beer, int stars) {
        Settings settings = plugin.settings();
        if (settings.effectsEnabled()) {
            give(player, beer, stars, settings.effectStrength());
        }
        long now = System.currentTimeMillis();
        boolean cheers = cheers(player, beer, stars, now);
        Tipsy.State tipsy = settings.tipsy() ? addTipsy(player, BrewRules.tipsy(beer)) : null;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.6f, 1.0f);
            }
        }, 12L);
        TextComponent.Builder bar = Component.text();
        if (cheers) {
            bar.append(Component.text("Cheers! ", NamedTextColor.GOLD));
        } else if (beer.legend() != null) {
            bar.append(Component.text("✦ " + beer.legend().displayName() + " ✦ ", Items.LEGEND_COLOR));
        }
        if (tipsy != null) {
            bar.append(meter(tipsy.level()));
        } else if (!cheers && beer.legend() == null) {
            bar.append(Component.text(BrewRules.name(beer) + ". Refreshing.", NamedTextColor.GREEN));
        }
        player.sendActionBar(bar.build());
        if (tipsy != null && tipsy.level() >= Tipsy.OUT_AT && settings.passOut()) {
            passOut(player);
        }
    }

    private void give(Player player, Beer beer, int stars, double factor) {
        for (Dose dose : BrewRules.doses(beer, stars, factor)) {
            Effects.give(player, dose);
        }
    }

    // ------------------------------------------------------------------------------------------ cheers

    /** Clinks with anyone who drank within a few blocks in the last two seconds. Returns whether anyone did. */
    private boolean cheers(Player player, Beer beer, int stars, long now) {
        for (Iterator<Drink> it = recent.iterator(); it.hasNext(); ) {
            if (now - it.next().at() > CHEERS_MS) {
                it.remove();
            }
        }
        Location here = player.getLocation();
        boolean clinked = false;
        for (Drink other : recent) {
            Player buddy = plugin.getServer().getPlayer(other.who());
            if (other.who().equals(player.getUniqueId()) || buddy == null || !other.where().getWorld().equals(here.getWorld())
                    || other.where().distanceSquared(here) > CHEERS_DISTANCE * CHEERS_DISTANCE) {
                continue;
            }
            clinked = true;
            clink(buddy, other.beer(), other.stars());
            buddy.sendActionBar(Component.text("Cheers! ", NamedTextColor.GOLD).append(Component.text("with " + player.getName(), NamedTextColor.GRAY)));
            plugin.journal().clinked(player, buddy);
            plugin.journal().clinked(buddy, player);
        }
        recent.add(new Drink(player.getUniqueId(), now, here, beer, stars));
        if (clinked) {
            clink(player, beer, stars);
            Location at = here.clone().add(0, 1.6, 0);
            at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, SoundCategory.PLAYERS, 1.0f, 1.6f);
            at.getWorld().playSound(at, Sound.BLOCK_GLASS_HIT, SoundCategory.PLAYERS, 0.8f, 1.8f);
        }
        return clinked;
    }

    /** Luck, and the beer's effects once more a quarter longer. */
    private void clink(Player player, Beer beer, int stars) {
        Settings settings = plugin.settings();
        if (settings.effectsEnabled()) {
            give(player, beer, stars, settings.effectStrength() * BrewRules.CHEERS);
            Effects.give(player, new Dose(Effect.LUCK, 0, CHEERS_LUCK_TICKS));
        }
        Location head = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.5));
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, head, 8, 0.15, 0.1, 0.15, 0.02);
        player.getWorld().spawnParticle(Particle.SPLASH, head, 10, 0.15, 0.1, 0.15, 0.1);
    }

    // ------------------------------------------------------------------------------------------ tipsy

    public Tipsy.State tipsy(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Tipsy.State stored = new Tipsy.State(pdc.getOrDefault(Keys.TIPSY, PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(Keys.TIPSY_SINCE, PersistentDataType.LONG, 0L));
        return Tipsy.decay(stored, System.currentTimeMillis(), plugin.settings().tipsyDecayMs());
    }

    public void setTipsy(Player player, Tipsy.State state) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (state.level() <= 0) {
            pdc.remove(Keys.TIPSY);
            pdc.remove(Keys.TIPSY_SINCE);
            nextWobble.remove(player.getUniqueId());
            return;
        }
        pdc.set(Keys.TIPSY, PersistentDataType.INTEGER, state.level());
        pdc.set(Keys.TIPSY_SINCE, PersistentDataType.LONG, state.since());
    }

    private Tipsy.State addTipsy(Player player, int amount) {
        Tipsy.State state = Tipsy.add(tipsy(player), amount, System.currentTimeMillis(), plugin.settings().tipsyDecayMs());
        setTipsy(player, state);
        return state;
    }

    private void soberUp(Player player, int amount) {
        if (!plugin.settings().tipsy() || tipsy(player).level() <= 0) {
            return;
        }
        Tipsy.State state = addTipsy(player, -amount);
        player.sendActionBar(state.level() <= 0 ? Component.text("Sober again.", NamedTextColor.GREEN) : meter(state.level()));
    }

    /** "Tipsy ▰▰▰▱▱ Tipsy" in the action bar. */
    public static Component meter(int level) {
        int shown = Math.min(level, Tipsy.OUT_AT);
        Tipsy.Tier tier = Tipsy.tier(level);
        NamedTextColor color = switch (tier) {
            case SOBER, MERRY -> NamedTextColor.GREEN;
            case TIPSY -> NamedTextColor.YELLOW;
            case OUT_COLD -> NamedTextColor.RED;
        };
        return Component.text("Tipsy ", NamedTextColor.GRAY)
                .append(Component.text("▰".repeat(shown), color))
                .append(Component.text("▱".repeat(Tipsy.OUT_AT - shown), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + tier.displayName(), color));
    }

    /** A short blackout, then a hangover; Tipsy goes back to 0. */
    private void passOut(Player player) {
        setTipsy(player, new Tipsy.State(0, System.currentTimeMillis()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLACKOUT_TICKS, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, BLACKOUT_TICKS, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BLACKOUT_TICKS, 3, false, false, false));
        player.sendActionBar(Component.text("Everything goes dark...", NamedTextColor.DARK_GRAY));
        UUID who = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player later = plugin.getServer().getPlayer(who);
            if (later == null || later.isDead()) {
                return;
            }
            later.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, HANGOVER_TICKS, 0, true, true, true));
            later.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, HANGOVER_TICKS, 0, true, true, true));
            later.sendActionBar(Component.text("Ugh. A hangover.", NamedTextColor.GRAY));
        }, BLACKOUT_TICKS);
    }

    /** Every second: tipsy players (3 or more) wobble now and then, a short Nausea and a stumble. */
    public void tick() {
        if (!plugin.settings().tipsy()) {
            nextWobble.clear();
            return;
        }
        long now = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!player.getPersistentDataContainer().has(Keys.TIPSY) || Tipsy.tier(tipsy(player).level()) != Tipsy.Tier.TIPSY) {
                nextWobble.remove(id);
                continue;
            }
            Long next = nextWobble.get(id);
            if (next == null) {
                nextWobble.put(id, now + random.nextLong(FIRST_WOBBLE_MIN, FIRST_WOBBLE_MAX + 1));
            } else if (now >= next) {
                nextWobble.put(id, now + random.nextLong(WOBBLE_MIN, WOBBLE_MAX + 1));
                wobble(player, random, tipsy(player).level());
            }
        }
    }

    /**
     * The world swims (Nausea), the legs go (a moment of Slowness) and the player stumbles sideways. Nausea draws
     * nothing for players who turned Distortion Effects off, so the stumble and the sound carry it too.
     */
    private static void wobble(Player player, ThreadLocalRandom random, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, WOBBLE_TICKS, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, false));
        if (!player.isFlying() && !player.isGliding() && !player.isInsideVehicle()) {
            double angle = random.nextDouble(Math.PI * 2);
            player.setVelocity(player.getVelocity().add(new Vector(Math.cos(angle) * 0.35, 0.1, Math.sin(angle) * 0.35)));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.4f);
        player.sendActionBar(Component.text("*hic* ", NamedTextColor.YELLOW).append(meter(level)));
    }

    /** For tests and admins: sets the Tipsy level now, with what that level does (5 or more: passing out). */
    public void setLevel(Player player, int level) {
        nextWobble.remove(player.getUniqueId());
        setTipsy(player, new Tipsy.State(Math.max(0, level), System.currentTimeMillis()));
        player.sendActionBar(meter(level));
        if (level >= Tipsy.OUT_AT && plugin.settings().passOut()) {
            passOut(player);
        }
    }
}
