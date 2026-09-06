package io.github.addoncommunity.galactifun.core.managers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

/**
 * Process-local reservation registry for rocket launches.
 *
 * <p>The persistent Slimefun block data is still used for player-visible state, but this registry is the
 * atomic source of truth that prevents two callbacks from reserving the same rocket at the same time.</p>
 */
public final class RocketLaunchRegistry {

    private static final Map<String, Reservation> RESERVATIONS = new ConcurrentHashMap<>();

    private RocketLaunchRegistry() {
    }

    public enum State {
        RESERVED,
        LAUNCHING
    }

    public record Reservation(@Nonnull UUID owner, @Nonnull State state) {
    }

    public static boolean reserve(@Nonnull String rocketKey, @Nonnull UUID owner) {
        return RESERVATIONS.putIfAbsent(rocketKey, new Reservation(owner, State.RESERVED)) == null;
    }

    public static boolean markLaunching(@Nonnull String rocketKey, @Nonnull UUID owner) {
        return RESERVATIONS.computeIfPresent(rocketKey, (key, reservation) -> {
            if (!reservation.owner().equals(owner)) {
                return reservation;
            }
            return new Reservation(owner, State.LAUNCHING);
        }) != null && isOwnedBy(rocketKey, owner, State.LAUNCHING);
    }

    public static boolean release(@Nonnull String rocketKey, @Nonnull UUID owner) {
        return RESERVATIONS.computeIfPresent(rocketKey, (key, reservation) ->
                reservation.owner().equals(owner) ? null : reservation
        ) == null;
    }

    public static boolean isLocked(@Nonnull String rocketKey) {
        return RESERVATIONS.containsKey(rocketKey);
    }

    public static boolean isOwnedBy(@Nonnull String rocketKey, @Nonnull UUID owner, @Nonnull State state) {
        Reservation reservation = RESERVATIONS.get(rocketKey);
        return reservation != null && reservation.owner().equals(owner) && reservation.state() == state;
    }

    @Nonnull
    public static Optional<Reservation> reservation(@Nonnull String rocketKey) {
        return Optional.ofNullable(RESERVATIONS.get(rocketKey));
    }

    static void clearForTests() {
        RESERVATIONS.clear();
    }
}
