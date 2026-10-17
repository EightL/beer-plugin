package com.beer.brew;

/** A poured beer: its style, whether it was hoppy, its extra, and the legend it is, if any. Stars are kept on the item beside it. */
public record Beer(Style style, boolean hoppy, Extra extra, Legend legend) {

    public static Beer plain(Style style) {
        return new Beer(style, false, null, null);
    }

    /** "stout|1|cocoa|miners_stout". */
    public String encode() {
        return style.id() + "|" + (hoppy ? 1 : 0) + "|" + (extra == null ? "" : extra.id()) + "|" + (legend == null ? "" : legend.id());
    }

    /** Reads {@link #encode}'s text; null or broken text gives null. */
    public static Beer decode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split("\\|", -1);
        if (parts.length < 4) {
            return null;
        }
        Style style = Style.byId(parts[0]);
        if (style == null) {
            return null;
        }
        Extra extra = parts[2].isEmpty() ? null : Extra.byId(parts[2]);
        Legend legend = parts[3].isEmpty() ? null : Legend.byId(parts[3]);
        return new Beer(style, parts[1].equals("1"), extra, legend);
    }
}
