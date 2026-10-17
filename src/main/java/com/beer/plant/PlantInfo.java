package com.beer.plant;

import com.beer.BeerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;

/** The one-line status shown when a player right-clicks a crop: crop, stage, and what helps or stops it. */
final class PlantInfo {

    private PlantInfo() {
    }

    static Component describe(BeerPlugin plugin, Plant plant) {
        Species species = plant.species();
        Block block = plant.block();
        TextComponent.Builder line = Component.text()
                .append(Component.text(species.displayName(), species == Species.HOPS ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(dot())
                .append(Component.text(plant.stageName() + " " + (plant.stage() + 1) + "/" + species.stages(), NamedTextColor.WHITE));
        if (plant.isRipe()) {
            String what = species == Species.HOPS ? "Ripe: right-click to pick the cones" : "Ripe: right-click to harvest";
            return line.append(dot()).append(Component.text(what, NamedTextColor.GREEN)).build();
        }
        int light = GrowthMath.light(block.getLightFromSky(), block.getLightFromBlocks());
        int need = GrowthTask.minLight(plugin.settings());
        line.append(dot()).append(Component.text("Light " + light, light >= need ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (light < need) {
            line.append(dot()).append(Component.text("Too dark: needs " + need, NamedTextColor.RED));
        } else {
            line.append(dot()).append(Component.text("Growing", NamedTextColor.GREEN));
        }
        return line.build();
    }

    private static Component dot() {
        return Component.text(" · ", NamedTextColor.DARK_GRAY);
    }
}
