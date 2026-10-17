package com.beer.brew;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Gives beer effects to players. */
public final class Effects {

    private Effects() {
    }

    /** Adds the dose, unless a stronger or longer effect of that type is already running. */
    public static void give(Player player, Dose dose) {
        PotionEffectType type = type(dose.effect());
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > dose.amplifier()
                || current != null && current.getAmplifier() == dose.amplifier() && (current.isInfinite() || current.getDuration() > dose.ticks())) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, dose.ticks(), dose.amplifier(), true, true, true));
    }

    public static PotionEffectType type(Effect effect) {
        return switch (effect) {
            case REGENERATION -> PotionEffectType.REGENERATION;
            case NIGHT_VISION -> PotionEffectType.NIGHT_VISION;
            case HASTE -> PotionEffectType.HASTE;
            case SPEED -> PotionEffectType.SPEED;
            case JUMP_BOOST -> PotionEffectType.JUMP_BOOST;
            case ABSORPTION -> PotionEffectType.ABSORPTION;
            case LUCK -> PotionEffectType.LUCK;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case HEALTH_BOOST -> PotionEffectType.HEALTH_BOOST;
            case HERO_OF_THE_VILLAGE -> PotionEffectType.HERO_OF_THE_VILLAGE;
        };
    }
}
