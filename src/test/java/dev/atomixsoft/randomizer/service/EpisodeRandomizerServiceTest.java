package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.model.HistorySnapshot;
import dev.atomixsoft.randomizer.model.SelectionHistoryEntry;
import dev.atomixsoft.randomizer.model.SessionRecord;
import dev.atomixsoft.randomizer.persistence.AnimeLibraryRepository;
import dev.atomixsoft.randomizer.persistence.EpisodeCatalogRepository;
import dev.atomixsoft.randomizer.persistence.SelectionHistoryRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public final class EpisodeRandomizerServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private final StubLibrary m_Library = new StubLibrary();
    private final StubCatalogs m_Catalogs = new StubCatalogs();
    private final StubHistory m_History = new StubHistory();

    @Test
    void oneAnimeNeverRepeatsAndEventuallyExhaustsItsCatalog() throws Exception {
        m_Library.add(anime(1));
        m_Catalogs.add(1, episode(1, false, false), episode(2, false, false), episode(3, false, false));
        EpisodeRandomizerService randomizer = randomizer();
        var request = request(List.of(1), false, false, EpisodeRandomizerService.Weighting.PER_EPISODE);

        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            selected.add(randomizer.select(request).episodeNumber());

        assertEquals(3, selected.stream().distinct().count());
        assertEquals(3, m_History.load().size());
        assertEquals(0, randomizer.availability(request).eligibleEpisodes());
        assertThrows(EpisodeRandomizerService.NoEligibleEpisodesException.class, () -> randomizer.select(request));
    }

    @Test
    void fillerAndRecapFiltersAreIndependent() throws Exception {
        m_Library.add(anime(1));
        m_Catalogs.add(1, episode(1, false, false), episode(2, true, false), episode(3, false, true));
        EpisodeRandomizerService randomizer = randomizer();

        assertEquals(3, randomizer.availability(request(List.of(1), false, false,
                EpisodeRandomizerService.Weighting.PER_EPISODE)).eligibleEpisodes());
        assertEquals(2, randomizer.availability(request(List.of(1), true, false,
                EpisodeRandomizerService.Weighting.PER_EPISODE)).eligibleEpisodes());
        assertEquals(2, randomizer.availability(request(List.of(1), false, true,
                EpisodeRandomizerService.Weighting.PER_EPISODE)).eligibleEpisodes());
        assertEquals(1, randomizer.availability(request(List.of(1), true, true,
                EpisodeRandomizerService.Weighting.PER_EPISODE)).eligibleEpisodes());
    }

    @Test
    void multiAnimeSelectionOnlyUsesRequestedShows() throws Exception {
        m_Library.add(anime(1));
        m_Library.add(anime(2));
        m_Library.add(anime(3));
        m_Catalogs.add(1, episode(1, false, false));
        m_Catalogs.add(2, episode(1, false, false), episode(2, false, false));
        m_Catalogs.add(3, episode(1, false, false));

        var request = request(List.of(1, 2), false, false, EpisodeRandomizerService.Weighting.PER_ANIME);
        EpisodeRandomizerService randomizer = randomizer();
        assertEquals(3, randomizer.availability(request).eligibleEpisodes());
        for (int i = 0; i < 3; i++)
            assertTrue(List.of(1, 2).contains(randomizer.select(request).animeId()));
    }

    @Test
    void oldSelectionsRemainExcludedAfterServiceRecreation() throws Exception {
        m_Library.add(anime(1));
        m_Catalogs.add(1, episode(1, false, false), episode(2, false, false));
        var request = request(List.of(1), false, false, EpisodeRandomizerService.Weighting.PER_EPISODE);

        SelectionRecord previous = randomizer().select(request);
        SelectionRecord next = randomizer().select(request);
        assertTrue(previous.episodeNumber() != next.episodeNumber());
    }

    @Test
    void failedHistorySaveNeverReturnsASuccessfulSelection() throws Exception {
        m_Library.add(anime(1));
        m_Catalogs.add(1, episode(1, false, false));
        m_History.m_FailSave = true;
        EpisodeRandomizerService randomizer = randomizer();
        var request = request(List.of(1), false, false, EpisodeRandomizerService.Weighting.PER_EPISODE);

        assertThrows(IOException.class, () -> randomizer.select(request));
        assertTrue(m_History.load().isEmpty());
        assertEquals(1, randomizer.availability(request).eligibleEpisodes());
    }

    @Test
    void missingCatalogIsReportedInsteadOfSelectingAnArbitraryEpisode() {
        m_Library.add(anime(1));
        assertThrows(IOException.class, () -> randomizer().select(
                request(List.of(1), false, false, EpisodeRandomizerService.Weighting.PER_EPISODE)));
    }

    @Test
    void perAnimeAndPerEpisodeWeightingsBothSelectFromEligiblePool() throws Exception {
        m_Library.add(anime(1));
        m_Library.add(anime(2));
        m_Catalogs.add(1, episode(1, false, false));
        m_Catalogs.add(2, episode(1, false, false), episode(2, false, false), episode(3, false, false));

        for (var weighting : EpisodeRandomizerService.Weighting.values()) {
            m_History.m_Selections.clear();
            var request = request(List.of(1, 2), false, false, weighting);
            assertEquals(4, randomizer().availability(request).eligibleEpisodes());
            for (int i = 0; i < 4; i++)
                randomizer().select(request);
            assertEquals(4, m_History.load().size());
        }
    }

    private EpisodeRandomizerService randomizer() {
        return new EpisodeRandomizerService(m_Library, m_Catalogs, m_History, new Random(42),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private EpisodeRandomizerService.Request request(List<Integer> ids, boolean filler, boolean recap,
                                                     EpisodeRandomizerService.Weighting weighting) {
        return new EpisodeRandomizerService.Request(ids, filler, recap, weighting, "test-session");
    }

    private Anime anime(int id) {
        return new Anime(id, "Anime " + id, "TV", null, "");
    }

    private AnimeEpisode episode(int number, boolean filler, boolean recap) {
        return new AnimeEpisode(number, "Episode " + number, filler, recap);
    }

    private static final class StubLibrary implements AnimeLibraryRepository {
        private final List<Anime> m_Anime = new ArrayList<>();
        private void add(Anime anime) { m_Anime.add(anime); }
        public List<Anime> load() { return List.copyOf(m_Anime); }
        public void save(List<Anime> anime) { throw new UnsupportedOperationException(); }
    }

    private static final class StubCatalogs implements EpisodeCatalogRepository {
        private final Map<Integer, EpisodeCatalog> m_Values = new HashMap<>();
        private void add(int id, AnimeEpisode... episodes) {
            m_Values.put(id, new EpisodeCatalog(id, List.of(episodes), NOW, 1, true));
        }
        public Optional<EpisodeCatalog> load(int id) { return Optional.ofNullable(m_Values.get(id)); }
        public void save(EpisodeCatalog catalog) { m_Values.put(catalog.animeId(), catalog); }
    }

    private static final class StubHistory implements SelectionHistoryRepository {
        private final List<SelectionRecord> m_Selections = new ArrayList<>();
        private boolean m_FailSave;
        public List<SelectionRecord> load() { return List.copyOf(m_Selections); }
        public void append(SelectionRecord record) throws IOException {
            if (m_FailSave)
                throw new IOException("Cannot save history");
            m_Selections.add(record);
        }
        public HistorySnapshot snapshot() {
            return new HistorySnapshot(List.of(), m_Selections.stream().map(record ->
                    new SelectionHistoryEntry(record, SelectionHistoryEntry.Status.ACTIVE, null)).toList());
        }
        public void createSession(SessionRecord session) { }
        public void renameSession(String id, String name) { }
        public void undo(String id, Instant changedAt) { }
        public int resetAnime(int animeId, Instant changedAt) { return 0; }
        public int resetAll(Instant changedAt) { return 0; }
        public boolean hasBackup() { return false; }
        public void restoreBackup() { }
    }
}
