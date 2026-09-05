package io.github.addoncommunity.galactifun.api.worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;

import io.github.addoncommunity.galactifun.Galactifun;
import io.github.addoncommunity.galactifun.api.aliens.Alien;
import io.github.addoncommunity.galactifun.api.universe.PlanetaryObject;
import io.github.addoncommunity.galactifun.api.universe.StarSystem;
import io.github.addoncommunity.galactifun.api.universe.attributes.DayCycle;
import io.github.addoncommunity.galactifun.api.universe.attributes.Gravity;
import io.github.addoncommunity.galactifun.api.universe.attributes.Orbit;
import io.github.addoncommunity.galactifun.api.universe.attributes.atmosphere.Atmosphere;
import io.github.addoncommunity.galactifun.api.universe.types.PlanetaryType;
import io.github.addoncommunity.galactifun.api.worlds.populators.relics.FallenSatellitePopulator;
import io.github.addoncommunity.galactifun.base.universe.earth.EarthOrbit;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * Any alien world.
 *
 * @author Seggan
 * @author Mooy1
 * @see EarthOrbit
 */
public abstract class AlienWorld extends PlanetaryWorld {

    public static final NamespacedKey CHUNK_VER_KEY = Galactifun.createKey("chunk_version");

    private final Map<Material, SlimefunItemStack> blockMappings = new EnumMap<>(Material.class);
    private final List<Alien<?>> species = new ArrayList<>();

    public AlienWorld(String name, PlanetaryType type, Orbit orbit, StarSystem orbiting, ItemStack baseItem,
                      DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    public AlienWorld(String name, PlanetaryType type, Orbit orbit, PlanetaryObject orbiting, ItemStack baseItem,
                      DayCycle dayCycle, Atmosphere atmosphere, Gravity gravity) {
        super(name, type, orbit, orbiting, baseItem, dayCycle, atmosphere, gravity);
    }

    @Nullable
    @Override
    protected World loadWorld() {
        if (!getSetting("enabled", Boolean.class, enabledByDefault())) {
            return null;
        }

        Galactifun.log(Level.INFO, "Loading planet " + name());
        String worldName = "world_galactifun_" + this.id;

        // Respect worlds already loaded by Multiverse or another compatible world manager.
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName)
                    .generator(createChunkGenerator())
                    .environment(atmosphere().environment())
                    .createWorld();
        }

        Validate.notNull(world, "There was an error loading the world for " + worldName);

        if (world.getEnvironment() == World.Environment.THE_END) {
            // Prevent an Ender Dragon/exit portal from becoming part of planet gameplay.
            world.getBlockAt(0, 0, 0).setType(Material.END_PORTAL);
            world.getBlockAt(0, 1, 0).setType(Material.BEDROCK);
            world.getBlockAt(1, 0, 0).setType(Material.BEDROCK);
            world.getBlockAt(-1, 0, 0).setType(Material.BEDROCK);
            world.getBlockAt(0, 0, 1).setType(Material.BEDROCK);
            world.getBlockAt(0, 0, -1).setType(Material.BEDROCK);
        }

        dayCycle().applyEffects(world);
        atmosphere().applyEffects(world);
        return world;
    }

    /**
     * Builds this planet's chunk generator without requiring the Bukkit world to already exist.
     * This lets Multiverse and other external world managers request Galactifun's generator first.
     */
    @Nonnull
    public final ChunkGenerator createChunkGenerator() {
        return replaceChunkGenerator(new ChunkGenerator() {
            @Nullable
            @Override
            public BiomeProvider getDefaultBiomeProvider(@Nonnull WorldInfo worldInfo) {
                return getBiomeProvider(worldInfo);
            }

            @Override
            public void generateBedrock(@Nonnull WorldInfo worldInfo, @Nonnull Random random,
                                        int chunkX, int chunkZ, @Nonnull ChunkData chunkData) {
                int bedrock = getBedrockLayer();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunkData.setBlock(x, bedrock, z, Material.BEDROCK);
                    }
                }
            }

            @Override
            public void generateNoise(@Nonnull WorldInfo worldInfo, @Nonnull Random random,
                                      int x, int z, @Nonnull ChunkData chunkData) {
                generateChunk(chunkData, random, worldInfo, x, z);
            }

            @Override
            public void generateSurface(@Nonnull WorldInfo worldInfo, @Nonnull Random random,
                                        int x, int z, @Nonnull ChunkData chunkData) {
                AlienWorld.this.generateSurface(chunkData, random, worldInfo, x, z);
            }

            @Nonnull
            @Override
            public List<BlockPopulator> getDefaultPopulators(@Nonnull World world) {
                List<BlockPopulator> list = new ArrayList<>();
                getPopulators(list);
                if (getSetting("generate-fallen-satellites", Boolean.class, true)) {
                    list.add(new FallenSatellitePopulator(0.5));
                }
                return list;
            }
        });
    }

    public final void addSpecies(@Nonnull Alien<?>... aliens) {
        for (Alien<?> alien : aliens) {
            if (alien.isRegistered()) {
                this.species.add(alien);
            } else {
                throw new IllegalStateException("You must register an alien before adding it to a world!");
            }
        }
    }

    public final <T> T getSetting(@Nonnull String path, @Nonnull Class<T> clazz, T defaultValue) {
        return Galactifun.worldManager().getSetting(this, path, clazz, defaultValue);
    }

    /**
     * Allows worlds to be made of Slimefun items without storing every generated block in BlockStorage.
     */
    public final void addBlockMapping(@Nonnull Material vanillaItem, @Nonnull SlimefunItemStack slimefunItem) {
        this.blockMappings.put(vanillaItem, slimefunItem);
    }

    @Nullable
    public SlimefunItemStack getMappedItem(Block b) {
        return this.blockMappings.get(b.getType());
    }

    public boolean canSpawnVanillaMobs() {
        return false;
    }

    protected boolean enabledByDefault() {
        return true;
    }

    /**
     * @deprecated generation has been changed. Use the WorldInfo-based method instead.
     */
    @Deprecated
    protected void generateChunk(@Nonnull ChunkGenerator.ChunkData chunk, @Nonnull ChunkGenerator.BiomeGrid grid,
                                 @Nonnull Random random, @Nonnull World world, int chunkX, int chunkZ) {
    }

    protected abstract void generateChunk(@Nonnull ChunkGenerator.ChunkData chunk, @Nonnull Random random,
                                          @Nonnull WorldInfo world, int chunkX, int chunkZ);

    protected void generateSurface(@Nonnull ChunkGenerator.ChunkData chunk, @Nonnull Random random,
                                   @Nonnull WorldInfo world, int chunkX, int chunkZ) {
    }

    protected abstract void getPopulators(@Nonnull List<BlockPopulator> populators);

    @Nullable
    protected BiomeProvider getBiomeProvider(@Nonnull WorldInfo info) {
        return null;
    }

    @Nonnull
    protected ChunkGenerator replaceChunkGenerator(@Nonnull ChunkGenerator defaultGenerator) {
        return defaultGenerator;
    }

    protected int getBedrockLayer() {
        return 0;
    }

    public final void applyEffects(@Nonnull Player p) {
        atmosphere().applyEffects(p);
    }

    public final void tickWorld() {
        World world = world();
        if (world == null || Bukkit.getWorld(world.getUID()) == null) {
            return;
        }

        dayCycle().tick(world);

        List<Player> players = world.getPlayers();
        for (Player p : players) {
            gravity().applyGravity(p);
            if (p.getGameMode() == GameMode.SURVIVAL) {
                applyEffects(p);
            }
        }

        if (this.species.isEmpty() || players.isEmpty()) {
            return;
        }

        Random rand = ThreadLocalRandom.current();
        Collections.shuffle(this.species, rand);

        int mobs = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (Galactifun.alienManager().getAlien(entity) != null) {
                mobs++;
            }
        }

        int max = players.size() * Galactifun.worldManager().maxAliensPerPlayer();
        if (mobs >= max) {
            return;
        }

        for (Alien<?> alien : this.species) {
            mobs += alien.attemptSpawn(rand, world);
            if (mobs >= max) {
                break;
            }
        }
    }
}
