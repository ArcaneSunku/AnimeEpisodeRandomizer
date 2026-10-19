package dev.atomixsoft.randomizer.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JsonEpisodeCatalogRepository implements EpisodeCatalogRepository {

    private static final int SCHEMA_VERSION = 1;

    private record CatalogDocument(int schemaVersion, int animeId, String fetchedAt, int pagesFetched, boolean snapshotComplete, List<AnimeEpisode> episodes) {

    }

    private final Path m_Directory;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    public JsonEpisodeCatalogRepository(Path directory) {
        m_Directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    @Override
    public Optional<EpisodeCatalog> load(int animeId) throws IOException {
        Path file = fileFor(animeId);
        if (Files.notExists(file))
            return Optional.empty();

        try {
            CatalogDocument document = m_Mapper.readValue(file.toFile(), CatalogDocument.class);

            if (document == null || document.schemaVersion() != SCHEMA_VERSION ||
                    document.animeId() != animeId || !document.snapshotComplete() ||
                    document.fetchedAt() == null || document.episodes() == null)
                throw new IOException("Invalid or unsupported episode catalog: " + file);

            return Optional.of(new EpisodeCatalog(document.animeId(), document.episodes(),
                    Instant.parse(document.fetchedAt()), document.pagesFetched(), document.snapshotComplete()));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IOException("Unable to read episode catalog: " + file, exception);
        }
    }

    @Override
    public void save(EpisodeCatalog catalog) throws IOException {
        Objects.requireNonNull(catalog, "Catalog cannot be null.");
        if (!catalog.snapshotComplete())
            throw new IOException("An incomplete episode catalog cannot replace a cached catalog.");

        Path file = fileFor(catalog.animeId());
        Files.createDirectories(m_Directory);

        Path temporary = Files.createTempFile(m_Directory, "episode-catalog-", ".tmp");

        try {
            CatalogDocument document = new CatalogDocument(
                    SCHEMA_VERSION,
                    catalog.animeId(),
                    catalog.fetchedAt().toString(),
                    catalog.pagesFetched(),
                    true,
                    catalog.episodes());

            Files.writeString(temporary, m_Mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document), StandardCharsets.UTF_8);

            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fileFor(int animeId) {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");

        return m_Directory.resolve(animeId + ".json");
    }

}