package io.github.addoncommunity.galactifun.util;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Galactifun adapter for Slimefun Legacy's current block-data controller.
 *
 * <p>This intentionally replaces the deprecated CS-CoreLib {@code BlockStorage} facade while
 * preserving its loaded-data semantics for Galactifun 1.x machines.
 */
public final class SFStorage {

    private SFStorage() {}

    private static BlockDataController controller() {
        return Slimefun.getDatabaseManager().getBlockDataController();
    }

    @Nullable
    public static SlimefunBlockData data(Location location) {
        SlimefunBlockData data = controller().getBlockData(location);
        if (data != null && !data.isDataLoaded()) {
            controller().loadBlockData(data);
        }
        return data;
    }

    @Nullable
    public static String getData(Location location, String key) {
        SlimefunBlockData data = data(location);
        if (data == null) {
            return null;
        }
        return "id".equals(key) ? data.getSfId() : data.getData(key);
    }

    public static void setData(Location location, String key, @Nullable String value) {
        if ("id".equals(key)) {
            if (value != null) {
                controller().createBlock(location, value);
            }
            return;
        }

        SlimefunBlockData data = data(location);
        if (data == null) {
            return;
        }
        if (value == null) {
            data.removeData(key);
        } else {
            data.setData(key, value);
        }
    }

    public static void setData(Block block, String key, @Nullable String value) {
        setData(block.getLocation(), key, value);
    }

    public static void create(Block block, String slimefunId) {
        controller().createBlock(block.getLocation(), slimefunId);
    }

    public static void remove(Location location) {
        controller().removeBlock(location);
    }

    public static void remove(Block block) {
        remove(block.getLocation());
    }

    public static boolean hasData(Block block) {
        return data(block.getLocation()) != null;
    }

    public static boolean isItem(Location location, String slimefunId) {
        SlimefunBlockData data = data(location);
        return data != null && slimefunId.equals(data.getSfId());
    }

    public static boolean isItem(Block block, String slimefunId) {
        return isItem(block.getLocation(), slimefunId);
    }

    @Nullable
    public static SlimefunItem item(Location location) {
        SlimefunBlockData data = data(location);
        return data == null ? null : SlimefunItem.getById(data.getSfId());
    }

    @Nullable
    public static SlimefunItem item(Block block) {
        return item(block.getLocation());
    }

    @Nullable
    public static BlockMenu menu(Location location) {
        SlimefunBlockData data = data(location);
        return data == null ? null : data.getBlockMenu();
    }

    @Nullable
    public static BlockMenu menu(Block block) {
        return menu(block.getLocation());
    }
}
