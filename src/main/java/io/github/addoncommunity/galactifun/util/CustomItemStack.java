package io.github.addoncommunity.galactifun.util;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;

/**
 * Small Bukkit {@link ItemStack} helper used by Galactifun menus and recipes.
 *
 * <p>Paper 26.x delegates Bukkit ItemStack calls through an internal CraftItemStack. Using Bukkit's
 * ItemStack(ItemStack) copy constructor with a SlimefunItemStack is unsafe because Slimefun's clone
 * remains a SlimefunItemStack, which can become the delegate and later fail CraftItemStack casts.
 * These constructors deliberately rebuild the stack from material/amount and copy metadata instead.
 */
public class CustomItemStack extends ItemStack {

    public CustomItemStack(@Nonnull ItemStack item) {
        super(item.getType(), item.getAmount());
        copyMeta(item);
    }

    public CustomItemStack(@Nonnull ItemStack item, @Nullable String name, @Nullable String... lore) {
        this(item);
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    public CustomItemStack(@Nonnull ItemStack item, @Nullable String name, @Nullable List<String> lore) {
        this(item);
        applyMeta(name, lore);
    }

    public CustomItemStack(@Nonnull ItemStack item, int amount) {
        this(item);
        setAmount(amount);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, int amount) {
        this((ItemStack) item);
        setAmount(amount);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, @Nullable String name, @Nullable String... lore) {
        this((ItemStack) item);
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    public CustomItemStack(@Nonnull SlimefunItemStack item, @Nullable String name, @Nullable List<String> lore) {
        this((ItemStack) item);
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
        this(SlimefunUtils.getCustomHead(headTexture));
        applyMeta(name, lore != null ? Arrays.asList(lore) : null);
    }

    private void copyMeta(@Nonnull ItemStack source) {
        if (source.hasItemMeta()) {
            setItemMeta(source.getItemMeta());
        }
    }

    private void applyMeta(@Nullable String name, @Nullable List<String> lore) {
        ItemMeta meta = getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(Messages.legacy(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> components = lore.stream().map(Messages::legacy).toList();
                meta.lore(components);
            }
            setItemMeta(meta);
        }
    }
}
