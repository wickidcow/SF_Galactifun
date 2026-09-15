package io.github.addoncommunity.galactifun.base.items;

import io.github.addoncommunity.galactifun.util.SFStorage;

import io.github.addoncommunity.galactifun.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import io.github.addoncommunity.galactifun.api.items.Rocket;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.util.BSUtils;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.mooy1.infinitylib.common.PersistentType;
import io.github.mooy1.infinitylib.common.StackUtils;
import io.github.mooy1.infinitylib.machines.TickingMenuBlock;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public final class LaunchPadCore extends TickingMenuBlock {

    private static final int[] BACKGROUND = {
            36, 37, 38, 39, 41, 42, 43, 44,
            45, 46, 47, 48, 50, 51, 52, 53
    };
    private static final int[] CARGO_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int STATUS_SLOT = 40;
    private static final int FUEL_SLOT = 49;

    public LaunchPadCore(ItemGroup category, SlimefunItemStack item, RecipeType type, ItemStack[] recipe) {
        super(category, item, type, recipe);
        addItemHandler((BlockUseHandler) LaunchPadCore::onInteract);
    }

    @Override
    protected void tick(@Nonnull Block block, @Nonnull BlockMenu menu) {
        Block b = block.getRelative(BlockFace.UP);

        SlimefunItem sfItem = SFStorage.item(b);
        if (!(sfItem instanceof Rocket rocket)) {
            menu.replaceExistingItem(STATUS_SLOT, idleStatusItem());
            return;
        }

        if (!Rocket.isLaunchLocked(b)) {
            // Older Galactifun builds moved launch-pad cargo into the rocket skull's PDC every tick.
            // Restore that cargo into the visible chest-like slots when possible so upgrades do not
            // strand previously stored items in hidden data.
            restoreLegacyCargo(menu, b);

            Location l = b.getLocation();
            String string = Objects.requireNonNullElse(SFStorage.getData(l, "fuel"), "0");
            int fuel = Integer.parseInt(string);

            string = SFStorage.getData(l, "fuelType");

            // Fuel remains intentionally different from cargo: valid fuel is consumed from this one
            // slot into the rocket's internal tank, preserving each fuel type's efficiency value.
            if (fuel < rocket.fuelCapacity()) {
                ItemStack fuelItem = menu.getItemInSlot(FUEL_SLOT);
                if (fuelItem != null) {
                    String id = StackUtils.getIdOrType(fuelItem);

                    if ((string == null || id.equals(string)) && rocket.allowedFuels().containsKey(id)) {
                        menu.consumeItem(FUEL_SLOT);
                        BSUtils.addBlockInfo(l.getBlock(), "fuel", ++fuel);
                        if (string == null) {
                            SFStorage.setData(l, "fuelType", id);
                        }
                    }
                }
            }
        }

        updateStatus(menu, b, rocket);
    }

    private static void restoreLegacyCargo(@Nonnull BlockMenu menu, @Nonnull Block rocketBlock) {
        if (!(rocketBlock.getState() instanceof Skull skull)) {
            return;
        }

        PersistentDataContainer container = skull.getPersistentDataContainer();
        List<ItemStack> legacyCargo = new ArrayList<>(
                container.getOrDefault(Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>())
        );
        if (legacyCargo.isEmpty()) {
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : legacyCargo) {
            int emptySlot = firstEmptyCargoSlot(menu);
            if (emptySlot == -1) {
                remaining.add(stack.clone());
                continue;
            }
            menu.replaceExistingItem(emptySlot, stack.clone());
        }

        if (remaining.isEmpty()) {
            container.remove(Rocket.CARGO_KEY);
        } else {
            container.set(Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, remaining);
        }
        skull.update();
    }

    private static int firstEmptyCargoSlot(@Nonnull BlockMenu menu) {
        for (int slot : CARGO_SLOTS) {
            ItemStack item = menu.getItemInSlot(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private static int visibleCargoStackCount(@Nonnull BlockMenu menu) {
        int count = 0;
        for (int slot : CARGO_SLOTS) {
            ItemStack item = menu.getItemInSlot(slot);
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private static int legacyCargoStackCount(@Nonnull Block rocketBlock) {
        if (!(rocketBlock.getState() instanceof Skull skull)) {
            return 0;
        }
        return skull.getPersistentDataContainer()
                .getOrDefault(Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>())
                .size();
    }

    /**
     * Returns all cargo stacks currently attached to this rocket, including any legacy hidden cargo
     * that has not yet been migrated back into visible launch-pad slots.
     */
    public static int cargoStackCountForRocket(@Nonnull Block rocketBlock) {
        int count = legacyCargoStackCount(rocketBlock);
        Block launchPad = rocketBlock.getRelative(BlockFace.DOWN);
        if (!SFStorage.isItem(launchPad, BaseItems.LAUNCH_PAD_CORE.getItemId())) {
            return count;
        }

        BlockMenu menu = SFStorage.menu(launchPad);
        return menu == null ? count : count + visibleCargoStackCount(menu);
    }

    /**
     * Removes visible launch-pad cargo and returns clones for delivery at the rocket destination.
     * Legacy hidden cargo remains on the rocket PDC and is handled by Rocket for backward compatibility.
     */
    @Nonnull
    public static List<ItemStack> takeVisibleCargoForLaunch(@Nonnull Block rocketBlock) {
        List<ItemStack> cargo = new ArrayList<>();
        Block launchPad = rocketBlock.getRelative(BlockFace.DOWN);
        if (!SFStorage.isItem(launchPad, BaseItems.LAUNCH_PAD_CORE.getItemId())) {
            return cargo;
        }

        BlockMenu menu = SFStorage.menu(launchPad);
        if (menu == null) {
            return cargo;
        }

        for (int slot : CARGO_SLOTS) {
            ItemStack item = menu.getItemInSlot(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            cargo.add(item.clone());
            menu.consumeItem(slot, item.getAmount());
        }
        return cargo;
    }

    private static void updateStatus(@Nonnull BlockMenu menu, @Nonnull Block rocketBlock, @Nonnull Rocket rocket) {
        Location location = rocketBlock.getLocation();
        int fuel = BSUtils.getStoredInt(location, "fuel");
        String fuelType = SFStorage.getData(location, "fuelType");
        String fuelName = "None";
        double efficiency = 0D;

        if (fuelType != null) {
            ItemStack fuelItem = StackUtils.itemByIdOrType(fuelType);
            fuelName = fuelItem == null ? fuelType : ItemUtils.getItemName(fuelItem);
            efficiency = rocket.allowedFuels().getOrDefault(fuelType, 0D);
        }

        int cargoStacks = visibleCargoStackCount(menu) + legacyCargoStackCount(rocketBlock);
        String cargoLine = cargoStacks > rocket.storageCapacity()
                ? "&cCargo: &f" + cargoStacks + "/" + rocket.storageCapacity() + " stacks &c(OVER CAPACITY)"
                : "&7Cargo: &f" + cargoStacks + "/" + rocket.storageCapacity() + " stacks";

        menu.replaceExistingItem(STATUS_SLOT, new CustomItemStack(
                HeadTexture.FUEL_BUCKET.getAsItemStack(),
                "&6Rocket Status",
                "&7Status: &f" + Rocket.launchStatus(rocketBlock),
                "&7Fuel: &f" + fuel + "/" + rocket.fuelCapacity(),
                "&7Fuel Type: &f" + fuelName,
                "&7Efficiency: &f" + efficiency + "x",
                "&7Maximum Range: &f" + Util.formatDistance(rocket.maxDistanceFor(fuel, fuelType)),
                cargoLine,
                "",
                "&7Cargo stays visible until liftoff.",
                "&7Insert rocket fuel in the slot below."
        ));
    }

    @Nonnull
    private static ItemStack idleStatusItem() {
        return new CustomItemStack(
                HeadTexture.FUEL_BUCKET.getAsItemStack(),
                "&6Rocket Status",
                "&7Place a rocket on the launch pad.",
                "&7The top 36 slots are visible cargo storage.",
                "&7Insert rocket fuel in the slot below."
        );
    }

    public static boolean canBreak(@Nonnull Player p, @Nonnull Block b) {
        if (Rocket.isLaunchLocked(b.getRelative(BlockFace.UP))) {
            Messages.red(p, "You cannot break the launchpad while a rocket is reserved or launching!");
            return false;
        }
        return true;
    }

    @Override
    protected void onBreak(BlockBreakEvent e, @Nonnull BlockMenu menu) {
        if (canBreak(e.getPlayer(), e.getBlock())) {
            Location l = e.getBlock().getLocation();
            menu.dropItems(l, CARGO_SLOTS);
            menu.dropItems(l, FUEL_SLOT);

            Block rocketBlock = e.getBlock().getRelative(BlockFace.UP);
            SlimefunItem item = SFStorage.item(rocketBlock);

            if (item instanceof Rocket) {
                dropLegacyCargo(l, rocketBlock);
                World world = l.getWorld();
                rocketBlock.setType(Material.AIR);
                SFStorage.remove(rocketBlock);
                world.dropItemNaturally(rocketBlock.getLocation(), item.getItem().clone());
            }
        } else {
            e.setCancelled(true);
        }
    }

    private static void dropLegacyCargo(@Nonnull Location location, @Nonnull Block rocketBlock) {
        if (!(rocketBlock.getState() instanceof Skull skull)) {
            return;
        }

        PersistentDataContainer container = skull.getPersistentDataContainer();
        List<ItemStack> legacyCargo = container.getOrDefault(
                Rocket.CARGO_KEY,
                PersistentType.ITEM_STACK_LIST,
                new ArrayList<>()
        );
        if (legacyCargo.isEmpty()) {
            return;
        }

        World world = location.getWorld();
        for (ItemStack stack : legacyCargo) {
            world.dropItemNaturally(location, stack.clone());
        }
        container.remove(Rocket.CARGO_KEY);
        skull.update();
    }

    @Override
    protected void setup(@Nonnull BlockMenuPreset preset) {
        preset.drawBackground(BACKGROUND);
        preset.addItem(STATUS_SLOT, idleStatusItem(), ChestMenuUtils.getEmptyClickHandler());
    }

    @Override
    protected int[] getInputSlots() {
        return new int[] {FUEL_SLOT};
    }

    @Override
    protected int[] getOutputSlots() {
        return new int[0];
    }

    private static void onInteract(@Nonnull PlayerRightClickEvent e) {
        Optional<Block> ob = e.getClickedBlock();
        if (ob.isPresent()) {
            Block b = ob.get();
            Player p = e.getPlayer();

            if (isSurroundedByFloors(b)) {
                SlimefunItem item = SlimefunItem.getByItem(e.getItem());
                if (!(item instanceof Rocket)) {
                    e.cancel();
                }

                BlockMenu menu = SFStorage.menu(b);
                if (menu != null) {
                    menu.open(p);
                }
            } else {
                e.cancel();
                Messages.red(p, "Surround this block with 8 launch pad floors before attempting to use it");
            }
        }
    }

    private static boolean isSurroundedByFloors(Block b) {
        for (BlockFace face : Util.SURROUNDING_FACES) {
            if (!SFStorage.isItem(b.getRelative(face), BaseItems.LAUNCH_PAD_FLOOR.getItemId())) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected boolean synchronous() {
        return true;
    }
}
