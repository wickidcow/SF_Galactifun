package io.github.addoncommunity.galactifun.core;

import org.bukkit.Material;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.base.GalactifunHead;
import io.github.addoncommunity.galactifun.core.categories.AssemblyItemGroup;
import io.github.addoncommunity.galactifun.core.categories.GalacticItemGroup;
import io.github.mooy1.infinitylib.groups.MultiGroup;
import io.github.mooy1.infinitylib.groups.SubGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.addoncommunity.galactifun.util.CustomItemStack;

/**
 * Slimefun item categories
 *
 * @author Mooy1
 */
public final class CoreItemGroup {

    private CoreItemGroup() {
    }

    /* cheat categories */
    public static final ItemGroup ASSEMBLY = new SubGroup(
            "assembly", new CustomItemStack(Material.SMITHING_TABLE, "&fAssembly Table Recipes")
    );

    /* normal categories */
    public static final ItemGroup EQUIPMENT = new SubGroup(
            "equipment", new CustomItemStack(Material.IRON_HELMET, "&fSpace Equipment")
    );
    public static final ItemGroup ITEMS = new SubGroup(
            "items", new CustomItemStack(GalactifunHead.ROCKET, "&fRockets, Fuel & Items")
    );
    public static final ItemGroup COMPONENTS = new SubGroup(
            "components", new CustomItemStack(Material.IRON_INGOT, "&fGases & Components")
    );
    public static final ItemGroup MACHINES = new SubGroup(
            "machines", new CustomItemStack(Material.REDSTONE_LAMP, "&fGalactifun Machines")
    );
    public static final ItemGroup BLOCKS = new SubGroup(
            "blocks", new CustomItemStack(Material.COBBLESTONE, "&fPlanet Blocks")
    );
    public static final ItemGroup RELICS = new SubGroup(
            "relics", new CustomItemStack(Material.CHISELED_POLISHED_BLACKSTONE, "&fRelics")
    );

    public static final AssemblyItemGroup ASSEMBLY_CATEGORY = new AssemblyItemGroup(
            Galactifun.createKey("assembly_flex"),
            new CustomItemStack(Material.SMITHING_TABLE, "&fAssembly Table Recipes"));

    public static void setup(Galactifun galactifun) {
        ItemGroup universe = new GalacticItemGroup(
                Galactifun.createKey("galactic_flex"),
                new CustomItemStack(Material.END_STONE, "&bPlanets & Universe")
        );

        // Preserve the existing Legacy MultiGroup implementation but surface the most useful player
        // workflows first, mirroring the clearer category layout used by newer Galactifun/SF5 ports.
        new MultiGroup(
                "main",
                new CustomItemStack(Material.BEACON, "&bGalactifun"),
                universe,
                EQUIPMENT,
                MACHINES,
                ITEMS,
                COMPONENTS,
                BLOCKS,
                ASSEMBLY_CATEGORY,
                RELICS
        ).register(galactifun);
    }
}
