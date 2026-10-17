package com.beer.command;

import com.beer.BeerPlugin;
import com.beer.brew.Beer;
import com.beer.brew.BrewItems;
import com.beer.brew.Colour;
import com.beer.brew.Extra;
import com.beer.brew.Legend;
import com.beer.brew.Style;
import com.beer.brew.Tipsy;
import com.beer.brew.Wood;
import com.beer.brew.Wort;
import com.beer.furniture.FurnitureType;
import com.beer.item.ItemId;
import com.beer.item.Items;
import com.beer.plant.Plant;
import com.beer.plant.PlantManager;
import com.beer.plant.Species;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /beer journal} for everyone; for admins {@code give <item> [amount] [player] [style|legend|wood] | grow [stages]
 * | plant <x> <y> <z> <barley|hops> [stage] | place <furniture> <x> <y> <z> [wood] | put <item> <amount> <x> <y> <z> [spec]
 * | skip [minutes] | tipsy [player] [level] | info | reload}.
 */
public final class BeerCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("journal", "give", "grow", "plant", "place", "put", "skip", "tipsy", "info", "reload");
    private static final String ADMIN = "beer.admin";

    private final BeerPlugin plugin;

    public BeerCommand(BeerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "journal" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("journal")) {
            if (sender instanceof Player player && player.hasPermission("beer.journal")) {
                plugin.journal().open(player);
            } else {
                reply(sender, "Only players can open the Beer Journal.", NamedTextColor.RED);
            }
            return;
        }
        if (!sender.hasPermission(ADMIN)) {
            reply(sender, "Use /beer journal.", NamedTextColor.GRAY);
            return;
        }
        switch (sub) {
            case "give" -> give(sender, args);
            case "grow" -> grow(sender, args);
            case "plant" -> plant(sender, args);
            case "place" -> place(sender, args);
            case "tipsy" -> tipsy(sender, args);
            case "put" -> put(sender, args);
            case "skip" -> skip(sender, args);
            case "info" -> info(sender);
            case "reload" -> {
                plugin.reload();
                reply(sender, "Beer config reloaded.", NamedTextColor.GREEN);
            }
            default -> reply(sender, "Usage: /beer journal | give <item> [amount] [player] [style|legend|wood] | grow [stages] | plant <x> <y> <z> <barley|hops> [stage] | place <furniture> <x> <y> <z> [wood] | put <item> <amount> <x> <y> <z> [spec] | skip [minutes] | tipsy [player] [level] | info | reload",
                    NamedTextColor.GRAY);
        }
    }

    private void give(CommandSender sender, String[] args) {
        Player target = args.length > 3 ? plugin.getServer().getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
        List<ItemStack> stacks = stacks(sender, args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : null,
                args.length > 4 ? args[4] : null);
        if (stacks == null) {
            return;
        }
        if (target == null) {
            reply(sender, "Who should get it? Add a player name.", NamedTextColor.RED);
            return;
        }
        for (ItemStack stack : stacks) {
            target.getInventory().addItem(stack).values()
                    .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        reply(sender, "Gave " + describe(stacks) + " to " + target.getName() + ".", NamedTextColor.GREEN);
    }

    /** {@code put <item> <amount> <x> <y> <z> [spec]}: fills a container block (for tests and admins; works from the console). */
    private void put(CommandSender sender, String[] args) {
        if (args.length < 6) {
            reply(sender, "Usage: /beer put <item> <amount> <x> <y> <z> [style|legend|wood]", NamedTextColor.RED);
            return;
        }
        Block block = block(sender, args, 3);
        if (block == null) {
            return;
        }
        if (!(block.getState(false) instanceof Container container)) {
            reply(sender, "No container at " + block.getX() + " " + block.getY() + " " + block.getZ() + ".", NamedTextColor.RED);
            return;
        }
        List<ItemStack> stacks = stacks(sender, args[1], args[2], args.length > 6 ? args[6] : null);
        if (stacks == null) {
            return;
        }
        int lost = 0;
        for (ItemStack stack : stacks) {
            lost += container.getInventory().addItem(stack).values().stream().mapToInt(ItemStack::getAmount).sum();
        }
        reply(sender, "Put " + describe(stacks) + " in the " + block.getType().getKey().getKey()
                + (lost > 0 ? " (" + lost + " did not fit)" : "") + ".", NamedTextColor.GREEN);
    }

    /** The item stacks {@code give} and {@code put} hand out, or null after telling the sender what is wrong. */
    private List<ItemStack> stacks(CommandSender sender, String item, String count, String spec) {
        ItemId id = ItemId.byId(item.toLowerCase(Locale.ROOT));
        if (id == null) {
            reply(sender, "Items: " + String.join(", ", itemIds()), NamedTextColor.RED);
            return null;
        }
        int amount = 1;
        if (count != null) {
            try {
                amount = Math.clamp(Integer.parseInt(count), 1, 64 * 36);
            } catch (NumberFormatException e) {
                reply(sender, "Not a number: " + count, NamedTextColor.RED);
                return null;
            }
        }
        ItemStack one = id.kind() == ItemId.Kind.BREW || id == ItemId.FERMENTING_BARREL ? brew(sender, id, spec) : Items.create(id, 1);
        if (one == null) {
            return null;
        }
        List<ItemStack> out = new ArrayList<>();
        int max = one.getMaxStackSize();
        for (int left = amount; left > 0; left -= max) {
            out.add(one.asQuantity(Math.min(left, max)));
        }
        return out;
    }

    /** "12 Barley" */
    private static String describe(List<ItemStack> stacks) {
        int total = stacks.stream().mapToInt(ItemStack::getAmount).sum();
        return total + " " + PlainTextComponentSerializer.plainText().serialize(stacks.getFirst().effectiveName());
    }

    /** A beer (by style or legend), a wort (by the style it makes) or a barrel (by wood); null after an error. */
    private static ItemStack brew(CommandSender sender, ItemId id, String spec) {
        if (id == ItemId.FERMENTING_BARREL) {
            return BrewItems.barrel(Wood.byId(spec == null ? "pale" : spec.toLowerCase(Locale.ROOT)), 1);
        }
        Style style = spec == null ? Style.PALE_ALE : Style.byId(spec);
        Legend legend = spec == null ? null : Legend.byId(spec);
        if (style == null && legend == null) {
            reply(sender, "Unknown style or legend " + spec + ".", NamedTextColor.RED);
            return null;
        }
        if (id == ItemId.WORT_BUCKET) {
            Colour colour = style != null ? style.colour() : legend.colour();
            return BrewItems.wort(switch (colour) {
                case WHEAT -> new Wort(4, 0, 0, 1, null);
                case PALE -> new Wort(0, 4, 0, 1, null);
                case AMBER -> new Wort(0, 3, 1, 1, null);
                case DARK -> new Wort(0, 2, 2, 1, null);
            });
        }
        if (legend == null) {
            return BrewItems.beer(Beer.plain(style), 3, 1);
        }
        Beer beer = switch (legend) {
            case MINERS_STOUT -> new Beer(Style.STOUT, true, Extra.COCOA, legend);
            case FESTBIER -> new Beer(Style.MARZEN, false, Extra.PUMPKIN, legend);
            case GLACIER_PILS -> new Beer(Style.PILSNER, true, null, legend);
            case BEES_KNEES -> new Beer(Style.WHEAT_BEER, false, Extra.HONEY, legend);
        };
        return BrewItems.beer(beer, legend == Legend.FESTBIER ? 5 : 3, 1);
    }

    /**
     * {@code skip [minutes]} looking at a kettle or barrel, or {@code skip <x> <y> <z> [minutes]}: finishes the boil, or
     * makes the beer ready (and that many minutes older, for more stars).
     */
    private void skip(CommandSender sender, String[] args) {
        Block block;
        int minutesAt;
        if (args.length >= 4) {
            block = block(sender, args, 1);
            if (block == null) {
                return;
            }
            minutesAt = 4;
        } else if (sender instanceof Player player) {
            block = player.getTargetBlockExact(6);
            minutesAt = 1;
        } else {
            reply(sender, "Usage: /beer skip <x> <y> <z> [minutes]", NamedTextColor.RED);
            return;
        }
        long ageMs = 0;
        if (args.length > minutesAt) {
            try {
                ageMs = Math.round(Double.parseDouble(args[minutesAt]) * 60_000);
            } catch (NumberFormatException e) {
                reply(sender, "Not a number: " + args[minutesAt], NamedTextColor.RED);
                return;
            }
        }
        var furniture = block == null ? null : plugin.furniture().at(block);
        if (furniture == null) {
            reply(sender, "Look at a Brew Kettle or a Fermenting Barrel (or give its coordinates).", NamedTextColor.RED);
            return;
        }
        reply(sender, plugin.furniture().skip(furniture, ageMs), NamedTextColor.GREEN);
    }

    /** {@code tipsy [player] [level]}: shows or sets a player's Tipsy level. */
    private void tipsy(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? plugin.getServer().getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            reply(sender, "Who? Add a player name.", NamedTextColor.RED);
            return;
        }
        if (args.length > 2) {
            try {
                plugin.brewing().setLevel(target, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                reply(sender, "Not a number: " + args[2], NamedTextColor.RED);
                return;
            }
        }
        int level = plugin.brewing().tipsy(target).level();
        reply(sender, target.getName() + " is at Tipsy " + level + " (" + Tipsy.tier(level).displayName() + ").", NamedTextColor.GRAY);
    }

    private void grow(CommandSender sender, String[] args) {
        Plant plant = lookedAt(sender);
        if (plant == null) {
            return;
        }
        int steps = 1;
        if (args.length > 1) {
            try {
                steps = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                reply(sender, "Not a number: " + args[1], NamedTextColor.RED);
                return;
            }
        }
        plugin.plants().setStage(plant, plant.stage() + steps);
        reply(sender, plant.species().displayName() + " is now " + plant.stageId() + ".", NamedTextColor.GREEN);
    }

    /** Plants barley or hops at a position (for testing and admins; works from the console in the first world). */
    private void plant(CommandSender sender, String[] args) {
        Species species = args.length > 4 ? Species.byId(args[4].toLowerCase(Locale.ROOT)) : null;
        if (args.length < 5 || species == null) {
            reply(sender, "Usage: /beer plant <x> <y> <z> <barley|hops> [stage]", NamedTextColor.RED);
            return;
        }
        Block block = block(sender, args, 1);
        if (block == null) {
            return;
        }
        if (!plugin.plants().canPlant(block, species)) {
            reply(sender, "Cannot plant there: it needs farmland below and the blocks above it free.", NamedTextColor.RED);
            return;
        }
        int stage = args.length > 5 ? species.stageOf(args[5]) : 0;
        if (stage < 0) {
            reply(sender, "Unknown stage " + args[5] + ".", NamedTextColor.RED);
            return;
        }
        Plant plant = plugin.plants().plant(block, species);
        plugin.plants().setStage(plant, stage);
        reply(sender, "Planted " + species.id() + " at " + block.getX() + " " + block.getY() + " " + block.getZ() + ".", NamedTextColor.GREEN);
    }

    /** Places furniture at a position without build checks (for admins and tests; works from the console). */
    private void place(CommandSender sender, String[] args) {
        FurnitureType type = args.length > 1 ? FurnitureType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
        if (type == null || args.length < 5) {
            reply(sender, "Usage: /beer place <" + String.join("|", Arrays.stream(FurnitureType.values()).map(FurnitureType::id).toList())
                    + "> <x> <y> <z> [pale|dark]", NamedTextColor.RED);
            return;
        }
        Block block = block(sender, args, 2);
        if (block == null) {
            return;
        }
        if (!block.isEmpty()) {
            reply(sender, "That block is not empty.", NamedTextColor.RED);
            return;
        }
        ItemStack source = type == FurnitureType.FERMENTING_BARREL && args.length > 5
                ? BrewItems.barrel(Wood.byId(args[5].toLowerCase(Locale.ROOT)), 1) : null;
        plugin.furniture().place(block, type, sender instanceof Player player ? player.getYaw() : 180f, source);
        reply(sender, "Placed " + type.id() + " at " + block.getX() + " " + block.getY() + " " + block.getZ() + ".", NamedTextColor.GREEN);
    }

    private void info(CommandSender sender) {
        int barley = 0;
        int barleyRipe = 0;
        int hops = 0;
        int hopsRipe = 0;
        for (Plant plant : plugin.plants().all()) {
            if (plant.species() == Species.BARLEY) {
                barley++;
                barleyRipe += plant.isRipe() ? 1 : 0;
            } else {
                hops++;
                hopsRipe += plant.isRipe() ? 1 : 0;
            }
        }
        reply(sender, plugin.plants().count() + " Beer crops loaded: barley=" + barley + " barley_ripe=" + barleyRipe
                + " hops=" + hops + " hops_ripe=" + hopsRipe + " tables=" + plugin.tables().count(), NamedTextColor.GRAY);
        if (sender instanceof Player player) {
            Plant plant = plugin.plants().byEntity(target(player));
            if (plant != null) {
                reply(sender, "Looking at: " + plant.species().id() + " " + plant.stageId() + " at "
                        + plant.x() + " " + plant.y() + " " + plant.z() + ", light "
                        + Math.max(plant.block().getLightFromSky(), plant.block().getLightFromBlocks()), NamedTextColor.GRAY);
            }
        }
    }

    /** The block at {@code args[from..from+2]} in the sender's world (the first world from the console), or null after an error. */
    private Block block(CommandSender sender, String[] args, int from) {
        World world = sender instanceof Player player ? player.getWorld() : plugin.getServer().getWorlds().getFirst();
        try {
            return world.getBlockAt(Integer.parseInt(args[from]), Integer.parseInt(args[from + 1]), Integer.parseInt(args[from + 2]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            reply(sender, "Coordinates must be whole numbers.", NamedTextColor.RED);
            return null;
        }
    }

    private Plant lookedAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            reply(sender, "Only players can use this (look at a crop).", NamedTextColor.RED);
            return null;
        }
        Plant plant = plugin.plants().byEntity(target(player));
        if (plant == null) {
            reply(sender, "Look at barley or hops within 6 blocks.", NamedTextColor.RED);
        }
        return plant;
    }

    private Entity target(Player player) {
        PlantManager plants = plugin.plants();
        RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                6.0, 0.0, entity -> plants.byEntity(entity) != null);
        return hit == null || hit.getHitEntity() == null ? player : hit.getHitEntity();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(ADMIN)) {
            return args.length <= 1 ? filter(List.of("journal"), args.length == 0 ? "" : args[0]) : List.of();
        }
        if (args.length <= 1) {
            return filter(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args[0].equalsIgnoreCase("put") && args.length == 2) {
            return filter(itemIds(), args[1]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(itemIds(), args[1]);
            }
            if (args.length == 4) {
                return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
            }
            if (args.length == 5) {
                ItemId id = ItemId.byId(args[1]);
                List<String> options = new ArrayList<>();
                if (id == ItemId.BEER || id == ItemId.WORT_BUCKET) {
                    Arrays.stream(Style.values()).map(Style::id).forEach(options::add);
                    if (id == ItemId.BEER) {
                        Arrays.stream(Legend.values()).map(Legend::id).forEach(options::add);
                    }
                } else if (id == ItemId.FERMENTING_BARREL) {
                    Arrays.stream(Wood.values()).map(Wood::id).forEach(options::add);
                }
                return filter(options, args[4]);
            }
        }
        if (args[0].equalsIgnoreCase("place") && args.length == 2) {
            return filter(Arrays.stream(FurnitureType.values()).map(FurnitureType::id).toList(), args[1]);
        }
        if (args[0].equalsIgnoreCase("place") && args.length == 6 && args[1].equalsIgnoreCase("fermenting_barrel")) {
            return filter(Arrays.stream(Wood.values()).map(Wood::id).toList(), args[5]);
        }
        if (args[0].equalsIgnoreCase("tipsy") && args.length == 2) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args[0].equalsIgnoreCase("plant") && args.length == 5) {
            return filter(Arrays.stream(Species.values()).map(Species::id).toList(), args[4]);
        }
        if (args[0].equalsIgnoreCase("plant") && args.length == 6) {
            Species species = Species.byId(args[4].toLowerCase(Locale.ROOT));
            List<String> stages = new ArrayList<>();
            for (int i = 0; species != null && i < species.stages(); i++) {
                stages.add(species.stageId(i));
            }
            return filter(stages, args[5]);
        }
        return List.of();
    }

    private static List<String> itemIds() {
        return Arrays.stream(ItemId.values()).map(ItemId::id).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }

    private static void reply(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }
}
