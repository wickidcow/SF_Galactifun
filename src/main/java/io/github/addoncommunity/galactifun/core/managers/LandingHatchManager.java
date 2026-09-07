package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.util.SFStorage;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Persistent registry of Landing Hatches used by the rocket landing selector.
 */
public final class LandingHatchManager {

    public record LandingTarget(
            @Nonnull String id,
            @Nonnull UUID worldId,
            @Nonnull String worldName,
            int x,
            int y,
            int z
    ) {
    }

    private final Galactifun plugin;
    private final File file;
    private final YamlConfiguration storage = new YamlConfiguration();
    private final List<LandingTarget> targets = new ArrayList<>();

    public LandingHatchManager(@Nonnull Galactifun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "landing-hatches.yml");
        load();
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }

        try {
            this.storage.load(this.file);
            ConfigurationSection root = this.storage.getConfigurationSection("hatches");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;
                try {
                    this.targets.add(new LandingTarget(
                            id,
                            UUID.fromString(section.getString("world-id", "")),
                            section.getString("world-name", "unknown"),
                            section.getInt("x"),
                            section.getInt("y"),
                            section.getInt("z")
                    ));
                } catch (IllegalArgumentException exception) {
                    this.plugin.getLogger().warning("Ignoring invalid Landing Hatch entry '" + id + "'");
                }
            }
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not load landing-hatches.yml", exception);
        }
    }

    private void save() {
        this.storage.set("hatches", null);
        for (LandingTarget target : this.targets) {
            String path = "hatches." + target.id();
            this.storage.set(path + ".world-id", target.worldId().toString());
            this.storage.set(path + ".world-name", target.worldName());
            this.storage.set(path + ".x", target.x());
            this.storage.set(path + ".y", target.y());
            this.storage.set(path + ".z", target.z());
        }
        try {
            this.storage.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save landing-hatches.yml", exception);
        }
    }

    @Nonnull
    public LandingTarget register(@Nonnull Block block) {
        LandingTarget existing = find(block.getLocation());
        if (existing != null) {
            return existing;
        }

        LandingTarget target = new LandingTarget(
                UUID.randomUUID().toString(),
                block.getWorld().getUID(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
        this.targets.add(target);
        save();
        return target;
    }

    public void unregister(@Nonnull Block block) {
        if (this.targets.removeIf(target -> sameLocation(target, block.getLocation()))) {
            save();
        }
    }

    @Nullable
    public LandingTarget find(@Nonnull Location location) {
        for (LandingTarget target : this.targets) {
            if (sameLocation(target, location)) {
                return target;
            }
        }
        return null;
    }

    @Nonnull
    public List<LandingTarget> targets(@Nonnull World world) {
        List<LandingTarget> matches = new ArrayList<>();
        for (LandingTarget target : this.targets) {
            if (target.worldId().equals(world.getUID())) {
                matches.add(target);
            }
        }
        matches.sort(Comparator.comparingInt(LandingTarget::x)
                .thenComparingInt(LandingTarget::z)
                .thenComparingInt(LandingTarget::y));
        return matches;
    }

    @Nullable
    public Location location(@Nonnull LandingTarget target) {
        World world = Bukkit.getWorld(target.worldId());
        if (world == null) {
            world = Bukkit.getWorld(target.worldName());
        }
        return world == null ? null : new Location(world, target.x(), target.y(), target.z());
    }

    public boolean validateLoaded(@Nonnull LandingTarget target) {
        Location location = location(target);
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        if (!world.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
            return true;
        }
        return SFStorage.isItem(location.getBlock(), BaseItems.LANDING_HATCH.getItemId());
    }

    /**
     * Imports already-loaded pre-1.0.6 Landing Hatches without scanning or loading every world chunk.
     */
    public void migrateLoadedHatches() {
        String hatchId = BaseItems.LANDING_HATCH.getItemId();
        var dataController = Slimefun.getDatabaseManager().getBlockDataController();
        boolean changed = false;

        for (var chunkData : dataController.getAllLoadedChunkData()) {
            for (var blockData : chunkData.getAllBlockData()) {
                if (!hatchId.equals(blockData.getSfId())) {
                    continue;
                }
                Location location = blockData.getLocation();
                if (find(location) == null && location.getWorld() != null) {
                    Block block = location.getBlock();
                    this.targets.add(new LandingTarget(
                            UUID.randomUUID().toString(),
                            block.getWorld().getUID(),
                            block.getWorld().getName(),
                            block.getX(), block.getY(), block.getZ()
                    ));
                    changed = true;
                }
            }
        }

        if (changed) {
            save();
        }
    }

    private static boolean sameLocation(@Nonnull LandingTarget target, @Nonnull Location location) {
        World world = location.getWorld();
        return world != null
                && target.worldId().equals(world.getUID())
                && target.x() == location.getBlockX()
                && target.y() == location.getBlockY()
                && target.z() == location.getBlockZ();
    }

    public void onDisable() {
        save();
    }
}
