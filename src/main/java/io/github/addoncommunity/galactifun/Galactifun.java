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
import io.github.addoncommunity.galactifun.core.commands.DiscoveriesCommand;
import io.github.addoncommunity.galactifun.core.commands.EffectsCommand;
import io.github.addoncommunity.galactifun.core.commands.GalactiportCommand;
import io.github.addoncommunity.galactifun.core.commands.OxygenZoneCommand;
import io.github.addoncommunity.galactifun.core.commands.SealedCommand;
import io.github.addoncommunity.galactifun.core.commands.StructureCommand;
import io.github.addoncommunity.galactifun.core.integrations.MultiverseIntegration;
import io.github.addoncommunity.galactifun.core.managers.AlienManager;
import io.github.addoncommunity.galactifun.core.managers.AtmosphereCacheListener;
import io.github.addoncommunity.galactifun.core.managers.DiscoveryManager;
import io.github.addoncommunity.galactifun.core.managers.LandingHatchListener;
import io.github.addoncommunity.galactifun.core.managers.LandingHatchManager;
import io.github.addoncommunity.galactifun.core.managers.OxygenZoneManager;
import io.github.addoncommunity.galactifun.core.managers.ProtectionManager;
import io.github.addoncommunity.galactifun.core.managers.TravelManager;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.core.AbstractAddon;
import io.github.mooy1.infinitylib.metrics.bukkit.Metrics;

public final class Galactifun extends AbstractAddon {

    private static Galactifun instance;

    private AlienManager alienManager;
    private WorldManager worldManager;
    private ProtectionManager protectionManager;
    private OxygenZoneManager oxygenZoneManager;
    private TravelManager travelManager;
    private DiscoveryManager discoveryManager;
    private LandingHatchManager landingHatchManager;

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

    @Nullable
    public static OxygenZoneManager oxygenZoneManager() {
        return instance == null ? null : instance.oxygenZoneManager;
    }

    @Nullable
    public static TravelManager travelManager() {
        return instance == null ? null : instance.travelManager;
    }

    @Nullable
    public static DiscoveryManager discoveryManager() {
        return instance == null ? null : instance.discoveryManager;
    }

    @Nullable
    public static LandingHatchManager landingHatchManager() {
        return instance == null ? null : instance.landingHatchManager;
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

        this.alienManager = new AlienManager(this);
        this.worldManager = new WorldManager(this);
        this.protectionManager = new ProtectionManager();
        this.oxygenZoneManager = new OxygenZoneManager(this);
        this.landingHatchManager = new LandingHatchManager(this);

        BaseAlien.setup(this.alienManager);
        BaseUniverse.setup(this);

        // Travel and discovery resolve against the fully registered planetary worlds.
        this.travelManager = new TravelManager(this);
        this.discoveryManager = new DiscoveryManager(this);

        // Galactifun must create its custom worlds first. Multiverse, when present, is attached only
        // after the planetary registry is complete so it never replaces a Galactifun generator.
        MultiverseIntegration.setup(this);

        CoreItemGroup.setup(this);
        BaseMats.setup();
        BaseItems.setup(this);

        // Import existing loaded Landing Hatches and install low-overhead cache invalidation/listeners.
        this.landingHatchManager.migrateLoadedHatches();
        new LandingHatchListener();
        new AtmosphereCacheListener();

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
                .addSub(new EffectsCommand())
                .addSub(new DiscoveriesCommand())
                .addSub(new OxygenZoneCommand());
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
        if (this.discoveryManager != null) {
            this.discoveryManager.onDisable();
        }
        if (this.oxygenZoneManager != null) {
            this.oxygenZoneManager.onDisable();
        }
        if (this.landingHatchManager != null) {
            this.landingHatchManager.onDisable();
        }
        if (this.protectionManager != null) {
            this.protectionManager.resetOxygenBlocks();
        }

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
