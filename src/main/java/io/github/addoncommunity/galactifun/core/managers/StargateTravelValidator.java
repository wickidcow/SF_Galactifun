package io.github.addoncommunity.galactifun.core.managers;

import javax.annotation.Nonnull;

/**
 * Pure validation logic for Stargate destinations. Kept separate from Bukkit block access so the
 * safety rules are easy to regression-test.
 */
public final class StargateTravelValidator {

    private StargateTravelValidator() {
    }

    public enum Result {
        VALID,
        MISSING_CONTROLLER,
        INCOMPLETE_RING,
        INACTIVE_GATE,
        BLOCKED_EXIT
    }

    @Nonnull
    public static Result validate(boolean controllerPresent, boolean ringAssembled, boolean gateActive, boolean exitClear) {
        if (!controllerPresent) {
            return Result.MISSING_CONTROLLER;
        }
        if (!ringAssembled) {
            return Result.INCOMPLETE_RING;
        }
        if (!gateActive) {
            return Result.INACTIVE_GATE;
        }
        if (!exitClear) {
            return Result.BLOCKED_EXIT;
        }
        return Result.VALID;
    }
}
