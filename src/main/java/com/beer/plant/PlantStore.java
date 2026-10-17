package com.beer.plant;

import com.beer.Keys;
import org.bukkit.Chunk;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Reads and writes the crops (barley, hops) of one chunk in the chunk's PersistentDataContainer (saved with the chunk). */
final class PlantStore {

    record Stored(int x, int y, int z, Species species, int stage) {
    }

    private PlantStore() {
    }

    static List<Stored> read(Chunk chunk) {
        List<PersistentDataContainer> list = chunk.getPersistentDataContainer()
                .get(Keys.PLANTS, PersistentDataType.LIST.dataContainers());
        List<Stored> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (PersistentDataContainer entry : list) {
            Integer x = entry.get(Keys.X, PersistentDataType.INTEGER);
            Integer y = entry.get(Keys.Y, PersistentDataType.INTEGER);
            Integer z = entry.get(Keys.Z, PersistentDataType.INTEGER);
            Species species = Species.byId(entry.get(Keys.SPECIES, PersistentDataType.STRING));
            if (x == null || y == null || z == null || species == null) {
                continue;
            }
            out.add(new Stored(x, y, z, species, entry.getOrDefault(Keys.STAGE, PersistentDataType.INTEGER, 0)));
        }
        return out;
    }

    static void write(Chunk chunk, Collection<Plant> plants) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (plants.isEmpty()) {
            pdc.remove(Keys.PLANTS);
            return;
        }
        List<PersistentDataContainer> list = new ArrayList<>(plants.size());
        for (Plant plant : plants) {
            PersistentDataContainer entry = pdc.getAdapterContext().newPersistentDataContainer();
            entry.set(Keys.X, PersistentDataType.INTEGER, plant.x());
            entry.set(Keys.Y, PersistentDataType.INTEGER, plant.y());
            entry.set(Keys.Z, PersistentDataType.INTEGER, plant.z());
            entry.set(Keys.STAGE, PersistentDataType.INTEGER, plant.stage());
            entry.set(Keys.SPECIES, PersistentDataType.STRING, plant.species().id());
            list.add(entry);
        }
        pdc.set(Keys.PLANTS, PersistentDataType.LIST.dataContainers(), list);
    }
}
