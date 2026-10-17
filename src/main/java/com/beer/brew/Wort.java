package com.beer.brew;

/**
 * What a kettle boiled: four grains, one or two hops and at most one extra. Carried in a Bucket of Wort and kept in
 * the barrel while it ferments.
 */
public record Wort(int wheat, int malt, int darkMalt, int hops, Extra extra) {

    public Colour colour() {
        return Colour.of(wheat, darkMalt);
    }

    /** Two hops: a Hoppy beer. */
    public boolean hoppy() {
        return hops >= 2;
    }

    /** "2,2,0|2|cocoa". */
    public String encode() {
        return wheat + "," + malt + "," + darkMalt + "|" + hops + "|" + (extra == null ? "" : extra.id());
    }

    /** Reads {@link #encode}'s text; null or broken text gives null. */
    public static Wort decode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split("\\|", -1);
        if (parts.length < 3) {
            return null;
        }
        try {
            String[] grain = parts[0].split(",");
            Extra extra = parts[2].isEmpty() ? null : Extra.byId(parts[2]);
            return new Wort(Integer.parseInt(grain[0]), Integer.parseInt(grain[1]), Integer.parseInt(grain[2]),
                    Integer.parseInt(parts[1]), extra);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
