package io.github.addoncommunity.galactifun.base.items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.core.managers.TravelManager.TravelType;
import io.github.addoncommunity.galactifun.util.BSUtils;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.mooy1.infinitylib.common.Events;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.machines.MenuBlock;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class StargateController extends SlimefunItem implements Listener {

    private static final int[] BACKGROUND = new int[] {1, 2, 6, 7, 8};
    private static final int ADDRESS_SLOT = 3;
    private static final int DESTINATION_SLOT = 4;
    private static final int DEACTIVATE_SLOT = 5;

    private static final ComponentPosition[] RING_POSITIONS = new ComponentPosition[] {
            new ComponentPosition(0, 1),
            new ComponentPosition(0, -1),
            new ComponentPosition(1, -2),
            new ComponentPosition(1, 2),
            new ComponentPosition(5, -2),
            new ComponentPosition(5, 2),
            new ComponentPosition(2, 3),
            new ComponentPosition(3, 3),
            new ComponentPosition(4, 3),
            new ComponentPosition(2, -3),
            new ComponentPosition(3, -3),
            new ComponentPosition(4, -3),
            new ComponentPosition(6, -1),
            new ComponentPosition(6, 0),
            new ComponentPosition(6, 1),
    };

    private static final ComponentPosition[] PORTAL_POSITIONS;
    private static final int GATEWAY_TICKS = 201;

    static {
        List<ComponentPosition> portalPositions = new LinkedList<>(Arrays.asList(
                new ComponentPosition(1, -1),
                new ComponentPosition(1, 0),
                new ComponentPosition(1, 1)
        ));
        for (int y = 2; y <= 4; y++) {
            for (int z = -2; z <= 2; z++) {
                portalPositions.add(new ComponentPosition(y, z));
            }
        }
        portalPositions.add(new ComponentPosition(5, -1));
        portalPositions.add(new ComponentPosition(5, 0));
        portalPositions.add(new ComponentPosition(5, 1));
        PORTAL_POSITIONS = portalPositions.toArray(new ComponentPosition[0]);
    }

    public StargateController(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);
        Events.registerListener(this);

        addItemHandler((BlockUseHandler) event ->
                event.getClickedBlock().ifPresent(block -> onUse(event, event.getPlayer(), block)));

        addItemHandler(new BlockBreakHandler(true, true) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                if (Boolean.parseBoolean(BlockStorage.getLocationInfo(event.getBlock().getLocation(), "locked"))) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Deactivate the Stargate before destroying it");
                }
            }
        });
    }

    public static boolean isPartOfStargate(@Nonnull Block block) {
        for (ComponentPosition position : RING_POSITIONS) {
            if (!position.isInSameRing(block)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    public static Optional<List<Block>> getRingBlocks(@Nonnull Block block) {
        List<Block> rings = new ArrayList<>();
        for (ComponentPosition position : RING_POSITIONS) {
            if (position.isInSameRing(block)) {
                rings.add(position.getBlock(block));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(rings);
    }

    @Nonnull
    public static Optional<List<Block>> getPortalBlocks(@Nonnull Block block) {
        List<Block> portals = new ArrayList<>();
        for (ComponentPosition position : PORTAL_POSITIONS) {
            if (position.isPortal(block)) {
                portals.add(position.getBlock(block));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(portals);
    }

    public static void lockBlocks(Block controller, boolean lock) {
        String data = Boolean.toString(lock);
        getRingBlocks(controller).ifPresent(blocks ->
                blocks.forEach(block -> BlockStorage.addBlockInfo(block, "locked", data)));
        getPortalBlocks(controller).ifPresent(blocks ->
                blocks.forEach(block -> BlockStorage.addBlockInfo(block, "locked", data)));
    }

    private void onUse(PlayerRightClickEvent event, Player player, Block block) {
        if (!isPartOfStargate(block)) {
            player.sendMessage(ChatColor.RED + "The Stargate is not assembled!");
            return;
        }

        event.cancel();
        if (getPortalBlocks(block).isEmpty()) {
            for (ComponentPosition position : PORTAL_POSITIONS) {
                Block portal = position.getBlock(block);
                portal.setType(Material.END_GATEWAY);
                EndGateway gateway = (EndGateway) portal.getState();
                gateway.setAge(GATEWAY_TICKS);
                gateway.setExitLocation(block.getLocation());
                gateway.update(false, false);
            }

            String destinationAddress = BlockStorage.getLocationInfo(block.getLocation(), "destination");
            if (destinationAddress != null) {
                setDestination(destinationAddress, block, player);
            }

            lockBlocks(block, true);
            player.sendMessage(ChatColor.YELLOW + "Stargate activated!");
            return;
        }

        getMenu(block).open(player);
    }

    @Nonnull
    private ChestMenu getMenu(@Nonnull Block block) {
        ChestMenu menu = new ChestMenu(this.getItemName());
        for (int i : BACKGROUND) {
            menu.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        Location location = block.getLocation();
        String address = BlockStorage.getLocationInfo(location, "gfsgAddress");
        if (address == null) {
            String locationString = String.format(
                    "%s-%d-%d-%d",
                    block.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
            address = Integer.toHexString(locationString.hashCode());
            BlockStorage.addBlockInfo(block, "gfsgAddress", address);
        }

        String destination = BlockStorage.getLocationInfo(location, "destination");
        destination = destination == null ? "" : destination;

        String copyAddress = address;
        menu.addItem(ADDRESS_SLOT, new CustomItemStack(
                Material.BOOK,
                "&fAddress: " + address,
                "&7Click to send the address to chat"
        ), (player, i, stack, click) -> {
            player.sendMessage(Component.text()
                    .color(NamedTextColor.YELLOW)
                    .content("Address (click to copy): " + copyAddress)
                    .clickEvent(ClickEvent.copyToClipboard(copyAddress))
                    .build());
            player.closeInventory();
            return false;
        });

        menu.addItem(DEACTIVATE_SLOT, new CustomItemStack(
                Material.BARRIER,
                "&fClick to Deactivate the Stargate"
        ), (player, i, stack, click) -> {
            getPortalBlocks(block).ifPresent(blocks -> {
                for (Block portal : blocks) {
                    portal.setType(Material.AIR);
                    BlockStorage.clearBlockInfo(portal);
                }
            });
            lockBlocks(block, false);
            player.closeInventory();
            return false;
        });

        menu.addItem(DESTINATION_SLOT, new CustomItemStack(
                Material.RAIL,
                "&fClick to Set Destination",
                "&7Current Destination: " + destination
        ), (player, i, stack, click) -> {
            player.sendMessage(ChatColor.YELLOW + "Type in the destination address");
            ChatUtils.awaitInput(player, input -> setDestination(input, block, player));
            player.closeInventory();
            return false;
        });

        return menu;
    }

    private static void setDestination(String destination, Block block, Player player) {
        Location target;
        worldLoop: {
            for (BlockStorage storage : Slimefun.getRegistry().getWorlds().values()) {
                for (Map.Entry<Location, Config> configEntry : storage.getRawStorage().entrySet()) {
                    String blockAddress = configEntry.getValue().getString("gfsgAddress");
                    if (blockAddress != null && blockAddress.equals(destination)) {
                        target = configEntry.getKey();
                        break worldLoop;
                    }
                }
            }
            player.sendMessage(ChatColor.RED + "No destination found!");
            return;
        }

        if (getPortalBlocks(block).isEmpty()) {
            player.sendMessage(ChatColor.RED + "The Stargate is not lit for some reason...");
            return;
        }

        BSUtils.setStoredLocation(block.getLocation(), "dest", target);
        player.sendMessage(ChatColor.YELLOW + String.format(
                "Set Stargate destination to %d %d %d in %s",
                target.getBlockX(),
                target.getBlockY(),
                target.getBlockZ(),
                target.getWorld().getName()
        ));
        BlockStorage.addBlockInfo(block, "destination", destination);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onGateBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.END_GATEWAY
                && Boolean.parseBoolean(BlockStorage.getLocationInfo(block.getLocation(), "locked"))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Deactivate the Stargate before destroying it");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUsePortal(PlayerTeleportEndGatewayEvent event) {
        Location exit = event.getGateway().getExitLocation();
        if (exit == null || !(BlockStorage.check(exit) instanceof StargateController)) {
            return;
        }

        Location destination = BSUtils.getStoredLocation(exit, "dest");
        if (destination == null || destination.getWorld() == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasMetadata("disableStargate")) {
            return;
        }

        Block destinationController = destination.getBlock();
        if (BlockStorage.check(destinationController, BaseItems.STARGATE_CONTROLLER.getItemId())
                && StargateController.getPortalBlocks(destinationController).isEmpty()) {
            player.sendMessage(ChatColor.RED + "The destination Stargate is not activated");
            return;
        }

        Block destinationBlock = destinationController.getRelative(1, 0, 0);
        if (!destinationBlock.getType().isEmpty()) {
            player.sendMessage(ChatColor.RED + "The destination is blocked");
            return;
        }

        player.setMetadata("disableStargate", new FixedMetadataValue(Galactifun.instance(), true));
        if (player.getWorld() != destinationBlock.getWorld()) {
            Galactifun.travelManager().authorize(player, destinationBlock.getWorld(), TravelType.STARGATE);
        }

        player.teleportAsync(destinationBlock.getLocation()).whenComplete((success, throwable) -> Scheduler.run(() -> {
            if (throwable != null || !Boolean.TRUE.equals(success)) {
                Galactifun.travelManager().clear(player);
                player.sendMessage(ChatColor.RED + "Stargate teleport failed");
            }
            Scheduler.run(10, () -> player.removeMetadata("disableStargate", Galactifun.instance()));
        }));
    }

    private record ComponentPosition(int y, int z) {
        public boolean isInSameRing(@Nonnull Block block) {
            return BlockStorage.check(block.getRelative(0, this.y, this.z)) instanceof StargateRing;
        }

        @Nonnull
        public Block getBlock(@Nonnull Block block) {
            return block.getRelative(0, this.y, this.z);
        }

        public boolean isPortal(@Nonnull Block block) {
            return block.getRelative(0, this.y, this.z).getType() == Material.END_GATEWAY;
        }
    }
}
