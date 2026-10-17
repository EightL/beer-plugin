package com.beer.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TipsyTest {

    private static final long STEP = 120_000;

    @Test
    void dropsOneLevelPerStep() {
        Tipsy.State three = new Tipsy.State(3, 0);
        assertEquals(3, Tipsy.decay(three, STEP - 1, STEP).level());
        assertEquals(2, Tipsy.decay(three, STEP, STEP).level());
        assertEquals(1, Tipsy.decay(three, 2 * STEP + 5, STEP).level());
        assertEquals(0, Tipsy.decay(three, 10 * STEP, STEP).level());
    }

    @Test
    void addingKeepsThePartOfAStepAlreadyDone() {
        Tipsy.State state = Tipsy.add(new Tipsy.State(0, 0), 2, 1_000, STEP);
        assertEquals(new Tipsy.State(2, 1_000), state);
        state = Tipsy.add(state, 1, 1_000 + STEP + 500, STEP);       // one step gone: 1, then +1
        assertEquals(2, state.level());
        assertEquals(1_000 + STEP, state.since());
        state = Tipsy.add(state, -5, 1_000 + STEP + 600, STEP);      // bread and milk cannot go below 0
        assertEquals(0, state.level());
    }

    @Test
    void tiers() {
        assertEquals(Tipsy.Tier.SOBER, Tipsy.tier(0));
        assertEquals(Tipsy.Tier.MERRY, Tipsy.tier(2));
        assertEquals(Tipsy.Tier.TIPSY, Tipsy.tier(3));
        assertEquals(Tipsy.Tier.TIPSY, Tipsy.tier(4));
        assertEquals(Tipsy.Tier.OUT_COLD, Tipsy.tier(5));
    }
}
