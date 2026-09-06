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

    // Oxygen is maintained incrementally per Sealer. Ref-counting keeps overlapping Sealer rooms
    // breathable when one Sealer is removed or rescanned.
    private final Map<BlockPosition, Set<BlockPosition>> oxygenBySource = new HashMap<>();
    private final Map<BlockPosition, Integer> oxygenRefCounts = new HashMap<>();
    private final Map<BlockPosition, Set<BlockPosition>> oxygenOwners = new HashMap<>();

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

    /** Clears the cached atmospheric protection map before the next protection scan. */
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

    /**
     * Replaces one Sealer's cached breathable room without disturbing overlapping Sealer rooms.
     */
    public void replaceOxygenSource(@Nonnull BlockPosition source, @Nonnull Set<BlockPosition> positions) {
        removeOxygenSource(source);

        Set<BlockPosition> copy = new HashSet<>(positions);
        this.oxygenBySource.put(source, copy);
        for (BlockPosition position : copy) {
            this.oxygenRefCounts.merge(position, 1, Integer::sum);
            this.oxygenOwners.computeIfAbsent(position, ignored -> new HashSet<>()).add(source);
        }
    }

    public void removeOxygenSource(@Nonnull BlockPosition source) {
        Set<BlockPosition> previous = this.oxygenBySource.remove(source);
        if (previous == null) {
            return;
        }

        for (BlockPosition position : previous) {
            this.oxygenRefCounts.computeIfPresent(position, (ignored, count) -> count <= 1 ? null : count - 1);
            this.oxygenOwners.computeIfPresent(position, (ignored, owners) -> {
                owners.remove(source);
                return owners.isEmpty() ? null : owners;
            });
        }
    }

    public int oxygenSourceSize(@Nonnull BlockPosition source) {
        return this.oxygenBySource.getOrDefault(source, Collections.emptySet()).size();
    }

    @Nonnull
    public Set<BlockPosition> oxygenSourcesNear(@Nonnull Block block) {
        Set<BlockPosition> sources = new HashSet<>();
        for (BlockFace face : DIRTY_NEIGHBORS) {
            BlockPosition position = new BlockPosition(block.getRelative(face).getLocation());
            sources.addAll(this.oxygenOwners.getOrDefault(position, Collections.emptySet()));
        }
        return sources;
    }

    /**
     * Compatibility helper for older callers. New Sealer code should use replaceOxygenSource.
     */
    public void addOxygenBlock(@Nonnull BlockPosition l) {
        this.oxygenRefCounts.merge(l, 1, Integer::sum);
    }

    public boolean isOxygenBlock(@Nonnull Location l) {
        OxygenZoneManager zones = Galactifun.oxygenZoneManager();
        return this.oxygenRefCounts.containsKey(new BlockPosition(l))
                || (zones != null && zones.providesOxygen(l));
    }

    /**
     * Samples feet/body/eye cells so slabs, trapdoors and block-edge positions do not cause false
     * suffocation while the player is otherwise inside a breathable room.
     */
    public boolean isOxygenProtected(@Nonnull Player player) {
        for (Location sample : playerSamples(player)) {
            if (isOxygenBlock(sample)) {
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

    /**
     * Full clear retained for shutdown/tests. Runtime Sealer scans should update only their source.
     */
    public void resetOxygenBlocks() {
        this.oxygenBySource.clear();
        this.oxygenRefCounts.clear();
        this.oxygenOwners.clear();
    }
}
