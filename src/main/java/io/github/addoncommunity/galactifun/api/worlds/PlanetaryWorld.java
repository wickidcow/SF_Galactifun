package io.github.addoncommunity.galactifun.api.worlds;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Marker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import com.google.common.collect.Iterables;
import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.PlanetaryObject;
import io.github.addoncommunity.galactifun.api.universe.StarSystem;
import io.github.addoncommunity.galactifun.api.universe.attributes.DayCycle;
import io.github.addoncommunity.galactifun.api.universe.attributes.Gravity;
import io.github.addoncommunity.galactifun.api.universe.attributes.Orbit;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Atmosphere;
import io.github.addoncommunity.galactifun.api.universe.types.PlanetaryType;
import io.github.addoncommunity.galactifun.base.universe.earth.Earth;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;

/**
 * Any world that can be travelled to by rockets or other means.
 *
 * <p>The legacy marker-backed world storage is deliberately retained for data compatibility. World
 * instances can, however, be rebound after an external world manager unloads/reloads the world.</p>
 */
public abstract class PlanetaryWorld extends PlanetaryObject {

    private static final NamespacedKey WORLD_STORAGE_KEY = Galactifun.createKey("world_storage");

    private World world;
    private WorldManager worldManager;
    private SlimefunAddon addon;
    private Marker worldStorage;

    private final Set<GEOResource> resources = new HashSet<>();

    public PlanetaryWorld(String name, PlanetaryType type, Orbit orbit, StarSystem orbiting, ItemStack baseItem,
                          DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    public PlanetaryWorld(String name, PlanetaryType type, Orbit orbit, PlanetaryObject orbiting, ItemStack baseItem,
                          DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    public final void register(@Nonnull SlimefunAddon addon) {
        if (isRegistered()) {
            throw new IllegalStateException("World already registered!");
        }

        this.worldManager = Galactifun.worldManager();
        this.addon = addon;
        this.world = loadWorld();
        if (this.world != null) {
            initializeWorldStorage();
            this.worldManager.register(this);
        }
    }

    private void initializeWorldStorage() {
        Collection<Marker> markers = this.world.getNearbyEntitiesByType(
                Marker.class,
                new Location(this.world, 0, 0, 0),
                0.1,
                entity -> entity.getPersistentDataContainer().has(WORLD_STORAGE_KEY, PersistentDataType.STRING)
        );

        if (markers.isEmpty()) {
            this.worldStorage = this.world.spawn(new Location(this.world, 0, 0, 0), Marker.class);
            PersistentDataAPI.setString(this.worldStorage, WORLD_STORAGE_KEY, "");
        } else {
            this.worldStorage = Iterables.get(markers, 0);
        }
    }

    /**
     * Rebinds this logical planet to a freshly loaded Bukkit World instance with the same name.
     */
    public final void rebindWorld(@Nonnull World world) {
        if (this.world != null && !this.world.getName().equalsIgnoreCase(world.getName())) {
            throw new IllegalArgumentException("Cannot rebind " + name() + " to unrelated world " + world.getName());
        }
        this.world = world;
        initializeWorldStorage();
    }

    public final boolean isRegistered() {
        return this.worldManager != null;
    }

    @Nullable
    protected abstract World loadWorld();

    @Nonnull
    public final PersistentDataHolder worldStorage() {
        Validate.notNull(this.worldStorage, "Attempted to get world storage of disabled world " + name());
        return this.worldStorage;
    }

    public final boolean enabled() {
        return this.world != null;
    }

    public final World world() {
        return this.world;
    }

    public final World getWorld() {
        return this.world;
    }

    public final SlimefunAddon addon() {
        return this.addon;
    }

    public final SlimefunAddon getAddon() {
        return this.addon;
    }

    public final Set<GEOResource> resources() {
        return this.resources;
    }

    public final Set<GEOResource> getResources() {
        return this.resources;
    }

    public void registerGEOResource(GEOResource resource) {
        resources.add(resource);
    }
}
