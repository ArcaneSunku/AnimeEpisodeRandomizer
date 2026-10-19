package dev.atomixsoft.randomizer.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class JsonSettingsRepository {
    private static final int SCHEMA_VERSION = 1;

    private record SettingsDocument(Integer schemaVersion, AppSettings.Theme theme, Boolean excludeFiller,
                                    Boolean excludeRecaps, AppSettings.GroupWeighting groupWeighting) {
    }

    private final Path m_File;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    public JsonSettingsRepository(Path file) {
        m_File = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    public synchronized AppSettings load() throws IOException {
        if (Files.notExists(m_File))
            return AppSettings.defaults();

        try {
            SettingsDocument document = m_Mapper.readValue(m_File.toFile(), SettingsDocument.class);
            if (document == null || document.schemaVersion() == null || document.schemaVersion() != SCHEMA_VERSION
                    || document.theme() == null
                    || document.excludeFiller() == null || document.excludeRecaps() == null
                    || document.groupWeighting() == null)
                throw new IOException("Invalid or unsupported preferences: " + m_File);

            return new AppSettings(document.theme(), document.excludeFiller(),
                    document.excludeRecaps(), document.groupWeighting());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IOException("Cannot read preferences: " + m_File, exception);
        }
    }

    public synchronized void save(AppSettings settings) throws IOException {
        Objects.requireNonNull(settings, "Preferences are required.");
        Path directory = m_File.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "settings-", ".tmp");

        try {
            SettingsDocument document = new SettingsDocument(SCHEMA_VERSION, settings.theme(),
                    settings.excludeFiller(), settings.excludeRecaps(), settings.groupWeighting());
            Files.writeString(temporary, m_Mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document),
                    StandardCharsets.UTF_8);

            try {
                Files.move(temporary, m_File, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, m_File, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
