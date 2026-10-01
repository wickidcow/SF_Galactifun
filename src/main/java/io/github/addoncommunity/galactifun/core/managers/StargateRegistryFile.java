package io.github.addoncommunity.galactifun.core.managers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Read the existing YAML strictly and stage a complete replacement before touching its previous bytes. */
final class StargateRegistryFile {
    private StargateRegistryFile() {}

    static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException missing) {
            // Only a genuinely absent file is a fresh install. A dangling symlink is existing data.
            if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw missing;
            }
        }
        return configuration;
    }

    static void save(FileConfiguration configuration, Path file) throws IOException {
        // Finish serialization before creating a temporary file or opening the previous file.
        byte[] contents = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
        Path target = file.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) {
            target = target.toRealPath();
        }
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(contents);
                while (bytes.hasRemaining()) {
                    output.write(bytes);
                }
                output.force(true);
            }
            if (Files.isRegularFile(target)
                    && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(target));
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
