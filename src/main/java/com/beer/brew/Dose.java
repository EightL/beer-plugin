package com.beer.brew;

/** One potion effect for a player: amplifier 0 is level I. */
public record Dose(Effect effect, int amplifier, int ticks) {
}
