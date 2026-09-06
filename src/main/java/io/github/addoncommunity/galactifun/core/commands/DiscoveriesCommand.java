package io.github.addoncommunity.galactifun.core.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.util.Messages;
import io.github.mooy1.infinitylib.commands.SubCommand;

public final class DiscoveriesCommand extends SubCommand {

    public DiscoveriesCommand() {
        super("discoveries", "Shows the planets you have visited", false);
    }

    @Override
    public void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }

        if (!Galactifun.discoveryManager().isEnabled()) {
            Messages.yellow(player, "Planet discovery tracking is disabled on this server.");
            return;
        }

        List<PlanetaryWorld> worlds = new ArrayList<>(Galactifun.worldManager().spaceWorlds());
        worlds.removeIf(world -> !world.enabled());
        worlds.sort(Comparator.comparing(PlanetaryWorld::name));

        long discovered = worlds.stream()
                .filter(world -> Galactifun.discoveryManager().hasDiscovered(player, world))
                .count();

        Messages.yellow(player, "Planet discoveries: " + discovered + "/" + worlds.size());
        for (PlanetaryWorld world : worlds) {
            boolean visited = Galactifun.discoveryManager().hasDiscovered(player, world);
            player.sendMessage((visited ? "[Visited] " : "[Not visited] ") + world.name());
        }
    }

    @Override
    public void complete(@Nonnull CommandSender sender, @Nonnull String[] args, @Nonnull List<String> completions) {
    }
}
