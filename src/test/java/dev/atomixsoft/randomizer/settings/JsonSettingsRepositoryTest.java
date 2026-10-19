package dev.atomixsoft.randomizer.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public final class JsonSettingsRepositoryTest {
    @TempDir
    Path m_Directory;

    @Test
    void missingFileUsesSafeDefaultsWithoutCreatingOne() throws IOException {
        Path file = m_Directory.resolve("settings.json");
        assertEquals(AppSettings.defaults(), new JsonSettingsRepository(file).load());
        assertFalse(Files.exists(file));
    }

    @Test
    void preferencesSurviveARepositoryRestart() throws IOException {
        Path file = m_Directory.resolve("settings.json");
        AppSettings updated = new AppSettings(AppSettings.Theme.LIGHT, true, false,
                AppSettings.GroupWeighting.PER_ANIME);
        new JsonSettingsRepository(file).save(updated);
        assertEquals(updated, new JsonSettingsRepository(file).load());
    }

    @Test
    void malformedFileIsNotSilentlyReplaced() throws IOException {
        Path file = m_Directory.resolve("settings.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> new JsonSettingsRepository(file).load());
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void unsupportedVersionIsRejected() throws IOException {
        Path file = m_Directory.resolve("settings.json");
        Files.writeString(file, """
                {"schemaVersion":99,"theme":"DARK","excludeFiller":false,
                 "excludeRecaps":false,"groupWeighting":"PER_EPISODE"}
                """);
        assertThrows(IOException.class, () -> new JsonSettingsRepository(file).load());
    }

    @Test
    void incompleteSettingsAreRejected() throws IOException {
        Path file = m_Directory.resolve("settings.json");
        Files.writeString(file, """
                {"schemaVersion":1,"theme":"DARK","groupWeighting":"PER_EPISODE"}
                """);
        assertThrows(IOException.class, () -> new JsonSettingsRepository(file).load());
    }
}
