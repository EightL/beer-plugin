package com.beer.brew;

/**
 * The Tipsy meter, free of Bukkit. Each mug adds to it and it drops by one every few minutes (wall clock, so it keeps
 * dropping while the player is offline). 1-2 is merry, 3-4 tipsy (a wobble now and then), 5 or more and you pass out.
 */
public final class Tipsy {

    /** The level and when it last changed (decay counts from then). */
    public record State(int level, long since) {
    }

    public enum Tier {
        SOBER("Sober"), MERRY("Merry"), TIPSY("Tipsy"), OUT_COLD("Out cold");

        private final String displayName;

        Tier(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final int TIPSY_AT = 3;
    public static final int OUT_AT = 5;

    private Tipsy() {
    }

    /** The state at {@code now}: one level gone per whole {@code decayMs}, keeping the part of a step already done. */
    public static State decay(State state, long now, long decayMs) {
        if (state.level() <= 0 || now <= state.since()) {
            return state.level() <= 0 ? new State(0, now) : state;
        }
        long steps = (now - state.since()) / Math.max(1, decayMs);
        if (steps >= state.level()) {
            return new State(0, now);
        }
        return new State((int) (state.level() - steps), state.since() + steps * decayMs);
    }

    /** The state after adding {@code amount} (negative to sober up) at {@code now}. */
    public static State add(State state, int amount, long now, long decayMs) {
        State current = decay(state, now, decayMs);
        int level = Math.max(0, current.level() + amount);
        return new State(level, current.level() == 0 || level == 0 ? now : current.since());
    }

    public static Tier tier(int level) {
        return level <= 0 ? Tier.SOBER : level < TIPSY_AT ? Tier.MERRY : level < OUT_AT ? Tier.TIPSY : Tier.OUT_COLD;
    }
}
