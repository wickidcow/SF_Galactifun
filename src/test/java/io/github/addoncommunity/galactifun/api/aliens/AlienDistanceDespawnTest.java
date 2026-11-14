package io.github.addoncommunity.galactifun.api.aliens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

class AlienDistanceDespawnTest {

    @Test
    void preservesNormalAlienAndBossDistanceDespawnFlags() {
        AtomicReference<Boolean> lastFlag = new AtomicReference<>();
        Mob mob = (Mob) Proxy.newProxyInstance(
                Mob.class.getClassLoader(),
                new Class<?>[] { Mob.class },
                (proxy, method, args) -> {
                    if ("setRemoveWhenFarAway".equals(method.getName())) {
                        lastFlag.set((Boolean) args[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected Mob API call: " + method.getName());
                }
        );

        Alien.setDistanceDespawn(mob, true);
        assertEquals(Boolean.TRUE, lastFlag.get(), "Normal aliens must retain distance despawning");
        Alien.setDistanceDespawn(mob, false);
        assertEquals(Boolean.FALSE, lastFlag.get(), "Boss aliens must not distance-despawn");
    }

    @Test
    void failsExplicitlyRatherThanSilentlyChangingDespawnSemantics() {
        Mob mob = (Mob) Proxy.newProxyInstance(
                Mob.class.getClassLoader(),
                new Class<?>[] { Mob.class },
                (proxy, method, args) -> {
                    throw new IllegalArgumentException("Simulated reflective setter failure");
                }
        );

        assertThrows(IllegalStateException.class, () -> Alien.setDistanceDespawn(mob, false));
    }
}
