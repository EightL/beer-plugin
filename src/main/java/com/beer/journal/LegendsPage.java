package com.beer.journal;

import com.beer.brew.Legend;
import com.beer.brew.Style;
import com.beer.item.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Tavern Legends: a riddle until brewed, then the exact brew and its bonus. */
final class LegendsPage extends Page {

    private static final int[] SLOTS = {19, 21, 23, 25};

    LegendsPage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Tavern Legends");
    }

    @Override
    protected void render() {
        List<Component> about = new ArrayList<>(wrap("One per colour. Brew it exactly for a bonus no extra gives.",
                NamedTextColor.GRAY));
        set(4, glint(model("mug", "copper", Component.text("Tavern Legends", Items.LEGEND_COLOR), about)));
        Legend[] legends = Legend.values();
        for (int i = 0; i < legends.length; i++) {
            set(SLOTS[i], legend(legends[i]));
        }
        backButton();
    }

    private ItemStack legend(Legend legend) {
        List<Component> lore = new ArrayList<>();
        lore.add(field("Colour", legend.colour().displayName(), TextColor.color(legend.colour().color())));
        lore.add(blank());
        if (!journal.has(player, legend)) {
            lore.addAll(wrap("\"" + legend.hint() + "\"", NamedTextColor.GRAY));
            return model("mug", null, Component.text("✦ ???", NamedTextColor.DARK_GRAY), lore);
        }
        lore.addAll(wrap(legend.recipe(), NamedTextColor.WHITE));
        lore.add(blank());
        lore.add(line("Bonus:", NamedTextColor.GRAY));
        lore.add(line(" ✦ " + legend.bonusText(), Items.LEGEND_COLOR));
        Style style = switch (legend) {
            case MINERS_STOUT -> Style.STOUT;
            case FESTBIER -> Style.MARZEN;
            case GLACIER_PILS -> Style.PILSNER;
            case BEES_KNEES -> Style.WHEAT_BEER;
        };
        return glint(model("mug", style.look(), Component.text("✦ " + legend.displayName(), Items.LEGEND_COLOR), lore));
    }
}
