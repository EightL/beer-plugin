package com.beer.journal;

import com.beer.Keys;
import com.beer.brew.Extra;
import com.beer.brew.Legend;
import com.beer.brew.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The Beer Journal's front page: progress at the top, one tile per section. */
final class BeerHomePage extends Page {

    BeerHomePage(Journal journal, Player player) {
        super(journal, player, "Beer Journal");
    }

    @Override
    protected void render() {
        int styles = journal.count(player, Keys.FOUND_STYLES);
        int extras = journal.count(player, Keys.FOUND_EXTRAS);
        int legends = journal.count(player, Keys.FOUND_LEGENDS);
        List<Component> progress = new ArrayList<>();
        progress.add(line("Everything you have brewed.", NamedTextColor.GRAY));
        progress.add(blank());
        progress.add(bar("Styles  ", styles, Style.values().length));
        progress.add(bar("Extras  ", extras, Extra.values().length));
        progress.add(bar("Legends", legends, Legend.values().length));
        progress.add(field("Drinking buddies", String.valueOf(journal.count(player, Keys.DRINKING_BUDDIES)), NamedTextColor.WHITE));
        set(4, model("beer_journal", null, Component.text(player.getName() + "'s Beer Journal", NamedTextColor.GOLD), progress));

        set(20, model("brew_kettle", "wort_gold", Component.text("Brewer's Guide", NamedTextColor.GOLD), tile(
                "From field to mug, step by step.")),
                () -> turnTo(new BrewGuidePage(journal, player)));
        set(22, model("mug", "gold", Component.text("Beer Styles", NamedTextColor.GOLD), tile(
                "Eight beers: four grains, warm or cold.",
                styles + "/" + Style.values().length + " poured")),
                () -> turnTo(new StylesPage(journal, player)));
        set(24, icon(Material.HONEY_BOTTLE, Component.text("Extras", NamedTextColor.GOLD), tile(
                "One per batch, for an extra effect.",
                extras + "/" + Extra.values().length + " tried")),
                () -> turnTo(new ExtrasPage(journal, player)));
        set(29, glint(model("mug", "dark", Component.text("Tavern Legends", com.beer.item.Items.LEGEND_COLOR), tile(
                "Four secret brews with bonus effects.",
                legends + "/" + Legend.values().length + " brewed"))),
                () -> turnTo(new LegendsPage(journal, player)));
        set(31, model("mug", "straw_clear", Component.text("Tipsy & Cheers", NamedTextColor.GOLD), tile(
                "Drinking, sobering up, and mugs on tables.")),
                () -> turnTo(new TavernPage(journal, player)));
        set(33, icon(Material.CRAFTING_TABLE, Component.text("Recipe Book", NamedTextColor.GOLD), tile(
                "How to make the kettle, barrels, mugs and malt.")),
                () -> turnTo(new BrewRecipesPage(journal, player)));
    }

    static List<Component> tile(String text, String... extra) {
        List<Component> lore = new ArrayList<>(wrap(text, NamedTextColor.GRAY));
        for (String line : extra) {
            lore.add(line(line, NamedTextColor.WHITE));
        }
        lore.add(blank());
        lore.add(hint("Click to open"));
        return lore;
    }
}
