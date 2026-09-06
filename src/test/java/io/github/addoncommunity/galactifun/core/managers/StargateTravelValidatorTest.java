package io.github.addoncommunity.galactifun.core.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.addoncommunity.galactifun.core.managers.StargateTravelValidator.Result;

class StargateTravelValidatorTest {

    @Test
    void requiresARealController() {
        assertEquals(Result.MISSING_CONTROLLER, StargateTravelValidator.validate(false, true, true, true));
    }

    @Test
    void requiresACompleteRing() {
        assertEquals(Result.INCOMPLETE_RING, StargateTravelValidator.validate(true, false, true, true));
    }

    @Test
    void requiresAnActiveDestinationGate() {
        assertEquals(Result.INACTIVE_GATE, StargateTravelValidator.validate(true, true, false, true));
    }

    @Test
    void refusesABlockedArrivalSpace() {
        assertEquals(Result.BLOCKED_EXIT, StargateTravelValidator.validate(true, true, true, false));
    }

    @Test
    void acceptsOnlyAFullyValidDestination() {
        assertEquals(Result.VALID, StargateTravelValidator.validate(true, true, true, true));
    }
}
