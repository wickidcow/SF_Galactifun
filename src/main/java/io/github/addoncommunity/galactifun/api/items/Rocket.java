package io.github.addoncommunity.galactifun.api.items;

import io.github.addoncommunity.galactifun.util.SFStorage;

import io.github.addoncommunity.galactifun.util.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.worlds.PlanetaryWorld;
import io.github.addoncommunity.galactifun.base.BaseItems;
import io.github.addoncommunity.galactifun.base.items.knowledge.KnowledgeLevel;
import io.github.addoncommunity.galactifun.core.WorldSelector;
import io.github.addoncommunity.galactifun.core.managers.RocketLaunchRegistry;
import io.github.addoncommunity.galactifun.core.managers.RocketLaunchRegistry.State;
import io.github.addoncommunity.galactifun.core.managers.WorldManager;
import io.github.addoncommunity.galactifun.util.BSUtils;
import io.github.addoncommunity.galactifun.util.Util;
import io.github.addoncommunity.galactifun.util.TeleportAccess;
import io.papermc.paper.datacomponent.DataComponentTypes;
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
import io.github.addoncommunity.galactifun.util.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public abstract class Rocket extends SlimefunItem implements RecipeDisplayItem {

    public static final NamespacedKey CARGO_KEY = Galactifun.createKey("cargo");

    private static final String IS_LAUNCHING = "isLaunching";
    private static final String LAUNCH_STATE = "launchState";
    private static final List<String> LAUNCH_MESSAGES = Galactifun.instance().getConfig().getStringList("rockets.launch-msgs");
    private static final double DISTANCE_PER_FUEL = 2_000_000 / Util.KM_PER_LY;

    private final int fuelCapacity;
    private final int storageCapacity;
    private final Map<String, Double> allowedFuels = new HashMap<>();

    public Rocket(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, int fuelCapacity, int storageCapacity) {
        super(category, item, recipeType, recipe);

        this.fuelCapacity = fuelCapacity;
        this.storageCapacity = storageCapacity;
        for (Map.Entry<ItemStack, Double> entry : getAllowedFuels().entrySet()) {
            allowedFuels.put(StackUtils.getIdOrType(entry.getKey()), entry.getValue());
        }

        addItemHandler((BlockUseHandler) e -> e.getClickedBlock().ifPresent(block -> {
            e.cancel();
            openGUI(e.getPlayer(), block);
        }));

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                Block b = e.getBlock();
                BlockData data = b.getBlockData();
                if (data instanceof Rotatable rotatable) {
                    rotatable.setRotation(BlockFace.NORTH);
                }
                b.setBlockData(data, true);
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            @ParametersAreNonnullByDefault
            public void onPlayerBreak(BlockBreakEvent e, ItemStack itemStack, List<ItemStack> list) {
                if (isLaunchLocked(e.getBlock())) {
                    e.setCancelled(true);
                    Messages.red(e.getPlayer(), "The rocket is currently reserved or launching!");
                }
            }
        });
    }

    public static boolean isLaunchLocked(@Nonnull Block rocket) {
        String key = rocketKey(rocket);
        if (RocketLaunchRegistry.isLocked(key)) {
            return true;
        }

        // Persistent launch flags from an interrupted shutdown are not authoritative. If no live
        // reservation exists, repair the stale state so a rocket can be used again after restart.
        if (BSUtils.getStoredBoolean(rocket.getLocation(), IS_LAUNCHING)) {
            BSUtils.addBlockInfo(rocket, IS_LAUNCHING, false);
            SFStorage.setData(rocket, LAUNCH_STATE, "READY");
        }
        return false;
    }

    @Nonnull
    public static String launchStatus(@Nonnull Block rocket) {
        return RocketLaunchRegistry.reservation(rocketKey(rocket))
                .map(reservation -> reservation.state() == State.LAUNCHING ? "Launching" : "Destination Reserved")
                .orElseGet(() -> {
                    isLaunchLocked(rocket);
                    return "Ready";
                });
    }

    private void openGUI(@Nonnull Player p, @Nonnull Block b) {
        if (!SFStorage.isItem(b, this.getId())) return;

        if (isLaunchLocked(b)) {
            Messages.red(p, "The rocket is already reserved or launching!");
            return;
        }

        WorldManager worldManager = Galactifun.worldManager();
        PlanetaryWorld currentWorld = worldManager.getWorld(p.getWorld());
        if (currentWorld == null) {
            Messages.red(p, "You cannot travel to space from this world!");
            return;
        }

        int fuel = BSUtils.getStoredInt(b.getLocation(), "fuel");
        if (fuel == 0) {
            Messages.red(p, "The rocket has no fuel!");
            return;
        }

        String fuelType = SFStorage.getData(b.getLocation(), "fuelType");
        if (fuelType == null) {
            Messages.red(p, "The rocket has no valid fuel type stored!");
            return;
        }

        Double efficiency = allowedFuels.get(fuelType);
        if (efficiency == null) {
            Messages.red(p, "The rocket contains a fuel type it can no longer use.");
            return;
        }
        double eff = efficiency;
        double maxDistance = maxDistanceFor(fuel, fuelType);

        sendStatusSummary(p, b, fuel, fuelType, maxDistance);

        new WorldSelector((player, obj, lore) -> {
            if (obj instanceof PlanetaryWorld) {
                double distance = obj.distanceTo(currentWorld);
                if (distance > maxDistance) return false;

                lore.add(Component.empty());
                lore.add(Component.text()
                        .color(NamedTextColor.YELLOW)
                        .append(Component.text("Fuel required: "))
                        .append(Component.text((long) Math.ceil(distance / (DISTANCE_PER_FUEL * eff))))
                        .build());
            }
            return true;
        }, (player, destination) -> {
            player.closeInventory();

            if (!reserveLaunch(p, b)) {
                Messages.red(p, "Another player reserved this rocket first.");
                return;
            }

            int usedFuel = Math.min(
                    fuel,
                    (int) Math.ceil(destination.distanceTo(currentWorld) / (DISTANCE_PER_FUEL * eff))
            );
            scheduleReservationTimeout(p, b);
            Messages.yellow(p, "Destination reserved. Enter landing coordinates as <x> <z> (for example -123 456), or type anything else to cancel:");

            ChatUtils.awaitInput(p, input -> handleDestinationInput(
                    p,
                    b,
                    destination,
                    input,
                    fuelType,
                    fuel,
                    usedFuel
            ));
        }).open(p);
    }

    private void handleDestinationInput(
            @Nonnull Player player,
            @Nonnull Block rocket,
            @Nonnull PlanetaryWorld destination,
            @Nonnull String input,
            @Nonnull String fuelType,
            int fuel,
            int usedFuel
    ) {
        String key = rocketKey(rocket);
        UUID owner = player.getUniqueId();
        if (!RocketLaunchRegistry.isOwnedBy(key, owner, State.RESERVED)) {
            Messages.red(player, "This rocket reservation expired or was cancelled.");
            return;
        }

        if (!Util.COORD_PATTERN.matcher(input).matches()) {
            Messages.red(player, "Launch cancelled");
            releaseLaunch(rocket, owner);
            return;
        }

        try {
            String[] coords = Util.SPACE_PATTERN.split(input);
            Block destBlock = Util.getHighestBlockAt(
                    destination.world(),
                    Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]),
                    location -> (location.isBuildable() || location.isLiquid())
                            && !SFStorage.isItem(location, BaseItems.LANDING_HATCH.getItemId())
            );
            destBlock.getChunk().load();

            if (!destBlock.getWorld().getWorldBorder().isInside(destBlock.getLocation())) {
                Messages.red(player, "Destination is outside of world border");
                releaseLaunch(rocket, owner);
                return;
            }
            if (!Slimefun.getProtectionManager().hasPermission(player, destBlock, Interaction.PLACE_BLOCK)) {
                Messages.red(player, "You do not have permission to land there");
                releaseLaunch(rocket, owner);
                return;
            }
            if (!beginLaunching(rocket, owner)) {
                Messages.red(player, "The rocket reservation is no longer valid.");
                releaseLaunch(rocket, owner);
                return;
            }

            Block down = destBlock.getRelative(BlockFace.DOWN);
            if (down.getType() == Material.CHEST) {
                destBlock = down;
            } else {
                destBlock.setType(Material.CHEST);
            }

            int remainingFuel = Math.max(0, fuel - usedFuel);
            ItemStack fuelLeft = remainingFuel > 0
                    ? StackUtils.itemByIdOrType(fuelType).asQuantity(remainingFuel)
                    : null;
            launch(player, rocket, fuelLeft, destination, destBlock);
        } catch (RuntimeException exception) {
            releaseLaunch(rocket, owner);
            Galactifun.instance().getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Rocket launch validation failed for " + player.getName(),
                    exception
            );
            Messages.red(player, "Launch aborted safely because the destination could not be prepared.");
        }
    }

    private boolean reserveLaunch(@Nonnull Player player, @Nonnull Block rocket) {
        String key = rocketKey(rocket);
        if (!RocketLaunchRegistry.reserve(key, player.getUniqueId())) {
            return false;
        }

        BSUtils.addBlockInfo(rocket, IS_LAUNCHING, true);
        SFStorage.setData(rocket, LAUNCH_STATE, State.RESERVED.name());
        return true;
    }

    private boolean beginLaunching(@Nonnull Block rocket, @Nonnull UUID owner) {
        String key = rocketKey(rocket);
        if (!RocketLaunchRegistry.markLaunching(key, owner)) {
            return false;
        }

        BSUtils.addBlockInfo(rocket, IS_LAUNCHING, true);
        SFStorage.setData(rocket, LAUNCH_STATE, State.LAUNCHING.name());
        return true;
    }

    private static void releaseLaunch(@Nonnull Block rocket, @Nonnull UUID owner) {
        RocketLaunchRegistry.release(rocketKey(rocket), owner);
        if (SFStorage.item(rocket) instanceof Rocket) {
            BSUtils.addBlockInfo(rocket, IS_LAUNCHING, false);
            SFStorage.setData(rocket, LAUNCH_STATE, "READY");
        }
    }

    private static void scheduleReservationTimeout(@Nonnull Player player, @Nonnull Block rocket) {
        int timeoutSeconds = Galactifun.instance().getConfig().getInt("rockets.reservation-timeout-seconds", 60);
        timeoutSeconds = Math.max(15, Math.min(timeoutSeconds, 300));
        String key = rocketKey(rocket);
        UUID owner = player.getUniqueId();
        Scheduler.run(timeoutSeconds * 20, () -> {
            if (RocketLaunchRegistry.isOwnedBy(key, owner, State.RESERVED)) {
                releaseLaunch(rocket, owner);
                if (player.isOnline()) {
                    Messages.red(player, "Rocket reservation expired. Select the destination again when ready.");
                }
            }
        });
    }

    private void launch(
            @Nonnull Player p,
            @Nonnull Block rocket,
            @Nullable ItemStack fuelLeft,
            @Nonnull PlanetaryWorld destination,
            @Nonnull Block destBlock
    ) {
        World playerWorld = p.getWorld();
        new BukkitRunnable() {
            private final Block pad = rocket.getRelative(BlockFace.DOWN);
            private int times = 0;

            @Override
            public void run() {
                if (this.times++ < 20) {
                    for (BlockFace face : Util.SURROUNDING_FACES) {
                        Block block = this.pad.getRelative(face);
                        playerWorld.spawnParticle(getLaunchParticles(), block.getLocation(), 100, 0.5, 0.5, 0.5);
                    }
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(Galactifun.instance(), 0, 10);

        RandomizedSet<String> launchMessages = new RandomizedSet<>(LAUNCH_MESSAGES);
        sendLaunchMessage(40, p, launchMessages);
        sendLaunchMessage(80, p, launchMessages);
        sendLaunchMessage(120, p, launchMessages);
        sendLaunchMessage(160, p, launchMessages);

        Scheduler.run(200, () -> finishLaunch(p, rocket, fuelLeft, destination, destBlock));
    }

    private void finishLaunch(
            @Nonnull Player p,
            @Nonnull Block rocket,
            @Nullable ItemStack fuelLeft,
            @Nonnull PlanetaryWorld destination,
            @Nonnull Block destBlock
    ) {
        UUID owner = p.getUniqueId();
        String key = rocketKey(rocket);
        if (!RocketLaunchRegistry.isOwnedBy(key, owner, State.LAUNCHING)
                || !SFStorage.isItem(rocket, this.getId())) {
            releaseLaunch(rocket, owner);
            return;
        }

        if (destBlock.getType() != Material.CHEST
                || !Slimefun.getProtectionManager().hasPermission(p, destBlock, Interaction.PLACE_BLOCK)) {
            Messages.red(p, "Launch aborted safely because the landing chest is no longer available.");
            releaseLaunch(rocket, owner);
            return;
        }

        BlockState state = rocket.getState();
        if (!(state instanceof Skull skull)) {
            Messages.red(p, "Launch aborted safely because the rocket block state is invalid.");
            releaseLaunch(rocket, owner);
            return;
        }

        Messages.yellow(p, "Verifying blast awesomeness...");

        List<ItemStack> delivery = new ArrayList<>();
        if (fuelLeft != null && fuelLeft.getAmount() > 0) {
            delivery.add(fuelLeft);
        }
        delivery.add(getItem().clone());

        PersistentDataContainer container = skull.getPersistentDataContainer();
        container.getOrDefault(CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>())
                .forEach(stack -> delivery.add(stack.clone()));

        ItemStack rocketVisual = new ItemStack(skull.getType());
        if (skull.getProfile() != null) {
            rocketVisual.setData(DataComponentTypes.PROFILE, skull.getProfile());
        }

        Location sourceLocation = rocket.getLocation();

        // Remove the source before delivering its contents. If anything after this point cannot enter
        // the chest, leftovers are dropped at the destination instead of leaving a duplicate source.
        rocket.setType(Material.AIR);
        SFStorage.remove(rocket);
        RocketLaunchRegistry.release(key, owner);

        Chest chest = (Chest) PaperLib.getBlockState(destBlock, false).getState();
        Inventory inv = chest.getBlockInventory();
        for (ItemStack stack : delivery) {
            Map<Integer, ItemStack> leftovers = inv.addItem(stack);
            leftovers.values().forEach(leftover ->
                    destBlock.getWorld().dropItemNaturally(destBlock.getLocation().add(0.5, 1, 0.5), leftover)
            );
        }

        boolean showLaunchAnimation = false;
        for (Entity entity : playerWorld.getEntities()) {
            if ((entity instanceof LivingEntity && !(entity instanceof ArmorStand)) || entity instanceof Item) {
                if (entity.getLocation().distanceSquared(sourceLocation) <= 25) {
                    if (entity instanceof Player teleportedPlayer) {
                        TeleportAccess.grant(teleportedPlayer);
                        if (KnowledgeLevel.get(teleportedPlayer, destination) == KnowledgeLevel.NONE) {
                            KnowledgeLevel.BASIC.set(teleportedPlayer, destination);
                        }
                    }

                    PaperLib.teleportAsync(entity, destBlock.getLocation().add(0, 1, 0))
                            .whenComplete((ignored, throwable) -> {
                                if (entity instanceof Player teleportedPlayer) {
                                    TeleportAccess.revoke(teleportedPlayer);
                                }
                            });
                } else if (entity.getLocation().distanceSquared(sourceLocation) <= 4096
                        && entity instanceof Player) {
                    showLaunchAnimation = true;
                }
            }
        }

        if (showLaunchAnimation) {
            Location rocketLocation = sourceLocation.clone().add(0.5, -1, 0.5);
            ArmorStand armorStand = rocketLocation.getWorld().spawn(rocketLocation, ArmorStand.class);
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
                    armorStand.setVelocity(new Vector(0, 0.8 + i / 10D, 0));
                    rocketLocation.getWorld().spawnParticle(getLaunchParticles(), armorStand.getLocation(), 10);
                    if (i > 40) {
                        armorStand.remove();
                        this.cancel();
                    }
                }
            }.runTaskTimer(Galactifun.instance(), 0, 8);
        }
    }

    private void sendStatusSummary(
            @Nonnull Player player,
            @Nonnull Block rocket,
            int fuel,
            @Nonnull String fuelType,
            double maxDistance
    ) {
        String fuelName = fuelType;
        ItemStack fuelItem = StackUtils.itemByIdOrType(fuelType);
        if (fuelItem != null) {
            fuelName = ItemUtils.getItemName(fuelItem);
        }

        int cargoStacks = 0;
        BlockState state = rocket.getState();
        if (state instanceof Skull skull) {
            cargoStacks = skull.getPersistentDataContainer()
                    .getOrDefault(CARGO_KEY, PersistentType.ITEM_STACK_LIST, new ArrayList<>())
                    .size();
        }

        Messages.yellow(player, "Rocket Status: " + launchStatus(rocket));
        Messages.yellow(player, "Fuel: " + fuel + "/" + this.fuelCapacity + " " + fuelName
                + " | Range: " + Util.formatDistance(maxDistance));
        Messages.yellow(player, "Cargo: " + cargoStacks + "/" + this.storageCapacity + " stacks");
    }

    @Nonnull
    private static String rocketKey(@Nonnull Block block) {
        Location location = block.getLocation();
        return block.getWorld().getUID() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private static void sendLaunchMessage(int delay, Player p, RandomizedSet<String> choices) {
        String msg = choices.getRandom();
        if (msg == null) {
            return;
        }
        choices.remove(msg);
        Scheduler.run(delay, () -> p.sendMessage(Component.text()
                .color(NamedTextColor.GOLD)
                .append(Component.text(msg))
                .append(Component.text("..."))
                .build()));
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

    public double maxDistanceFor(int fuel, @Nullable String fuelType) {
        if (fuelType == null) {
            return 0D;
        }
        return fuel * DISTANCE_PER_FUEL * allowedFuels.getOrDefault(fuelType, 0D);
    }

    public int fuelCapacity() { return this.fuelCapacity; }
    public int getFuelCapacity() { return this.fuelCapacity; }
    public int storageCapacity() { return this.storageCapacity; }
    public int getStorageCapacity() { return this.storageCapacity; }
    public Map<String, Double> allowedFuels() { return this.allowedFuels; }
    public Map<String, Double> getAllowedFuelsMap() { return this.allowedFuels; }
}
