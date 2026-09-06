package io.github.addoncommunity.galactifun.core.integrations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;

/**
 * Optional Multiverse-Core integration.
 *
 * <p>Galactifun remains the owner of planetary world creation and generation. Galactifun is ordered
 * before Multiverse-Core, creates its Bukkit worlds with the correct custom generators, and then
 * attaches those already-loaded worlds to Multiverse once its API is available.</p>
 *
 * <p>The Multiverse entries are marked as non-autoloading. On later restarts Galactifun therefore
 * creates the planets first again, while Multiverse only attaches its management state afterward.</p>
 *
 * <p>This bridge intentionally uses reflection. Multiverse shades several API implementation types
 * in its release JAR, so avoiding a binary link keeps Multiverse optional and prevents a shaded API
 * return type from leaking into Galactifun's bytecode.</p>
 */
public final class MultiverseIntegration {

    private static final String MULTIVERSE_PLUGIN = "Multiverse-Core";
    private static final String GENERATOR_NAME = "Galactifun";
    private static final String API_CLASS = "org.mvplugins.multiverse.core.MultiverseCoreApi";
    private static final String IMPORT_OPTIONS_CLASS =
            "org.mvplugins.multiverse.core.world.options.ImportWorldOptions";

    private MultiverseIntegration() {
    }

    public static void setup(@Nonnull Galactifun plugin) {
        if (!plugin.getConfig().getBoolean("integrations.multiverse.register-worlds", true)) {
            plugin.getLogger().info("Multiverse planet registration is disabled in config.");
            return;
        }

        // PlanetaryWorld stores the active Bukkit World object and Galactifun ticks that object directly.
        // Letting a world manager unload one while Galactifun is enabled would leave a stale runtime reference.
        Bukkit.getPluginManager().registerEvents(new ManagedWorldUnloadGuard(plugin), plugin);

        Plugin multiverse = Bukkit.getPluginManager().getPlugin(MULTIVERSE_PLUGIN);
        if (multiverse != null && multiverse.isEnabled()) {
            registerWorlds(plugin, multiverse);
            return;
        }

        // Normal 1.0.2 path: plugin.yml orders Galactifun before Multiverse-Core. The planets are
        // therefore already correct before Multiverse reads/imports its world configuration.
        Bukkit.getPluginManager().registerEvents(new MultiverseEnableListener(plugin), plugin);
    }

    private static void registerWorlds(Galactifun plugin, Plugin multiverse) {
        try {
            Bridge bridge = new Bridge(plugin, multiverse);
            int registered = 0;
            for (AlienWorld alienWorld : Galactifun.worldManager().alienWorlds()) {
                World world = alienWorld.world();
                if (world != null && bridge.register(world)) {
                    registered++;
                }
            }
            bridge.save();
            plugin.getLogger().info("Multiverse integration: " + registered
                    + " Galactifun alien world(s) registered; Galactifun remains the world generator/loader.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Multiverse-Core was detected, but its API is not compatible with the Galactifun bridge. "
                            + "Planet worlds remain fully usable through Galactifun, but Multiverse will not manage them.",
                    unwrap(exception));
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return throwable;
    }

    private static final class MultiverseEnableListener implements Listener {

        private final Galactifun plugin;

        private MultiverseEnableListener(Galactifun plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        private void onPluginEnable(PluginEnableEvent event) {
            if (!MULTIVERSE_PLUGIN.equals(event.getPlugin().getName())) {
                return;
            }

            HandlerList.unregisterAll(this);
            registerWorlds(plugin, event.getPlugin());
        }
    }

    private static final class ManagedWorldUnloadGuard implements Listener {

        private final Galactifun plugin;

        private ManagedWorldUnloadGuard(Galactifun plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onWorldUnload(WorldUnloadEvent event) {
            if (!plugin.isEnabled()
                    || !plugin.getConfig().getBoolean("worlds.protect-managed-worlds", true)
                    || Galactifun.worldManager().getAlienWorld(event.getWorld()) == null) {
                return;
            }

            event.setCancelled(true);
            plugin.getLogger().warning("Prevented unload of Galactifun-managed world "
                    + event.getWorld().getName()
                    + ". Galactifun keeps planetary worlds loaded to preserve generators, effects, and world state.");
        }
    }

    private static final class Bridge {

        private final Galactifun plugin;
        private final Object worldManager;
        private final Class<?> importOptionsClass;
        private final Method getWorld;
        private final Method importWorld;
        private final Method saveWorldsConfig;

        private Bridge(Galactifun plugin, Plugin multiverse) throws ReflectiveOperationException {
            this.plugin = plugin;

            ClassLoader loader = multiverse.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, loader);
            this.importOptionsClass = Class.forName(IMPORT_OPTIONS_CLASS, true, loader);

            Object api = apiClass.getMethod("get").invoke(null);
            this.worldManager = apiClass.getMethod("getWorldManager").invoke(api);
            Class<?> worldManagerClass = this.worldManager.getClass();
            this.getWorld = worldManagerClass.getMethod("getWorld", World.class);
            this.importWorld = worldManagerClass.getMethod("importWorld", this.importOptionsClass);
            this.saveWorldsConfig = worldManagerClass.getMethod("saveWorldsConfig");
        }

        private boolean register(World bukkitWorld) throws ReflectiveOperationException {
            Object multiverseWorld = findWorld(bukkitWorld);
            if (multiverseWorld == null) {
                importExistingBukkitWorld(bukkitWorld);
                multiverseWorld = findWorld(bukkitWorld);
            }

            if (multiverseWorld == null) {
                plugin.getLogger().warning("Multiverse integration could not register " + bukkitWorld.getName()
                        + "; Galactifun will continue managing the world directly.");
                return false;
            }

            // Galactifun must always create/load this world first on startup. This avoids Multiverse
            // constructing the planet before Galactifun has initialized its custom generator.
            invokeAndCheck(multiverseWorld, "setAutoLoad", new Class<?>[] { boolean.class }, false);

            // Multiverse spawn adjustment is inappropriate for void/orbit and other custom planetary worlds.
            invokeAndCheck(multiverseWorld, "setAdjustSpawn", new Class<?>[] { boolean.class }, false);

            // Do not rewrite generator metadata through newer Multiverse property-handle APIs here.
            // Multiverse 5.1+ already stores GENERATOR_NAME when a world is imported above, while
            // existing entries are safe because Galactifun is load-before Multiverse and auto-load is false.
            return true;
        }

        private Object findWorld(World world) throws ReflectiveOperationException {
            Object option = this.getWorld.invoke(this.worldManager, world);
            Method isDefined = option.getClass().getMethod("isDefined");
            if (!Boolean.TRUE.equals(isDefined.invoke(option))) {
                return null;
            }
            return option.getClass().getMethod("get").invoke(option);
        }

        private void importExistingBukkitWorld(World world) throws ReflectiveOperationException {
            Object options = this.importOptionsClass.getMethod("worldName", String.class)
                    .invoke(null, world.getName());
            options = this.importOptionsClass.getMethod("environment", World.Environment.class)
                    .invoke(options, world.getEnvironment());
            options = this.importOptionsClass.getMethod("generator", String.class)
                    .invoke(options, GENERATOR_NAME);
            options = this.importOptionsClass.getMethod("useSpawnAdjust", boolean.class)
                    .invoke(options, false);

            Object result = this.importWorld.invoke(this.worldManager, options);
            if (!isSuccessful(result)) {
                plugin.getLogger().warning("Multiverse import reported a failure for Galactifun world "
                        + world.getName() + ".");
            }
        }

        private void save() throws ReflectiveOperationException {
            Object result = this.saveWorldsConfig.invoke(this.worldManager);
            if (!isSuccessful(result)) {
                plugin.getLogger().warning("Multiverse integration could not save worlds.yml after planet registration.");
            }
        }

        private void invokeAndCheck(Object target, String methodName, Class<?>[] parameterTypes, Object argument)
                throws ReflectiveOperationException {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            Object result = method.invoke(target, argument);
            if (!isSuccessful(result)) {
                plugin.getLogger().warning("Multiverse integration could not apply " + methodName
                        + " to " + target + '.');
            }
        }

        private boolean isSuccessful(Object result) throws ReflectiveOperationException {
            if (result == null) {
                return true;
            }
            try {
                Method isSuccess = result.getClass().getMethod("isSuccess");
                return Boolean.TRUE.equals(isSuccess.invoke(result));
            } catch (NoSuchMethodException ignored) {
                // A future API may return void or another success wrapper. Reaching this point means
                // the reflective invocation itself succeeded, so do not turn compatibility into failure.
                return true;
            }
        }
    }
}
