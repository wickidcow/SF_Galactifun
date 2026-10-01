package io.github.addoncommunity.galactifun.core.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real Bukkit YAML and filesystem regressions for the production persistence boundary. */
class StargateRegistryFileTest {
    @TempDir
    Path directory;

    private Path dataFile() {
        return directory.resolve("stargates.yml");
    }

    @Test
    void genuinelyMissingFileIsFreshButIsNotCreatedByARead() throws Exception {
        assertTrue(StargateRegistryFile.load(dataFile()).getKeys(false).isEmpty());
        assertFalse(Files.exists(dataFile()));
        Path nested = directory.resolve("new/nested/stargates.yml");
        assertTrue(StargateRegistryFile.load(nested).getKeys(false).isEmpty());
        assertFalse(Files.exists(nested.getParent()));
    }

    @Test
    void validEmptyYamlIsReadOnlyIncludingCommentsAndCrLf() throws Exception {
        byte[] original = "# Existing empty file\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dataFile(), original);
        assertTrue(StargateRegistryFile.load(dataFile()).getKeys(false).isEmpty());
        assertArrayEquals(original, Files.readAllBytes(dataFile()));
    }

    @Test
    void invalidYamlIsNeverReturnedAsEmptyData() throws Exception {
        byte[] original = "existing:\n  owner: ExactOwner\ninvalid: [\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dataFile(), original);
        assertThrows(InvalidConfigurationException.class, () -> StargateRegistryFile.load(dataFile()));
        assertArrayEquals(original, Files.readAllBytes(dataFile()));
    }

    @Test
    void invalidUtf8AndDirectoriesFailWithoutReplacement() throws Exception {
        byte[] original = {(byte) 0xff, (byte) 0xfe};
        Files.write(dataFile(), original);
        assertThrows(IOException.class, () -> StargateRegistryFile.load(dataFile()));
        assertArrayEquals(original, Files.readAllBytes(dataFile()));
        assertThrows(IOException.class, () -> StargateRegistryFile.load(directory));
    }

    @Test
    void fullYamlRoundTripPreservesUnknownKeysAndLargeValues() throws Exception {
        String text = "# Existing custom data\nowner: ExactOwner\ncount: 9223372036854775807\n"
                + "charge: 123.4567\nfuture:\n  enabled: false\n  labels: [first, second]\n";
        Files.writeString(dataFile(), text);
        for (int i = 0; i < 3; i++) {
            var data = StargateRegistryFile.load(dataFile());
            assertEquals("ExactOwner", data.getString("owner"));
            assertEquals(Long.MAX_VALUE, data.getLong("count"));
            assertEquals(Double.doubleToRawLongBits(123.4567), Double.doubleToRawLongBits(data.getDouble("charge")));
            assertFalse(data.getBoolean("future.enabled", true));
            assertEquals(List.of("first", "second"), data.getStringList("future.labels"));
            StargateRegistryFile.save(data, dataFile());
        }
        assertNoTemporaryFiles();
    }

    @Test
    void serializationFailureDoesNotTouchExistingBytes() throws Exception {
        byte[] original = "owner: Original\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dataFile(), original);
        YamlConfiguration broken = new YamlConfiguration() {
            @Override
            public String saveToString() {
                throw new IllegalStateException("Expected test serialization failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> StargateRegistryFile.save(broken, dataFile()));
        assertArrayEquals(original, Files.readAllBytes(dataFile()));
        assertNoTemporaryFiles();
    }

    @Test
    void failedReplacementKeepsTheExistingDirectoryAndRemovesStagingFile() throws Exception {
        Files.createDirectory(dataFile());
        Path original = dataFile().resolve("keep.txt");
        Files.writeString(original, "retained");
        var data = new YamlConfiguration();
        data.set("new", true);
        assertThrows(IOException.class, () -> StargateRegistryFile.save(data, dataFile()));
        assertEquals("retained", Files.readString(original));
        assertNoTemporaryFiles();
    }

    @Test
    void existingFileSymlinkAndTargetArePreserved() throws Exception {
        Path target = directory.resolve("actual.yml");
        Files.writeString(target, "owner: Original\n");
        Files.createSymbolicLink(dataFile(), target.getFileName());
        var data = StargateRegistryFile.load(dataFile());
        data.set("new", true);
        StargateRegistryFile.save(data, dataFile());
        assertTrue(Files.isSymbolicLink(dataFile()));
        assertEquals(target.getFileName(), Files.readSymbolicLink(dataFile()));
        assertEquals("Original", StargateRegistryFile.load(target).getString("owner"));
        assertTrue(StargateRegistryFile.load(target).getBoolean("new"));
        assertNoTemporaryFiles();
    }

    @Test
    void danglingSymlinkIsNotMisclassifiedAsFreshData() throws Exception {
        Path target = directory.resolve("missing.yml");
        Files.createSymbolicLink(dataFile(), target.getFileName());
        assertThrows(IOException.class, () -> StargateRegistryFile.load(dataFile()));
        assertThrows(IOException.class, () -> StargateRegistryFile.save(new YamlConfiguration(), dataFile()));
        assertTrue(Files.isSymbolicLink(dataFile()));
        assertFalse(Files.exists(target));
        assertNoTemporaryFiles();
    }

    @Test
    void cyclicSymlinkIsNeverReplaced() throws Exception {
        Files.createSymbolicLink(dataFile(), dataFile().getFileName());
        assertThrows(IOException.class, () -> StargateRegistryFile.load(dataFile()));
        assertThrows(IOException.class, () -> StargateRegistryFile.save(new YamlConfiguration(), dataFile()));
        assertTrue(Files.isSymbolicLink(dataFile()));
        assertNoTemporaryFiles();
    }

    @Test
    void posixModeIsRetainedOnSuccessfulReplacement() throws Exception {
        Files.writeString(dataFile(), "owner: Original\n");
        var mode = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(dataFile(), mode);
        StargateRegistryFile.save(StargateRegistryFile.load(dataFile()), dataFile());
        assertEquals(mode, Files.getPosixFilePermissions(dataFile()));
    }

    @Test
    void firstWriteCreatesParentsAndCleanupIsComplete() throws Exception {
        var data = new YamlConfiguration();
        data.set("owner", "FirstOwner");
        Path nested = directory.resolve("new/nested/stargates.yml");
        StargateRegistryFile.save(data, nested);
        assertEquals("FirstOwner", StargateRegistryFile.load(nested).getString("owner"));
        try (var files = Files.walk(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void historicalGateAddressesAndFullSignedCoordinatesRemainExact() throws Exception {
        Files.writeString(dataFile(), "gates:\n  ffffffff:\n    world: unloaded_planet\n"
                + "    x: -2147483648\n    y: -64\n    z: 2147483647\n"
                + "    custom: retain\n  deadbeef:\n    world: world\n    x: -37\n    y: 320\n    z: 9001\n");
        for (int cycle = 0; cycle < 3; cycle++) {
            var data = StargateRegistryFile.load(dataFile());
            assertEquals("unloaded_planet", data.getString("gates.ffffffff.world"));
            assertEquals(Integer.MIN_VALUE, data.getInt("gates.ffffffff.x"));
            assertEquals(-64, data.getInt("gates.ffffffff.y"));
            assertEquals(Integer.MAX_VALUE, data.getInt("gates.ffffffff.z"));
            assertEquals("retain", data.getString("gates.ffffffff.custom"));
            assertEquals(9001, data.getInt("gates.deadbeef.z"));
            StargateRegistryFile.save(data, dataFile());
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
