package io.github.addoncommunity.galactifun.base.items;

import javax.annotation.Nonnull;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Gas;
import io.github.addoncommunity.galactifun.base.BaseMats;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.mooy1.infinitylib.machines.MenuBlock;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;

public class Electrolyzer extends AContainer {

    public Electrolyzer(SlimefunItemStack item, ItemStack[] recipe) {
        super(CoreItemGroup.MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
        setCapacity(512);
        setEnergyConsumption(128);
        setProcessingSpeed(1);
    }

    @Override
    protected void registerDefaultRecipes() {
        registerRecipe(new ItemStack(Material.WATER_BUCKET), Gas.WATER.item(), new ItemStack(Material.BUCKET));
        registerRecipe(Gas.WATER.item(), Gas.HYDROGEN.item().asQuantity(2), Gas.OXYGEN.item());

        registerRecipe(SlimefunItems.OIL_BUCKET, Gas.HYDROCARBONS.item(), new ItemStack(Material.BUCKET));
        registerRecipe(Gas.HYDROCARBONS.item(), Gas.METHANE.item().asQuantity(6));
        registerRecipe(Gas.METHANE.item(), Gas.HYDROGEN.item().asQuantity(4), SlimefunItems.CARBON);
        registerRecipe(Gas.CARBON_DIOXIDE.item(), Gas.OXYGEN.item().asQuantity(2), SlimefunItems.CARBON);

        registerRecipe(Gas.AMMONIA.item(), Gas.HYDROGEN.item().asQuantity(3), Gas.NITROGEN.item());

        registerRecipe(BaseMats.MARS_DUST, SlimefunItems.IRON_DUST.asQuantity(2), Gas.OXYGEN.item().asQuantity(3));
        registerRecipe(BaseMats.MOON_DUST, Gas.HELIUM.item().asQuantity(3));
    }

    private void registerRecipe(Object in, Object... out) {
        ItemStack inItem = toItem(in);
        ItemStack[] outItems = new ItemStack[out.length];
        for (int i = 0; i < out.length; i++) {
            outItems[i] = toItem(out[i]);
        }
        registerRecipe(10, new ItemStack[]{inItem}, outItems);
    }

    private static ItemStack toItem(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ItemStack is) return is;
        if (obj instanceof SlimefunItemStack sfi) return sfi.clone();
        if (obj instanceof Material mat) return new ItemStack(mat);
        if (obj instanceof Gas gas) return gas.item().clone();
        throw new IllegalArgumentException("Unknown item type: " + obj);
    }

    @Override
    public ItemStack getProgressBar() {
        return MenuBlock.PROCESSING_ITEM;
    }

    @Nonnull
    @Override
    public String getMachineIdentifier() {
        return "ELECTROLYZER";
    }

}
