package io.github.mooy1.infinitylib.common;

import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.github.mooy1.infinitylib.core.AbstractAddon;

@ParametersAreNonnullByDefault
public final class Events implements Listener {

    private static final Listener LISTENER = new Events();

    private Events() {}

    public static <T extends Event> T call(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, AbstractAddon.addonInstance());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Event> void addHandler(Class<T> eventClass, EventPriority priority,
                                                    boolean ignoreCancelled, Consumer<T> handler) {
        Bukkit.getPluginManager().registerEvent(eventClass, LISTENER, priority,
                (listener, event) -> handler.accept((T) event), AbstractAddon.addonInstance(), ignoreCancelled);
    }
}
