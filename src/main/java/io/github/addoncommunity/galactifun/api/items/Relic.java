package io.github.addoncommunity.galactifun.api.items;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.addoncommunity.galactifun.core.CoreRecipeType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.collections.RandomizedSet;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;

public class Relic extends SlimefunItem {

    private final RandomizedSet<ItemStack> optionals;
    private final Map<ItemStack, IntIntPair> required = new HashMap<>();

    public Relic(SlimefunItemStack item, RelicSettings settings, PlanetaryWorld... planets) {
        super(CoreItemGroup.RELICS, item, CoreRecipeType.WORLD_GEN, getFromPlanets(planets));
        this.optionals = settings.optionals;
        this.required.putAll(settings.required);
    }

    private static ItemStack[] getFromPlanets(PlanetaryWorld[] planets) {
        ItemStack[] items = new ItemStack[planets.length];

        for (int i = 0; i < planets.length; i++) {
            items[i] = planets[i].item();
        }

        return Arrays.copyOf(items, 9);
    }

    public RandomizedSet<ItemStack> optionals() { return this.optionals; }
    public RandomizedSet<ItemStack> getOptionals() { return this.optionals; }
    public Map<ItemStack, IntIntPair> required() { return this.required; }
    public Map<ItemStack, IntIntPair> getRequired() { return this.required; }

    public static final class RelicSettings {
        private final RandomizedSet<ItemStack> optionals = new RandomizedSet<>();
        private final Map<ItemStack, IntIntPair> required = new HashMap<>();

        public RelicSettings addOptional(@Nonnull ItemStack item, float weight) {
            optionals.add(item, weight);
            return this;
        }

        public RelicSettings addOptional(@Nonnull SlimefunItemStack item, float weight) {
            optionals.add(item.clone(), weight);
            return this;
        }

        public RelicSettings addRequired(@Nonnull ItemStack item, int min, int max) {
            required.put(item, new IntIntImmutablePair(min, max));
            return this;
        }

        public RelicSettings addRequired(@Nonnull SlimefunItemStack item, int min, int max) {
            required.put(item.clone(), new IntIntImmutablePair(min, max));
            return this;
        }

        public RandomizedSet<ItemStack> optionals() { return this.optionals; }
        public RandomizedSet<ItemStack> getOptionals() { return this.optionals; }
        public Map<ItemStack, IntIntPair> required() { return this.required; }
        public Map<ItemStack, IntIntPair> getRequired() { return this.required; }
    }
}
