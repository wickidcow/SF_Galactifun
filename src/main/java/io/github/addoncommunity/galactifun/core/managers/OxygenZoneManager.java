package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.AtmosphericEffect;

/**
 * Persistent administrator-defined oxygen/full-atmosphere regions.
 */
public final class OxygenZoneManager {

    public enum Mode {
        OXYGEN,
        FULL
    }

    public record Zone(
            @Nonnull String name,
            @Nonnull UUID worldId,
            @Nonnull String worldName,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            @Nonnull Mode mode
    ) {
        public boolean contains(@Nonnull Location location) {
            World world = location.getWorld();
            return world != null
                    && world.getUID().equals(this.worldId)
                    && location.getBlockX() >= this.minX
                    && location.getBlockX() <= this.maxX
                    && location.getBlockY() >= this.minY
                    && location.getBlockY() <= this.maxY
                    && location.getBlockZ() >= this.minZ
                    && location.getBlockZ() <= this.maxZ;
        }

        public int volume() {
            long volume = (long) (this.maxX - this.minX + 1)
                    * (this.maxY - this.minY + 1)
                    * (this.maxZ - this.minZ + 1);
            return (int) Math.min(Integer.MAX_VALUE, volume);
        }
    }

    private final Galactifun plugin;
    private final File file;
    private final YamlConfiguration storage = new YamlConfiguration();
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public OxygenZoneManager(@Nonnull Galactifun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "oxygen-zones.yml");
        load();
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }

        try {
            this.storage.load(this.file);
            ConfigurationSection root = this.storage.getConfigurationSection("zones");
            if (root == null) {
                return;
            }

            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                try {
                    UUID worldId = UUID.fromString(section.getString("world-id", ""));
                    String worldName = section.getString("world-name", "unknown");
                    Mode mode = Mode.valueOf(section.getString("mode", Mode.OXYGEN.name()).toUpperCase(Locale.ROOT));
                    Zone zone = new Zone(
                            key,
                            worldId,
                            worldName,
                            section.getInt("min-x"),
                            section.getInt("min-y"),
                            section.getInt("min-z"),
                            section.getInt("max-x"),
                            section.getInt("max-y"),
                            section.getInt("max-z"),
                            mode
                    );
                    this.zones.put(key, zone);
                } catch (IllegalArgumentException exception) {
                    this.plugin.getLogger().warning("Ignoring invalid atmosphere zone '" + key + "'");
                }
            }
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not load oxygen-zones.yml", exception);
        }
    }

    private void save() {
        this.storage.set("zones", null);
        for (Zone zone : this.zones.values()) {
            String path = "zones." + zone.name();
            this.storage.set(path + ".world-id", zone.worldId().toString());
            this.storage.set(path + ".world-name", zone.worldName());
            this.storage.set(path + ".min-x", zone.minX());
            this.storage.set(path + ".min-y", zone.minY());
            this.storage.set(path + ".min-z", zone.minZ());
            this.storage.set(path + ".max-x", zone.maxX());
            this.storage.set(path + ".max-y", zone.maxY());
            this.storage.set(path + ".max-z", zone.maxZ());
            this.storage.set(path + ".mode", zone.mode().name());
        }

        try {
            this.storage.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save oxygen-zones.yml", exception);
        }
    }

    public boolean create(@Nonnull String rawName, @Nonnull Location a, @Nonnull Location b, @Nonnull Mode mode) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return false;
        }

        String name = normalizeName(rawName);
        if (name == null || this.zones.containsKey(name)) {
            return false;
        }

        World world = a.getWorld();
        Zone zone = new Zone(
                name,
                world.getUID(),
                world.getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()),
                mode
        );
        this.zones.put(name, zone);
        save();
        return true;
    }

    public boolean delete(@Nonnull String rawName) {
        String name = normalizeName(rawName);
        if (name == null || this.zones.remove(name) == null) {
            return false;
        }
        save();
        return true;
    }

    @Nullable
    public Zone zoneAt(@Nonnull Location location) {
        for (Zone zone : this.zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    public boolean providesOxygen(@Nonnull Location location) {
        return zoneAt(location) != null;
    }

    public int protectionAt(@Nonnull Location location, @Nonnull AtmosphericEffect effect) {
        Zone zone = zoneAt(location);
        return zone != null && zone.mode() == Mode.FULL ? Integer.MAX_VALUE : 0;
    }

    @Nonnull
    public Collection<Zone> zones() {
        return Collections.unmodifiableCollection(new ArrayList<>(this.zones.values()));
    }

    @Nullable
    public static String normalizeName(@Nonnull String rawName) {
        String normalized = rawName.toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,32}") ? normalized : null;
    }

    @Nullable
    public World resolveWorld(@Nonnull Zone zone) {
        World world = Bukkit.getWorld(zone.worldId());
        return world != null ? world : Bukkit.getWorld(zone.worldName());
    }

    public void onDisable() {
        save();
    }
}
