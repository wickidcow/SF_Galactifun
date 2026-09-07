package io.github.addoncommunity.galactifun.core.managers;

import javax.annotation.Nonnull;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import io.github.addoncommunity.galactifun.base.items.protection.OxygenSealer;
import io.github.mooy1.infinitylib.common.Events;

/**
 * Invalidates only Oxygen Sealer caches that touch a changed room boundary.
 */
public final class AtmosphereCacheListener implements Listener {

    public AtmosphereCacheListener() {
        Events.registerListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(@Nonnull BlockPlaceEvent event) {
        OxygenSealer.markDirtyAround(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onBreak(@Nonnull BlockBreakEvent event) {
        OxygenSealer.markDirtyAround(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onBlockExplode(@Nonnull BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            OxygenSealer.markDirtyAround(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onEntityExplode(@Nonnull EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            OxygenSealer.markDirtyAround(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPistonExtend(@Nonnull BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            OxygenSealer.markDirtyAround(block);
            OxygenSealer.markDirtyAround(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPistonRetract(@Nonnull BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            OxygenSealer.markDirtyAround(block);
            OxygenSealer.markDirtyAround(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onInteract(@Nonnull PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            OxygenSealer.markDirtyAround(event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onRedstone(@Nonnull BlockRedstoneEvent event) {
        OxygenSealer.markDirtyAround(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onFluid(@Nonnull BlockFromToEvent event) {
        OxygenSealer.markDirtyAround(event.getBlock());
        OxygenSealer.markDirtyAround(event.getToBlock());
    }
}
