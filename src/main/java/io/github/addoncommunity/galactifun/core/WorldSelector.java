package io.github.addoncommunity.galactifun.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.universe.UniversalObject;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseUniverse;
import io.github.addoncommunity.galactifun.base.items.knowledge.KnowledgeLevel;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Class for exploring the universe through fixed-layout {@link ChestMenu}s.
 */
@SuppressWarnings("deprecation")
public final class WorldSelector {

    private static final int OBJECTS_PER_PAGE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int CURRENT_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int[] NAV_BACKGROUND = {46, 47, 51, 52, 53};

    private static final ItemStack NAV_BACKGROUND_ITEM = new CustomItemStack(
            Material.BLACK_STAINED_GLASS_PANE,
            " "
    );

    private final Map<UUID, UniversalObject> history = new HashMap<>();

    @Nonnull
    private final ChestMenu.MenuClickHandler exitHandler;
    @Nonnull
    private final ItemModifier modifier;
    @Nonnull
    private final SelectHandler selectHandler;

    public WorldSelector(@Nonnull ChestMenu.MenuClickHandler exitHandler, @Nonnull ItemModifier modifier, @Nonnull SelectHandler selectHandler) {
        this.exitHandler = exitHandler;
        this.selectHandler = selectHandler;
        this.modifier = modifier;
    }

    public WorldSelector(@Nonnull ChestMenu.MenuClickHandler exitHandler) {
        this(exitHandler, (p, world, lore) -> true, (p, world) -> {
        });
    }

    public WorldSelector(@Nonnull ItemModifier modifier, @Nonnull SelectHandler selectHandler) {
        this((p, i, s, a) -> false, modifier, selectHandler);
    }

    public WorldSelector(@Nonnull SelectHandler selectHandler) {
        this((p, world, lore) -> true, selectHandler);
    }

    public void open(@Nonnull Player p) {
        open(p, this.history.computeIfAbsent(p.getUniqueId(), k -> BaseUniverse.THE_UNIVERSE), false, 0);
    }

    private void open(@Nonnull Player p, @Nonnull UniversalObject object, boolean remember, int page) {
        List<UniversalObject> orbiters = object.orbiters();
        if (orbiters.isEmpty()) {
            UniversalObject parent = object.orbiting();
            if (parent != null) {
                open(p, parent, true, 0);
            }
            return;
        }

        if (remember) {
            this.history.put(p.getUniqueId(), object);
        }

        int pageCount = Math.max(1, (orbiters.size() + OBJECTS_PER_PAGE - 1) / OBJECTS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        ChestMenu menu = new ChestMenu(object.name());
        menu.setEmptySlotsClickable(false);

        for (int slot : NAV_BACKGROUND) {
            menu.addItem(slot, NAV_BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(BACK_SLOT, ChestMenuUtils.getBackButton(p));
        if (object.orbiting() == null) {
            menu.addMenuClickHandler(BACK_SLOT, exitHandler);
        } else {
            menu.addMenuClickHandler(BACK_SLOT, (player, slot, item, action) -> {
                open(player, object.orbiting(), true, 0);
                return false;
            });
        }

        if (safePage > 0) {
            menu.addItem(PREVIOUS_SLOT, new CustomItemStack(
                    Material.ARROW,
                    "&fPrevious Page",
                    "&7Page " + safePage + " / " + pageCount
            ), (player, slot, item, action) -> {
                open(player, object, false, safePage - 1);
                return false;
            });
        } else {
            menu.addItem(PREVIOUS_SLOT, NAV_BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        if (safePage + 1 < pageCount) {
            menu.addItem(NEXT_SLOT, new CustomItemStack(
                    Material.ARROW,
                    "&fNext Page",
                    "&7Page " + (safePage + 2) + " / " + pageCount
            ), (player, slot, item, action) -> {
                open(player, object, false, safePage + 1);
                return false;
            });
        } else {
            menu.addItem(NEXT_SLOT, NAV_BACKGROUND_ITEM, ChestMenuUtils.getEmptyClickHandler());
        }

        PlanetaryWorld current = resolveCurrentWorld(p);
        addCurrentObject(menu, p, object, current, safePage, pageCount);

        int from = safePage * OBJECTS_PER_PAGE;
        int to = Math.min(orbiters.size(), from + OBJECTS_PER_PAGE);
        for (int index = from; index < to; index++) {
            int slot = index - from;
            UniversalObject orbiter = orbiters.get(index);

            if (orbiter instanceof PlanetaryWorld planetaryWorld && !planetaryWorld.enabled()) {
                continue;
            }

            ItemStack item = buildDisplayItem(p, orbiter, current);
            if (item == null) {
                continue;
            }

            if (!(orbiter instanceof PlanetaryWorld) && !showObject(p, orbiter)) {
                continue;
            }

            menu.addItem(slot, item);
            if (orbiter.orbiters().isEmpty()) {
                menu.addMenuClickHandler(slot, (clicker, clickedSlot, clickedItem, action) -> {
                    if (orbiter instanceof PlanetaryWorld planetaryWorld) {
                        selectHandler.onSelect(clicker, planetaryWorld);
                    }
                    return false;
                });
            } else {
                menu.addMenuClickHandler(slot, (clicker, clickedSlot, clickedItem, action) -> {
                    open(clicker, orbiter, true, 0);
                    return false;
                });
            }
        }

        menu.open(p);
    }

    private void addCurrentObject(
            @Nonnull ChestMenu menu,
            @Nonnull Player player,
            @Nonnull UniversalObject object,
            @Nullable PlanetaryWorld current,
            int page,
            int pageCount
    ) {
        if (object instanceof PlanetaryWorld planetaryWorld) {
            ItemStack currentItem = buildDisplayItem(player, planetaryWorld, current);
            if (currentItem != null) {
                menu.addItem(CURRENT_SLOT, currentItem, (clicker, slot, item, action) -> {
                    selectHandler.onSelect(clicker, planetaryWorld);
                    return false;
                });
                return;
            }
        }

        menu.addItem(CURRENT_SLOT, new CustomItemStack(
                Material.COMPASS,
                "&f" + object.name(),
                "&7Page " + (page + 1) + " / " + pageCount,
                "&8Navigation controls stay in this row"
        ), ChestMenuUtils.getEmptyClickHandler());
    }

    @Nullable
    private ItemStack buildDisplayItem(
            @Nonnull Player player,
            @Nonnull UniversalObject object,
            @Nullable PlanetaryWorld current
    ) {
        ItemStack item = object.item();
        if (item == null) {
            return null;
        }

        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);

        if (!lore.isEmpty()) {
            lore.remove(lore.size() - 1);
        }

        if (current != null) {
            double distance = object.distanceTo(current);
            if (distance > 0) {
                lore.add(Component.text("Distance: " + Util.formatDistance(distance)).color(NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("You are here!").color(NamedTextColor.GRAY));
            }
        }

        if (object instanceof PlanetaryWorld planetaryWorld) {
            KnowledgeLevel.get(player, planetaryWorld).addLore(lore, planetaryWorld);

            if (Galactifun.discoveryManager() != null && Galactifun.discoveryManager().isEnabled()) {
                boolean discovered = Galactifun.discoveryManager().hasDiscovered(player, planetaryWorld);
                lore.add(Component.empty());
                lore.add(Component.text("Discovery: ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(discovered ? "Visited" : "Not visited")
                                .color(discovered ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            }
        }

        if (!modifier.modifyItem(player, object, lore)) {
            return null;
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Nullable
    private static PlanetaryWorld resolveCurrentWorld(@Nonnull Player player) {
        if (Galactifun.travelManager() != null) {
            return Galactifun.travelManager().resolveTravelOrigin(player.getWorld());
        }
        return Galactifun.worldManager().getWorld(player.getWorld());
    }

    private boolean showObject(@Nonnull Player p, @Nonnull UniversalObject object) {
        for (UniversalObject o : object.orbiters()) {
            if (o instanceof PlanetaryWorld world && world.enabled()) {
                List<Component> lore = new ArrayList<>();
                if (modifier.modifyItem(p, world, lore)) {
                    return true;
                }
            } else if (showObject(p, o)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface SelectHandler {
        void onSelect(@Nonnull Player p, @Nonnull PlanetaryWorld world);
    }

    @FunctionalInterface
    public interface ItemModifier {
        boolean modifyItem(@Nonnull Player p, @Nonnull UniversalObject object, @Nonnull List<Component> lore);
    }
}
