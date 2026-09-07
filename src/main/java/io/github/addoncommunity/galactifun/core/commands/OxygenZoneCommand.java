package io.github.addoncommunity.galactifun.core.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.core.managers.OxygenZoneManager;
import io.github.addoncommunity.galactifun.core.managers.OxygenZoneManager.Mode;
import io.github.addoncommunity.galactifun.core.managers.OxygenZoneManager.Zone;
import io.github.addoncommunity.galactifun.util.Messages;
import io.github.mooy1.infinitylib.commands.SubCommand;

public final class OxygenZoneCommand extends SubCommand {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public OxygenZoneCommand() {
        super("oxygenzone", "Creates persistent admin oxygen/atmosphere zones", true);
    }

    @Override
    public void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) {
            return;
        }

        OxygenZoneManager manager = Galactifun.oxygenZoneManager();
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1" -> {
                this.pos1.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
                Messages.green(player, "Atmosphere zone position 1 set.");
            }
            case "pos2" -> {
                this.pos2.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
                Messages.green(player, "Atmosphere zone position 2 set.");
            }
            case "create" -> create(player, manager, args);
            case "delete" -> {
                if (args.length != 2) {
                    Messages.red(player, "Usage: /galactifun oxygenzone delete <name>");
                    return;
                }
                if (manager.delete(args[1])) {
                    Messages.green(player, "Deleted atmosphere zone '" + args[1] + "'.");
                } else {
                    Messages.red(player, "No atmosphere zone exists with that name.");
                }
            }
            case "list" -> {
                if (manager.zones().isEmpty()) {
                    Messages.yellow(player, "No administrator atmosphere zones are configured.");
                    return;
                }
                Messages.yellow(player, "Atmosphere zones:");
                for (Zone zone : manager.zones()) {
                    player.sendMessage("- " + zone.name() + " [" + zone.mode() + "] "
                            + zone.worldName() + " " + zone.minX() + "," + zone.minY() + "," + zone.minZ()
                            + " -> " + zone.maxX() + "," + zone.maxY() + "," + zone.maxZ()
                            + " (" + zone.volume() + " blocks)");
                }
            }
            default -> usage(player);
        }
    }

    private void create(@Nonnull Player player, @Nonnull OxygenZoneManager manager, @Nonnull String[] args) {
        if (args.length < 2 || args.length > 3) {
            Messages.red(player, "Usage: /galactifun oxygenzone create <name> [oxygen|full]");
            return;
        }

        Location first = this.pos1.get(player.getUniqueId());
        Location second = this.pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            Messages.red(player, "Set both pos1 and pos2 first.");
            return;
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            Messages.red(player, "Both positions must be in the same world.");
            return;
        }

        Mode mode = Mode.OXYGEN;
        if (args.length == 3) {
            try {
                mode = Mode.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                Messages.red(player, "Mode must be oxygen or full.");
                return;
            }
        }

        if (OxygenZoneManager.normalizeName(args[1]) == null) {
            Messages.red(player, "Zone names may contain only a-z, 0-9, _ and - (max 32 characters).");
            return;
        }

        if (manager.create(args[1], first, second, mode)) {
            Messages.green(player, "Created '" + args[1] + "' as a " + mode.name().toLowerCase(Locale.ROOT) + " atmosphere zone.");
        } else {
            Messages.red(player, "Could not create that zone. The name may already exist.");
        }
    }

    private static void usage(@Nonnull Player player) {
        Messages.yellow(player, "/galactifun oxygenzone pos1");
        Messages.yellow(player, "/galactifun oxygenzone pos2");
        Messages.yellow(player, "/galactifun oxygenzone create <name> [oxygen|full]");
        Messages.yellow(player, "/galactifun oxygenzone delete <name>");
        Messages.yellow(player, "/galactifun oxygenzone list");
    }

    @Override
    public void complete(@Nonnull CommandSender sender, @Nonnull String[] args, @Nonnull List<String> completions) {
        if (args.length == 1) {
            completions.add("pos1");
            completions.add("pos2");
            completions.add("create");
            completions.add("delete");
            completions.add("list");
        } else if (args.length == 3 && "create".equalsIgnoreCase(args[0])) {
            completions.add("oxygen");
            completions.add("full");
        }
    }
}
