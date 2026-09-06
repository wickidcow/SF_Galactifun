package io.github.addoncommunity.galactifun.core.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class StargateRegistryTest {

    @Test
    void preservesTheLegacyDeterministicAddressFormat() {
        String legacyInput = "world_galactifun_mars-120-64--450";
        String expected = Integer.toHexString(legacyInput.hashCode());

        assertEquals(expected, StargateRegistry.addressFor("world_galactifun_mars", 120, 64, -450));
    }

    @Test
    void differentControllerLocationsProduceDifferentTypicalAddresses() {
        assertNotEquals(
                StargateRegistry.addressFor("world", 0, 64, 0),
                StargateRegistry.addressFor("world", 1, 64, 0)
        );
    }
}
