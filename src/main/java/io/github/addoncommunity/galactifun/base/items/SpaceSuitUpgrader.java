package io.github.addoncommunity.galactifun.base.items;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.addoncommunity.galactifun.api.items.spacesuit.SpaceSuit;
import io.github.addoncommunity.galactifun.api.items.spacesuit.SpaceSuitUpgrade;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

public final class SpaceSuitUpgrader extends AContainer {

    public SpaceSuitUpgrader(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);
    }

    @Nullable
    @Override
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        ItemStack suitStack = null;
        SpaceSuit suit = null;
        SpaceSuitUpgrade upgrade = null;
        ItemStack upgradeStack = null;

        for (int slot : getInputSlots()) {
            ItemStack item = inv.getItemInSlot(slot);

            if (item != null && item.hasItemMeta()) {
                SlimefunItem sfItem = SlimefunItem.getByItem(item);

                if (suit == null && sfItem instanceof SpaceSuit) {
                    suit = (SpaceSuit) sfItem;
                    suitStack = item;
                } else if (upgrade == null && sfItem instanceof SpaceSuitUpgrade) {
                    upgrade = (SpaceSuitUpgrade) sfItem;
                    upgradeStack = item;
                }

                if (suit != null && upgrade != null) {
                    // Split exactly one suit item before changing metadata. Applying the upgrade to
                    // a clone of the full input stack upgrades every item in that stack at once.
                    ItemStack newSuit = suitStack.clone();
                    newSuit.setAmount(1);
                    ItemMeta meta = newSuit.getItemMeta();
                    if (upgrade.addTo(meta, suit.maxUpgrades())) {
                        newSuit.setItemMeta(meta);

                        ItemStack consumedUpgrade = upgradeStack.clone();
                        consumedUpgrade.setAmount(1);

                        upgradeStack.setAmount(upgradeStack.getAmount() - 1);
                        suitStack.setAmount(suitStack.getAmount() - 1);
                        return new MachineRecipe(
                                5 / getSpeed(),
                                new ItemStack[] {consumedUpgrade},
                                new ItemStack[] {newSuit}
                        );
                    }
                }
            }
        }

        return null;
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.ANVIL);
    }

    @Nonnull
    @Override
    public String getMachineIdentifier() {
        return "SPACE_SUIT_UPGRADER";
    }

}
