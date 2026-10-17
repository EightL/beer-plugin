package com.beer.brew;

/** One effect a beer gives, at a level: 0 is level I. */
public record Trait(Effect effect, int level) {
}
