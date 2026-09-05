package io.github.addoncommunity.galactifun.core.commands;

import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.items.knowledge.KnowledgeLevel;
import io.github.addoncommunity.galactifun.core.managers.TravelManager.TravelType;
import io.github.mooy1.infinitylib.commands.SubCommand;
import io.github.mooy1.infinitylib.common.Scheduler;

/**
 * Command to teleport to world spawns.
 */
public final class GalactiportCommand extends SubCommand {

    public GalactiportCommand() {
        super("world", "Teleports you to the spawn of the specified world", true);
    }

    @Override
    public void execute(@Nonnull CommandSender commandSender, @Nonnull String[] strings) {
        if (!(commandSender instanceof Player player) || strings.length != 1) {
            return;
        }

        World world = Bukkit.getWorld(strings[0]);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Invalid World!");
            return;
        }

        if (player.getWorld() != world) {
            Galactifun.travelManager().authorize(player, world, TravelType.GALACTIPORT);
        }

        player.teleportAsync(world.getSpawnLocation()).thenAccept(success -> {
            if (!success) {
                Galactifun.travelManager().clear(player);
                return;
            }

            Scheduler.run(() -> {
                PlanetaryWorld planetaryWorld = Galactifun.worldManager().getWorld(world);
                if (planetaryWorld != null && KnowledgeLevel.get(player, planetaryWorld) == KnowledgeLevel.NONE) {
                    KnowledgeLevel.BASIC.set(player, planetaryWorld);
                }
            });
        });
    }

    @Override
    public void complete(@Nonnull CommandSender commandSender, @Nonnull String[] strings, @Nonnull List<String> worlds) {
        if (strings.length == 1) {
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
        }
    }
}
