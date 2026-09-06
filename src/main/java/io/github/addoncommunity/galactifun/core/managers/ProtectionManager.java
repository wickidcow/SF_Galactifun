package io.github.addoncommunity.galactifun.core.managers;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Atmosphere;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.AtmosphericEffect;
import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;

public final class ProtectionManager {

    private static final BlockFace[] DIRTY_NEIGHBORS = {
            BlockFace.SELF,
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private final Map<BlockPosition, Map<AtmosphericEffect, Integer>> protectedBlocks = new HashMap<>();

    private final Map<BlockPosition, Set<BlockPosition>> oxygenBySource = new HashMap<>();
    private final Map<BlockPosition, Integer> oxygenRefCounts = new HashMap<>();

    @Nonnull
    public Map<AtmosphericEffect, Integer> protectionsAt(@Nonnull Location l) {
        return this.protectedBlocks.getOrDefault(new BlockPosition(l), new HashMap<>());
    }

    public int protectionAt(@Nonnull Location l, @Nonnull AtmosphericEffect effect) {
        int cached = protectionsAt(l).getOrDefault(effect, 0);
        OxygenZoneManager zones = Galactifun.oxygenZoneManager();
        if (zones != null) {
            cached = Math.max(cached, zones.protectionAt(l, effect));
        }
        return cached;
    }

    public int protectionAt(@Nonnull Player player, @Nonnull AtmosphericEffect effect) {
        int max = 0;
        for (Location sample : playerSamples(player)) {
            max = Math.max(max, protectionAt(sample, effect));
        }
        return max;
    }

    public void addProtection(@Nonnull BlockPosition pos, @Nonnull AtmosphericEffect effect, int level) {
        this.protectedBlocks.computeIfAbsent(pos, k -> new HashMap<>()).merge(effect, level, Integer::sum);
    }

    public void resetProtectedBlocks() {
        this.protectedBlocks.clear();
    }

    @Nonnull
    public Map<AtmosphericEffect, Integer> getEffectsAt(@Nonnull Location l) {
        AlienWorld world = Galactifun.worldManager().getAlienWorld(l.getWorld());
        if (world == null) return new HashMap<>();
        Map<AtmosphericEffect, Integer> protections = new HashMap<>();
        for (AtmosphericEffect effect : world.atmosphere().effects().keySet()) {
            protections.put(effect, protectionAt(l, effect));
        }
        return subtractProtections(world.atmosphere(), protections);
    }

    @Nonnull
    public Map<AtmosphericEffect, Integer> getEffectsAt(@Nonnull Player player) {
        AlienWorld world = Galactifun.worldManager().getAlienWorld(player.getWorld());
        if (world == null) return new HashMap<>();
        Map<AtmosphericEffect, Integer> protections = new HashMap<>();
        for (AtmosphericEffect effect : world.atmosphere().effects().keySet()) {
            protections.put(effect, protectionAt(player, effect));
        }
        return subtractProtections(world.atmosphere(), protections);
    }

    public int getEffectAt(@Nonnull Location l, @Nonnull AtmosphericEffect effect) {
        return getEffectsAt(l).getOrDefault(effect, 0);
    }

    @Nonnull
    public Map<AtmosphericEffect, Integer> subtractProtections(
            @Nonnull Atmosphere atmosphere,
            @Nonnull Map<AtmosphericEffect, Integer> protections
    ) {
        Map<AtmosphericEffect, Integer> ret = new HashMap<>();
        for (Map.Entry<AtmosphericEffect, Integer> eff : atmosphere.effects().entrySet()) {
            int val = eff.getValue() - protections.getOrDefault(eff.getKey(), 0);
            if (val > 0) ret.put(eff.getKey(), val);
        }

        return ret;
    }

    public void replaceOxygenSource(@Nonnull BlockPosition source, @Nonnull Set<BlockPosition> positions) {
        removeOxygenSource(source);

        Set<BlockPosition> copy = new HashSet<>(positions);
        this.oxygenBySource.put(source, copy);
        for (BlockPosition position : copy) {
            this.oxygenRefCounts.merge(position, 1, Integer::sum);
        }
    }

    public void removeOxygenSource(@Nonnull BlockPosition source) {
        Set<BlockPosition> previous = this.oxygenBySource.remove(source);
        if (previous == null) {
            return;
        }

        for (BlockPosition position : previous) {
            this.oxygenRefCounts.computeIfPresent(position, (ignored, count) -> count <= 1 ? null : count - 1);
        }
    }

    public int oxygenSourceSize(@Nonnull BlockPosition source) {
        return this.oxygenBySource.getOrDefault(source, Collections.emptySet()).size();
    }

    @Nonnull
    public Set<BlockPosition> oxygenSourcesNear(@Nonnull Block block) {
        Set<BlockPosition> neighbors = new HashSet<>();
        for (BlockFace face : DIRTY_NEIGHBORS) {
            neighbors.add(new BlockPosition(block.getRelative(face).getLocation()));
        }

        Set<BlockPosition> sources = new HashSet<>();
        for (Map.Entry<BlockPosition, Set<BlockPosition>> entry : this.oxygenBySource.entrySet()) {
            for (BlockPosition neighbor : neighbors) {
                if (entry.getValue().contains(neighbor)) {
                    sources.add(entry.getKey());
                    break;
                }
            }
        }
        return sources;
    }

    public void addOxygenBlock(@Nonnull BlockPosition l) {
        this.oxygenRefCounts.merge(l, 1, Integer::sum);
    }

    private boolean isOxygenCell(@Nonnull Location location) {
        OxygenZoneManager zones = Galactifun.oxygenZoneManager();
        return this.oxygenRefCounts.containsKey(new BlockPosition(location))
                || (zones != null && zones.providesOxygen(location));
    }

    /**
     * Compatibility lookup used by existing player oxygen checks. It samples feet, torso and head
     * so standing on slabs/trapdoors or exactly across a block boundary does not cause false suffocation.
     */
    public boolean isOxygenBlock(@Nonnull Location l) {
        return isOxygenCell(l)
                || isOxygenCell(l.clone().add(0, 0.75, 0))
                || isOxygenCell(l.clone().add(0, 1.5, 0));
    }

    public boolean isOxygenProtected(@Nonnull Player player) {
        for (Location sample : playerSamples(player)) {
            if (isOxygenCell(sample)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static Location[] playerSamples(@Nonnull Player player) {
        Location feet = player.getLocation();
        Location body = feet.clone().add(0, 0.75, 0);
        Location eyes = player.getEyeLocation();
        return new Location[] {feet, body, eyes};
    }

    public void resetOxygenBlocks() {
        this.oxygenBySource.clear();
        this.oxygenRefCounts.clear();
    }
}
