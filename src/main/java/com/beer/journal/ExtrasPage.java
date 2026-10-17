package com.beer.journal;

import com.beer.brew.Extra;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every extra as its real item; what it does shows once the player has put it in a kettle. */
final class ExtrasPage extends Page {

    private static final int[] SLOTS = {20, 22, 24, 30, 32};

    ExtrasPage(Journal journal, Player player) {
        super(journal, player, "Beer Journal › Extras");
    }

    @Override
    protected void render() {
        List<Component> about = new ArrayList<>(wrap("One extra per batch, in the kettle. It names the beer: Honey IPA, "
                + "Chocolate Stout.", NamedTextColor.GRAY));
        set(4, model("brew_kettle", "wort_copper", Component.text("Extras", NamedTextColor.GOLD), about));
        Extra[] extras = Extra.values();
        for (int i = 0; i < extras.length; i++) {
            set(SLOTS[i], extra(extras[i]));
        }
        backButton();
    }

    private ItemStack extra(Extra extra) {
        boolean known = journal.has(player, extra);
        List<Component> lore = new ArrayList<>();
        if (known) {
            lore.add(field("Name", extra.prefix() + " …", NamedTextColor.WHITE));
            lore.addAll(wrap(extra.describe(), NamedTextColor.GRAY));
        } else {
            lore.add(field("Effect", "???", NamedTextColor.DARK_GRAY));
            lore.addAll(wrap("Put it in a kettle to find out.", NamedTextColor.GRAY));
        }
        Component name = Component.text(extra.displayName(), known ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        return icon(Material.valueOf(extra.material()), name, lore);
    }
}
