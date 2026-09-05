package io.github.mooy1.infinitylib.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;

import io.github.addoncommunity.galactifun.util.CustomItemStack;

/**
 * Persistent-data adapters used by the bundled InfinityLib compatibility layer.
 *
 * <p>New item data uses Paper's NBT byte serializers so Minecraft's data fixer can migrate it across
 * versions. Legacy Bukkit object streams are retained only as a read fallback for data written by
 * older Galactifun builds.
 */
@ParametersAreNonnullByDefault
public final class PersistentType<T, Z> implements PersistentDataType<T, Z> {

    private static final int LOCATION_MAGIC = 0x47464C32; // GFL2

    public static final PersistentDataType<byte[], ItemStack> ITEM_STACK = new PersistentType<>(
            byte[].class, ItemStack.class,
            ItemStack::serializeAsBytes,
            PersistentType::deserializeItem
    );

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final PersistentDataType<byte[], List<ItemStack>> ITEM_STACK_LIST = new PersistentType<byte[], List<ItemStack>>(
            byte[].class, (Class) List.class,
            ItemStack::serializeItemsAsBytes,
            PersistentType::deserializeItems
    );

    public static final PersistentDataType<byte[], Location> LOCATION = new PersistentType<>(
            byte[].class, Location.class,
            PersistentType::serializeLocation,
            PersistentType::deserializeLocation
    );

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final PersistentDataType<byte[], List<String>> STRING_LIST = new PersistentType<byte[], List<String>>(
            byte[].class, (Class) List.class,
            list -> {
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (DataOutputStream output = new DataOutputStream(bytes)) {
                        output.writeInt(list.size());
                        for (String value : list) {
                            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                            output.writeInt(encoded.length);
                            output.write(encoded);
                        }
                    }
                    return bytes.toByteArray();
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not serialize Galactifun string list", exception);
                }
            },
            PersistentType::deserializeStrings
    );

    /**
     * Old YAML-backed item format retained only for migrations from historical Galactifun data.
     */
    @Deprecated
    public static final PersistentDataType<String, ItemStack> ITEM_STACK_OLD = new PersistentType<>(
            String.class, ItemStack.class,
            itemStack -> {
                YamlConfiguration config = new YamlConfiguration();
                config.set("item", itemStack);
                return config.saveToString();
            },
            string -> {
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.loadFromString(string);
                } catch (InvalidConfigurationException exception) {
                    exception.printStackTrace();
                    return errorItem();
                }
                ItemStack item = config.getItemStack("item");
                return item != null ? item : errorItem();
            }
    );

    private final Class<T> primitive;
    private final Class<Z> complex;
    private final Function<Z, T> toPrimitive;
    private final Function<T, Z> toComplex;

    public PersistentType(Class<T> primitive, Class<Z> complex, Function<Z, T> toPrimitive, Function<T, Z> toComplex) {
        this.primitive = primitive;
        this.complex = complex;
        this.toPrimitive = toPrimitive;
        this.toComplex = toComplex;
    }

    @Nonnull
    @Override
    public Class<T> getPrimitiveType() {
        return primitive;
    }

    @Nonnull
    @Override
    public Class<Z> getComplexType() {
        return complex;
    }

    @Nonnull
    @Override
    public T toPrimitive(Z complex, PersistentDataAdapterContext context) {
        return toPrimitive.apply(complex);
    }

    @Nonnull
    @Override
    public Z fromPrimitive(T primitive, PersistentDataAdapterContext context) {
        return toComplex.apply(primitive);
    }

    private static ItemStack deserializeItem(byte[] bytes) {
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException modernFailure) {
            ItemStack legacy = readLegacyItem(bytes);
            return legacy != null ? legacy : errorItem();
        }
    }

    private static List<ItemStack> deserializeItems(byte[] bytes) {
        try {
            return new ArrayList<>(Arrays.asList(ItemStack.deserializeItemsFromBytes(bytes)));
        } catch (RuntimeException modernFailure) {
            return readLegacyItems(bytes);
        }
    }

    private static byte[] serializeLocation(Location location) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(LOCATION_MAGIC);
                World world = location.getWorld();
                output.writeBoolean(world != null);
                if (world != null) {
                    UUID id = world.getUID();
                    output.writeLong(id.getMostSignificantBits());
                    output.writeLong(id.getLeastSignificantBits());
                }
                output.writeDouble(location.getX());
                output.writeDouble(location.getY());
                output.writeDouble(location.getZ());
                output.writeFloat(location.getYaw());
                output.writeFloat(location.getPitch());
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize Galactifun location", exception);
        }
    }

    private static Location deserializeLocation(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != LOCATION_MAGIC) {
                Location legacy = readLegacyLocation(bytes);
                return legacy != null ? legacy : new Location(null, 0, 0, 0);
            }

            World world = null;
            if (input.readBoolean()) {
                world = Bukkit.getWorld(new UUID(input.readLong(), input.readLong()));
            }
            return new Location(
                    world,
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readFloat(),
                    input.readFloat()
            );
        } catch (Exception modernFailure) {
            Location legacy = readLegacyLocation(bytes);
            return legacy != null ? legacy : new Location(null, 0, 0, 0);
        }
    }

    private static List<String> deserializeStrings(byte[] bytes) {
        // New format: int count + int byte length + UTF-8 payload.
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = input.readInt();
            if (count < 0 || count > 100_000) {
                throw new IllegalArgumentException("Invalid string count: " + count);
            }
            List<String> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int length = input.readInt();
                if (length < 0 || length > bytes.length) {
                    throw new IllegalArgumentException("Invalid string byte length: " + length);
                }
                values.add(new String(input.readNBytes(length), StandardCharsets.UTF_8));
            }
            return values;
        } catch (Exception modernFailure) {
            // Historical format used a one-byte length prefix. Keep read compatibility while all new
            // writes use the bounded UTF-8 format above.
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            List<String> values = new ArrayList<>();
            while (input.available() > 0) {
                int length = input.read();
                if (length < 0 || length > input.available()) {
                    break;
                }
                values.add(new String(input.readNBytes(length), StandardCharsets.UTF_8));
            }
            return values;
        }
    }

    @SuppressWarnings("deprecation")
    private static ItemStack readLegacyItem(byte[] bytes) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            return value instanceof ItemStack item ? item : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static List<ItemStack> readLegacyItems(byte[] bytes) {
        List<ItemStack> items = new ArrayList<>();
        ByteArrayInputStream raw = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(raw)) {
            while (raw.available() > 0) {
                Object value = input.readObject();
                if (value instanceof ItemStack item) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
            // Return every item recovered before a malformed legacy payload was encountered.
        }
        return items;
    }

    @SuppressWarnings("deprecation")
    private static Location readLegacyLocation(byte[] bytes) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            return value instanceof Location location ? location : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack errorItem() {
        return new CustomItemStack(Material.STONE, "&cERROR");
    }
}
