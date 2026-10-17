package com.beer.journal;

import com.beer.Settings;
import com.beer.brew.BrewRules;
import com.beer.item.Items;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Five steps from field to mug with the times from the config, the two crops, how stars add up, and hoppers. */
final class BrewGuidePage extends Page {

    BrewGuidePage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Brewer's Guide");
    }

    @Override
    protected void render() {
        Settings b = journal.plugin().settings();
        set(11, step(model("barley", null, null, null), 1, "Grow",
                "Plant Barley or Hops on farmland. Each is its own seed."));
        set(12, step(model("malt", null, null, null), 2, "Smoke",
                "Smoke Barley into Malt, and Malt into Dark Malt. Wheat needs nothing."));
        set(13, step(model("brew_kettle", "wort_gold", null, null), 3, "Boil",
                "Kettle over a fire: Water Bucket, 4 grain, 1-2 Hops, an extra if you like. Boils "
                        + minutes(b.boilMs()) + "; take it out with a Bucket."));
        set(14, step(model("fermenting_barrel", "pale", null, null), 4, "Ferment",
                "Pour the wort into a barrel. Ale in " + minutes(b.aleMs()) + "; beside ice or snow, lager in "
                        + minutes(b.lagerMs()) + "."));
        set(15, step(model("mug", "gold", null, null), 5, "Pour",
                "Right-click the barrel with Empty Mugs. " + b.mugsPerBatch() + " mugs a batch."));

        List<Component> barley = new ArrayList<>(wrap("Grows like wheat. Right-click it ripe to harvest and replant.", NamedTextColor.GRAY));
        barley.add(blank());
        barley.addAll(wrap("Found in grass on plains, savannas and meadows.", NamedTextColor.YELLOW));
        set(29, model("barley", null, Component.text("Barley", NamedTextColor.YELLOW), barley));
        List<Component> hops = new ArrayList<>(wrap("Needs two free blocks above. Right-click it ripe to pick; it grows back.",
                NamedTextColor.GRAY));
        hops.add(blank());
        hops.addAll(wrap("Found in grass in taigas and forests, and from traders.", NamedTextColor.YELLOW));
        set(30, model("hops", null, Component.text("Hops", NamedTextColor.GREEN), hops));

        List<Component> stars = new ArrayList<>();
        stars.add(field("Fresh beer", BrewRules.FRESH_STARS + "★", NamedTextColor.WHITE));
        stars.add(field("Poured after " + minutes(b.agedMs()), "+1★", NamedTextColor.GREEN));
        stars.add(field("Poured after " + minutes(b.longAgedMs()), "+2★", NamedTextColor.GREEN));
        stars.add(field("Pale wood: Wheat and Pale beers", "+1★", NamedTextColor.GREEN));
        stars.add(field("Dark wood: Amber and Dark beers", "+1★", NamedTextColor.GREEN));
        stars.add(blank());
        stars.addAll(wrap("Beer never goes off. More stars, longer effects.", NamedTextColor.YELLOW));
        set(32, icon(Material.NETHER_STAR, Component.text("Stars ★", NamedTextColor.GOLD), stars));

        List<Component> redstone = new ArrayList<>(wrap("Hoppers feed the kettle; a dispenser fills it with water. Its tap "
                + "pours into a barrel in front. A hopper under a barrel fills mugs after " + minutes(b.longAgedMs())
                + ". Dispenser shears harvest the crops.", NamedTextColor.GRAY));
        set(33, icon(Material.HOPPER, Component.text("Redstone & Hoppers", NamedTextColor.RED), redstone));
        backButton();
    }

    private static ItemStack step(ItemStack icon, int number, String title, String text) {
        List<Component> lore = new ArrayList<>(wrap(text, NamedTextColor.GRAY));
        icon.setData(DataComponentTypes.ITEM_NAME,
                Items.plain(number + ". ", NamedTextColor.GOLD).append(Items.plain(title, NamedTextColor.YELLOW)));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return icon;
    }
}
