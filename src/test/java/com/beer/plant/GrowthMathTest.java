package com.beer.plant;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthMathTest {

    private static final GrowthMath.PlantAt ALONE = (dx, dz) -> false;

    @Test
    void lonePlantOnDryFarmlandHasTwoPoints() {
        float points = GrowthMath.growthPoints((dx, dz) -> dx == 0 && dz == 0 ? GrowthMath.Soil.DRY : GrowthMath.Soil.NONE, ALONE);
        assertEquals(2.0f, points);
        assertEquals(1.0 / 13, GrowthMath.growChance(points), 1e-9);   // vanilla nextInt(25 / 2 + 1)
    }

    @Test
    void wetFieldGivesTenPoints() {
        float points = GrowthMath.growthPoints((dx, dz) -> GrowthMath.Soil.WET, ALONE);
        assertEquals(10.0f, points);
        assertEquals(1.0 / 3, GrowthMath.growChance(points), 1e-9);
    }

    @Test
    void dryFieldGivesFourPoints() {
        assertEquals(4.0f, GrowthMath.growthPoints((dx, dz) -> GrowthMath.Soil.DRY, ALONE));
    }

    @Test
    void crowdingHalvesPoints() {
        GrowthMath.SoilAt wet = (dx, dz) -> GrowthMath.Soil.WET;
        // a single row (one axis) is fine, like vanilla
        assertEquals(10.0f, GrowthMath.growthPoints(wet, (dx, dz) -> dz == 0 && dx != 0));
        // neighbours on both axes, or any diagonal, halve it
        assertEquals(5.0f, GrowthMath.growthPoints(wet, (dx, dz) -> (dx == 0) != (dz == 0)));
        assertEquals(5.0f, GrowthMath.growthPoints(wet, (dx, dz) -> dx == 1 && dz == 1));
    }

    @Test
    void ticksPerSecondMatchesVanillaRate() {
        assertEquals(60.0 / 4096, GrowthMath.ticksPerSecond(3, 1.0), 1e-12);
        assertEquals(120.0 / 4096, GrowthMath.ticksPerSecond(3, 2.0), 1e-12);
        assertEquals(0.0, GrowthMath.ticksPerSecond(0, 1.0));
    }

    @Test
    void randomTicksAverageToExpected() {
        SplittableRandom random = new SplittableRandom(1);
        for (double expected : new double[] {0.0146, 0.7, 2.3}) {
            long sum = 0;
            int n = 200_000;
            for (int i = 0; i < n; i++) {
                sum += GrowthMath.randomTicks(expected, random);
            }
            assertEquals(expected, (double) sum / n, expected * 0.03 + 0.001);
        }
    }

    /** Barley from planted to ripe on a wet, uncrowded field at randomTickSpeed 3 takes about as long as wheat (~25-40 min). */
    @Test
    void seedToRipeTakesAboutHalfAnHour() {
        SplittableRandom random = new SplittableRandom(7);
        double perSecond = GrowthMath.ticksPerSecond(3, 1.0);
        double chance = GrowthMath.growChance(GrowthMath.growthPoints((dx, dz) -> GrowthMath.Soil.WET, ALONE));
        int runs = 2000;
        long total = 0;
        for (int run = 0; run < runs; run++) {
            int stage = 0;
            int seconds = 0;
            while (stage < Species.BARLEY.ripe()) {
                seconds++;
                for (int t = GrowthMath.randomTicks(perSecond, random); t > 0; t--) {
                    if (random.nextDouble() < chance) {
                        stage++;
                    }
                }
            }
            total += seconds;
        }
        double minutes = total / (double) runs / 60.0;
        assertTrue(minutes > 20 && minutes < 40, "average " + minutes + " min");
    }

    @Test
    void stagesClampAndRipeIsLast() {
        assertEquals(Species.BARLEY.ripe(), Species.BARLEY.clamp(99));
        assertEquals(0, Species.HOPS.clamp(-3));
        assertEquals("tillering", Species.BARLEY.stageId(3));
        assertEquals(Species.HOPS_PICKED, Species.HOPS.stageOf("leafy"));
    }

    @Test
    void blockKeysAreDistinctForNegativeCoordinates() {
        assertTrue(Plant.blockKey(-1, 64, -1) != Plant.blockKey(1, 64, 1));
        assertTrue(Plant.blockKey(0, -64, 0) != Plant.blockKey(0, 64, 0));
    }
}
