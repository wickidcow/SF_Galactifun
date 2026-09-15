package io.github.addoncommunity.galactifun.base.universe.earth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final String LEGACY_DEFAULT_WORLD = "world";
    private static final String GALACTIFUN_WORLD_PREFIX = "world_galactifun_";
    private static final String EARTH_NAME_PATH = "worlds.earth-name";
    private static final String EARTH_SELECTION_COMPLETE_PATH = "worlds.earth-selection-complete";

    public Earth(String name, PlanetaryType type, Orbit orbit, StarSystem orbiting, ItemStack baseItem,
                 DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    @Nonnull
    @Override
    public World loadWorld() {
        Galactifun plugin = Galactifun.instance();
        String configuredName = plugin.getConfig().getString(EARTH_NAME_PATH, AUTO_WORLD);
        configuredName = configuredName == null ? AUTO_WORLD : configuredName.trim();

        boolean selectionComplete = plugin.getConfig().getBoolean(EARTH_SELECTION_COMPLETE_PATH, false);
        boolean autoRequested = configuredName.isEmpty() || AUTO_WORLD.equalsIgnoreCase(configuredName);
        boolean legacyDefaultNeedsMigration = !selectionComplete
                && LEGACY_DEFAULT_WORLD.equalsIgnoreCase(configuredName);

        if (autoRequested || legacyDefaultNeedsMigration) {
            World detectedWorld = detectEarthWorld();
            if (detectedWorld == null) {
                throw new IllegalStateException(
                        "Galactifun could not safely auto-detect a loaded NORMAL survival world to use as Earth. "
                                + "Set worlds.earth-name in plugins/Galactifun/config.yml."
                );
            }

            saveEarthSelection(plugin, detectedWorld);
            String reason = legacyDefaultNeedsMigration ? "Migrated legacy Earth default to" : "Auto-detected Earth/survival world";
            plugin.getLogger().info(reason + " '" + detectedWorld.getName() + "' and saved the selection.");
            return detectedWorld;
        }

        World loadedWorld = Bukkit.getWorld(configuredName);
        if (loadedWorld != null) {
            validateEarthWorld(loadedWorld);
            markSelectionComplete(plugin);
            return loadedWorld;
        }

        // A non-default explicit name is intentional. Preserve historical behavior and load/create
        // that configured world rather than replacing a deliberate administrator choice.
        World world = new WorldCreator(Objects.requireNonNull(configuredName)).createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to load configured Earth world '" + configuredName + "'.");
        }
        validateEarthWorld(world);
        markSelectionComplete(plugin);
        return world;
    }

    private static void saveEarthSelection(@Nonnull Galactifun plugin, @Nonnull World world) {
        validateEarthWorld(world);
        plugin.getConfig().set(EARTH_NAME_PATH, world.getName());
        plugin.getConfig().set(EARTH_SELECTION_COMPLETE_PATH, true);
        plugin.saveConfig();
    }

    private static void markSelectionComplete(@Nonnull Galactifun plugin) {
        if (!plugin.getConfig().getBoolean(EARTH_SELECTION_COMPLETE_PATH, false)) {
            plugin.getConfig().set(EARTH_SELECTION_COMPLETE_PATH, true);
            plugin.saveConfig();
        }
    }

    private static void validateEarthWorld(@Nonnull World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            throw new IllegalStateException(
                    "Configured Earth world '" + world.getName() + "' is " + world.getEnvironment()
                            + ", but Galactifun Earth must be a NORMAL Overworld."
            );
        }
    }

    @Nullable
    private static World detectEarthWorld() {
        List<World> candidates = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isEarthCandidate(world)) {
                candidates.add(world);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // A loaded world explicitly named "survival" is the strongest signal on Multiverse servers
        // and should win over a lobby/spawn world that may still be named "world".
        World survivalNamed = findCandidate(candidates, "survival");
        if (survivalNamed != null) {
            return survivalNamed;
        }

        // Otherwise prefer the server's configured primary level when it is a normal Overworld.
        String primaryLevelName = readPrimaryLevelName();
        if (primaryLevelName != null) {
            World primaryWorld = findCandidate(candidates, primaryLevelName);
            if (primaryWorld != null) {
                return primaryWorld;
            }
        }

        // Preserve vanilla/default installations where the normal survival world is still named "world".
        World vanillaNamed = findCandidate(candidates, LEGACY_DEFAULT_WORLD);
        if (vanillaNamed != null) {
            return vanillaNamed;
        }

        // If there is only one valid normal world, it is unambiguous. With multiple unknown candidates,
        // fail safely rather than choosing a creative/lobby world at random.
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    @Nullable
    private static World findCandidate(@Nonnull List<World> candidates, @Nonnull String name) {
        for (World candidate : candidates) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
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
