package io.github.addoncommunity.galactifun.core.commands;

import io.github.addoncommunity.galactifun.util.Messages;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import io.github.addoncommunity.galactifun.Galactifun;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.util.Util;
import io.github.mooy1.infinitylib.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;

public final class SealedCommand extends SubCommand {

    public SealedCommand() {
        super("sealed", "Checks if the area is sealed", false);
    }

    @Override
    public void execute(@Nonnull CommandSender commandSender, @Nonnull String[] strings) {
        if (!(commandSender instanceof Player p)) return;
        if (strings.length != 1) {
            Messages.red(p, "Usage: /galactifun sealed <range>");
            return;
        }

        int range;

        try {
            range = Integer.parseInt(strings[0]);
        } catch (NumberFormatException e) {
            Messages.red(p, "Range must be an integer between 1 and " + Galactifun.instance().getConfig().getInt("other.sealed-command-max-range"));
            return;
        }

        if (range < 1 || range > Galactifun.instance().getConfig().getInt("other.sealed-command-max-range")) {
            Messages.red(p, "Range must be an integer between 1 and " + Galactifun.instance().getConfig().getInt("other.sealed-command-max-range"));
            return;
        }

        double time = System.nanoTime();
        Optional<Set<BlockPosition>> filled = Util.floodFill(p.getLocation(), range);

        if (filled.isPresent()) {
            p.sendMessage("Sealed");
        } else {
            p.sendMessage("Not sealed");
        }

        p.sendMessage(Util.timeSince(time));
    }

    @Override
    public void complete(@Nonnull CommandSender commandSender, @Nonnull String[] strings, @Nonnull List<String> list) {

    }

}
