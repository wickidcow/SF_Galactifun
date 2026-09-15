package io.github.addoncommunity.galactifun.base.universe.earth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.StarSystem;
import io.github.addoncommunity.galactifun.api.universe.attributes.DayCycle;
import io.github.addoncommunity.galactifun.api.universe.attributes.Gravity;
import io.github.addoncommunity.galactifun.api.universe.attributes.Orbit;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Atmosphere;
import io.github.addoncommunity.galactifun.api.universe.types.PlanetaryType;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;

/**
 * A class to connect the server's survival world into the API.
 *
 * @author Mooy1
 */
public final class Earth extends PlanetaryWorld {

    private static final String AUTO_WORLD = "auto";
    private static final String GALACTIFUN_WORLD_PREFIX = "world_galactifun_";

    public Earth(String name, PlanetaryType type, Orbit orbit, StarSystem orbiting, ItemStack baseItem,
                 DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    @Nonnull
    @Override
    public World loadWorld() {
        Galactifun plugin = Galactifun.instance();
        String configuredName = plugin.getConfig().getString("worlds.earth-name", AUTO_WORLD);
        configuredName = configuredName == null ? AUTO_WORLD : configuredName.trim();

        if (configuredName.isEmpty() || AUTO_WORLD.equalsIgnoreCase(configuredName)) {
            World detectedWorld = detectEarthWorld();
            if (detectedWorld == null) {
                throw new IllegalStateException(
                        "Galactifun could not auto-detect a loaded NORMAL world to use as Earth. "
                                + "Set worlds.earth-name in plugins/Galactifun/config.yml."
                );
            }

            plugin.getConfig().set("worlds.earth-name", detectedWorld.getName());
            plugin.saveConfig();
            plugin.getLogger().info("Auto-detected Earth/survival world '" + detectedWorld.getName()
                    + "' and saved it to worlds.earth-name.");
            return detectedWorld;
        }

        World loadedWorld = Bukkit.getWorld(configuredName);
        if (loadedWorld != null) {
            return loadedWorld;
        }

        World world = new WorldCreator(Objects.requireNonNull(configuredName)).createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to load configured Earth world '" + configuredName + "'.");
        }
        return world;
    }

    @Nullable
    private static World detectEarthWorld() {
        String primaryLevelName = readPrimaryLevelName();
        if (primaryLevelName != null) {
            World primaryWorld = Bukkit.getWorld(primaryLevelName);
            if (isEarthCandidate(primaryWorld)) {
                return primaryWorld;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            if (isEarthCandidate(world)) {
                return world;
            }
        }
        return null;
    }

    private static boolean isEarthCandidate(@Nullable World world) {
        return world != null
                && world.getEnvironment() == World.Environment.NORMAL
                && !world.getName().startsWith(GALACTIFUN_WORLD_PREFIX);
    }

    @Nullable
    private static String readPrimaryLevelName() {
        Path serverProperties = Path.of("server.properties");
        if (!Files.isRegularFile(serverProperties)) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(serverProperties)) {
            properties.load(input);
        } catch (IOException ignored) {
            return null;
        }

        String levelName = properties.getProperty("level-name");
        if (levelName == null || levelName.isBlank()) {
            return null;
        }
        return levelName.trim();
    }
}
