package io.github.addoncommunity.galactifun.base.items.protection;

import io.github.addoncommunity.galactifun.util.SFStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Gas;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
import io.github.addoncommunity.galactifun.core.managers.OxygenZoneManager.Zone;
import io.github.addoncommunity.galactifun.util.BSUtils;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.mooy1.infinitylib.machines.MenuBlock;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public final class OxygenSealer extends MenuBlock implements EnergyNetComponent, HologramOwner {

    private static final String PROTECTING = "oxygenating";
    private static final String NO_OXYGEN = "no_oxygen";
    private static final Set<BlockPosition> allBlocks = new HashSet<>();
    private static final Map<BlockPosition, ScanState> scanStates = new HashMap<>();
    private static final int OXYGEN_SLOT = 4;

    private final int range;
    private final double superFanBonus;
    private final int maxSuperFans;
    private final int maxSealedBlocks;
    private final int scanIntervalSlimefunTicks;
    private final long safetyRefreshMillis;
    private int scanCounter;

    public OxygenSealer(SlimefunItemStack item, ItemStack[] recipe, int range) {
        super(CoreItemGroup.MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
        this.range = range;

        double configuredBonus = Galactifun.instance().getConfig()
                .getDouble("oxygen-sealer.super-fan-range-bonus-percent", 15.0D);
        this.superFanBonus = Math.max(0D, Math.min(configuredBonus, 500D)) / 100D;
        this.maxSuperFans = Math.max(0, Galactifun.instance().getConfig()
                .getInt("oxygen-sealer.maximum-super-fans", 4));
        this.maxSealedBlocks = Math.max(0, Galactifun.instance().getConfig()
                .getInt("oxygen-sealer.max-sealed-blocks", 300000));
        int scanIntervalSeconds = Math.max(1, Math.min(
                Galactifun.instance().getConfig().getInt("oxygen-sealer.scan-interval-seconds", 3),
                60
        ));
        this.scanIntervalSlimefunTicks = scanIntervalSeconds * 2;

        int refreshSeconds = Math.max(10, Math.min(
                Galactifun.instance().getConfig().getInt("oxygen-sealer.safety-refresh-seconds", 60),
                600
        ));
        this.safetyRefreshMillis = refreshSeconds * 1000L;

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block b, SlimefunItem item, ASlimefunDataContainer data) {
                BlockPosition source = new BlockPosition(b);
                allBlocks.add(source);
                ScanState state = scanStates.computeIfAbsent(source, ignored -> new ScanState());

                int req = 64;
                boolean wasPowered = BSUtils.getStoredBoolean(b.getLocation(), PROTECTING);
                boolean powered = getChargeLong(b.getLocation(), data) >= req;
                if (!powered) {
                    SFStorage.setData(b, PROTECTING, "false");
                    Galactifun.protectionManager().removeOxygenSource(source);
                    state.status = "Not Enough Energy";
                    state.roomSize = 0;
                } else {
                    SFStorage.setData(b, PROTECTING, "true");
                    removeCharge(b.getLocation(), (long) req, data);
                }

                if (powered != wasPowered) {
                    state.dirty = true;
                }
            }

            @Override
            public void uniqueTick() {
                Iterator<BlockPosition> iterator = allBlocks.iterator();
                while (iterator.hasNext()) {
                    BlockPosition pos = iterator.next();
                    if (!(SFStorage.item(pos.toLocation()) instanceof OxygenSealer)) {
                        Galactifun.protectionManager().removeOxygenSource(pos);
                        scanStates.remove(pos);
                        iterator.remove();
                    }
                }

                scanCounter++;
                if (scanCounter >= scanIntervalSlimefunTicks) {
                    scanCounter = 0;
                    OxygenSealer.this.uniqueTick();
                }
            }
        });
    }

    private void uniqueTick() {
        for (BlockPosition l : allBlocks) {
            updateProtections(l);
        }
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent e, @Nonnull Block b) {
        BlockPosition source = new BlockPosition(b);
        allBlocks.add(source);
        scanStates.computeIfAbsent(source, ignored -> new ScanState()).dirty = true;
        updateProtections(source);
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent e, @Nonnull BlockMenu menu) {
        removeHologram(e.getBlock());
        BlockPosition source = new BlockPosition(e.getBlock());
        allBlocks.remove(source);
        scanStates.remove(source);
        Galactifun.protectionManager().removeOxygenSource(source);
    }

    @Override
    protected void setup(@Nonnull BlockMenuPreset preset) {
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            preset.addItem(i, MenuBlock.BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @Override
    protected int[] getInputSlots() {
        return new int[] { OXYGEN_SLOT };
    }

    @Override
    protected int[] getOutputSlots() {
        return new int[0];
    }

    @Nonnull
    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public long getCapacityLong() {
        return 256L;
    }

    /** Slimefun Legacy 4.1.45 compatibility bridge. */
    @Deprecated
    @SuppressWarnings("deprecation")
    @Override
    public int getCapacity() {
        return (int) getCapacityLong();
    }

    private void updateProtections(@Nonnull BlockPosition pos) {
        Location l = pos.toLocation();
        Block b = pos.getBlock();
        ScanState state = scanStates.computeIfAbsent(pos, ignored -> new ScanState());

        if (!BSUtils.getStoredBoolean(l, PROTECTING)) {
            Galactifun.protectionManager().removeOxygenSource(pos);
            state.status = "Not Enough Energy";
            state.roomSize = 0;
            updateHologram(b, "&cNot Enough Energy");
            return;
        }

        BlockMenu menu = SFStorage.menu(b);
        if (menu == null || !SlimefunUtils.isItemSimilar(
                menu.getItemInSlot(OXYGEN_SLOT),
                Gas.OXYGEN.item().clone(),
                false,
                false
        )) {
            Galactifun.protectionManager().removeOxygenSource(pos);
            state.status = "No Oxygen";
            state.roomSize = 0;
            updateHologram(b, "&cNo Oxygen");
            BSUtils.addBlockInfo(b, NO_OXYGEN, true);
            return;
        }

        long now = System.currentTimeMillis();
        if (state.lastOxygenConsumeMillis == 0L
                || now - state.lastOxygenConsumeMillis >= 9_000L
                || BSUtils.getStoredBoolean(l, NO_OXYGEN)) {
            menu.consumeItem(OXYGEN_SLOT);
            state.lastOxygenConsumeMillis = now;
            BSUtils.addBlockInfo(b, NO_OXYGEN, false);
        }

        double effectiveRange = this.range;
        int fans = 0;
        for (BlockFace face : Util.SURROUNDING_FACES) {
            if (SFStorage.isItem(b.getRelative(face), BaseItems.SUPER_FAN.getItemId())
                    && (this.maxSuperFans == 0 || fans < this.maxSuperFans)) {
                effectiveRange += effectiveRange * this.superFanBonus;
                fans++;
            }
        }

        int floodFillLimit = (int) Math.min(Integer.MAX_VALUE, Math.round(effectiveRange));
        if (this.maxSealedBlocks > 0) {
            floodFillLimit = Math.min(floodFillLimit, this.maxSealedBlocks);
        }

        if (fans != state.fans || floodFillLimit != state.limit) {
            state.dirty = true;
        }
        state.fans = fans;
        state.limit = floodFillLimit;

        boolean needsScan = state.dirty
                || Galactifun.protectionManager().oxygenSourceSize(pos) == 0
                || now - state.lastScanMillis >= this.safetyRefreshMillis;

        if (needsScan) {
            long started = System.nanoTime();
            Optional<Set<BlockPosition>> returned = Util.floodFill(l, floodFillLimit);
            state.lastScanNanos = System.nanoTime() - started;
            state.lastScanMillis = now;
            state.dirty = false;

            if (returned.isEmpty()) {
                Galactifun.protectionManager().removeOxygenSource(pos);
                state.status = "Area Not Sealed or Too Big";
                state.roomSize = 0;
                updateHologram(b, "&cArea Not Sealed or Too Big");
                return;
            }

            Set<BlockPosition> room = returned.get();
            Galactifun.protectionManager().replaceOxygenSource(pos, room);
            state.roomSize = room.size();
        }

        state.status = "Operational";
        updateHologram(b, "&aOperational");
    }

    /** Marks only Sealer caches touching a changed block (or directly adjacent Sealer) dirty. */
    public static void markDirtyAround(@Nonnull Block block) {
        if (Galactifun.protectionManager() == null) {
            return;
        }

        for (BlockPosition source : Galactifun.protectionManager().oxygenSourcesNear(block)) {
            scanStates.computeIfAbsent(source, ignored -> new ScanState()).dirty = true;
        }

        BlockFace[] faces = {
                BlockFace.SELF, BlockFace.UP, BlockFace.DOWN,
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        };
        for (BlockFace face : faces) {
            Block candidate = block.getRelative(face);
            if (SFStorage.item(candidate) instanceof OxygenSealer) {
                scanStates.computeIfAbsent(new BlockPosition(candidate), ignored -> new ScanState()).dirty = true;
            }
        }
    }

    @Nonnull
    public static List<String> diagnostics(@Nonnull Player player) {
        List<String> lines = new ArrayList<>();
        Zone zone = Galactifun.oxygenZoneManager() == null ? null : Galactifun.oxygenZoneManager().zoneAt(player.getLocation());
        if (zone != null) {
            lines.add("Admin zone: " + zone.name() + " [" + zone.mode() + "]");
        }

        Set<BlockPosition> sources = Galactifun.protectionManager().oxygenSourcesNear(player.getLocation().getBlock());
        if (sources.isEmpty()) {
            lines.add("Breathable here: " + (Galactifun.protectionManager().isOxygenProtected(player) ? "YES" : "NO"));
            lines.add("No cached Oxygen Sealer room touches your current position.");
            return lines;
        }

        for (BlockPosition source : sources) {
            ScanState state = scanStates.get(source);
            if (state == null) {
                continue;
            }
            Location location = source.toLocation();
            lines.add("Sealer: " + location.getWorld().getName() + " "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            lines.add("Status: " + state.status);
            lines.add("Room size: " + state.roomSize + " blocks");
            lines.add("Scan limit: " + state.limit);
            lines.add("Super Fans: " + state.fans);
            lines.add("Last scan: " + (state.lastScanMillis == 0L ? "never" : (System.currentTimeMillis() - state.lastScanMillis) + "ms ago"));
            lines.add("Scan time: " + String.format("%.3fms", state.lastScanNanos / 1_000_000.0D));
            lines.add("Dirty: " + state.dirty);
        }
        lines.add("Breathable here: " + (Galactifun.protectionManager().isOxygenProtected(player) ? "YES" : "NO"));
        return lines;
    }

    private static final class ScanState {
        private boolean dirty = true;
        private int roomSize;
        private int limit;
        private int fans;
        private long lastScanMillis;
        private long lastScanNanos;
        private long lastOxygenConsumeMillis;
        private String status = "Initializing";
    }
}
