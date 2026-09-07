package io.github.addoncommunity.galactifun.core.managers;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseUniverse;

/**
 * Resolves rocket travel origins and optional per-planet permissions.
 */
public final class TravelManager {

    private final Set<String> additionalEarthLaunchWorlds = new HashSet<>();
    private final boolean enforcePlanetPermissions;
    private final boolean permissionDefaultAllow;

    public TravelManager(@Nonnull Galactifun plugin) {
        for (String world : plugin.getConfig().getStringList("travel.additional-earth-launch-worlds")) {
            if (world != null && !world.isBlank()) {
                this.additionalEarthLaunchWorlds.add(world.toLowerCase(Locale.ROOT));
            }
        }

        this.enforcePlanetPermissions = plugin.getConfig()
                .getBoolean("travel.enforce-planet-permissions", true);
        this.permissionDefaultAllow = plugin.getConfig()
                .getBoolean("travel.permission-default-allow", true);
    }

    /**
     * Gets the planetary object used for rocket distance calculations from a Bukkit world.
     * Additional configured launch worlds behave like Earth for travel only; they do not become
     * Galactifun planetary worlds and do not inherit Earth respawn/atmosphere behavior.
     */
    @Nullable
    public PlanetaryWorld resolveTravelOrigin(@Nonnull World world) {
        PlanetaryWorld registered = Galactifun.worldManager().getWorld(world);
        if (registered != null) {
            return registered;
        }

        if (this.additionalEarthLaunchWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) {
            return BaseUniverse.EARTH;
        }

        return null;
    }

    public boolean isAdditionalEarthLaunchWorld(@Nonnull World world) {
        return this.additionalEarthLaunchWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public boolean canTravel(@Nonnull Player player, @Nonnull PlanetaryWorld destination) {
        if (!this.enforcePlanetPermissions || destination == BaseUniverse.EARTH) {
            return true;
        }

        String permission = permissionNode(destination);
        if (this.permissionDefaultAllow && !player.isPermissionSet(permission)) {
            return true;
        }
        return player.hasPermission(permission);
    }

    @Nonnull
    public String permissionNode(@Nonnull PlanetaryWorld destination) {
        return "galactifun.travel." + destination.id();
    }
}
