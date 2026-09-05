package io.github.addoncommunity.galactifun.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;

/**
 * Startup and post-start compatibility checks for the maintained Galactifun build.
 *
 * <p>These checks are intentionally observational. They validate the runtime Galactifun is about to use,
 * report optional integrations, and fail startup only when a required platform or Slimefun linkage is not usable.
 */
public final class RuntimeCompatibility {

    private static final int MINIMUM_JAVA_FEATURE = 21;

    private RuntimeCompatibility() {
    }

    public static boolean preflight(Galactifun plugin) {
        boolean compatible = true;

        plugin.getLogger().info("Runtime compatibility preflight:");
        plugin.getLogger().info(" - Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
        plugin.getLogger().info(" - Minecraft/Paper API target: " + Bukkit.getMinecraftVersion() + " / 26.2");
        plugin.getLogger().info(" - Java: " + Runtime.version());

        if (!PaperLib.isPaper()) {
            plugin.getLogger().severe("Galactifun 1.0.1 requires Paper or a Paper-compatible fork such as Purpur.");
            compatible = false;
        }

        if (Runtime.version().feature() < MINIMUM_JAVA_FEATURE) {
            plugin.getLogger().severe("Galactifun requires Java 21 or newer. Paper 26.2 is expected to run on Java 25.");
            compatible = false;
        }

        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (slimefun == null || !slimefun.isEnabled()) {
            plugin.getLogger().severe("The required Slimefun provider is missing or disabled.");
            compatible = false;
        } else {
            plugin.getLogger().info(" - Slimefun provider: " + slimefun.getPluginMeta().getVersion()
                    + " (" + slimefun.getClass().getName() + ")");
            compatible &= verifySlimefunLinkage(plugin);
        }

        if (isClassPresent("io.papermc.paper.threadedregions.RegionizedServer")) {
            plugin.getLogger().warning("Folia runtime detected. Galactifun world ticking and generation are experimental on Folia;");
            plugin.getLogger().warning("use Paper 26.2 or Purpur for the supported production path.");
        }

        compatible &= rejectKnownConflict(plugin, "ClayTech");
        compatible &= rejectKnownConflict(plugin, "ChatColor2");

        reportOptionalPlugin(plugin, "Multiverse-Core",
                "detected; Galactifun keeps ownership of its planetary generators and uses no hard Multiverse API linkage");
        reportOptionalPlugin(plugin, "BentoBox",
                "detected; integration remains soft-linked and does not become a startup requirement");

        if (compatible) {
            plugin.getLogger().info("Runtime compatibility preflight passed.");
        }

        return compatible;
    }

    public static void postStartup(Galactifun plugin) {
        if (Galactifun.worldManager() == null) {
            plugin.getLogger().warning("Post-start world verification skipped because the world manager is unavailable.");
            return;
        }

        List<String> worlds = new ArrayList<>();
        int invalidWorlds = 0;

        for (PlanetaryWorld planetaryWorld : Galactifun.worldManager().spaceWorlds()) {
            World world = planetaryWorld.world();
            if (world == null || Bukkit.getWorld(world.getUID()) == null) {
                invalidWorlds++;
                continue;
            }
            worlds.add(world.getName());
        }

        plugin.getLogger().info("Planetary runtime check: " + worlds.size() + " registered world(s), "
                + Galactifun.worldManager().alienWorlds().size() + " alien world(s).");
        if (!worlds.isEmpty()) {
            plugin.getLogger().info(" - Loaded planetary worlds: " + String.join(", ", worlds));
        }
        if (invalidWorlds > 0) {
            plugin.getLogger().warning("Planetary runtime check found " + invalidWorlds
                    + " registered world reference(s) that are not currently loaded by Bukkit.");
        }
    }

    private static boolean verifySlimefunLinkage(Galactifun plugin) {
        try {
            Slimefun.getMinecraftVersion();
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class.forName("io.github.thebusybiscuit.slimefun4.api.SlimefunAddon", false, loader);
            Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem", false, loader);
            Class<?> slimefunItemStack = Class.forName(
                    "io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack", false, loader);
            slimefunItemStack.getMethod("item");
            Class.forName("me.mrCookieSlime.Slimefun.api.BlockStorage", false, loader);
            plugin.getLogger().info(" - Slimefun API linkage probe: passed");
            return true;
        } catch (NoSuchMethodException missingBridge) {
            plugin.getLogger().log(Level.SEVERE,
                    "Slimefun Legacy is too old for this Galactifun build. Install a Legacy build that includes the SlimefunItemStack.item() compatibility bridge.",
                    missingBridge);
            return false;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE,
                    "Slimefun API linkage probe failed. This build expects the classic Slimefun 4/Legacy API surface.",
                    throwable);
            return false;
        }
    }

    private static boolean rejectKnownConflict(Galactifun plugin, String pluginName) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return true;
        }

        plugin.getLogger().severe("Known incompatible plugin detected: " + pluginName + '.');
        plugin.getLogger().severe("Disable " + pluginName + " before enabling Galactifun.");
        return false;
    }

    private static void reportOptionalPlugin(Galactifun plugin, String pluginName, String message) {
        Plugin optional = Bukkit.getPluginManager().getPlugin(pluginName);
        if (optional != null && optional.isEnabled()) {
            plugin.getLogger().info(" - " + pluginName + " " + optional.getPluginMeta().getVersion() + ": " + message);
        } else {
            plugin.getLogger().info(" - " + pluginName + ": not installed (optional)");
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, RuntimeCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
