package dev.atomixsoft.randomizer.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ApplicationPathsTest {

    @TempDir
    Path m_TempDirectory;

    @Test
    void linuxUsesXdgDataAndCacheLocations() {
        Path home = m_TempDirectory.resolve("home");
        Path data = m_TempDirectory.resolve("storage").resolve("share");
        Path cache = m_TempDirectory.resolve("storage").resolve("cache");

        assertEquals(data.resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Linux", home,
                        Map.of("XDG_DATA_HOME", data.toString())));

        assertEquals(cache.resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Linux", home,
                        Map.of("XDG_CACHE_HOME", cache.toString())));
    }

    @Test
    void invalidRelativeXdgValueUsesFallback() {
        Path home = m_TempDirectory.resolve("home");

        assertEquals(home.resolve(".local").resolve("share").resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Linux", home,
                        Map.of("XDG_DATA_HOME", "relative/path")));
    }

    @Test
    void windowsSeparatesRoamingDataFromLocalCache() {
        Path home = m_TempDirectory.resolve("home");
        Path roaming = m_TempDirectory.resolve("roaming");
        Path local = m_TempDirectory.resolve("local");

        assertEquals(roaming.resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Windows 11", home,
                        Map.of("APPDATA", roaming.toString())));

        assertEquals(local.resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Windows 11", home,
                        Map.of("LOCALAPPDATA", local.toString())));
    }

    @Test
    void macUsesApplicationSupportAndCaches() {
        Path home = m_TempDirectory.resolve("home");

        assertEquals(home.resolve("Library").resolve("Application Support")
                        .resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Mac OS X", home, Map.of()));

        assertEquals(home.resolve("Library").resolve("Caches")
                        .resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Mac OS X", home, Map.of()));
    }

    @Test
    void windowsUsesFallbackWhenEnvironmentVariablesAreMissing() {
        Path home = m_TempDirectory.resolve("home");

        assertEquals(home.resolve("AppData").resolve("Roaming")
                        .resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(false, "Windows 11", home, Map.of()));

        assertEquals(home.resolve("AppData").resolve("Local")
                        .resolve("AnimeEpisodeRandomizer"),
                ApplicationPaths.directory(true, "Windows 11", home, Map.of()));
    }
}