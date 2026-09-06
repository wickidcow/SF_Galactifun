package io.github.addoncommunity.galactifun.base.items;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.core.managers.LandingHatchManager.LandingTarget;
import io.github.addoncommunity.galactifun.util.Messages;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;

/**
 * A persistent, selectable rocket landing target that remains impermeable to Oxygen Sealer scans.
 */
public final class LandingHatch extends SlimefunItem {

    public LandingHatch(
            @Nonnull ItemGroup category,
            @Nonnull SlimefunItemStack item,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe
    ) {
        super(category, item, recipeType, recipe);

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                Galactifun.landingHatchManager().register(event.getBlock());
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                Galactifun.landingHatchManager().unregister(event.getBlock());
            }
        });

        addItemHandler((BlockUseHandler) event -> event.getClickedBlock().ifPresent(block -> {
            event.cancel();
            LandingTarget target = Galactifun.landingHatchManager().register(block);
            Messages.yellow(event.getPlayer(), "Landing Hatch registered at "
                    + target.x() + " " + target.y() + " " + target.z()
                    + " in " + target.worldName() + ".");
        }));
    }
}
