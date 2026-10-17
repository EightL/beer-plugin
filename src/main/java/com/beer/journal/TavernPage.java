package com.beer.journal;

import com.beer.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Tipsy, sobering up, Cheers and mugs on tables. */
final class TavernPage extends Page {

    TavernPage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Tipsy & Cheers");
    }

    @Override
    protected void render() {
        Settings b = journal.plugin().settings();
        List<Component> tipsy = new ArrayList<>();
        if (b.tipsy()) {
            tipsy.addAll(wrap("Each mug adds 1 (Strong: 2). Drops by 1 every " + minutes(b.tipsyDecayMs()) + ".",
                    NamedTextColor.GRAY));
            tipsy.add(blank());
            tipsy.add(field("1-2", "Merry", NamedTextColor.GREEN));
            tipsy.add(field("3-4", "Tipsy: the world wobbles now and then", NamedTextColor.YELLOW));
            if (b.passOut()) {
                tipsy.add(field("5", "Out cold, then a hangover", NamedTextColor.RED));
            }
        } else {
            tipsy.addAll(wrap("Switched off on this server: beers are only buffs.", NamedTextColor.GRAY));
        }
        set(20, model("mug", "copper", Component.text("Tipsy", NamedTextColor.GOLD), tipsy));

        List<Component> sober = new ArrayList<>(wrap("Bread: -1. Milk Bucket: -2.", NamedTextColor.GRAY));
        if (b.passOut()) {
            sober.add(blank());
            sober.addAll(wrap("Passing out leaves a 2 minute hangover.", NamedTextColor.YELLOW));
        }
        set(22, icon(Material.BREAD, Component.text("Sobering up", NamedTextColor.GOLD), sober));

        List<Component> cheers = new ArrayList<>(wrap("Drink close to a friend at the same time: Luck, and both beers last "
                + "longer.", NamedTextColor.GRAY));
        cheers.add(blank());
        cheers.add(field("Drinking buddies", String.valueOf(journal.count(player, com.beer.Keys.DRINKING_BUDDIES)), NamedTextColor.WHITE));
        set(24, icon(Material.AMETHYST_SHARD, Component.text("Cheers!", NamedTextColor.GOLD), cheers));

        List<Component> table = new ArrayList<>(wrap("Sneak + right-click a block top to put a mug down. Right-click to drink, "
                + "hit to pick up.", NamedTextColor.GRAY));
        set(31, icon(Material.OAK_SLAB, Component.text("Mugs on tables", NamedTextColor.GOLD), table));
        backButton();
    }
}
