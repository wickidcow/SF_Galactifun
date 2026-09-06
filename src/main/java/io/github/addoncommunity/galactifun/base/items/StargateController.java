package io.github.addoncommunity.galactifun.base.items;

import io.github.addoncommunity.galactifun.util.SFStorage;

import io.github.addoncommunity.galactifun.util.Messages;
import io.github.addoncommunity.galactifun.util.TeleportAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.core.managers.StargateRegistry;
import io.github.addoncommunity.galactifun.core.managers.StargateTravelValidator;
import io.github.addoncommunity.galactifun.core.managers.StargateTravelValidator.Result;
import io.github.addoncommunity.galactifun.util.BSUtils;
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
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.paper.PaperLib;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

// TODO clean up if possible
@SuppressWarnings("deprecation") // Slimefun Legacy 4.1.45 ChestMenu compatibility boundary.
public final class StargateController extends SlimefunItem implements Listener {

    private static NamespacedKey stargateCooldownKey() {
        return new NamespacedKey(Galactifun.instance(), "stargate_cooldown");
    }

    private static final int[] BACKGROUND = new int[] { 1, 2, 6, 7, 8 };
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

        addItemHandler((BlockUseHandler) e -> e.getClickedBlock().ifPresent(b -> onUse(e, e.getPlayer(), b)));

        addItemHandler(new BlockBreakHandler(true, true) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent e, ItemStack item, List<ItemStack> drops) {
                if (Boolean.parseBoolean(SFStorage.getData(e.getBlock().getLocation(), "locked"))) {
                    e.setCancelled(true);
                    Messages.red(e.getPlayer(), "Deactivate the Stargate before destroying it");
                    return;
                }
                StargateRegistry.unregister(e.getBlock());
            }
        });
    }

    public static boolean isPartOfStargate(@Nonnull Block b) {
        for (ComponentPosition position : RING_POSITIONS) {
            if (!position.isInSameRing(b)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    public static Optional<List<Block>> getRingBlocks(@Nonnull Block b) {
        List<Block> rings = new ArrayList<>();
        for (ComponentPosition position : RING_POSITIONS) {
            if (position.isInSameRing(b)) {
                rings.add(position.getBlock(b));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(rings);
    }

    @Nonnull
    public static Optional<List<Block>> getPortalBlocks(@Nonnull Block b) {
        List<Block> portals = new ArrayList<>();
        for (ComponentPosition position : PORTAL_POSITIONS) {
            if (position.isPortal(b)) {
                portals.add(position.getBlock(b));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(portals);
    }

    public static void lockBlocks(Block controller, boolean lock) {
        String data = Boolean.toString(lock);
        getRingBlocks(controller).ifPresent(l -> l.forEach(b -> SFStorage.setData(b, "locked", data)));
        getPortalBlocks(controller).ifPresent(l -> l.forEach(b -> SFStorage.setData(b, "locked", data)));
    }

    private void onUse(PlayerRightClickEvent event, Player p, Block b) {
        if (!isPartOfStargate(b)) {
            Messages.red(p, "The Stargate is not assembled!");
            return;
        }
        event.cancel();
        StargateRegistry.register(b);

        if (getPortalBlocks(b).isEmpty()) {
            for (ComponentPosition position : PORTAL_POSITIONS) {
                Block portal = position.getBlock(b);
                portal.setType(Material.END_GATEWAY);
                EndGateway gateway = (EndGateway) portal.getState();
                gateway.setAge(GATEWAY_TICKS);
                gateway.setExitLocation(b.getLocation());
                gateway.update(false, false);
            }

            String destAddress = SFStorage.getData(b.getLocation(), "destination");
            if (destAddress != null) {
                setDestination(destAddress, b, p);
            }

            lockBlocks(b, true);
            Messages.yellow(p, "Stargate activated!");
            return;
        }

        getMenu(b).open(p);
    }

    @Nonnull
    private ChestMenu getMenu(@Nonnull Block b) {
        ChestMenu menu = new ChestMenu(this.getItemName());
        for (int i : BACKGROUND) {
            menu.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        String address = StargateRegistry.register(b);
        String destination = SFStorage.getData(b.getLocation(), "destination");
        destination = destination == null ? "" : destination;

        String temp = address;
        menu.addItem(ADDRESS_SLOT, new CustomItemStack(
                Material.BOOK,
                "&fAddress: " + address,
                "&7Click to send the address to chat",
                "&7Registered persistently for unloaded chunks"
        ), (p, i, s, c) -> {
            p.sendMessage(
                    Component.text()
                            .color(NamedTextColor.YELLOW)
                            .content("Address (click to copy): " + temp)
                            .clickEvent(ClickEvent.copyToClipboard(temp))
                            .build()
            );
            p.closeInventory();
            return false;
        });

        menu.addItem(DEACTIVATE_SLOT, new CustomItemStack(
                Material.BARRIER,
                "&fClick to Deactivate the Stargate"
        ), (p, i, s, c) -> {
            getPortalBlocks(b).ifPresent(li -> {
                for (Block block : li) {
                    block.setType(Material.AIR);
                    SFStorage.remove(block);
                }
            });
            lockBlocks(b, false);
            p.closeInventory();
            return false;
        });

        menu.addItem(DESTINATION_SLOT, new CustomItemStack(
                Material.RAIL,
                "&fClick to Set Destination",
                "&7Current Destination: " + destination,
                "&7Destination is revalidated before every teleport"
        ), (p, i, s, c) -> {
            Messages.yellow(p, "Type in the destination address");
            ChatUtils.awaitInput(p, st -> setDestination(st, b, p));
            p.closeInventory();
            return false;
        });

        return menu;
    }

    private static void setDestination(String destination, Block b, Player p) {
        Location dest = StargateRegistry.resolve(destination).orElse(null);
        if (dest == null) {
            dest = findLoadedDestination(destination);
        }

        if (dest == null) {
            Messages.red(p, "No registered Stargate destination found for that address.");
            return;
        }

        if (getPortalBlocks(b).isEmpty()) {
            Messages.red(p, "The Stargate is not lit for some reason...");
            return;
        }

        if (dest.getChunk().isLoaded()) {
            Block controller = dest.getBlock();
            if (!SFStorage.isItem(controller, BaseItems.STARGATE_CONTROLLER.getItemId()) || !isPartOfStargate(controller)) {
                StargateRegistry.unregister(destination);
                Messages.red(p, "That address no longer points to an assembled Stargate.");
                return;
            }
        }

        BSUtils.setStoredLocation(b.getLocation(), "dest", dest);
        SFStorage.setData(b, "destination", destination);

        Messages.yellow(p, String.format(
                "Set Stargate destination to %d %d %d in %s",
                dest.getBlockX(),
                dest.getBlockY(),
                dest.getBlockZ(),
                dest.getWorld().getName()
        ));
    }

    private static Location findLoadedDestination(String destination) {
        String controllerId = BaseItems.STARGATE_CONTROLLER.getItemId();
        var dataController = Slimefun.getDatabaseManager().getBlockDataController();

        for (var chunkData : dataController.getAllLoadedChunkData()) {
            for (var blockData : chunkData.getAllBlockData()) {
                if (!controllerId.equals(blockData.getSfId())) {
                    continue;
                }

                String address = blockData.getData("gfsgAddress");
                if (destination.equals(address)) {
                    Location location = blockData.getLocation();
                    StargateRegistry.register(destination, location);
                    return location;
                }
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onGateBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.END_GATEWAY
                && Boolean.parseBoolean(SFStorage.getData(b.getLocation(), "locked"))) {
            e.setCancelled(true);
            Messages.red(e.getPlayer(), "Deactivate the Stargate before destroying it");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUsePortal(PlayerTeleportEndGatewayEvent e) {
        Location exit = e.getGateway().getExitLocation();
        if (exit == null || !(SFStorage.item(exit) instanceof StargateController)) return;

        Location dest = BSUtils.getStoredLocation(exit, "dest");
        if (dest == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        NamespacedKey cooldownKey = stargateCooldownKey();
        if (p.getPersistentDataContainer().has(cooldownKey, PersistentDataType.BYTE)) return;

        p.getPersistentDataContainer().set(cooldownKey, PersistentDataType.BYTE, (byte) 1);
        Scheduler.run(20, () -> p.getPersistentDataContainer().remove(cooldownKey));

        String destinationAddress = SFStorage.getData(exit, "destination");
        PaperLib.getChunkAtAsync(dest).whenComplete((chunk, throwable) -> Scheduler.run(() -> {
            if (throwable != null) {
                Messages.red(p, "The destination Stargate chunk could not be loaded safely.");
                return;
            }
            completeTeleport(p, dest, destinationAddress);
        }));
    }

    private static void completeTeleport(@Nonnull Player p, @Nonnull Location dest, String destinationAddress) {
        if (!p.isOnline()) {
            return;
        }

        Block controller = dest.getBlock();
        boolean controllerPresent = SFStorage.isItem(controller, BaseItems.STARGATE_CONTROLLER.getItemId());
        boolean ringAssembled = controllerPresent && isPartOfStargate(controller);
        boolean gateActive = ringAssembled && getPortalBlocks(controller).isPresent();
        Block destBlock = controller.getRelative(1, 0, 0);
        boolean exitClear = destBlock.getType().isAir();

        Result result = StargateTravelValidator.validate(controllerPresent, ringAssembled, gateActive, exitClear);
        if (result != Result.VALID) {
            if (result == Result.MISSING_CONTROLLER && destinationAddress != null) {
                StargateRegistry.unregister(destinationAddress);
            }
            sendValidationFailure(p, result);
            return;
        }

        AlienWorld world = Galactifun.worldManager().getAlienWorld(destBlock.getWorld());
        if (world != null) {
            TeleportAccess.grant(p);
        }

        p.teleportAsync(destBlock.getLocation().add(0.5, 0, 0.5)).whenComplete((success, throwable) -> {
            if (world != null) {
                TeleportAccess.revoke(p);
            }
            if (throwable != null || !Boolean.TRUE.equals(success)) {
                Scheduler.run(() -> Messages.red(p, "Stargate teleport failed safely."));
            }
        });
    }

    private static void sendValidationFailure(@Nonnull Player player, @Nonnull Result result) {
        switch (result) {
            case MISSING_CONTROLLER -> Messages.red(player, "The destination Stargate controller no longer exists.");
            case INCOMPLETE_RING -> Messages.red(player, "The destination Stargate is no longer fully assembled.");
            case INACTIVE_GATE -> Messages.red(player, "The destination Stargate is not activated.");
            case BLOCKED_EXIT -> Messages.red(player, "The destination Stargate exit is blocked.");
            case VALID -> {
            }
        }
    }

    private record ComponentPosition(int y, int z) {

        public boolean isInSameRing(@Nonnull Block b) {
            return SFStorage.item(b.getRelative(0, this.y, this.z)) instanceof StargateRing;
        }

        @Nonnull
        public Block getBlock(@Nonnull Block b) {
            return b.getRelative(0, this.y, this.z);
        }

        public boolean isPortal(@Nonnull Block b) {
            return b.getRelative(0, this.y, this.z).getType() == Material.END_GATEWAY;
        }
    }
}
