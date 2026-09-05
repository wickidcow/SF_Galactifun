package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import io.github.addoncommunity.galactifun.Galactifun;

/**
 * Persistent address index for Stargates.
 *
 * <p>Older Galactifun builds searched Slimefun's internal BlockStorage registry directly. That
 * registry API is no longer exposed by Slimefun Legacy, so Galactifun now owns the small address
 * index it needs. Existing gates are indexed lazily the first time they are opened/activated; their
 * already-stored destination coordinates continue to work without migration.</p>
 */
public final class StargateRegistry {

    private final File file;
    private final YamlConfiguration config = new YamlConfiguration();
    private final Map<String, StoredLocation> addresses = new HashMap<>();

    public StargateRegistry(@Nonnull Galactifun plugin) {
        this.file = new File(plugin.getDataFolder(), "stargates.yml");
        load();
    }

    private void load() {
        if (file.isFile()) {
            try {
                config.load(file);
            } catch (Exception exception) {
                Galactifun.log(Level.SEVERE, "Could not load stargates.yml", exception.toString());
            }
        }

        ConfigurationSection section = config.getConfigurationSection("addresses");
        if (section == null) {
            return;
        }

        for (String address : section.getKeys(false)) {
            String base = "addresses." + address + '.';
            String world = config.getString(base + "world");
            if (world == null) {
                continue;
            }
            addresses.put(normalize(address), new StoredLocation(
                    world,
                    config.getInt(base + "x"),
                    config.getInt(base + "y"),
                    config.getInt(base + "z")
            ));
        }
    }

    public synchronized void register(@Nonnull String address, @Nonnull Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        String key = normalize(address);
        StoredLocation stored = new StoredLocation(
                world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (stored.equals(addresses.get(key))) {
            return;
        }

        addresses.put(key, stored);
        write(key, stored);
        save();
    }

    public synchronized void unregister(@Nonnull String address, @Nonnull Location expectedLocation) {
        String key = normalize(address);
        StoredLocation stored = addresses.get(key);
        if (stored == null || !stored.matches(expectedLocation)) {
            return;
        }

        addresses.remove(key);
        config.set("addresses." + key, null);
        save();
    }

    @Nullable
    public synchronized Location find(@Nonnull String address) {
        StoredLocation stored = addresses.get(normalize(address));
        if (stored == null) {
            return null;
        }

        World world = Bukkit.getWorld(stored.world());
        return world == null ? null : new Location(world, stored.x(), stored.y(), stored.z());
    }

    private void write(String key, StoredLocation stored) {
        String base = "addresses." + key + '.';
        config.set(base + "world", stored.world());
        config.set(base + "x", stored.x());
        config.set(base + "y", stored.y());
        config.set(base + "z", stored.z());
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            Galactifun.log(Level.SEVERE, "Could not save stargates.yml", exception.toString());
        }
    }

    private static String normalize(String address) {
        return address.toLowerCase(Locale.ROOT);
    }

    private record StoredLocation(String world, int x, int y, int z) {
        boolean matches(Location location) {
            return location.getWorld() != null
                    && world.equals(location.getWorld().getName())
                    && x == location.getBlockX()
                    && y == location.getBlockY()
                    && z == location.getBlockZ();
        }
    }
}
