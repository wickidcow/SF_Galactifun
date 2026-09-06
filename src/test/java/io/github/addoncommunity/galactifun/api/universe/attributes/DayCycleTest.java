package io.github.addoncommunity.galactifun.api.universe.attributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DayCycleTest {

    @BeforeEach
    void clearBefore() {
        DayCycle.clearClocklessWorldCache();
    }

    @AfterEach
    void clearAfter() {
        DayCycle.clearClocklessWorldCache();
    }

    @Test
    void cachesAWorldThatRejectsTimeUpdates() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.getTime()).thenReturn(100L);
        doThrow(new IllegalArgumentException("Cannot set time in world without world clock"))
                .when(world).setTime(anyLong());

        DayCycle cycle = DayCycle.hours(1);

        assertDoesNotThrow(() -> cycle.tick(world));
        assertTrue(DayCycle.isClocklessWorldCached(worldId));
        assertDoesNotThrow(() -> cycle.tick(world));

        verify(world, times(1)).setTime(anyLong());
    }

    @Test
    void keepsUpdatingAWorldWithAClock() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.getTime()).thenReturn(100L);

        DayCycle cycle = DayCycle.hours(1);
        assertDoesNotThrow(() -> cycle.tick(world));

        assertFalse(DayCycle.isClocklessWorldCached(worldId));
        verify(world).setTime(104L);
    }

    @Test
    void doesNotHideUnexpectedIllegalArguments() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        doThrow(new IllegalArgumentException("different problem"))
                .when(world).setTime(anyLong());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DayCycle.setTimeSafely(world, 100L)
        );
    }
}
