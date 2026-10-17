package com.beer.furniture;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Mining furniture like the block it is made of. Its barrier is unbreakable to the client, which keeps swinging while
 * the button is held, so the progress is counted here with vanilla's formula (tool speed, Efficiency, Haste and Mining
 * Fatigue over the block's hardness; a third as fast without the right tool where vanilla needs one). The client draws
 * no cracks on an invisible block, so hits show as the material's particles and sounds.
 */
final class Mining implements Listener {

    /** Without a swing for this many ticks the player has let go. */
    private static final int IDLE_TICKS = 6;

    private static final class Session {
        final Furniture furniture;
        float progress;
        int idle;
        int ticks;

        Session(Furniture furniture) {
            this.furniture = furniture;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BiConsumer<Player, Furniture> breaker;

    Mining(BiConsumer<Player, Furniture> breaker) {
        this.breaker = breaker;
    }

    /** A left click on furniture: creative breaks it at once, survival starts mining it. */
    void start(Player player, Furniture furniture) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            breaker.accept(player, furniture);
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Session current = sessions.get(player.getUniqueId());
        if (current == null || !current.furniture.display().equals(furniture.display())) {
            sessions.put(player.getUniqueId(), new Session(furniture));
        } else {
            current.idle = 0;
        }
        effects(player, furniture, true);
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.idle = 0;
        }
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /** Every tick: adds progress for players still holding the button on the same furniture, and breaks it when done. */
    void tick() {
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            Block block = session.furniture.block();
            if (player == null || !session.furniture.display().isValid() || player.getGameMode() != GameMode.SURVIVAL
                    || ++session.idle > IDLE_TICKS || !block.equals(player.getTargetBlockExact(6))) {
                it.remove();
                continue;
            }
            session.progress += perTick(player, session.furniture);
            if (++session.ticks % 4 == 0) {
                effects(player, session.furniture, false);
            }
            if (session.progress >= 1) {
                it.remove();
                breaker.accept(player, session.furniture);
            }
        }
    }

    /** Vanilla's destroy progress per tick for the furniture's block with the player's main-hand item. */
    static float perTick(Player player, Furniture furniture) {
        BlockData like = furniture.type().like().createBlockData();
        float hardness = furniture.type().like().getHardness();
        if (hardness <= 0) {
            return 1;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        float speed = like.getDestroySpeed(tool, true);
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null) {
            speed *= 1 + 0.2f * (haste.getAmplifier() + 1);
        }
        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        boolean harvests = !like.requiresCorrectToolForDrops() || like.isPreferredTool(tool);
        return speed / hardness / (harvests ? 30f : 100f);
    }

    /** The material's hit sound and a puff of its particles on the side facing the player. */
    private static void effects(Player player, Furniture furniture, boolean always) {
        BlockData like = furniture.type().like().createBlockData();
        Location center = furniture.center();
        Location toward = center.clone().add(player.getEyeLocation().toVector().subtract(center.toVector()).normalize().multiply(0.45));
        center.getWorld().spawnParticle(Particle.BLOCK, toward, always ? 6 : 3, 0.12, 0.12, 0.12, like);
        center.getWorld().playSound(center, like.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.5f, 0.75f);
    }
}
