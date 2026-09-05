package io.github.addoncommunity.galactifun.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;

/**
 * CustomItemStack extending Bukkit's {@link ItemStack} directly.
 * Ensures binary and runtime compatibility across modern Paper and Slimefun Legacy.
 */
public class CustomItemStack extends ItemStack {

    public CustomItemStack(@Nonnull ItemStack item) {
        super(item);
    }

    public CustomItemStack(@Nonnull ItemStack item, @Nullable String name, @Nullable String... lore) {
        super(item);
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    public CustomItemStack(@Nonnull ItemStack item, @Nullable String name, @Nullable List<String> lore) {
        super(item);
        applyMeta(name, lore);
    }

    public CustomItemStack(@Nonnull ItemStack item, int amount) {
        super(item);
        setAmount(amount);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, int amount) {
        super(item);
        setAmount(amount);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, @Nullable String name, @Nullable String... lore) {
        super(item);
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, @Nullable String name, @Nullable List<String> lore) {
        super(item);
        applyMeta(name, lore);
    }

    public CustomItemStack(@Nonnull Material type, @Nullable String name, @Nullable String... lore) {
        super(type);
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    public CustomItemStack(@Nonnull Material type, @Nullable String name, @Nullable List<String> lore) {
        super(type);
        applyMeta(name, lore);
    }

    public CustomItemStack(@Nonnull Material type, int amount) {
        super(type, amount);
    }

    public CustomItemStack(@Nonnull String headTexture, @Nullable String name, @Nullable String... lore) {
        super(SlimefunUtils.getCustomHead(headTexture));
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    private void applyMeta(@Nullable String name, @Nullable List<String> lore) {
        ItemMeta meta = getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> list = new ArrayList<>();
                for (String line : lore) {
                    list.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(list);
            }
            setItemMeta(meta);
        }
    }
}
