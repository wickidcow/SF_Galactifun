package io.github.addoncommunity.galactifun.base.items.protection;

import io.github.addoncommunity.galactifun.util.SFStorage;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Gas;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.core.CoreItemGroup;
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
    private static final int OXYGEN_SLOT = 4;

    private final int range;
    private final double superFanBonus;
    private final int maxSuperFans;
    private final int maxSealedBlocks;
    private final int scanIntervalSlimefunTicks;
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
        // Galactifun/Slimefun unique ticks run twice per second.
        this.scanIntervalSlimefunTicks = scanIntervalSeconds * 2;

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block b, SlimefunItem item, ASlimefunDataContainer data) {
                allBlocks.add(new BlockPosition(b));

                int req = 64;
                if (getChargeLong(b.getLocation(), data) < req) {
                    SFStorage.setData(b, PROTECTING, "false");
                } else {
                    SFStorage.setData(b, PROTECTING, "true");
                    removeCharge(b.getLocation(), (long) req, data);
                }
            }

            @Override
            public void uniqueTick() {
                allBlocks.removeIf(pos -> !(SFStorage.item(pos.toLocation()) instanceof OxygenSealer));

                scanCounter++;
                if (scanCounter >= scanIntervalSlimefunTicks) {
                    scanCounter = 0;
                    OxygenSealer.this.uniqueTick();
                }
            }
        });
    }

    private void uniqueTick() {
        Galactifun.protectionManager().resetOxygenBlocks();
        for (BlockPosition l : allBlocks) {
            updateProtections(l);
        }
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent e, @Nonnull Block b) {
        updateProtections(new BlockPosition(b));
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent e, @Nonnull BlockMenu menu) {
        removeHologram(e.getBlock());
        allBlocks.remove(new BlockPosition(e.getBlock()));
        uniqueTick();
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

    private void updateProtections(BlockPosition pos) {
        Location l = pos.toLocation();
        Block b = pos.getBlock();

        if (!BSUtils.getStoredBoolean(l, PROTECTING)) {
            updateHologram(b, "&cNot Enough Energy");
            return;
        }

        BlockMenu menu = SFStorage.menu(b);
        if (!SlimefunUtils.isItemSimilar(menu.getItemInSlot(OXYGEN_SLOT), Gas.OXYGEN.item().clone(), false, false)) {
            updateHologram(b, "&cNo Oxygen");
            BSUtils.addBlockInfo(b, NO_OXYGEN, true);
            return;
        }

        if (Galactifun.slimefunTickCount() % 18 == 0 || BSUtils.getStoredBoolean(l, NO_OXYGEN)) {
            menu.consumeItem(OXYGEN_SLOT);
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

        Optional<Set<BlockPosition>> returned = Util.floodFill(l, floodFillLimit);
        if (returned.isEmpty()) {
            updateHologram(b, "&cArea Not Sealed or Too Big");
            return;
        }

        for (BlockPosition bp : returned.get()) {
            Galactifun.protectionManager().addOxygenBlock(bp);
        }

        updateHologram(b, "&aOperational");
    }
}
