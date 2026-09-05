package io.github.addoncommunity.galactifun.core.commands;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.core.integrations.IntegrationManager;
import io.github.mooy1.infinitylib.commands.SubCommand;

/**
 * Small runtime health report for server owners.
 */
public final class DoctorCommand extends SubCommand {

    public DoctorCommand() {
        super("doctor", "Shows Galactifun Legacy compatibility diagnostics", true);
    }

    @Override
    public void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        Galactifun plugin = Galactifun.instance();
        IntegrationManager integrations = Galactifun.integrations();
        List<String> warnings = new ArrayList<>();

        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (slimefun == null || !slimefun.isEnabled()) {
            warnings.add("Slimefun is not enabled.");
        }

        int registeredWorlds = Galactifun.worldManager().spaceWorlds().size();
        int loadedWorlds = 0;
        for (PlanetaryWorld planetaryWorld : Galactifun.worldManager().spaceWorlds()) {
            World world = planetaryWorld.world();
            if (world != null && Bukkit.getWorld(world.getUID()) != null) {
                loadedWorlds++;
            }
        }

        String earthName = plugin.getConfig().getString("worlds.earth-name", "world");
        if (earthName == null || Bukkit.getWorld(earthName) == null) {
            warnings.add("Configured Earth world is not currently loaded: " + earthName);
        }
        if (plugin.getConfig().getStringList("rockets.launch-msgs").size() < 4) {
            warnings.add("rockets.launch-msgs should contain at least four entries.");
        }
        if (integrations.isFolia()) {
            warnings.add("Folia was detected, but Galactifun 1.0 is not release-supported on Folia. "
                    + "Planet world lifecycle and region-thread scheduling still require a dedicated Folia port.");
        }

        sender.sendMessage(ChatColor.GOLD + "----- Galactifun Legacy Doctor -----");
        sender.sendMessage(ChatColor.YELLOW + "Galactifun: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Server: " + ChatColor.WHITE + Bukkit.getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Java: " + ChatColor.WHITE + System.getProperty("java.version"));
        sender.sendMessage(ChatColor.YELLOW + "Slimefun: " + ChatColor.WHITE
                + (slimefun == null ? "not found" : slimefun.getDescription().getVersion()));
        sender.sendMessage(ChatColor.YELLOW + "Planet worlds: " + ChatColor.WHITE
                + loadedWorlds + "/" + registeredWorlds + " loaded");
        sender.sendMessage(ChatColor.YELLOW + "Integrations: " + ChatColor.WHITE
                + "MV-Core=" + yesNo(integrations.isMultiverseCore())
                + ", MV-Inv=" + yesNo(integrations.isMultiverseInventories())
                + ", MV-Portals=" + yesNo(integrations.isMultiversePortals())
                + ", BentoBox=" + yesNo(integrations.isBentoBox())
                + ", Geyser=" + yesNo(integrations.isGeyser())
                + ", Floodgate=" + yesNo(integrations.isFloodgate())
                + ", Folia=" + yesNo(integrations.isFolia()));

        if (integrations.isMultiverseCore()) {
            sender.sendMessage(ChatColor.YELLOW + "Multiverse travel policy: " + ChatColor.WHITE
                    + "planet entry=" + allowed(integrations.allowMultiversePlanetEntry())
                    + ", planet exit=" + allowed(integrations.allowMultiversePlanetExit()));
        }

        if (warnings.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Status: OK - no obvious configuration/runtime problems found.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Status: " + warnings.size() + " warning(s)");
            for (String warning : warnings) {
                sender.sendMessage(ChatColor.YELLOW + " - " + warning);
            }
        }
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String allowed(boolean value) {
        return value ? "allowed" : "blocked";
    }

    @Override
    public void complete(@Nonnull CommandSender sender, @Nonnull String[] args, @Nonnull List<String> completions) {
        // No arguments.
    }
}
