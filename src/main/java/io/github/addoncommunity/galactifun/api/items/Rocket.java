package io.github.addoncommunity.galactifun.api.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.base.items.knowledge.KnowledgeLevel;
import io.github.addoncommunity.galactifun.core.WorldSelector;
import io.github.addoncommunity.galactifun.core.managers.TravelManager.TravelType;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.addoncommunity.galactifun.util.BSUtils;
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.mooy1.infinitylib.common.PersistentType;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.common.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.collections.RandomizedSet;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public abstract class Rocket extends SlimefunItem implements RecipeDisplayItem {

    public static final NamespacedKey CARGO_KEY = Galactifun.createKey("cargo");

    private static final List<String> LAUNCH_MESSAGES =
            Galactifun.instance().getConfig().getStringList("rockets.launch-msgs");
    private static final double DISTANCE_PER_FUEL = 2_000_000 / Util.KM_PER_LY;

    private final int fuelCapacity;
    private final int storageCapacity;
    private final Map<String, Double> allowedFuels = new HashMap<>();

    public Rocket(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                  int fuelCapacity, int storageCapacity) {
        super(category, item, recipeType, recipe);

        this.fuelCapacity = fuelCapacity;
        this.storageCapacity = storageCapacity;
        for (Map.Entry<ItemStack, Double> entry : getAllowedFuels().entrySet()) {
            allowedFuels.put(StackUtils.getIdOrType(entry.getKey()), entry.getValue());
        }

        addItemHandler((BlockUseHandler) event -> event.getClickedBlock().ifPresent(block -> {
            event.cancel();
            openGUI(event.getPlayer(), block);
        }));

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                Block block = event.getBlock();
                BlockData data = block.getBlockData();
                if (data instanceof Rotatable rotatable) {
                    rotatable.setRotation(BlockFace.NORTH);
                }
                block.setBlockData(data, true);
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent event, ItemStack itemStack, List<ItemStack> drops) {
                if (isLaunchLocked(event.getBlock())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "The rocket is currently launching!");
                }
            }
        });
    }

    /**
     * Returns whether a launch lock is still active. Legacy/stale locks are automatically recovered.
     */
    public static boolean isLaunchLocked(@Nonnull Block rocket) {
        if (!BSUtils.getStoredBoolean(rocket.getLocation(), "isLaunching")) {
            return false;
        }

        long startedAt = 0L;
        String raw = BlockStorage.getLocationInfo(rocket.getLocation(), "launchStartedAt");
        if (raw != null) {
            try {
                startedAt = Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                startedAt = 0L;
            }
        }

        long timeoutMillis = Math.max(15,
                Galactifun.instance().getConfig().getInt("rockets.launch-lock-timeout-seconds", 90)) * 1000L;
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt > timeoutMillis) {
            clearLaunchLock(rocket);
            return false;
        }
        return true;
    }

    private static void beginLaunchLock(Block rocket) {
        BSUtils.addBlockInfo(rocket, "isLaunching", true);
        BSUtils.addBlockInfo(rocket, "launchStartedAt", System.currentTimeMillis());
    }

    public static void clearLaunchLock(@Nonnull Block rocket) {
        BSUtils.addBlockInfo(rocket, "isLaunching", false);
        BSUtils.addBlockInfo(rocket, "launchStartedAt", 0L);
    }

    private void openGUI(@Nonnull Player player, @Nonnull Block rocket) {
        if (!BlockStorage.check(rocket, this.getId())) {
            return;
        }

        if (isLaunchLocked(rocket)) {
            player.sendMessage(ChatColor.RED + "The rocket is already launching!");
            return;
        }

        WorldManager worldManager = Galactifun.worldManager();
        PlanetaryWorld currentWorld = worldManager.getWorld(player.getWorld());
        if (currentWorld == null) {
            player.sendMessage(ChatColor.RED + "You cannot travel to space from this world!");
            return;
        }

        int fuel = BSUtils.getStoredInt(rocket.getLocation(), "fuel");
        if (fuel <= 0) {
            player.sendMessage(ChatColor.RED + "The rocket has no fuel!");
            return;
        }

        String fuelType = BlockStorage.getLocationInfo(rocket.getLocation(), "fuelType");
        Double efficiency = fuelType == null ? null : allowedFuels.get(fuelType);
        if (fuelType == null || efficiency == null || efficiency <= 0D) {
            player.sendMessage(ChatColor.RED + "The rocket's stored fuel is invalid.");
            return;
        }

        double maxDistance = fuel * DISTANCE_PER_FUEL * efficiency;

        new WorldSelector((clicker, object, lore) -> {
            if (object instanceof PlanetaryWorld) {
                double distance = object.distanceTo(currentWorld);
                if (distance > maxDistance) {
                    return false;
                }

                lore.add(Component.empty());
                lore.add(Component.text()
                        .color(NamedTextColor.YELLOW)
                        .append(Component.text("Fuel: "))
                        .append(Component.text((long) Math.ceil(distance / (DISTANCE_PER_FUEL * efficiency))))
                        .build());
            }
            return true;
        }, (clicker, destination) -> {
            clicker.closeInventory();
            int usedFuel = (int) Math.ceil(destination.distanceTo(currentWorld)
                    / (DISTANCE_PER_FUEL * efficiency));
            int remainingFuel = Math.max(0, fuel - usedFuel);

            player.sendMessage(ChatColor.YELLOW
                    + "Please enter destination coordinates in the form of <x> <z> (i.e. -123 456) "
                    + "or type in anything else to cancel:");

            ChatUtils.awaitInput(player, input -> {
                if (!Util.COORD_PATTERN.matcher(input).matches()) {
                    player.sendMessage(ChatColor.RED + "Launch cancelled");
                    return;
                }

                String[] coords = Util.SPACE_PATTERN.split(input);
                final int x;
                final int z;
                try {
                    x = Integer.parseInt(coords[0]);
                    z = Integer.parseInt(coords[1]);
                } catch (NumberFormatException exception) {
                    player.sendMessage(ChatColor.RED + "Invalid coordinates");
                    return;
                }

                World destinationWorld = destination.world();
                if (destinationWorld == null) {
                    player.sendMessage(ChatColor.RED + "That planet is currently disabled or unloaded.");
                    return;
                }

                Location borderCheck = new Location(destinationWorld, x + 0.5, destinationWorld.getMinHeight(), z + 0.5);
                if (!destinationWorld.getWorldBorder().isInside(borderCheck)) {
                    player.sendMessage(ChatColor.RED + "Destination is outside of world border");
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + "Preparing destination chunk...");
                destinationWorld.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, throwable) -> Scheduler.run(() -> {
                    if (throwable != null || chunk == null) {
                        player.sendMessage(ChatColor.RED + "Could not load the destination chunk.");
                        return;
                    }
                    if (!player.isOnline() || !BlockStorage.check(rocket, this.getId())) {
                        return;
                    }
                    if (isLaunchLocked(rocket)) {
                        player.sendMessage(ChatColor.RED + "This rocket is already launching.");
                        return;
                    }

                    Block destinationBlock = Util.getHighestBlockAt(
                            destinationWorld,
                            x,
                            z,
                            block -> (block.isBuildable() || block.isLiquid())
                                    && !BlockStorage.check(block, BaseItems.LANDING_HATCH.getItemId())
                    );

                    if (!Slimefun.getProtectionManager().hasPermission(
                            player, destinationBlock, Interaction.PLACE_BLOCK)) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to land there");
                        return;
                    }

                    Block below = destinationBlock.getRelative(BlockFace.DOWN);
                    Block landingChest = below.getType() == Material.CHEST ? below : destinationBlock;
                    if (landingChest.getType() != Material.CHEST && !landingChest.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "The landing location became blocked.");
                        return;
                    }

                    chunk.addPluginChunkTicket(Galactifun.instance());
                    launch(player, rocket, fuelType, remainingFuel, destination, landingChest, chunk);
                }));
            });
        }).open(player);
    }

    private void launch(Player pilot, Block rocket, String fuelType, int fuelRemaining,
                        PlanetaryWorld destination, Block landingChest, Chunk destinationChunk) {
        beginLaunchLock(rocket);

        World sourceWorld = pilot.getWorld();
        new BukkitRunnable() {
            private final Block pad = rocket.getRelative(BlockFace.DOWN);
            private int times = 0;

            @Override
            public void run() {
                if (this.times++ < 20 && rocket.getWorld() == sourceWorld) {
                    for (BlockFace face : Util.SURROUNDING_FACES) {
                        Block block = this.pad.getRelative(face);
                        sourceWorld.spawnParticle(getLaunchParticles(), block.getLocation(), 100, 0.5, 0.5, 0.5);
                    }
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(Galactifun.instance(), 0, 10);

        RandomizedSet<String> launchMessages = new RandomizedSet<>(LAUNCH_MESSAGES);
        int[] messageDelays = {40, 80, 120, 160};
        for (int i = 0; i < Math.min(messageDelays.length, LAUNCH_MESSAGES.size()); i++) {
            sendLaunchMessage(messageDelays[i], pilot, launchMessages);
        }

        Scheduler.run(200, () -> commitLaunch(
                pilot, rocket, fuelType, fuelRemaining, destination, landingChest, destinationChunk));
    }

    private void commitLaunch(Player pilot, Block rocket, String fuelType, int fuelRemaining,
                              PlanetaryWorld destination, Block landingChest, Chunk destinationChunk) {
        try {
            if (!BlockStorage.check(rocket, this.getId()) || !isLaunchLocked(rocket)) {
                return;
            }

            World destinationWorld = destination.world();
            if (destinationWorld == null || Bukkit.getWorld(destinationWorld.getUID()) == null) {
                clearLaunchLock(rocket);
                pilot.sendMessage(ChatColor.RED + "Destination world unloaded; launch cancelled.");
                return;
            }

            if (landingChest.getType() != Material.CHEST) {
                if (!landingChest.isEmpty()) {
                    clearLaunchLock(rocket);
                    pilot.sendMessage(ChatColor.RED + "Landing site became blocked; launch cancelled.");
                    return;
                }
                landingChest.setType(Material.CHEST);
            }

            if (!(landingChest.getState() instanceof Chest chest)) {
                clearLaunchLock(rocket);
                pilot.sendMessage(ChatColor.RED + "Could not prepare landing cargo chest.");
                return;
            }

            pilot.sendMessage(ChatColor.YELLOW + "Verifying blast awesomeness...");

            List<ItemStack> payload = new ArrayList<>();
            if (fuelRemaining > 0) {
                payload.add(StackUtils.itemByIdOrType(fuelType).asQuantity(fuelRemaining));
            }
            payload.add(getItem().clone());

            ItemStack rocketVisual = null;
            if (rocket.getState() instanceof Skull skull) {
                PersistentDataContainer container = skull.getPersistentDataContainer();
                for (ItemStack cargo : container.getOrDefault(
                        CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>())) {
                    if (cargo != null && !cargo.getType().isAir()) {
                        payload.add(cargo.clone());
                    }
                }

                rocketVisual = new ItemStack(skull.getType());
                if (skull.getPlayerProfile() != null) {
                    ItemStack finalRocketVisual = rocketVisual;
                    finalRocketVisual.editMeta(meta ->
                            ((SkullMeta) meta).setPlayerProfile(skull.getPlayerProfile()));
                }
            }

            // Remove the authoritative source before creating destination copies. This closes the
            // historical crash/piston/interaction window where both source and destination could exist.
            Location sourceLocation = rocket.getLocation().clone();
            rocket.setType(Material.AIR);
            BlockStorage.clearBlockInfo(rocket);

            Inventory inventory = chest.getBlockInventory();
            for (ItemStack stack : payload) {
                storeOrDrop(inventory, landingChest.getLocation(), stack);
            }

            Location arrival = landingChest.getLocation().add(0.5, 1, 0.5);
            teleportPassengers(sourceWorldFor(sourceLocation), sourceLocation, arrival, destination);
            showLaunchAnimation(sourceLocation, rocketVisual);
        } catch (Throwable throwable) {
            if (BlockStorage.check(rocket, this.getId())) {
                clearLaunchLock(rocket);
            }
            Galactifun.log(java.util.logging.Level.SEVERE,
                    "Rocket launch failed safely at " + rocket.getLocation(), throwable.toString());
            pilot.sendMessage(ChatColor.RED + "Rocket launch failed; check the server console.");
        } finally {
            destinationChunk.removePluginChunkTicket(Galactifun.instance());
        }
    }

    private static World sourceWorldFor(Location sourceLocation) {
        return sourceLocation.getWorld();
    }

    private void teleportPassengers(World sourceWorld, Location sourceLocation, Location arrival,
                                    PlanetaryWorld destination) {
        if (sourceWorld == null || arrival.getWorld() == null) {
            return;
        }

        double radius = Math.max(1D,
                Galactifun.instance().getConfig().getDouble("rockets.passenger-radius", 5D));
        double radiusSquared = radius * radius;

        Collection<Entity> nearby = sourceWorld.getNearbyEntities(
                sourceLocation.clone().add(0.5, 0.5, 0.5),
                radius, radius, radius,
                entity -> (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) || entity instanceof Item
        );

        for (Entity entity : nearby) {
            if (entity.getLocation().distanceSquared(sourceLocation) > radiusSquared) {
                continue;
            }

            if (entity instanceof Player player && player.getWorld() != arrival.getWorld()) {
                Galactifun.travelManager().authorize(player, arrival.getWorld(), TravelType.ROCKET);
            }

            entity.teleportAsync(arrival).whenComplete((success, throwable) -> {
                if (entity instanceof Player player) {
                    Scheduler.run(() -> {
                        if (throwable != null || !Boolean.TRUE.equals(success)) {
                            Galactifun.travelManager().clear(player);
                            player.sendMessage(ChatColor.RED + "Rocket teleport failed.");
                        } else if (KnowledgeLevel.get(player, destination) == KnowledgeLevel.NONE) {
                            KnowledgeLevel.BASIC.set(player, destination);
                        }
                    });
                }
            });
        }
    }

    private void showLaunchAnimation(Location sourceLocation, ItemStack rocketVisual) {
        World world = sourceLocation.getWorld();
        if (world == null || rocketVisual == null) {
            return;
        }

        boolean hasViewer = false;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(sourceLocation) <= 64D * 64D) {
                hasViewer = true;
                break;
            }
        }
        if (!hasViewer) {
            return;
        }

        Location animationLocation = sourceLocation.clone().add(0.5, -1, 0.5);
        ArmorStand armorStand = world.spawn(animationLocation, ArmorStand.class);
        armorStand.getEquipment().setHelmet(rocketVisual);
        armorStand.setInvisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setMarker(false);
        armorStand.setBasePlate(false);

        new BukkitRunnable() {
            private int i = 0;

            @Override
            public void run() {
                i++;
                if (!armorStand.isValid()) {
                    this.cancel();
                    return;
                }
                armorStand.setVelocity(new Vector(0, 0.8 + i / 10D, 0));
                world.spawnParticle(getLaunchParticles(), armorStand.getLocation(), 10);
                if (i > 40) {
                    armorStand.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(Galactifun.instance(), 0, 8);
    }

    private static void storeOrDrop(Inventory inventory, Location location, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = inventory.addItem(stack.clone());
        for (ItemStack leftover : leftovers.values()) {
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 1, 0.5), leftover);
        }
    }

    private static void sendLaunchMessage(int delay, Player player, RandomizedSet<String> choices) {
        String message = choices.getRandom();
        choices.remove(message);
        Scheduler.run(delay, () -> {
            if (player.isOnline()) {
                player.sendMessage(Component.text()
                        .color(NamedTextColor.GOLD)
                        .append(Component.text(message))
                        .append(Component.text("..."))
                        .build());
            }
        });
    }

    protected abstract Map<ItemStack, Double> getAllowedFuels();

    @Nonnull
    protected Particle getLaunchParticles() {
        return Particle.ASH;
    }

    @Nonnull
    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> ret = new ArrayList<>();
        for (Map.Entry<ItemStack, Double> entry : getAllowedFuels().entrySet()) {
            ret.add(new CustomItemStack(
                    entry.getKey(),
                    ItemUtils.getItemName(entry.getKey()),
                    "&7Efficiency: " + entry.getValue() + 'x'
            ));
        }
        return ret;
    }

    public int fuelCapacity() {
        return this.fuelCapacity;
    }

    public int getFuelCapacity() {
        return this.fuelCapacity;
    }

    public int storageCapacity() {
        return this.storageCapacity;
    }

    public int getStorageCapacity() {
        return this.storageCapacity;
    }

    public Map<String, Double> allowedFuels() {
        return this.allowedFuels;
    }

    public Map<String, Double> getAllowedFuelsMap() {
        return this.allowedFuels;
    }
}
