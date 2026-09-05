package io.github.addoncommunity.galactifun.base.items.knowledge;

import io.github.addoncommunity.galactifun.util.Messages;

import javax.annotation.Nonnull;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;

public final class PlanetaryAnalyzer extends SimpleSlimefunItem<BlockUseHandler> {

    public PlanetaryAnalyzer(SlimefunItemStack item, ItemStack[] recipe) {
        super(CoreItemGroup.MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
    }

    @Nonnull
    @Override
    public BlockUseHandler getItemHandler() {
        return e -> {
            Player p = e.getPlayer();
            NamespacedKey key = Galactifun.createKey("analyzing_" + p.getUniqueId());

            PlanetaryWorld world = Galactifun.worldManager().getWorld(p.getWorld());
            if (world == null) {
                Messages.red(p, "You must be on a planet to use this!");
                return;
            }

            if (PersistentDataAPI.getBoolean(world.worldStorage(), key)) {
                Messages.red(p, "Already analyzing!");
                return;
            }

            Messages.green(p, "Analyzing planet " + world.name());
            PersistentDataAPI.setBoolean(world.worldStorage(), key, true);
            Scheduler.run(30 * 60 * 20, () -> {
                PersistentDataAPI.setBoolean(world.worldStorage(), key, false);
                KnowledgeLevel.BASIC.set(p, world);
            });
        };
    }

}
