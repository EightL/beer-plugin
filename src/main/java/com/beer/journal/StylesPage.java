package com.beer.journal;

import com.beer.brew.Colour;
import com.beer.brew.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The eight styles as a grid: one column per grain colour, ales on one row and lagers below. Grey until poured. */
final class StylesPage extends Page {

    /** Column slots on the header row; the ale is one row down, the lager two. */
    private static final int[] COLUMNS = {11, 13, 15, 17};

    StylesPage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Beer Styles");
    }

    @Override
    protected void render() {
        List<Component> about = new ArrayList<>();
        about.addAll(wrap("Grain sets the colour, the barrel's temperature ale or lager.", NamedTextColor.GRAY));
        about.add(blank());
        about.addAll(wrap("2 Hops: Hoppy, +Strength.", NamedTextColor.YELLOW));
        set(4, model("hops", null, Component.text("Grain, hops and cold", NamedTextColor.GOLD), about));

        set(18, icon(Material.CAMPFIRE, Component.text("Ales (warm)", NamedTextColor.GOLD),
                wrap("Fermented warm.", NamedTextColor.GRAY)));
        set(27, icon(Material.BLUE_ICE, Component.text("Lagers (cold)", NamedTextColor.AQUA),
                wrap("Fermented beside ice or snow. Takes twice as long, effects x1.5.", NamedTextColor.GRAY)));
        Colour[] colours = Colour.values();
        for (int i = 0; i < colours.length; i++) {
            set(COLUMNS[i], grain(colours[i]));
            set(COLUMNS[i] + 9, style(Style.of(colours[i], false)));
            set(COLUMNS[i] + 18, style(Style.of(colours[i], true)));
        }
        backButton();
    }

    /** The column header: what grain makes this colour. */
    private static ItemStack grain(Colour colour) {
        String text = switch (colour) {
            case WHEAT -> "2 or more Wheat, no Dark Malt";
            case PALE -> "Malt, no Dark Malt";
            case AMBER -> "1 Dark Malt";
            case DARK -> "2 or more Dark Malt";
        };
        List<Component> lore = new ArrayList<>(wrap(text, NamedTextColor.WHITE));
        lore.add(field("Effect", colour.base().displayName(), TextColor.color(colour.base().color())));
        lore.add(field("Best barrel", colour.wood().displayName() + " wood", NamedTextColor.WHITE));
        Component name = Component.text(colour.displayName() + " beers", TextColor.color(colour.color()));
        return switch (colour) {
            case WHEAT -> icon(Material.WHEAT, name, lore);
            case PALE -> model("malt", null, name, lore);
            case AMBER, DARK -> model("dark_malt", null, name, lore);
        };
    }

    private ItemStack style(Style style) {
        Colour colour = style.colour();
        List<Component> lore = new ArrayList<>();
        lore.add(field("Fermented", style.lager() ? "cold" : "warm", style.lager() ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        if (!journal.has(player, style)) {
            lore.add(blank());
            lore.add(line("Pour one to record it.", NamedTextColor.DARK_GRAY));
            return model("mug", null, Component.text("???", NamedTextColor.DARK_GRAY), lore);
        }
        lore.add(field("Effect", colour.base().displayName() + (style.lager() ? " (x1.5)" : ""),
                TextColor.color(colour.base().color())));
        if (style == Style.PALE_ALE) {
            lore.add(line("With 2 Hops it is an IPA.", NamedTextColor.GRAY));
        }
        return model("mug", style.look(), Component.text(style.displayName(), TextColor.color(colour.color())), lore);
    }
}
