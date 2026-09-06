package io.github.addoncommunity.galactifun.api.universe.attributes;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.apache.commons.lang3.Validate;

/**
 * Represents the amount of sunlight a celestial object s
 *
 * @author Mooy1
 */
public final class DayCycle {

    public static final DayCycle ETERNAL_DAY = DayCycle.eternal(6000L);
    public static final DayCycle ETERNAL_NIGHT = DayCycle.eternal(18000L);
    public static final DayCycle EARTH_LIKE = DayCycle.hours(24);

    /**
     * Paper/Purpur 26.x can expose dimensions without a world clock. Remember them globally so every
     * {@link DayCycle} instance stops retrying {@link World#setTime(long)} after the first rejection.
     */
    private static final Set<UUID> WORLDS_WITHOUT_CLOCK = ConcurrentHashMap.newKeySet();

    @Nonnull
    public static DayCycle eternal(long time) {
        return new DayCycle(time);
    }

    @Nonnull
    public static DayCycle relativeToEarth(double ratio) {
        return hours((int) (24 * ratio));
    }

    @Nonnull
    public static DayCycle days(int days) {
        return new DayCycle(days, 0);
    }

    @Nonnull
    public static DayCycle hours(int hours) {
        return new DayCycle(hours / 24, hours % 24);
    }

    @Nonnull
    public static DayCycle of(int days, int hours) {
        return new DayCycle(days + hours / 24, hours % 24);
    }

    @Nonnull
    private final String description;
    private final long startTime;
    private final long perFiveSeconds;

    private DayCycle(int days, int hours) {
        Validate.isTrue((days > 0 && hours >= 0) || (hours > 0 && days >= 0), "Day cycles must last at least 1 hour!");

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days);
            builder.append(" day");
            if (days != 1) {
                builder.append('s');
            }
            builder.append(' ');
        }
        if (hours > 0) {
            builder.append(hours);
            builder.append(" hour");
            if (hours != 1) {
                builder.append('s');
            }
        }

        this.description = builder.toString();
        this.startTime = -1;
        this.perFiveSeconds = days * 100L + hours * 4L;
    }

    /**
     * Eternal constructor
     */
    private DayCycle(long time) {
        Validate.isTrue(time >= 0 && time < 24000, "Eternal time must be between 0 and 24000!");

        this.description = "Eternal " + (time < 12000 ? "Day" : "Night");
        this.startTime = time;
        this.perFiveSeconds = 0;
    }

    public void applyEffects(@Nonnull World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        if (this.startTime != -1) {
            setTimeSafely(world, this.startTime);
        }
    }

    /**
     * Apply time effects to world every 5 seconds.
     */
    public void tick(@Nonnull World world) {
        if (this.perFiveSeconds == 0 || WORLDS_WITHOUT_CLOCK.contains(world.getUID())) {
            return;
        }
        setTimeSafely(world, world.getTime() + this.perFiveSeconds);
    }

    /**
     * Sets time only for dimensions that actually expose a world clock.
     *
     * <p>Recent Paper/Purpur builds throw {@link IllegalArgumentException} when {@code setTime} is used
     * on a clockless dimension. Bukkit currently has no capability query for this, so Galactifun learns
     * that property once and then avoids the hot exception path forever for that world UUID.</p>
     */
    static boolean setTimeSafely(@Nonnull World world, long time) {
        UUID worldId = world.getUID();
        if (WORLDS_WITHOUT_CLOCK.contains(worldId)) {
            return false;
        }

        try {
            world.setTime(time);
            return true;
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains("without world clock")) {
                WORLDS_WITHOUT_CLOCK.add(worldId);
                return false;
            }
            throw exception;
        }
    }

    static boolean isClocklessWorldCached(@Nonnull UUID worldId) {
        return WORLDS_WITHOUT_CLOCK.contains(worldId);
    }

    static void clearClocklessWorldCache() {
        WORLDS_WITHOUT_CLOCK.clear();
    }

    public String description() {
        return this.description;
    }

    public String getDescription() {
        return this.description;
    }
}
