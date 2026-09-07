package io.github.addoncommunity.galactifun.core.managers;

import javax.annotation.Nonnull;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.util.Messages;
import io.github.addoncommunity.galactifun.util.SFStorage;
import io.github.mooy1.infinitylib.common.Events;

/**
 * Adds persistent destination behavior to the existing Landing Hatch item without changing its ID/recipe.
 */
public final class LandingHatchListener implements Listener {

    public LandingHatchListener() {
        Events.registerListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(@Nonnull BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (SFStorage.isItem(block, BaseItems.LANDING_HATCH.getItemId())) {
            Galactifun.landingHatchManager().register(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBreak(@Nonnull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (SFStorage.isItem(block, BaseItems.LANDING_HATCH.getItemId())) {
            Galactifun.landingHatchManager().unregister(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onInteract(@Nonnull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!SFStorage.isItem(block, BaseItems.LANDING_HATCH.getItemId())) {
            return;
        }

        var target = Galactifun.landingHatchManager().register(block);
        Messages.yellow(event.getPlayer(), "Landing Hatch destination: "
                + target.x() + " " + target.y() + " " + target.z()
                + " in " + target.worldName() + ".");
    }
}
