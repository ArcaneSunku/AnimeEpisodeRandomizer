package dev.atomixsoft.randomizer.persistence;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class ApplicationPaths {

    private static final String DIRECTORY_NAME = "AnimeEpisodeRandomizer";

    private ApplicationPaths() {

    }

    public static Path dataDirectory() {
        return directory(false, System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv());
    }

    public static Path cacheDirectory() {
        return directory(true, System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv());
    }

    static Path directory(boolean cache, String osName, Path home, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path base;

        if (os.contains("mac") || os.contains("darwin")) {
            base = home.resolve("Library").resolve(cache ? "Caches" : "Application Support");
        } else if (os.contains("win")) {
            String variable = cache ? "LOCALAPPDATA" : "APPDATA";
            String fallback = cache ? "Local" : "Roaming";

            base = environmentPath(environment, variable, home.resolve("AppData").resolve(fallback));
        } else {
            String variable = cache ? "XDG_CACHE_HOME" : "XDG_DATA_HOME";
            Path fallback = home.resolve(cache ? ".cache" : ".local/share");

            base = environmentPath(environment, variable, fallback);
        }

        return base.resolve(DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private static Path environmentPath(Map<String, String> environment, String name, Path fallback) {
        String value = environment.get(name);

        if (value != null && !value.isBlank()) {
            Path path = Path.of(value);
            if (path.isAbsolute())
                return path;
        }

        return fallback;
    }

}