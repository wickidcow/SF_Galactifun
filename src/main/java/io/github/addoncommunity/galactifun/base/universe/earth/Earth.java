package io.github.addoncommunity.galactifun.base.universe.earth;

import java.util.Objects;

import javax.annotation.Nonnull;

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
 * Connects the server's configured Earth world into Galactifun without taking ownership of its generator.
 */
public final class Earth extends PlanetaryWorld {

    public Earth(String name, PlanetaryType type, Orbit orbit, StarSystem orbiting, ItemStack baseItem,
                 DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    @Nonnull
    @Override
    public World loadWorld() {
        String name = Objects.requireNonNull(
                Galactifun.instance().getConfig().getString("worlds.earth-name"),
                "worlds.earth-name"
        );

        // Multiverse/BentoBox/custom-generator worlds should already be loaded by their owning plugin.
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }

        if (!Galactifun.instance().getConfig().getBoolean("worlds.create-missing-earth", false)) {
            throw new IllegalStateException(
                    "Configured Earth world '" + name + "' is not loaded. "
                            + "Load/import it with your world manager or enable worlds.create-missing-earth."
            );
        }

        world = new WorldCreator(name).createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to load configured Earth world '" + name + "'.");
        }
        return world;
    }
}
