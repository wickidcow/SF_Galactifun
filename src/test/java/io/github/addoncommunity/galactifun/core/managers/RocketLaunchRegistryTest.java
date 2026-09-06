package io.github.addoncommunity.galactifun.core.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.addoncommunity.galactifun.core.managers.RocketLaunchRegistry.State;

class RocketLaunchRegistryTest {

    @BeforeEach
    void clearBefore() {
        RocketLaunchRegistry.clearForTests();
    }

    @AfterEach
    void clearAfter() {
        RocketLaunchRegistry.clearForTests();
    }

    @Test
    void onlyOnePlayerCanReserveARocket() {
        String rocket = "world:1:2:3";
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(RocketLaunchRegistry.reserve(rocket, first));
        assertFalse(RocketLaunchRegistry.reserve(rocket, second));
        assertTrue(RocketLaunchRegistry.isOwnedBy(rocket, first, State.RESERVED));
        assertFalse(RocketLaunchRegistry.isOwnedBy(rocket, second, State.RESERVED));
    }

    @Test
    void onlyTheOwnerCanAdvanceOrReleaseTheReservation() {
        String rocket = "world:4:5:6";
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertTrue(RocketLaunchRegistry.reserve(rocket, owner));
        assertFalse(RocketLaunchRegistry.markLaunching(rocket, other));
        assertTrue(RocketLaunchRegistry.isOwnedBy(rocket, owner, State.RESERVED));
        assertFalse(RocketLaunchRegistry.release(rocket, other));
        assertTrue(RocketLaunchRegistry.isLocked(rocket));

        assertTrue(RocketLaunchRegistry.markLaunching(rocket, owner));
        assertTrue(RocketLaunchRegistry.isOwnedBy(rocket, owner, State.LAUNCHING));
        assertTrue(RocketLaunchRegistry.release(rocket, owner));
        assertFalse(RocketLaunchRegistry.isLocked(rocket));
    }
}
