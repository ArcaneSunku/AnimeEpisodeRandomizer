package dev.atomixsoft.randomizer.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public final class CacheMaintenanceServiceTest {
    @TempDir
    Path m_Directory;

    @Test
    void clearingArtworkPreservesEpisodeCatalogsAndUserFiles() throws IOException {
        Path cache = m_Directory.resolve("cache");
        Path artwork = cache.resolve("artwork");
        Path catalogs = cache.resolve("episode-catalogs");
        Files.createDirectories(artwork.resolve("nested"));
        Files.createDirectories(catalogs);
        Files.writeString(artwork.resolve("nested/cover.img"), "cover");
        Files.writeString(catalogs.resolve("1.json"), "catalog");
        Path history = m_Directory.resolve("history.json");
        Files.writeString(history, "history");

        new CacheMaintenanceService(cache).clear(CacheMaintenanceService.CacheType.ARTWORK);

        assertFalse(Files.exists(artwork));
        assertEquals("catalog", Files.readString(catalogs.resolve("1.json")));
        assertEquals("history", Files.readString(history));
    }

    @Test
    void clearingEpisodeCatalogsNeverTouchesArtworkOrUserData() throws IOException {
        Path cache = m_Directory.resolve("cache");
        Files.createDirectories(cache.resolve("episode-catalogs"));
        Files.createDirectories(cache.resolve("artwork"));
        Files.writeString(cache.resolve("episode-catalogs/20.json"), "episodes");
        Files.writeString(cache.resolve("artwork/icon.img"), "image");
        Files.writeString(m_Directory.resolve("library.json"), "library");

        new CacheMaintenanceService(cache).clear(CacheMaintenanceService.CacheType.EPISODE_CATALOGS);

        assertFalse(Files.exists(cache.resolve("episode-catalogs")));
        assertTrue(Files.exists(cache.resolve("artwork/icon.img")));
        assertEquals("library", Files.readString(m_Directory.resolve("library.json")));
    }

    @Test
    void missingCacheDirectoryIsNoOp() throws IOException {
        new CacheMaintenanceService(m_Directory.resolve("absent"))
                .clear(CacheMaintenanceService.CacheType.ARTWORK);
        assertFalse(Files.exists(m_Directory.resolve("absent")));
    }

    @Test
    void cachePathMustBeDirectory() throws IOException {
        Path cache = m_Directory.resolve("cache");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve("artwork"), "not a directory");
        assertThrows(IOException.class, () -> new CacheMaintenanceService(cache)
                .clear(CacheMaintenanceService.CacheType.ARTWORK));
    }
}
