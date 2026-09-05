package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.aliens.Alien;
import io.github.addoncommunity.galactifun.api.aliens.BossAlien;
import io.github.mooy1.infinitylib.common.Events;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;

public final class AlienManager implements Listener {

    private final NamespacedKey key;
    private final NamespacedKey bossKey;
    private final Map<String, Alien<?>> aliens = new ConcurrentHashMap<>();
    private final Set<UUID> alienIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> alienWorlds = new ConcurrentHashMap<>();
    private final YamlConfiguration config;
    private final File configFile;

    public AlienManager(Galactifun galactifun) {
        Events.registerListener(this);
        Scheduler.repeat(galactifun.getConfig().getInt("aliens.tick-interval", 1, 20), this::tick);

        this.configFile = new File(galactifun.getDataFolder(), "uuids.yml");
        this.config = new YamlConfiguration();

        if (this.configFile.exists()) {
            try {
                this.config.load(this.configFile);
            } catch (Exception e) {
                Galactifun.log(java.util.logging.Level.WARNING,
                        "Could not load alien UUID data", e.toString());
            }
        }

        Scheduler.run(() -> {
            try {
                this.config.save(this.configFile);
            } catch (IOException e) {
                Galactifun.log(java.util.logging.Level.WARNING,
                        "Could not save alien UUID data", e.toString());
            }
        });

        this.key = Galactifun.createKey("alien");
        this.bossKey = Galactifun.createKey("boss_alien");
        this.alienIds.addAll(this.config.getStringList("uuids").stream().map(UUID::fromString).toList());
    }

    public void register(Alien<?> alien) {
        if (this.aliens.putIfAbsent(alien.id(), alien) != null) {
            throw new IllegalArgumentException("Alien " + alien.id() + " has already been registered!");
        }
    }

    @Nullable
    public Alien<?> getAlien(@Nonnull String id) {
        return this.aliens.get(id);
    }

    @Nullable
    public Alien<?> getAlien(@Nonnull Entity entity) {
        String id = PersistentDataAPI.getString(entity, this.key);
        return id == null ? null : getAlien(id);
    }

    @Nonnull
    public Collection<Alien<?>> aliens() {
        return Collections.unmodifiableCollection(this.aliens.values());
    }

    @Nonnull
    public Set<UUID> alienIds() {
        return Collections.unmodifiableSet(this.alienIds);
    }

    /**
     * Tracks a loaded alien and its current world without requiring a later world-wide entity scan.
     */
    public void addAlien(@Nonnull Entity entity) {
        if (getAlien(entity) == null) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        this.alienIds.add(uuid);
        this.alienWorlds.put(uuid, entity.getWorld().getUID());
    }

    /**
     * Compatibility overload for older call sites.
     */
    public void addAlien(@Nonnull UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            addAlien(entity);
        }
    }

    /**
     * Returns the number of currently loaded Galactifun aliens tracked in a world.
     */
    public int countInWorld(@Nonnull World world) {
        UUID worldId = world.getUID();
        int count = 0;
        for (UUID trackedWorld : this.alienWorlds.values()) {
            if (worldId.equals(trackedWorld)) {
                count++;
            }
        }
        return count;
    }

    private void tick() {
        for (Alien<?> alien : this.aliens.values()) {
            alien.onUniqueTick();
        }

        for (UUID uuid : this.alienIds) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity livingEntity) {
                Alien<?> alien = getAlien(livingEntity);
                if (alien != null) {
                    this.alienWorlds.put(uuid, livingEntity.getWorld().getUID());
                    alien.onEntityTick(livingEntity);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienTarget(@Nonnull EntityTargetEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            alien.onTarget(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienInteract(@Nonnull PlayerInteractEntityEvent e) {
        Alien<?> alien = getAlien(e.getRightClicked());
        if (alien != null) {
            alien.onInteract(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienHit(@Nonnull EntityDamageByEntityEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            alien.onHit(e);
        }
        alien = getAlien(e.getDamager());
        if (alien != null) {
            alien.onAttack(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienDie(@Nonnull EntityDeathEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            alien.onDeath(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienCombust(@Nonnull EntityCombustEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienCastSpell(@Nonnull EntitySpellCastEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            alien.onCastSpell(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienDamage(@Nonnull EntityDamageEvent e) {
        Alien<?> alien = getAlien(e.getEntity());
        if (alien != null) {
            alien.onDamage(e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onAlienShoot(@Nonnull ProjectileLaunchEvent e) {
        ProjectileSource source = e.getEntity().getShooter();
        if (source instanceof Mob mob) {
            Alien<?> alien = getAlien(mob);
            if (alien != null) {
                alien.onShoot(e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onAlienAdd(@Nonnull EntityAddToWorldEvent e) {
        if (getAlien(e.getEntity()) != null) {
            addAlien(e.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onAlienRemove(@Nonnull EntityRemoveFromWorldEvent e) {
        UUID uuid = e.getEntity().getUniqueId();
        this.alienIds.remove(uuid);
        this.alienWorlds.remove(uuid);
    }

    public void onDisable() {
        this.aliens().forEach(a -> {
            if (a instanceof BossAlien<?> b) {
                b.removeBossBars();
            }
        });
        this.config.set("uuids", this.alienIds.stream().map(UUID::toString).toList());
        try {
            this.config.save(this.configFile);
        } catch (IOException e) {
            Galactifun.log(java.util.logging.Level.WARNING,
                    "Could not save alien UUID data", e.toString());
        }
    }

    public NamespacedKey key() { return this.key; }
    public NamespacedKey getKey() { return this.key; }
    public NamespacedKey bossKey() { return this.bossKey; }
    public NamespacedKey getBossKey() { return this.bossKey; }
}
