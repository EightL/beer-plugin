package com.beer.plant;

import com.beer.BeerPlugin;
import com.beer.Settings;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Once a second, gives every crop in a ticking chunk the random ticks vanilla would have given its block that second,
 * and applies the wheat rules to each: enough light, then a chance from the soil around it. Hops grow at their own
 * speed.
 */
public final class GrowthTask implements Runnable {

    private record ChunkRef(UUID world, long chunkKey) {
    }

    private final BeerPlugin plugin;
    private final PlantManager plants;

    public GrowthTask(BeerPlugin plugin, PlantManager plants) {
        this.plugin = plugin;
        this.plants = plants;
    }

    @Override
    public void run() {
        Settings settings = plugin.settings();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Map<UUID, Double> ticksPerSecond = new HashMap<>();
        Map<ChunkRef, Boolean> ticking = new HashMap<>();
        for (Plant plant : plants.all()) {
            World world = plant.world();
            double expected = ticksPerSecond.computeIfAbsent(world.getUID(), id -> GrowthMath.ticksPerSecond(
                    world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED), settings.growthSpeed()));
            int ticks = GrowthMath.randomTicks(expected * speed(plant, settings), random);
            if (ticks == 0 || !ticking.computeIfAbsent(new ChunkRef(world.getUID(), plant.chunkKey()),
                    key -> isTicking(world, plant.x() >> 4, plant.z() >> 4))) {
                continue;
            }
            for (int i = 0; i < ticks && randomTick(plant, settings, random); i++) {
                // each tick may grow one stage
            }
        }
    }

    /** One vanilla random tick. Returns false once the plant is gone or cannot grow further this second. */
    private boolean randomTick(Plant plant, Settings settings, ThreadLocalRandom random) {
        if (!plants.canStay(plant)) {
            plants.pop(plant);
            return false;
        }
        int stage = plant.stage();
        if (plant.isRipe()) {
            return false;
        }
        Block block = plant.block();
        if (GrowthMath.light(block.getLightFromSky(), block.getLightFromBlocks()) < minLight(settings)) {
            return false;
        }
        Block below = block.getRelative(0, -1, 0);
        float points = GrowthMath.growthPoints(
                (dx, dz) -> soil(below.getRelative(dx, 0, dz)),
                (dx, dz) -> sameKind(plants.at(plant.world(), plant.x() + dx, plant.y(), plant.z() + dz), plant));
        if (random.nextDouble() < GrowthMath.growChance(points)) {
            plants.setStage(plant, stage + 1);
        }
        return true;
    }

    /** Vanilla only halves growth for crops of the same kind around it. */
    private static boolean sameKind(Plant other, Plant plant) {
        return other != null && other.species() == plant.species();
    }

    /**
     * Growth speed: barley like wheat; hops climb a little slower (about 25 minutes to ripe) and, once leafy, flower and
     * ripen again at the configured regrow speed (about 8 minutes after a picking).
     */
    static double speed(Plant plant, Settings settings) {
        return switch (plant.species()) {
            case BARLEY -> 1.0;
            case HOPS -> plant.stage() < Species.HOPS_PICKED ? 0.75 : 0.9 * settings.hopsRegrowSpeed();
        };
    }

    /** The light crops need (config growth.min-light, wheat's 9 by default). */
    public static int minLight(Settings settings) {
        return Math.clamp(settings.minLight(), 0, 15);
    }

    /** Farmland as vanilla sees it. */
    private static GrowthMath.Soil soil(Block block) {
        if (block.getType() != Material.FARMLAND) {
            return GrowthMath.Soil.NONE;
        }
        return ((Farmland) block.getBlockData()).getMoisture() > 0 ? GrowthMath.Soil.WET : GrowthMath.Soil.DRY;
    }

    /** Vanilla only random-ticks blocks in chunks within simulation distance; those are entity-ticking. */
    private static boolean isTicking(World world, int cx, int cz) {
        if (!world.isChunkLoaded(cx, cz)) {
            return false;
        }
        Chunk chunk = world.getChunkAt(cx, cz);
        return chunk.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING;
    }
}
