package io.github.addoncommunity.galactifun.api.events;

import javax.annotation.Nonnull;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;

/**
 * Fired once when Galactifun records a player's first visit to a planetary world.
 */
public final class PlanetFirstVisitEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final PlanetaryWorld world;

    public PlanetFirstVisitEvent(@Nonnull Player player, @Nonnull PlanetaryWorld world) {
        super(player);
        this.world = world;
    }

    @Nonnull
    public PlanetaryWorld getWorld() {
        return this.world;
    }

    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @Nonnull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
