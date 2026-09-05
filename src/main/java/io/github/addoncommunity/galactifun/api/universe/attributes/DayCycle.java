package io.github.addoncommunity.galactifun.api.universe.attributes;

import javax.annotation.Nonnull;

import org.bukkit.GameRule;
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
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        if (this.startTime != -1) {
            setTimeSafely(world, this.startTime);
        }
    }

    /**
     * Apply time effects to world every 5 seconds.
     */
    public void tick(@Nonnull World world) {
        if (this.perFiveSeconds != 0) {
            setTimeSafely(world, world.getTime() + this.perFiveSeconds);
        }
    }

    /**
     * Paper 26.x can expose custom/dimension worlds without a world clock. Calling setTime on one of
     * those worlds throws IllegalArgumentException. Skip only that known clockless-world condition so
     * the Galactifun world ticker cannot spam the server log every five seconds.
     */
    private static void setTimeSafely(@Nonnull World world, long time) {
        try {
            world.setTime(time);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message == null || !message.contains("without world clock")) {
                throw exception;
            }
        }
    }

    public String description() {
        return this.description;
    }

    public String getDescription() {
        return this.description;
    }
}
