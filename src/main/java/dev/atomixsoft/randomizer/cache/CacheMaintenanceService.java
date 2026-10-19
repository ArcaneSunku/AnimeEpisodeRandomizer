package dev.atomixsoft.randomizer.cache;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public final class CacheMaintenanceService {
    public enum CacheType {
        ARTWORK("artwork"), EPISODE_CATALOGS("episode-catalogs");

        private final String m_Directory;

        CacheType(String directory) {
            m_Directory = directory;
        }
    }

    private final Path m_BaseDirectory;

    public CacheMaintenanceService(Path baseDirectory) {
        m_BaseDirectory = Objects.requireNonNull(baseDirectory).toAbsolutePath().normalize();
    }

    public void clear(CacheType type) throws IOException {
        Objects.requireNonNull(type);
        Path directory = m_BaseDirectory.resolve(type.m_Directory);

        if (Files.isSymbolicLink(m_BaseDirectory) || Files.isSymbolicLink(directory))
            throw new IOException("Cannot clear a cache directory that is a symbolic link.");
        if (Files.notExists(directory))
            return;
        if (!Files.isDirectory(directory))
            throw new IOException("The requested cache path is not a directory: " + directory);

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null)
                    throw exception;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
