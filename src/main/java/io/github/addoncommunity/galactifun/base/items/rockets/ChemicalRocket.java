package io.github.addoncommunity.galactifun.base.items.rockets;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.api.items.Rocket;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Gas;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;

public final class ChemicalRocket extends Rocket {

    public ChemicalRocket(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                          int fuelCapacity, int storageCapacity) {
        super(category, item, recipeType, recipe, fuelCapacity, storageCapacity);
    }

    @Override
    protected Map<ItemStack, Double> getAllowedFuels() {
        return Map.of(
                SlimefunItems.OIL_BUCKET.clone(), .5,
                Gas.HYDROCARBONS.item().clone(), .5,
                SlimefunItems.FUEL_BUCKET.clone(), 1.0,
                Gas.HYDROGEN.item().clone(), 3.5,
                Gas.AMMONIA.item().clone(), 4.0,
                Gas.METHANE.item().clone(), 6.0
        );
    }
}
