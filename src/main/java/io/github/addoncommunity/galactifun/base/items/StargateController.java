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
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.addoncommunity.galactifun.base.BaseItems;
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
            // bottom
            new ComponentPosition(0, 1),
            new ComponentPosition(0, -1),

            // corners
            new ComponentPosition(1, -2),
            new ComponentPosition(1, 2),
            new ComponentPosition(5, -2),
            new ComponentPosition(5, 2),

            // left side
            new ComponentPosition(2, 3),
            new ComponentPosition(3, 3),
            new ComponentPosition(4, 3),

            // right side
            new ComponentPosition(2, -3),
            new ComponentPosition(3, -3),
            new ComponentPosition(4, -3),

            // top
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
                }
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

        ChestMenu menu = getMenu(b);
        menu.open(p);
    }

    @Nonnull
    private ChestMenu getMenu(@Nonnull Block b) {
        ChestMenu menu = new ChestMenu(this.getItemName());
        for (int i : BACKGROUND) {
            menu.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        Location l = b.getLocation();

        String address = SFStorage.getData(l, "gfsgAddress");
        if (address == null) {
            String lString = String.format(
                    "%s-%d-%d-%d",
                    b.getWorld().getName(),
                    l.getBlockX(),
                    l.getBlockY(),
                    l.getBlockZ()
            );
            address = Integer.toHexString(lString.hashCode());
            SFStorage.setData(b, "gfsgAddress", address);
        }

        String destination = SFStorage.getData(l, "destination");
        destination = destination == null ? "" : destination;

        String temp = address;
        menu.addItem(ADDRESS_SLOT, new CustomItemStack(
                Material.BOOK,
                "&fAddress: " + address,
                "&7Click to send the address to chat"
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
                "&7Current Destination: " + destination
        ), (p, i, s, c) -> {
            Messages.yellow(p, "Type in the destination address");
            ChatUtils.awaitInput(p, st -> setDestination(st, b, p));
            p.closeInventory();
            return false;
        });

        return menu;
    }

    private static void setDestination(String destination, Block b, Player p) {
        Location dest = null;
        String controllerId = BaseItems.STARGATE_CONTROLLER.getItemId();
        var dataController = Slimefun.getDatabaseManager().getBlockDataController();

        search:
        for (var chunkData : dataController.getAllLoadedChunkData()) {
            for (var blockData : chunkData.getAllBlockData()) {
                if (!controllerId.equals(blockData.getSfId())) {
                    continue;
                }

                String address = blockData.getData("gfsgAddress");
                if (destination.equals(address)) {
                    dest = blockData.getLocation();
                    break search;
                }
            }
        }

        if (dest == null) {
            Messages.red(p, "No destination found! Make sure the destination world/chunk is loaded.");
            return;
        }

        Optional<List<Block>> portalOptional = getPortalBlocks(b);
        if (portalOptional.isEmpty()) {
            Messages.red(p, "The Stargate is not lit for some reason...");
            return;
        }

        BSUtils.setStoredLocation(b.getLocation(), "dest", dest);

        Messages.yellow(p, String.format(
                "Set Stargate destination to %d %d %d in %s",
                dest.getBlockX(),
                dest.getBlockY(),
                dest.getBlockZ(),
                dest.getWorld().getName()
        ));

        SFStorage.setData(b, "destination", destination);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onGateBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.END_GATEWAY &&
                Boolean.parseBoolean(SFStorage.getData(b.getLocation(), "locked"))) {
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
        if (p.getPersistentDataContainer().has(stargateCooldownKey(), PersistentDataType.BYTE)) return;

        Block b = dest.getBlock();
        if (SFStorage.isItem(b, BaseItems.STARGATE_CONTROLLER.getItemId()) &&
                StargateController.getPortalBlocks(b).isEmpty()) {
            Messages.red(p, "The destination Stargate is not activated");
            return;
        }

        Block destBlock = b.getRelative(1, 0, 0);
        if (destBlock.getType().isAir()) {
            // Check if the player is teleporting to an alien world, and if so, allow them to
            AlienWorld world = Galactifun.worldManager().getAlienWorld(destBlock.getWorld());
            if (world != null) {
                TeleportAccess.grant(e.getPlayer());
            }
            p.teleportAsync(destBlock.getLocation());
            NamespacedKey cooldownKey = stargateCooldownKey();
            p.getPersistentDataContainer().set(cooldownKey, PersistentDataType.BYTE, (byte) 1);
            Scheduler.run(10, () -> p.getPersistentDataContainer().remove(cooldownKey));
        } else {
            Messages.red(p, "The destination is blocked");
        }
    }

    private static final record ComponentPosition(int y, int z) {

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
