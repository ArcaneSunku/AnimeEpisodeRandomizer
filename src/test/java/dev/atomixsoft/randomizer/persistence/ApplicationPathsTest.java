package dev.atomixsoft.randomizer.persistence;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public final class ApplicationPathsTest {
    private final Path m_Home = Path.of("/home/example");

    @Test
    void linuxUsesXdgDataAndCacheLocations() {
        assertEquals(Path.of("/storage/share/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Linux", m_Home, Map.of("XDG_DATA_HOME", "/storage/share")));
        assertEquals(Path.of("/storage/cache/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Linux", m_Home, Map.of("XDG_CACHE_HOME", "/storage/cache")));
    }

    @Test
    void invalidRelativeXdgValueUsesFallback() {
        assertEquals(m_Home.resolve(".local/share/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Linux", m_Home, Map.of("XDG_DATA_HOME", "relative/path")));
    }

    @Test
    void windowsSeparatesRoamingDataFromLocalCache() {
        assertEquals(Path.of("/roaming/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Windows 11", m_Home, Map.of("APPDATA", "/roaming")));
        assertEquals(Path.of("/local/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Windows 11", m_Home, Map.of("LOCALAPPDATA", "/local")));
    }

    @Test
    void macUsesApplicationSupportAndCaches() {
        assertEquals(m_Home.resolve("Library/Application Support/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Mac OS X", m_Home, Map.of()));
        assertEquals(m_Home.resolve("Library/Caches/AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Mac OS X", m_Home, Map.of()));
    }
}
