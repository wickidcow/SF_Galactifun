package io.github.addoncommunity.galactifun.api.universe.attributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        TestWorld testWorld = testWorld("Cannot set time in world without world clock");
        DayCycle cycle = DayCycle.hours(1);

        assertDoesNotThrow(() -> cycle.tick(testWorld.world()));
        assertTrue(DayCycle.isClocklessWorldCached(testWorld.id()));
        assertEquals(1, testWorld.timeReads().get());
        assertEquals(1, testWorld.timeWrites().get());

        assertDoesNotThrow(() -> cycle.tick(testWorld.world()));

        // Once Purpur/Paper tells us this dimension has no clock, the hot tick path must not
        // touch either getTime() or setTime() again.
        assertEquals(1, testWorld.timeReads().get());
        assertEquals(1, testWorld.timeWrites().get());
    }

    @Test
    void keepsUpdatingAWorldWithAClock() {
        TestWorld testWorld = testWorld(null);
        DayCycle cycle = DayCycle.hours(1);

        assertDoesNotThrow(() -> cycle.tick(testWorld.world()));

        assertFalse(DayCycle.isClocklessWorldCached(testWorld.id()));
        assertEquals(1, testWorld.timeReads().get());
        assertEquals(1, testWorld.timeWrites().get());
        assertEquals(104L, testWorld.lastWrittenTime().get());
    }

    @Test
    void doesNotHideUnexpectedIllegalArguments() {
        TestWorld testWorld = testWorld("different problem");

        assertThrows(
                IllegalArgumentException.class,
                () -> DayCycle.setTimeSafely(testWorld.world(), 100L)
        );
        assertFalse(DayCycle.isClocklessWorldCached(testWorld.id()));
    }

    private static TestWorld testWorld(String rejectionMessage) {
        UUID id = UUID.randomUUID();
        AtomicInteger timeReads = new AtomicInteger();
        AtomicInteger timeWrites = new AtomicInteger();
        AtomicLong lastWrittenTime = new AtomicLong(Long.MIN_VALUE);

        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> id;
                    case "getTime" -> {
                        timeReads.incrementAndGet();
                        yield 100L;
                    }
                    case "setTime" -> {
                        timeWrites.incrementAndGet();
                        if (rejectionMessage != null) {
                            throw new IllegalArgumentException(rejectionMessage);
                        }
                        lastWrittenTime.set((Long) args[0]);
                        yield null;
                    }
                    case "toString" -> "GalactifunTestWorld[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Unexpected World method in DayCycle test: " + method.getName());
                }
        );

        return new TestWorld(id, world, timeReads, timeWrites, lastWrittenTime);
    }

    private record TestWorld(
            UUID id,
            World world,
            AtomicInteger timeReads,
            AtomicInteger timeWrites,
            AtomicLong lastWrittenTime
    ) {
    }
}
