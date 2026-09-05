package io.github.addoncommunity.galactifun.base.aliens;

import javax.annotation.Nonnull;


import org.bukkit.entity.Blaze;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.aliens.Alien;
import io.github.mooy1.infinitylib.common.Events;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Firestorm extends Alien<Blaze> implements Listener {

    private static NamespacedKey electrifiedKey() {
        return new NamespacedKey(Galactifun.instance(), "electrified");
    }

    public Firestorm(@Nonnull String id, @Nonnull String name, double maxHealth, double spawnChance) {
        super(Blaze.class, id, name, maxHealth, spawnChance);

        Events.registerListener(this);
    }

    @Override
    public void onShoot(@Nonnull ProjectileLaunchEvent e) {
        e.getEntity().getPersistentDataContainer().set(
                electrifiedKey(), PersistentDataType.BYTE, (byte) 1);
    }

    @Override
    public void onDamage(@Nonnull EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onFireballHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        if (projectile.getPersistentDataContainer().has(electrifiedKey(), PersistentDataType.BYTE)) {
            projectile.getWorld().strikeLightning(projectile.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerDeath(PlayerDeathEvent e) {
        Component msg = e.deathMessage();
        if (msg == null) return;

        String s = PlainTextComponentSerializer.plainText().serialize(msg);
        if (s.endsWith(" was struck by lightning whilst fighting Firestorm")) {
            e.deathMessage(Component.text(e.getEntity().getName() + " was electrocuted by Firestorm"));
        }
    }

    @Override
    protected boolean canSpawnInLightLevel(int lightLevel) {
        return true;
    }

}
