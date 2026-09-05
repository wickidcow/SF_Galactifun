package io.github.addoncommunity.galactifun.base.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
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
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.mooy1.infinitylib.common.PersistentType;
import io.github.mooy1.infinitylib.common.StackUtils;
import io.github.mooy1.infinitylib.machines.TickingMenuBlock;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public final class LaunchPadCore extends TickingMenuBlock {

    private static final int[] BACKGROUND = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            23, 25, 26,
            32, 34, 35,
            41, 42, 43, 44,
            50, 51, 52, 53
    };
    private static final int[] BORDER = {
            18, 19, 20, 21, 22, 31, 40, 49
    };
    private static final int[] INVENTORY_SLOTS = {
            27, 28, 29, 30, 36, 37, 38, 39, 45, 46, 47, 48
    };
    private static final int FUEL_SLOT = 33;

    public LaunchPadCore(ItemGroup category, SlimefunItemStack item, RecipeType type, ItemStack[] recipe) {
        super(category, item, type, recipe);
        addItemHandler((io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler) LaunchPadCore::onInteract);
    }

    @Override
    protected void tick(@Nonnull Block block, @Nonnull BlockMenu menu) {
        Block rocketBlock = block.getRelative(BlockFace.UP);
        SlimefunItem sfItem = BlockStorage.check(rocketBlock);
        if (!(sfItem instanceof Rocket rocket) || Rocket.isLaunchLocked(rocketBlock)) {
            return;
        }

        Location location = rocketBlock.getLocation();
        String string = Objects.requireNonNullElse(BlockStorage.getLocationInfo(location, "fuel"), "0");
        int fuel = Integer.parseInt(string);
        string = BlockStorage.getLocationInfo(location, "fuelType");

        if (fuel < rocket.fuelCapacity()) {
            ItemStack fuelItem = menu.getItemInSlot(FUEL_SLOT);
            if (fuelItem != null) {
                String id = StackUtils.getIdOrType(fuelItem);
                if ((string == null || id.equals(string)) && rocket.allowedFuels().containsKey(id)) {
                    menu.consumeItem(FUEL_SLOT);
                    BSUtils.addBlockInfo(rocketBlock, "fuel", ++fuel);
                    if (string == null) {
                        BlockStorage.addBlockInfo(location, "fuelType", id);
                    }
                }
            }
        }

        if (!(rocketBlock.getState() instanceof Skull skull)) {
            return;
        }

        PersistentDataContainer container = skull.getPersistentDataContainer();
        List<ItemStack> cargo = container.getOrDefault(
                Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>());

        for (int slot : INVENTORY_SLOTS) {
            ItemStack input = menu.getItemInSlot(slot);
            if (input == null || input.getType().isAir()) {
                continue;
            }

            ItemStack one = input.asOne();
            boolean stored = false;
            for (ItemStack stack : cargo) {
                if (ItemUtils.canStack(stack, one) && stack.getAmount() < stack.getMaxStackSize()) {
                    stack.setAmount(stack.getAmount() + 1);
                    stored = true;
                    break;
                }
            }

            if (!stored && cargo.size() < rocket.storageCapacity()) {
                cargo.add(one);
                stored = true;
            }

            if (stored) {
                menu.consumeItem(slot);
            }
            break;
        }

        container.set(Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, cargo);
        skull.update();
    }

    public static boolean canBreak(@Nonnull Player player, @Nonnull Block block) {
        Block rocket = block.getRelative(BlockFace.UP);
        if (Rocket.isLaunchLocked(rocket)) {
            player.sendMessage(ChatColor.RED + "You cannot break the launchpad a rocket is launching on!");
            return false;
        }
        return true;
    }

    @Override
    protected void onBreak(BlockBreakEvent event, @Nonnull BlockMenu menu) {
        if (!canBreak(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            return;
        }

        Location location = event.getBlock().getLocation();
        menu.dropItems(location, INVENTORY_SLOTS);
        menu.dropItems(location, FUEL_SLOT);

        Block rocketBlock = event.getBlock().getRelative(BlockFace.UP);
        SlimefunItem item = BlockStorage.check(rocketBlock);
        if (!(item instanceof Rocket)) {
            return;
        }

        World world = location.getWorld();
        dropStoredRocketContents(world, rocketBlock);
        rocketBlock.setType(Material.AIR);
        BlockStorage.clearBlockInfo(rocketBlock);
        world.dropItemNaturally(rocketBlock.getLocation(), item.getItem().clone());
    }

    private static void dropStoredRocketContents(World world, Block rocketBlock) {
        Location dropLocation = rocketBlock.getLocation().add(0.5, 0.5, 0.5);

        if (rocketBlock.getState() instanceof Skull skull) {
            List<ItemStack> cargo = skull.getPersistentDataContainer().getOrDefault(
                    Rocket.CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>());
            for (ItemStack stack : cargo) {
                if (stack != null && !stack.getType().isAir()) {
                    world.dropItemNaturally(dropLocation, stack.clone());
                }
            }
        }

        int fuel = BSUtils.getStoredInt(rocketBlock.getLocation(), "fuel");
        String fuelType = BlockStorage.getLocationInfo(rocketBlock.getLocation(), "fuelType");
        if (fuel <= 0 || fuelType == null) {
            return;
        }

        ItemStack fuelItem = StackUtils.itemByIdOrType(fuelType);
        int max = Math.max(1, fuelItem.getMaxStackSize());
        while (fuel > 0) {
            int amount = Math.min(max, fuel);
            world.dropItemNaturally(dropLocation, fuelItem.asQuantity(amount));
            fuel -= amount;
        }
    }

    @Override
    protected void setup(@Nonnull BlockMenuPreset preset) {
        preset.drawBackground(BACKGROUND);
        for (int i : BORDER) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }
        preset.addItem(24, new CustomItemStack(
                HeadTexture.FUEL_BUCKET.getAsItemStack(),
                "&6Insert Fuel Here"
        ), ChestMenuUtils.getEmptyClickHandler());
    }

    @Override
    protected int[] getInputSlots() {
        return new int[] {FUEL_SLOT};
    }

    @Override
    protected int[] getOutputSlots() {
        return new int[0];
    }

    private static void onInteract(@Nonnull PlayerRightClickEvent event) {
        Optional<Block> optional = event.getClickedBlock();
        if (optional.isEmpty()) {
            return;
        }

        Block block = optional.get();
        Player player = event.getPlayer();
        if (isSurroundedByFloors(block)) {
            SlimefunItem item = SlimefunItem.getByItem(event.getItem());
            if (!(item instanceof Rocket)) {
                event.cancel();
            }
            BlockStorage.getInventory(block).open(player);
        } else {
            event.cancel();
            player.sendMessage(ChatColor.RED
                    + "Surround this block with 8 launch pad floors before attempting to use it");
        }
    }

    private static boolean isSurroundedByFloors(Block block) {
        for (BlockFace face : Util.SURROUNDING_FACES) {
            if (!BlockStorage.check(block.getRelative(face), BaseItems.LAUNCH_PAD_FLOOR.getItemId())) {
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
