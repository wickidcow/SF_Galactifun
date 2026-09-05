package io.github.mooy1.infinitylib.commands;

import io.github.addoncommunity.galactifun.util.Messages;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.command.CommandSender;

import io.github.thebusybiscuit.slimefun4.libraries.dough.common.ChatColors;

@ParametersAreNonnullByDefault
final class HelpCommand extends SubCommand {

    private final ParentCommand command;

    HelpCommand(ParentCommand command) {
        super("help", "Displays this");
        this.command = command;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("");
        sender.sendMessage(ChatColors.color("&7----------&b /" + command.fullName() + " Help &7----------"));
        sender.sendMessage("");
        for (SubCommand sub : command.available(sender)) {
            sender.sendMessage(Messages.legacy("/" + sub.fullName() + "&e - " + sub.description()));
        }
        sender.sendMessage("");
    }

    @Override
    public void complete(CommandSender sender, String[] args, List<String> tabs) {

    }

}
