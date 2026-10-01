package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.util.SFStorage;

/**
 * Persistent address directory for Stargates.
 *
 * <p>Historically Galactifun searched only Slimefun's loaded chunk data, which meant a valid Stargate
 * became impossible to target whenever its chunk unloaded. This registry stores the controller location
 * in {@code plugins/Galactifun/stargates.yml}; the real block is still revalidated before every teleport.</p>
 */
public final class StargateRegistry {

    private static final Object LOCK = new Object();
    private static final String ROOT = "gates.";

    private static File file;
    private static YamlConfiguration config;
    private static boolean dirty;

    private StargateRegistry() {
    }

    /** Validate persisted addresses before registering gameplay handlers or creating worlds. */
    public static void initialize() {
        synchronized (LOCK) {
            ensureLoaded();
        }
    }

    /** Retry a failed save at normal shutdown, then release only this lifecycle's cached directory. */
    public static void shutdown() {
        synchronized (LOCK) {
            if (config != null && dirty) {
                save();
            }
            config = null;
            file = null;
            dirty = false;
        }
    }

    @Nonnull
    public static String addressFor(@Nonnull String worldName, int x, int y, int z) {
        String locationString = String.format("%s-%d-%d-%d", worldName, x, y, z);
        return Integer.toHexString(locationString.hashCode());
    }

    @Nonnull
    public static String register(@Nonnull Block controller) {
        String address = SFStorage.getData(controller.getLocation(), "gfsgAddress");
        if (address == null || address.isBlank()) {
            Location location = controller.getLocation();
            address = addressFor(
                    controller.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
            SFStorage.setData(controller, "gfsgAddress", address);
        }

        register(address, controller.getLocation());
        return address;
    }

    public static void register(@Nonnull String address, @Nonnull Location location) {
        synchronized (LOCK) {
            ensureLoaded();
            String path = ROOT + address;
            String worldName = location.getWorld().getName();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            boolean changed = !worldName.equals(config.getString(path + ".world"))
                    || x != config.getInt(path + ".x")
                    || y != config.getInt(path + ".y")
                    || z != config.getInt(path + ".z");
            if (!changed) {
                if (dirty) {
                    save();
                }
                return;
            }

            config.set(path + ".world", worldName);
            config.set(path + ".x", x);
            config.set(path + ".y", y);
            config.set(path + ".z", z);
            dirty = true;
            save();
        }
    }

    @Nonnull
    public static Optional<Location> resolve(@Nonnull String address) {
        synchronized (LOCK) {
            ensureLoaded();
            String path = ROOT + address;
            String worldName = config.getString(path + ".world");
            if (worldName == null) {
                return Optional.empty();
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return Optional.empty();
            }

            return Optional.of(new Location(
                    world,
                    config.getInt(path + ".x"),
                    config.getInt(path + ".y"),
                    config.getInt(path + ".z")
            ));
        }
    }

    public static void unregister(@Nonnull Block controller) {
        String address = SFStorage.getData(controller.getLocation(), "gfsgAddress");
        if (address != null && !address.isBlank()) {
            unregister(address);
        }
    }

    public static void unregister(@Nonnull String address) {
        synchronized (LOCK) {
            ensureLoaded();
            String path = ROOT + address;
            if (!config.contains(path)) {
                if (dirty) {
                    save();
                }
                return;
            }
            config.set(path, null);
            dirty = true;
            save();
        }
    }

    private static void ensureLoaded() {
        if (config != null) {
            return;
        }

        file = new File(Galactifun.instance().getDataFolder(), "stargates.yml");
        try {
            // Assign the shared cache only after the entire file has parsed successfully.
            config = StargateRegistryFile.load(file.toPath());
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot load Stargate registry; original stargates.yml is retained", exception);
        }
    }

    private static void save() {
        try {
            StargateRegistryFile.save(config, file.toPath());
            dirty = false;
        } catch (IOException | RuntimeException exception) {
            Galactifun.instance().getLogger().log(Level.SEVERE,
                    "Could not save Stargate registry; previous file is retained and changes remain pending", exception);
        }
    }
}
