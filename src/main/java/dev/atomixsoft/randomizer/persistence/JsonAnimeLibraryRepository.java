package dev.atomixsoft.randomizer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atomixsoft.randomizer.model.Anime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public final class JsonAnimeLibraryRepository implements AnimeLibraryRepository {

    private static final int SCHEMA_VERSION = 1;

    public record LibraryDocument(int schemaVersion, List<Anime> anime) {

    }

    private final Path m_File;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    public JsonAnimeLibraryRepository(Path file) {
        m_File = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    @Override
    public List<Anime> load() throws IOException {
        if (Files.notExists(m_File))
            return List.of();

        LibraryDocument document;
        try {
            document = m_Mapper.readValue(m_File.toFile(), LibraryDocument.class);
        } catch (IOException exception) {
            throw new IOException("Unable to read saved anime library: " + m_File, exception);
        }

        if (document == null || document.schemaVersion() != SCHEMA_VERSION || document.anime() == null)
            throw new IOException("Unsupported or invalid anime library format: " + m_File);

        return List.copyOf(document.anime());
    }

    @Override
    public void save(List<Anime> anime) throws IOException {
        Path directory = m_File.getParent();
        Files.createDirectories(directory);

        Path temporary = Files.createTempFile(directory, "library-", ".tmp");
        try {
            LibraryDocument document = new LibraryDocument(SCHEMA_VERSION, List.copyOf(anime));
            Files.writeString(temporary, m_Mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document), StandardCharsets.UTF_8);

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