package io.github.addoncommunity.galactifun.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Adventure-native text helpers for Galactifun.
 *
 * <p>The addon historically stores many item labels using ampersand color codes. Those strings are
 * decoded only at the compatibility boundary; all runtime messages and item metadata use Adventure
 * {@link Component Components} on modern Paper.
 */
public final class Messages {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Messages() {}

    public static Component legacy(String text) {
        return AMPERSAND.deserialize(text);
    }

    public static Component legacySection(String text) {
        return SECTION.deserialize(text);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static void red(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    public static void yellow(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    public static void green(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    public static void gold(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GOLD));
    }
}
