package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class JsonEpisodeCatalogRepositoryTest {

    @TempDir
    Path m_Directory;

    @Test
    void missingCatalogReturnsEmpty() throws IOException {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void completeCatalogSurvivesReload() throws IOException {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog catalog = catalog(1, true);

        repository.save(catalog);

        assertEquals(catalog, new JsonEpisodeCatalogRepository(m_Directory).load(1).orElseThrow());
        assertTrue(Files.exists(m_Directory.resolve("1.json")));
    }

    @Test
    void incompleteCatalogCannotReplaceExistingCatalog() throws IOException {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = catalog(1, true);
        repository.save(original);

        assertThrows(IOException.class, () -> repository.save(catalog(1, false)));
        assertEquals(original, repository.load(1).orElseThrow());
    }

    @Test
    void damagedCatalogIsReportedRatherThanTreatedAsMissing() throws IOException {
        Files.writeString(m_Directory.resolve("1.json"), "{invalid json");
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        assertThrows(IOException.class, () -> repository.load(1));
        assertTrue(Files.exists(m_Directory.resolve("1.json")));
    }

    @Test
    void catalogIdentityMustMatchRequestedAnime() throws IOException {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        repository.save(catalog(1, true));
        Files.copy(m_Directory.resolve("1.json"), m_Directory.resolve("2.json"));

        assertThrows(IOException.class, () -> repository.load(2));
    }

    @Test
    void duplicateEpisodeNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EpisodeCatalog(1, List.of(
                new AnimeEpisode(1, "Episode 1", false, false),
                new AnimeEpisode(1, "Another episode 1", false, false)),
                Instant.parse("2026-10-18T12:00:00Z"), 1, true));
    }

    @Test
    void episodeListCannotBeModifiedAfterCatalogCreation() {
        List<AnimeEpisode> episodes = new ArrayList<>();
        episodes.add(new AnimeEpisode(1, "Episode 1", false, false));

        EpisodeCatalog catalog = new EpisodeCatalog(1, episodes,
                Instant.parse("2026-10-18T12:00:00Z"), 1, true);
        episodes.clear();

        assertEquals(1, catalog.episodes().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.episodes().clear());
    }

    private EpisodeCatalog catalog(int animeId, boolean complete) {
        return new EpisodeCatalog(animeId, List.of(
                new AnimeEpisode(1, "Asteroid Blues", false, false),
                new AnimeEpisode(2, "Stray Dog Strut", false, false)),
                Instant.parse("2026-10-18T12:00:00Z"), 1, complete);
    }

}