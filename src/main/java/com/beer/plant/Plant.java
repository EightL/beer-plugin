package com.beer.plant;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

/** One planted Barley or Hops. The chunk's PersistentDataContainer is the source of truth; the entities are only its look. */
public final class Plant {

    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final Species species;
    /** Index into the species' stages. */
    private int stage;
    ItemDisplay display;
    Interaction hitbox;

    Plant(World world, int x, int y, int z, Species species, int stage) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.species = species;
        this.stage = species.clamp(stage);
    }

    public World world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public Species species() {
        return species;
    }

    public int stage() {
        return stage;
    }

    void stage(int stage) {
        this.stage = species.clamp(stage);
    }

    public boolean isRipe() {
        return stage == species.ripe();
    }

    public String stageId() {
        return species.stageId(stage);
    }

    public String stageName() {
        return species.stageName(stage);
    }

    /** The air block the crop occupies (it stands on the farmland below). */
    public Block block() {
        return world.getBlockAt(x, y, z);
    }

    /** Centre of the crop's block, at the farmland surface. */
    public Location base() {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public long blockKey() {
        return blockKey(x, y, z);
    }

    /** Packs a block position into a long, like vanilla's BlockPos#asLong (x and z 26 bits, y 12 bits). */
    public static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    public long chunkKey() {
        return org.bukkit.Chunk.getChunkKey(x >> 4, z >> 4);
    }
}
