package com.beer;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** config.yml, read once per load or reload. */
public record Settings(
        double growthSpeed, int minLight, float viewDistance,
        Range barleyDrops, Range hopCones, double hopsRegrowSpeed,
        double barleyGrassChance, double hopsGrassChance, double villageChestChance, double traderChance,
        long boilMs, int mugsPerBatch, long aleMs, long lagerMs, long agedMs, long longAgedMs,
        boolean effectsEnabled, double effectStrength,
        boolean tipsy, boolean passOut, long tipsyDecayMs,
        boolean hoppers, boolean dispensers) {

    /** An inclusive amount range, for example {@code [1, 3]}. */
    public record Range(int min, int max) {
        public int roll(java.util.random.RandomGenerator random) {
            return min >= max ? min : random.nextInt(min, max + 1);
        }
    }

    public static Settings from(FileConfiguration config) {
        return new Settings(
                config.getDouble("growth.speed", 1.0),
                config.getInt("growth.min-light", 9),
                (float) config.getDouble("crops.view-distance", 48),
                range(config, "barley.drops", 2, 4),
                range(config, "hops.cones", 2, 4),
                Math.max(0.05, config.getDouble("hops.regrow-speed", 1.0)),
                config.getDouble("seeds.barley-grass-chance", 0.03),
                config.getDouble("seeds.hops-grass-chance", 0.015),
                config.getDouble("seeds.village-chest-chance", 0.25),
                config.getDouble("seeds.wandering-trader-chance", 0.5),
                minutes(config, "brewing.boil-minutes", 3),
                Math.clamp(config.getInt("brewing.mugs-per-batch", 8), 1, 64),
                minutes(config, "fermenting.ale-minutes", 20),
                minutes(config, "fermenting.lager-minutes", 20),
                minutes(config, "fermenting.aged-minutes", 60),
                minutes(config, "fermenting.long-aged-minutes", 180),
                config.getBoolean("effects.enabled", true),
                Math.max(0.0, config.getDouble("effects.duration-multiplier", 1.0)),
                config.getBoolean("tipsy.enabled", true),
                config.getBoolean("tipsy.pass-out", true),
                minutes(config, "tipsy.decay-minutes", 2),
                config.getBoolean("automation.hoppers", true),
                config.getBoolean("automation.dispensers", true));
    }

    private static long minutes(FileConfiguration config, String path, double fallback) {
        return Math.max(1L, Math.round(config.getDouble(path, fallback) * 60_000));
    }

    private static Range range(FileConfiguration config, String path, int min, int max) {
        List<Integer> values = config.getIntegerList(path);
        if (values.size() == 2) {
            return new Range(Math.max(0, Math.min(values.get(0), values.get(1))), Math.max(values.get(0), values.get(1)));
        }
        return new Range(min, max);
    }
}
