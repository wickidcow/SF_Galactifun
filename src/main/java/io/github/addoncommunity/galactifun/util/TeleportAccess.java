package io.github.addoncommunity.galactifun.util;

import io.github.addoncommunity.galactifun.Galactifun;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * One-shot permission marker used while Galactifun transports players between planetary worlds.
 * Replaces Bukkit's deprecated metadata API with the standard Persistent Data Container.
 */
public final class TeleportAccess {

    private TeleportAccess() {}

    private static NamespacedKey key() {
        return new NamespacedKey(Galactifun.instance(), "can_tp_alien_world");
    }

    public static void grant(Entity entity) {
        entity.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
    }

    public static void revoke(Entity entity) {
        entity.getPersistentDataContainer().remove(key());
    }

    public static boolean consume(Player player) {
        NamespacedKey key = key();
        Byte allowed = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        if (allowed != null && allowed == 1) {
            player.getPersistentDataContainer().remove(key);
            return true;
        }
        return false;
    }
}
