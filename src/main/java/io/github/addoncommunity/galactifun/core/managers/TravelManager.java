package io.github.addoncommunity.galactifun.core.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Short-lived, destination-bound authorization for cross-world Galactifun travel.
 *
 * <p>This replaces the old {@code CanTpAlienWorld} metadata flag. Authorizations are tied to a
 * player, a specific destination world and an expiry time, which prevents an unrelated teleport
 * from reusing a stale permission.</p>
 */
public final class TravelManager {

    private static final long DEFAULT_TTL_MILLIS = 15_000L;

    private final Map<UUID, Authorization> authorizations = new ConcurrentHashMap<>();

    public void authorize(@Nonnull Player player, @Nonnull World target, @Nonnull TravelType type) {
        authorize(player, target, type, DEFAULT_TTL_MILLIS);
    }

    public void authorize(@Nonnull Player player, @Nonnull World target, @Nonnull TravelType type, long ttlMillis) {
        long expiresAt = System.currentTimeMillis() + Math.max(1_000L, ttlMillis);
        this.authorizations.put(player.getUniqueId(), new Authorization(target.getUID(), expiresAt, type));
    }

    public boolean consume(@Nonnull Player player, @Nonnull World target) {
        Authorization authorization = this.authorizations.remove(player.getUniqueId());
        if (authorization == null) {
            return false;
        }
        return authorization.expiresAt() >= System.currentTimeMillis()
                && authorization.targetWorld().equals(target.getUID());
    }

    public void clear(@Nonnull Player player) {
        this.authorizations.remove(player.getUniqueId());
    }

    public void clearExpired() {
        long now = System.currentTimeMillis();
        this.authorizations.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public enum TravelType {
        ROCKET,
        STARGATE,
        GALACTIPORT,
        RESPAWN,
        MULTIVERSE,
        ADMIN
    }

    private record Authorization(UUID targetWorld, long expiresAt, TravelType type) {
    }
}
