package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.events.PlanetFirstVisitEvent;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.util.Messages;
import io.github.mooy1.infinitylib.common.Events;

/**
 * Persistent per-player planet discovery state and first-visit hooks.
 */
public final class DiscoveryManager implements Listener {

    private final Galactifun plugin;
    private final File file;
    private final YamlConfiguration storage = new YamlConfiguration();
    private final Map<UUID, Set<String>> discoveries = new HashMap<>();
    private final boolean enabled;

    public DiscoveryManager(@Nonnull Galactifun plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discovery.enabled", true);
        this.file = new File(plugin.getDataFolder(), "discoveries.yml");
        load();
        Events.registerListener(this);
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }

        try {
            this.storage.load(this.file);
            for (String uuidText : this.storage.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    this.discoveries.put(uuid, new HashSet<>(this.storage.getStringList(uuidText)));
                } catch (IllegalArgumentException ignored) {
                    this.plugin.getLogger().warning("Ignoring invalid UUID in discoveries.yml: " + uuidText);
                }
            }
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not load discoveries.yml", exception);
        }
    }

    private void save() {
        try {
            for (Map.Entry<UUID, Set<String>> entry : this.discoveries.entrySet()) {
                List<String> worlds = new ArrayList<>(entry.getValue());
                Collections.sort(worlds);
                this.storage.set(entry.getKey().toString(), worlds);
            }
            this.storage.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save discoveries.yml", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onJoin(@Nonnull PlayerJoinEvent event) {
        recordCurrentWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onWorldChange(@Nonnull PlayerChangedWorldEvent event) {
        recordCurrentWorld(event.getPlayer());
    }

    private void recordCurrentWorld(@Nonnull Player player) {
        if (!this.enabled) {
            return;
        }

        PlanetaryWorld world = Galactifun.worldManager().getWorld(player.getWorld());
        if (world != null) {
            recordVisit(player, world);
        }
    }

    public boolean recordVisit(@Nonnull Player player, @Nonnull PlanetaryWorld world) {
        if (!this.enabled) {
            return false;
        }

        Set<String> visited = this.discoveries.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (!visited.add(world.id())) {
            return false;
        }

        save();
        Bukkit.getPluginManager().callEvent(new PlanetFirstVisitEvent(player, world));

        if (this.plugin.getConfig().getBoolean("discovery.first-visit.show-message", true)) {
            Messages.green(player, "First visit recorded: " + world.name());
        }

        runCommands(player, world, this.plugin.getConfig().getStringList("discovery.first-visit.global-commands"));
        runCommands(player, world, this.plugin.getConfig().getStringList("discovery.first-visit.commands." + world.id()));
        return true;
    }

    private void runCommands(@Nonnull Player player, @Nonnull PlanetaryWorld world, @Nonnull List<String> commands) {
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String rendered = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{planet}", world.name())
                    .replace("{planet_id}", world.id());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean hasDiscovered(@Nonnull UUID player, @Nonnull PlanetaryWorld world) {
        return this.discoveries.getOrDefault(player, Collections.emptySet()).contains(world.id());
    }

    public boolean hasDiscovered(@Nonnull Player player, @Nonnull PlanetaryWorld world) {
        return hasDiscovered(player.getUniqueId(), world);
    }

    @Nonnull
    public Set<String> discoveries(@Nonnull UUID player) {
        return Collections.unmodifiableSet(this.discoveries.getOrDefault(player, Collections.emptySet()));
    }

    public void onDisable() {
        save();
    }
}
