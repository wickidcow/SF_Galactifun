package io.github.addoncommunity.galactifun.core.integrations;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import io.github.addoncommunity.galactifun.Galactifun;

/**
 * Lightweight optional-integration registry.
 *
 * <p>Galactifun intentionally does not hard-depend on any world manager. This class keeps all
 * integration detection in one place so the rest of the addon can stay safe when a plugin is not
 * installed.</p>
 */
public final class IntegrationManager {

    private final Galactifun plugin;
    private final boolean multiverseCore;
    private final boolean multiverseInventories;
    private final boolean multiversePortals;
    private final boolean bentoBox;
    private final boolean geyser;
    private final boolean floodgate;
    private final boolean folia;

    public IntegrationManager(Galactifun plugin) {
        this.plugin = plugin;
        this.multiverseCore = isEnabled("Multiverse-Core");
        this.multiverseInventories = isEnabled("Multiverse-Inventories");
        this.multiversePortals = isEnabled("Multiverse-Portals");
        this.bentoBox = isEnabled("BentoBox");
        this.geyser = isEnabled("Geyser-Spigot");
        this.floodgate = isEnabled("floodgate");
        this.folia = detectFolia();

        logSummary();
    }

    private static boolean isEnabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    IntegrationManager.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void logSummary() {
        Galactifun.log(Level.INFO,
                "Optional integrations: Multiverse-Core=" + status(multiverseCore)
                        + ", Multiverse-Inventories=" + status(multiverseInventories)
                        + ", Multiverse-Portals=" + status(multiversePortals)
                        + ", BentoBox=" + status(bentoBox)
                        + ", Geyser=" + status(geyser)
                        + ", Floodgate=" + status(floodgate)
                        + ", Folia=" + (folia ? "experimental" : "not detected"));
    }

    private static String status(boolean enabled) {
        return enabled ? "detected" : "not detected";
    }

    public boolean isMultiverseCore() {
        return multiverseCore;
    }

    public boolean isMultiverseInventories() {
        return multiverseInventories;
    }

    public boolean isMultiversePortals() {
        return multiversePortals;
    }

    public boolean isBentoBox() {
        return bentoBox;
    }

    public boolean isGeyser() {
        return geyser;
    }

    public boolean isFloodgate() {
        return floodgate;
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean allowMultiversePlanetEntry() {
        return multiverseCore
                && plugin.getConfig().getBoolean("integrations.multiverse.portals.allow-entry-to-planets", false);
    }

    public boolean allowMultiversePlanetExit() {
        return multiverseCore
                && plugin.getConfig().getBoolean("integrations.multiverse.portals.allow-exit-from-planets", true);
    }
}
