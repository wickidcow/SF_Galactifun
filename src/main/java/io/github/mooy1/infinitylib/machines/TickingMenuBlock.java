package io.github.mooy1.infinitylib.machines;

import io.github.addoncommunity.galactifun.util.SFStorage;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

@ParametersAreNonnullByDefault
public abstract class TickingMenuBlock extends MenuBlock {

    public TickingMenuBlock(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        addItemHandler(new BlockTicker() {

            @Override
            public boolean isSynchronized() {
                return synchronous();
            }

            @Override
            public void tick(Block b, SlimefunItem item, ASlimefunDataContainer data) {
                BlockMenu menu = SFStorage.menu(b);
                if (menu != null) {
                    TickingMenuBlock.this.tick(b, menu);
                }
            }

        });
    }

    protected abstract void tick(Block b, BlockMenu menu);

    protected boolean synchronous() {
        return false;
    }

}
