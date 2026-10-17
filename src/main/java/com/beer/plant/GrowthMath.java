package com.beer.plant;

import java.util.random.RandomGenerator;

/** The vanilla crop growth rules (CropBlock), free of Bukkit so they can be unit-tested. */
public final class GrowthMath {

    /** Soil under and around the plant, as vanilla sees it. */
    public enum Soil { NONE, DRY, WET }

    @FunctionalInterface
    public interface SoilAt {
        /** The soil at offset (dx, dz) from the block under the plant, dx and dz in -1..1. */
        Soil at(int dx, int dz);
    }

    @FunctionalInterface
    public interface PlantAt {
        /** Whether another crop of the same kind stands at offset (dx, dz) from this one, same height. */
        boolean at(int dx, int dz);
    }

    private GrowthMath() {
    }

    /**
     * Vanilla {@code CropBlock.getGrowthSpeed}: 1, plus 1 for farmland below (3 if wet), plus a quarter of that for
     * each of the 8 surrounding farmland blocks; halved when same-kind plants crowd it (both axes, or any diagonal).
     * Ranges from 1 (no farmland) to 10 (all wet, no crowding).
     */
    public static float growthPoints(SoilAt soil, PlantAt plants) {
        float points = 1.0f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float value = switch (soil.at(dx, dz)) {
                    case NONE -> 0.0f;
                    case DRY -> 1.0f;
                    case WET -> 3.0f;
                };
                if (dx != 0 || dz != 0) {
                    value /= 4.0f;
                }
                points += value;
            }
        }
        boolean eastWest = plants.at(-1, 0) || plants.at(1, 0);
        boolean northSouth = plants.at(0, -1) || plants.at(0, 1);
        if (eastWest && northSouth) {
            points /= 2.0f;
        } else if (plants.at(-1, -1) || plants.at(1, -1) || plants.at(1, 1) || plants.at(-1, 1)) {
            points /= 2.0f;
        }
        return points;
    }

    /** Chance that one random tick grows the plant a stage: vanilla's {@code nextInt((int) (25 / points) + 1) == 0}. */
    public static double growChance(float points) {
        return 1.0 / ((int) (25.0f / points) + 1);
    }

    /**
     * Expected random ticks per block per second: vanilla picks {@code randomTickSpeed} blocks per 16x16x16 section
     * every game tick, 20 ticks a second. {@code speed} is the config multiplier.
     */
    public static double ticksPerSecond(int randomTickSpeed, double speed) {
        return 20.0 * Math.max(0, randomTickSpeed) / 4096.0 * Math.max(0.0, speed);
    }

    /** How many random ticks a plant gets this second: the whole part of {@code expected}, plus one more by chance. */
    public static int randomTicks(double expected, RandomGenerator random) {
        int whole = (int) expected;
        return whole + (random.nextDouble() < expected - whole ? 1 : 0);
    }

    /** Light vanilla compares against: the brighter of raw sky light (ignoring night) and block light. */
    public static int light(int sky, int block) {
        return Math.max(sky, block);
    }
}
