package com.beer;

import com.beer.brew.BrewListener;
import com.beer.brew.TableMugs;
import com.beer.command.BeerCommand;
import com.beer.furniture.Automation;
import com.beer.furniture.FurnitureListener;
import com.beer.furniture.FurnitureManager;
import com.beer.item.CraftGuard;
import com.beer.item.Recipes;
import com.beer.journal.Journal;
import com.beer.pack.PackHost;
import com.beer.plant.GrowthTask;
import com.beer.plant.PlantListener;
import com.beer.plant.PlantManager;
import com.beer.world.SeedSources;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BeerPlugin extends JavaPlugin {

    private volatile Settings settings;
    private PlantManager plants;
    private FurnitureManager furniture;
    private Recipes recipes;
    private PackHost pack;
    private Journal journal;
    private BrewListener brewing;
    private TableMugs tables;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());
        journal = new Journal(this);
        plants = new PlantManager(this);
        var events = getServer().getPluginManager();
        events.registerEvents(new PlantListener(this, plants), this);
        events.registerEvents(new SeedSources(this), this);
        events.registerEvents(new CraftGuard(), this);
        furniture = new FurnitureManager(this);
        events.registerEvents(new FurnitureListener(this, furniture), this);
        events.registerEvents(journal, this);
        recipes = new Recipes(getServer());
        recipes.register();
        events.registerEvents(recipes, this);
        getServer().getScheduler().runTaskTimer(this, new GrowthTask(this, plants), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, furniture::tick, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, new Automation(this, furniture), 8L, 8L);
        brewing = new BrewListener(this);
        events.registerEvents(brewing, this);
        getServer().getScheduler().runTaskTimer(this, brewing::tick, 20L, 20L);
        tables = new TableMugs(this);
        events.registerEvents(tables, this);
        getServer().getScheduler().runTaskTimer(this, tables::tick, 40L, 40L);
        plants.loadAll();
        furniture.loadAll();
        tables.loadAll();
        pack = new PackHost(this);
        pack.start();
        registerCommand("beer", "Beer journal and admin command", List.of(), new BeerCommand(this));
        getLogger().info("Loaded " + plants.count() + " crops in loaded chunks; registered " + recipes.count() + " recipes.");
    }

    @Override
    public void onDisable() {
        if (pack != null) {
            pack.stop();
        }
        if (plants != null) {
            plants.unloadAll();
        }
        if (tables != null) {
            tables.unloadAll();
        }
        if (recipes != null) {
            recipes.unregister();
        }
    }

    public void reload() {
        reloadConfig();
        settings = Settings.from(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public FurnitureManager furniture() {
        return furniture;
    }

    public PlantManager plants() {
        return plants;
    }

    public Recipes recipes() {
        return recipes;
    }

    public Journal journal() {
        return journal;
    }

    /** Drinking beer and the Tipsy meter. */
    public BrewListener brewing() {
        return brewing;
    }

    public TableMugs tables() {
        return tables;
    }
}
