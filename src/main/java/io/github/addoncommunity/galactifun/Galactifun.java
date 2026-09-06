package io.github.addoncommunity.galactifun;

import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseAlien;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.base.BaseMats;
import io.github.addoncommunity.galactifun.base.BaseUniverse;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.addoncommunity.galactifun.core.RuntimeCompatibility;
import io.github.addoncommunity.galactifun.core.commands.AlienRemoveCommand;
import io.github.addoncommunity.galactifun.core.commands.AlienSpawnCommand;
import io.github.addoncommunity.galactifun.core.commands.EffectsCommand;
import io.github.addoncommunity.galactifun.core.commands.GalactiportCommand;
import io.github.addoncommunity.galactifun.core.commands.SealedCommand;
import io.github.addoncommunity.galactifun.core.commands.StructureCommand;
import io.github.addoncommunity.galactifun.core.integrations.MultiverseIntegration;
import io.github.addoncommunity.galactifun.core.managers.AlienManager;
import io.github.addoncommunity.galactifun.core.managers.ProtectionManager;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.core.AbstractAddon;
import io.github.mooy1.infinitylib.metrics.bukkit.Metrics;

public final class Galactifun extends AbstractAddon {

    private static Galactifun instance;

    private AlienManager alienManager;
    private WorldManager worldManager;
    private ProtectionManager protectionManager;

    private boolean shouldDisable = false;

    public Galactifun() {
        super("Slimefun-Addon-Community", "Galactifun", "master", "auto-update");
    }

    public static AlienManager alienManager() {
        return instance.alienManager;
    }

    public static Galactifun instance() {
        return instance;
    }

    public static WorldManager worldManager() {
        return instance.worldManager;
    }

    public static ProtectionManager protectionManager() {
        return instance.protectionManager;
    }

    @Override
    protected void enable() {
        instance = this;

        if (!RuntimeCompatibility.preflight(this)) {
            shouldDisable = true;
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        new Metrics(this, 11613);

        // Auto updater removed for modern standalone builds.

        this.alienManager = new AlienManager(this);
        this.worldManager = new WorldManager(this);
        this.protectionManager = new ProtectionManager();

        BaseAlien.setup(this.alienManager);
        BaseUniverse.setup(this);

        // Galactifun must create its custom worlds first. Multiverse, when present, is attached only
        // after the planetary registry is complete so it never replaces a Galactifun generator.
        MultiverseIntegration.setup(this);

        CoreItemGroup.setup(this);
        BaseMats.setup();
        BaseItems.setup(this);

        // Verify the fully initialized world registry, then log the normal startup banner.
        Scheduler.run(() -> {
            RuntimeCompatibility.postStartup(this);
            log(Level.INFO,
                    "################# Galactifun " + getPluginVersion() + " #################",
                    "",
                    "Galactifun is open source, you can contribute or report bugs at:",
                    getBugTrackerURL(),
                    "Maintained Slimefun Legacy fork: https://github.com/wickidcow/SF_Galactifun",
                    "",
                    "###################################################"
            );
        });

        getAddonCommand()
                .addSub(new GalactiportCommand())
                .addSub(new AlienSpawnCommand())
                .addSub(new AlienRemoveCommand())
                .addSub(new StructureCommand(this))
                .addSub(new SealedCommand())
                .addSub(new EffectsCommand());
    }

    @Override
    protected void disable() {
        if (shouldDisable) {
            instance = null;
            return;
        }

        if (this.alienManager != null) {
            this.alienManager.onDisable();
        }

        // Do this last.
        instance = null;
    }

    @Nullable
    @Override
    public ChunkGenerator getDefaultWorldGenerator(@Nonnull String worldName, @Nullable String id) {
        if (this.worldManager == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            PlanetaryWorld planetaryWorld = this.worldManager.getWorld(world);
            if (planetaryWorld instanceof AlienWorld alienWorld) {
                return alienWorld.world().getGenerator();
            }
        }

        // Multiverse and other world managers can request a generator by name before Bukkit resolves the world.
        // Fall back to the Galactifun registry without hard-linking to an external world-management API.
        for (PlanetaryWorld planetaryWorld : this.worldManager.spaceWorlds()) {
            if (planetaryWorld instanceof AlienWorld alienWorld
                    && alienWorld.world() != null
                    && alienWorld.world().getName().equals(worldName)) {
                return alienWorld.world().getGenerator();
            }
        }

        return null;
    }
}
