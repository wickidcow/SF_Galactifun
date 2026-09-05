package io.github.addoncommunity.galactifun.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.items.ExclusiveGEOResource;
import io.github.addoncommunity.galactifun.api.items.spacesuit.SpaceSuitProfile;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.AtmosphericEffect;
import io.github.addoncommunity.galactifun.api.worlds.AlienWorld;
import io.github.addoncommunity.galactifun.api.worlds.OrbitWorld;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseUniverse;
import io.github.addoncommunity.galactifun.base.universe.earth.Earth;
import io.github.addoncommunity.galactifun.core.managers.TravelManager.TravelType;
import io.github.addoncommunity.galactifun.util.ChunkStorage;
import io.github.mooy1.infinitylib.common.Events;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.thebusybiscuit.slimefun4.api.events.ExplosiveToolBreakBlocksEvent;
import io.github.thebusybiscuit.slimefun4.api.events.GEOResourceGenerationEvent;
import io.github.thebusybiscuit.slimefun4.api.events.WaypointCreateEvent;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;

public final class WorldManager implements Listener {

    private static final String PLACED = "placed";

    private final int maxAliensPerPlayer;
    private final Map<UUID, PlanetaryWorld> spaceWorldsByUuid = new HashMap<>();
    private final Map<String, PlanetaryWorld> spaceWorldsByName = new HashMap<>();
    private final Map<UUID, AlienWorld> alienWorldsByUuid = new HashMap<>();
    private final Map<String, AlienWorld> alienWorldsByName = new HashMap<>();
    private final YamlConfiguration config;
    private final YamlConfiguration defaultConfig;

    private final Map<UUID, Integer> respawnTimes = new HashMap<>();
    private final Map<UUID, Long> lastDeaths = new HashMap<>();
    private final Map<UUID, Long> oxygenDamage = new HashMap<>();

    public WorldManager(Galactifun galactifun) {
        this.maxAliensPerPlayer = galactifun.getConfig().getInt("aliens.max-per-player", 8);

        Events.registerListener(this);

        int worldTickInterval = Math.max(1,
                galactifun.getConfig().getInt("performance.world-tick-interval",
                        galactifun.getConfig().getInt("aliens.tick-interval", 100)));
        int oxygenInterval = Math.max(1,
                galactifun.getConfig().getInt("performance.oxygen-check-interval", 20));

        Scheduler.repeat(worldTickInterval,
                () -> this.alienWorldsByName.values().forEach(AlienWorld::tickWorld));
        Scheduler.repeat(oxygenInterval, this::tickOxygen);
        Scheduler.repeat(1200, Galactifun.travelManager()::clearExpired);

        if (!galactifun.getDataFolder().exists() && !galactifun.getDataFolder().mkdirs()) {
            Galactifun.log(Level.WARNING, "Could not create Galactifun data directory.");
        }

        File configFile = new File(galactifun.getDataFolder(), "worlds.yml");
        this.config = new YamlConfiguration();
        this.defaultConfig = new YamlConfiguration();
        this.config.setDefaults(this.defaultConfig);

        if (configFile.exists()) {
            try {
                this.config.load(configFile);
            } catch (Exception exception) {
                Galactifun.log(Level.SEVERE, "Could not load worlds.yml", exception.toString());
            }
        }

        Scheduler.run(() -> {
            try {
                this.config.options().copyDefaults(true);
                this.config.save(configFile);
            } catch (IOException exception) {
                Galactifun.log(Level.SEVERE, "Could not save worlds.yml", exception.toString());
            }
        });
    }

    public void register(@Nonnull PlanetaryWorld planetaryWorld) {
        World world = planetaryWorld.world();
        if (world == null) {
            throw new IllegalArgumentException("Cannot register a disabled planet " + planetaryWorld.id());
        }

        String name = normalizeWorldName(world.getName());
        PlanetaryWorld existing = this.spaceWorldsByName.get(name);
        if (existing != null && existing != planetaryWorld) {
            throw new IllegalArgumentException("World " + world.getName() + " is already registered to " + existing.id());
        }

        this.spaceWorldsByName.put(name, planetaryWorld);
        this.spaceWorldsByUuid.put(world.getUID(), planetaryWorld);

        if (planetaryWorld instanceof AlienWorld alienWorld) {
            this.alienWorldsByName.put(name, alienWorld);
            this.alienWorldsByUuid.put(world.getUID(), alienWorld);
        }
    }

    private static String normalizeWorldName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSetting(AlienWorld world, String path, Class<T> clazz, T defaultValue) {
        path = world.id() + '.' + path;
        this.defaultConfig.set(path, defaultValue);
        if (clazz == String.class) {
            return (T) this.config.getString(path);
        }
        return this.config.getObject(path, clazz);
    }

    private void tickOxygen() {
        for (PlanetaryWorld world : this.spaceWorldsByName.values()) {
            World bukkitWorld = world.world();
            if (bukkitWorld == null || org.bukkit.Bukkit.getWorld(bukkitWorld.getUID()) == null) {
                continue;
            }

            if (!world.atmosphere().requiresOxygenTank()) {
                for (Player player : bukkitWorld.getPlayers()) {
                    oxygenDamage.remove(player.getUniqueId());
                }
                continue;
            }

            for (Player player : bukkitWorld.getPlayers()) {
                if (player.getGameMode() != GameMode.SURVIVAL || player.isDead()) {
                    oxygenDamage.remove(player.getUniqueId());
                    continue;
                }

                if (!Galactifun.protectionManager().isOxygenBlock(player.getLocation())
                        && !SpaceSuitProfile.get(player).consumeOxygen(20)) {
                    player.sendMessage(ChatColor.RED + "You have run out of oxygen!");
                    double damage = oxygenDamage.merge(player.getUniqueId(), 2L, (a, b) -> a * b);
                    player.setHealth(Math.max(player.getHealth() - damage, 0));
                } else {
                    oxygenDamage.remove(player.getUniqueId());
                }
            }
        }
    }

    @Nullable
    public PlanetaryWorld getWorld(@Nonnull World world) {
        PlanetaryWorld planetaryWorld = this.spaceWorldsByUuid.get(world.getUID());
        if (planetaryWorld != null) {
            return planetaryWorld;
        }

        planetaryWorld = this.spaceWorldsByName.get(normalizeWorldName(world.getName()));
        if (planetaryWorld != null) {
            planetaryWorld.rebindWorld(world);
            this.spaceWorldsByUuid.put(world.getUID(), planetaryWorld);
            if (planetaryWorld instanceof AlienWorld alienWorld) {
                this.alienWorldsByUuid.put(world.getUID(), alienWorld);
            }
        }
        return planetaryWorld;
    }

    @Nullable
    public AlienWorld getAlienWorld(@Nonnull World world) {
        AlienWorld alienWorld = this.alienWorldsByUuid.get(world.getUID());
        if (alienWorld != null) {
            return alienWorld;
        }

        PlanetaryWorld planetaryWorld = getWorld(world);
        return planetaryWorld instanceof AlienWorld found ? found : null;
    }

    @Nonnull
    public Collection<PlanetaryWorld> spaceWorlds() {
        return Collections.unmodifiableCollection(this.spaceWorldsByName.values());
    }

    @Nonnull
    public Collection<AlienWorld> alienWorlds() {
        return Collections.unmodifiableCollection(this.alienWorldsByName.values());
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        getWorld(event.getWorld());
    }

    @EventHandler
    private void onWorldUnload(WorldUnloadEvent event) {
        this.spaceWorldsByUuid.remove(event.getWorld().getUID());
        this.alienWorldsByUuid.remove(event.getWorld().getUID());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!Galactifun.instance().getConfig().getBoolean("worlds.allow-nether-portals")
                && getAlienWorld(event.getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent event) {
        if (!Galactifun.instance().getConfig().getBoolean("worlds.allow-nether-portals")
                && getAlienWorld(event.getFrom().getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlanetChange(@Nonnull PlayerChangedWorldEvent event) {
        AlienWorld previous = getAlienWorld(event.getFrom());
        if (previous != null) {
            previous.gravity().removeGravity(event.getPlayer());
        }

        AlienWorld current = getAlienWorld(event.getPlayer().getWorld());
        if (current != null) {
            current.applyEffects(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlanetJoin(@Nonnull PlayerJoinEvent event) {
        AlienWorld world = getAlienWorld(event.getPlayer().getWorld());
        if (world != null) {
            world.applyEffects(event.getPlayer());
        }
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.oxygenDamage.remove(uuid);
        this.lastDeaths.remove(uuid);
        this.respawnTimes.remove(uuid);
        Galactifun.travelManager().clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerChangeGameMode(@Nonnull PlayerGameModeChangeEvent event) {
        AlienWorld world = getAlienWorld(event.getPlayer().getWorld());
        if (world != null
                && event.getNewGameMode() != GameMode.CREATIVE
                && event.getNewGameMode() != GameMode.SPECTATOR) {
            world.applyEffects(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerTeleport(@Nonnull PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || event.getFrom().getWorld() == to.getWorld()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("galactifun.admin")) {
            return;
        }

        PlanetaryWorld fromWorld = getWorld(event.getFrom().getWorld());
        PlanetaryWorld toWorld = getWorld(to.getWorld());
        if (!requiresTravelAuthorization(fromWorld, toWorld)) {
            return;
        }

        if (Galactifun.travelManager().consume(player, to.getWorld())) {
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && Galactifun.integrations().isMultiverseCore()) {
            boolean enteringPlanet = toWorld != null && toWorld != BaseUniverse.EARTH;
            boolean exitingPlanet = fromWorld != null && fromWorld != BaseUniverse.EARTH
                    && (toWorld == null || toWorld == BaseUniverse.EARTH);

            if ((enteringPlanet && Galactifun.integrations().allowMultiversePlanetEntry())
                    || (exitingPlanet && Galactifun.integrations().allowMultiversePlanetExit())) {
                return;
            }
        }

        event.setCancelled(true);
    }

    private static boolean requiresTravelAuthorization(@Nullable PlanetaryWorld fromWorld,
                                                       @Nullable PlanetaryWorld toWorld) {
        if (fromWorld == null && toWorld == null) {
            return false;
        }
        if (toWorld == BaseUniverse.EARTH) {
            return false;
        }
        if (fromWorld == BaseUniverse.EARTH) {
            return toWorld != null && toWorld != BaseUniverse.EARTH;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onCreatureSpawn(@Nonnull CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            AlienWorld world = getAlienWorld(event.getEntity().getWorld());
            if (world != null && !world.canSpawnVanillaMobs()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onWaypointCreate(@Nonnull WaypointCreateEvent event) {
        if (getAlienWorld(event.getPlayer().getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onCropGrow(@Nonnull BlockGrowEvent event) {
        Block block = event.getBlock();
        AlienWorld world = getAlienWorld(block.getWorld());
        if (world == null) {
            return;
        }

        ProtectionManager manager = Galactifun.protectionManager();
        Location location = block.getLocation();
        if (manager.getEffectAt(location, AtmosphericEffect.COLD) > 1) {
            Scheduler.run(() -> block.setType(Material.ICE));
        } else if (manager.getEffectAt(location, AtmosphericEffect.HEAT) > 1) {
            Scheduler.run(block::breakNaturally);
        } else {
            int attempts = world.atmosphere().growthAttempts();
            if (attempts != 0 && SlimefunTag.CROPS.isTagged(block.getType())) {
                BlockData data = block.getBlockData();
                if (data instanceof Ageable ageable) {
                    ageable.setAge(Math.max(0, Math.min(ageable.getMaximumAge(), ageable.getAge() + attempts)));
                    block.setBlockData(ageable);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPistonExtend(BlockPistonExtendEvent event) {
        protectMappedBlocksFromPistons(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPistonRetract(BlockPistonRetractEvent event) {
        protectMappedBlocksFromPistons(event.getBlocks(), event);
    }

    private void protectMappedBlocksFromPistons(List<Block> blocks, org.bukkit.event.Cancellable event) {
        if (!Galactifun.instance().getConfig().getBoolean("security.prevent-piston-mapped-block-moves", true)) {
            return;
        }

        for (Block block : blocks) {
            AlienWorld world = getAlienWorld(block.getWorld());
            if (world != null && world.getMappedItem(block) != null) {
                // Moving a mapped resource can desynchronise its placed/generated provenance and was
                // historically a duplication vector. Cancel the entire piston action instead.
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World bukkitWorld = block.getWorld();
        AlienWorld world = getAlienWorld(bukkitWorld);
        if (world == null) {
            return;
        }

        SlimefunItemStack item = world.getMappedItem(block);
        if (item != null && !removePlacedBlock(block)) {
            event.setDropItems(false);
            List<ItemStack> drops = new ArrayList<>();
            ItemStack itemStack = item.item().clone();
            drops.add(itemStack);
            item.getItem().callItemHandler(BlockBreakHandler.class,
                    handler -> handler.onPlayerBreak(event, itemStack, drops));
            for (ItemStack drop : drops) {
                bukkitWorld.dropItemNaturally(block.getLocation().add(0.5, 0, 0.5), drop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onExplosivePickUse(ExplosiveToolBreakBlocksEvent event) {
        handleExplosion(event.getAdditionalBlocks().iterator());
    }

    private void handleExplosion(Iterator<Block> blocks) {
        while (blocks.hasNext()) {
            Block block = blocks.next();
            World bukkitWorld = block.getWorld();
            AlienWorld world = getAlienWorld(bukkitWorld);
            if (world != null) {
                SlimefunItemStack item = world.getMappedItem(block);
                if (item != null && !removePlacedBlock(block)) {
                    blocks.remove();
                    bukkitWorld.dropItemNaturally(block.getLocation().add(0.5, 0, 0.5), item.item().clone());
                    Scheduler.run(() -> block.setType(Material.AIR));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    private void onSleep(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        PlanetaryWorld world = getWorld(player.getWorld());
        if (world == null || world.atmosphere().environment() == World.Environment.NORMAL) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block != null && Tag.BEDS.isTagged(block.getType())) {
            event.setCancelled(true);
            player.setBedSpawnLocation(player.getLocation(), true);
            player.sendMessage("Respawn point set");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        AlienWorld world = getAlienWorld(block.getWorld());
        if (world != null && world.getMappedItem(block) != null) {
            addPlacedBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onRespawnLoop(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (getWorld(player.getWorld()) == null) {
            return;
        }

        Long lastDeath = this.lastDeaths.get(player.getUniqueId());
        if (lastDeath != null && System.currentTimeMillis() - lastDeath < 60_000L) {
            int times = this.respawnTimes.merge(player.getUniqueId(), 1, Integer::sum);
            if (times > 3) {
                player.sendMessage(ChatColor.YELLOW + """
                        A possible respawn loop has been detected!
                        Do you wish to go back to Earth? (yes/no)""");
                ChatUtils.awaitInput(player, input -> {
                    if (input.equalsIgnoreCase("yes") && BaseUniverse.EARTH.world() != null) {
                        Galactifun.travelManager().authorize(player, BaseUniverse.EARTH.world(), TravelType.RESPAWN);
                        player.teleportAsync(BaseUniverse.EARTH.world().getSpawnLocation());
                        this.respawnTimes.remove(player.getUniqueId());
                    }
                });
            }
        }

        this.lastDeaths.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onPlayerPlaceWater(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }

        Player player = event.getPlayer();
        PlanetaryWorld world = getWorld(player.getWorld());
        if (world == null || world == BaseUniverse.EARTH) {
            return;
        }

        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = player.getInventory().getItem(event.getHand());
            if (item != null) {
                ItemUtils.consumeItem(item, true);
            }
        }

        ProtectionManager manager = Galactifun.protectionManager();
        Block clicked = event.getBlockClicked();
        Block toBePlaced = clicked.getRelative(event.getBlockFace());
        Location location = toBePlaced.getLocation();
        if (manager.getEffectAt(location, AtmosphericEffect.COLD) > 1) {
            if (toBePlaced.isEmpty()) {
                toBePlaced.setType(Material.ICE);
            }
        } else if (manager.getEffectAt(location, AtmosphericEffect.HEAT) > 1) {
            player.getWorld().spawnParticle(Particle.SMOKE, location, 5);
        } else {
            event.setCancelled(false);
        }
    }

    @EventHandler
    private void onGEOResourceGenerate(GEOResourceGenerationEvent event) {
        PlanetaryWorld world = getWorld(event.getWorld());
        if (world == null) {
            return;
        }

        if (event.getResource() instanceof ExclusiveGEOResource exclusiveResource) {
            if (exclusiveResource.getWorlds().contains(world)) {
                return;
            }
        } else {
            if (world instanceof Earth) {
                return;
            }

            for (GEOResource resource : world.resources()) {
                if (resource.equals(event.getResource())) {
                    return;
                }
            }
        }

        event.setValue(0);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerFallInOrbit(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        PlanetaryWorld world = getWorld(event.getEntity().getWorld());
        if (!(world instanceof OrbitWorld orbitWorld)
                || !(orbitWorld.getPlanet() instanceof PlanetaryWorld planet)
                || planet.world() == null) {
            return;
        }

        event.setCancelled(true);
        Location current = event.getEntity().getLocation();
        Location destination = new Location(
                planet.world(),
                current.getX(),
                planet.world().getMaxHeight(),
                current.getZ()
        );

        if (event.getEntity() instanceof Player player) {
            Galactifun.travelManager().authorize(player, planet.world(), TravelType.RESPAWN);
        }
        event.getEntity().teleportAsync(destination);
    }

    public void addPlacedBlock(Block block) {
        ChunkStorage.tag(block, PLACED);
    }

    public boolean isPlacedBlock(Block block) {
        return ChunkStorage.isTagged(block, PLACED);
    }

    /**
     * Removes a non-world-mapped block from the placed-block list.
     *
     * @return true if the block was player placed, false if it was generated by the planet
     */
    public boolean removePlacedBlock(Block block) {
        return ChunkStorage.untag(block, PLACED);
    }

    public int maxAliensPerPlayer() {
        return this.maxAliensPerPlayer;
    }

    public int getMaxAliensPerPlayer() {
        return this.maxAliensPerPlayer;
    }
}
