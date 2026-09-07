package io.github.addoncommunity.galactifun.core;

import java.util.List;

import javax.annotation.Nonnull;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.core.managers.LandingHatchManager.LandingTarget;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * Fixed-layout landing target menu. Navigation buttons never move as target counts change.
 */
@SuppressWarnings("deprecation")
public final class LandingSelector {

    private static final int TARGETS_PER_PAGE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int MANUAL_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int[] NAV_BACKGROUND = {46, 47, 51, 52, 53};
    private static final ItemStack BACKGROUND = new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " ");

    private final PlanetaryWorld world;
    private final List<LandingTarget> targets;
    private final BackHandler backHandler;
    private final SelectHandler selectHandler;
    private final ManualHandler manualHandler;

    public LandingSelector(
            @Nonnull PlanetaryWorld world,
            @Nonnull List<LandingTarget> targets,
            @Nonnull BackHandler backHandler,
            @Nonnull SelectHandler selectHandler,
            @Nonnull ManualHandler manualHandler
    ) {
        this.world = world;
        this.targets = List.copyOf(targets);
        this.backHandler = backHandler;
        this.selectHandler = selectHandler;
        this.manualHandler = manualHandler;
    }

    public void open(@Nonnull Player player) {
        open(player, 0);
    }

    private void open(@Nonnull Player player, int page) {
        int pageCount = Math.max(1, (this.targets.size() + TARGETS_PER_PAGE - 1) / TARGETS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        ChestMenu menu = new ChestMenu("Land on " + this.world.name());
        menu.setEmptySlotsClickable(false);
        for (int slot : NAV_BACKGROUND) {
            menu.addItem(slot, BACKGROUND, ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(BACK_SLOT, ChestMenuUtils.getBackButton(player), (p, slot, item, action) -> {
            this.backHandler.onBack(p);
            return false;
        });

        if (safePage > 0) {
            menu.addItem(PREVIOUS_SLOT, new CustomItemStack(Material.ARROW, "&fPrevious Page"), (p, slot, item, action) -> {
                open(p, safePage - 1);
                return false;
            });
        } else {
            menu.addItem(PREVIOUS_SLOT, BACKGROUND, ChestMenuUtils.getEmptyClickHandler());
        }

        if (safePage + 1 < pageCount) {
            menu.addItem(NEXT_SLOT, new CustomItemStack(Material.ARROW, "&fNext Page"), (p, slot, item, action) -> {
                open(p, safePage + 1);
                return false;
            });
        } else {
            menu.addItem(NEXT_SLOT, BACKGROUND, ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(MANUAL_SLOT, new CustomItemStack(
                Material.COMPASS,
                "&fManual Coordinates",
                "&7Enter X/Z coordinates instead of",
                "&7using a registered Landing Hatch."
        ), (p, slot, item, action) -> {
            this.manualHandler.onManual(p);
            return false;
        });

        int from = safePage * TARGETS_PER_PAGE;
        int to = Math.min(this.targets.size(), from + TARGETS_PER_PAGE);
        for (int index = from; index < to; index++) {
            LandingTarget target = this.targets.get(index);
            int slot = index - from;
            menu.addItem(slot, new CustomItemStack(
                    Material.IRON_TRAPDOOR,
                    "&fLanding Hatch",
                    "&7World: &f" + target.worldName(),
                    "&7Coordinates: &f" + target.x() + " " + target.y() + " " + target.z(),
                    "&7Click to land here"
            ), (p, clickedSlot, item, action) -> {
                this.selectHandler.onSelect(p, target);
                return false;
            });
        }

        menu.open(player);
    }

    @FunctionalInterface
    public interface BackHandler {
        void onBack(@Nonnull Player player);
    }

    @FunctionalInterface
    public interface SelectHandler {
        void onSelect(@Nonnull Player player, @Nonnull LandingTarget target);
    }

    @FunctionalInterface
    public interface ManualHandler {
        void onManual(@Nonnull Player player);
    }
}
