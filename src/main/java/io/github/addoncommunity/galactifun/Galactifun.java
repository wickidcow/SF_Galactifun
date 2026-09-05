package io.github.addoncommunity.galactifun;

import java.io.File;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;

import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseAlien;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.base.BaseMats;
import io.github.addoncommunity.galactifun.base.BaseUniverse;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.addoncommunity.galactifun.core.commands.AlienRemoveCommand;
import io.github.addoncommunity.galactifun.core.commands.AlienSpawnCommand;
import io.github.addoncommunity.galactifun.core.commands.EffectsCommand;
import io.github.addoncommunity.galactifun.core.commands.GalactiportCommand;
import io.github.addoncommunity.galactifun.core.commands.SealedCommand;
import io.github.addoncommunity.galactifun.core.commands.StructureCommand;
import io.github.addoncommunity.galactifun.core.integrations.IntegrationManager;
import io.github.addoncommunity.galactifun.core.managers.AlienManager;
import io.github.addoncommunity.galactifun.core.managers.ProtectionManager;
import io.github.addoncommunity.galactifun.core.managers.TravelManager;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.core.AbstractAddon;
import io.github.mooy1.infinitylib.metrics.bukkit.Metrics;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;

public final class Galactifun extends AbstractAddon {

    private static Galactifun instance;

    private boolean isTest = false;
    private boolean shouldDisable = false;

    private AlienManager alienManager;
    private WorldManager worldManager;
    private ProtectionManager protectionManager;
    private TravelManager travelManager;
    private IntegrationManager integrationManager;

    public Galactifun() {
        super("wickidcow", "SF_Galactifun", "master", "auto-update");
    }

    public Galactifun(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file, "wickidcow", "SF_Galactifun", "master", "auto-update");
        isTest = true;
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

    public static TravelManager travelManager() {
        return instance.travelManager;
    }

    public static IntegrationManager integrations() {
        return instance.integrationManager;
    }

    @Override
    protected void enable() {
        instance = this;

        if (!isTest) {
            if (!PaperLib.isPaper()) {
                log(Level.SEVERE, "Galactifun Legacy requires Paper or a compatible Paper fork such as Purpur.");
                shouldDisable = true;
            }
            if (Slimefun.getMinecraftVersion().isBefore(MinecraftVersion.MINECRAFT_1_17)) {
                log(Level.SEVERE, "Galactifun requires a modern Minecraft server.");
                shouldDisable = true;
            }
            if (Bukkit.getPluginManager().isPluginEnabled("ClayTech")) {
                log(Level.SEVERE, "Galactifun will not work properly with ClayTech. Please disable ClayTech.");
                shouldDisable = true;
            }
            if (Bukkit.getPluginManager().isPluginEnabled("ChatColor2")) {
                log(Level.SEVERE, "Galactifun will not work properly with ChatColor2. Please disable ChatColor2.");
                shouldDisable = true;
            }

            if (shouldDisable) {
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        saveDefaultConfig();
        new Metrics(this, 11613);

        this.integrationManager = new IntegrationManager(this);
        this.travelManager = new TravelManager();
        this.protectionManager = new ProtectionManager();
        this.alienManager = new AlienManager(this);
        this.worldManager = new WorldManager(this);

        BaseAlien.setup(this.alienManager);
        if (!isTest) {
            BaseUniverse.setup(this);
        }
        CoreItemGroup.setup(this);
        BaseMats.setup();
        BaseItems.setup(this);

        Scheduler.run(() -> log(Level.INFO,
                "################# Galactifun Legacy " + getPluginVersion() + " #################",
                "",
                "Primary target: Slimefun Legacy / Paper 26.2 / Java 25",
                "Source and bug tracker: " + getBugTrackerURL(),
                "",
                "########################################################"
        ));

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
            return;
        }

        if (this.alienManager != null) {
            this.alienManager.onDisable();
        }
        if (this.travelManager != null) {
            this.travelManager.clearExpired();
        }

        instance = null;
    }

    @Override
    public void load() {
        // Galactifun Legacy deliberately does not mutate global Paper/Spigot settings.
    }

    @Nullable
    @Override
    public ChunkGenerator getDefaultWorldGenerator(@Nonnull String worldName, @Nullable String id) {
        World world = Bukkit.getWorld(worldName);
        if (world != null && this.worldManager != null) {
            PlanetaryWorld planetaryWorld = this.worldManager.getWorld(world);
            if (planetaryWorld instanceof AlienWorld) {
                return planetaryWorld.world().getGenerator();
            }
        }

        AlienWorld pending = BaseUniverse.findAlienWorld(worldName, id);
        return pending == null ? null : pending.createChunkGenerator();
    }
}
