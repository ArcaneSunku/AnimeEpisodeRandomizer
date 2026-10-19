package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.api.AnimeCatalogClient;
import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import dev.atomixsoft.randomizer.model.Page;
import dev.atomixsoft.randomizer.persistence.EpisodeCatalogRepository;
import dev.atomixsoft.randomizer.persistence.JsonEpisodeCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public final class EpisodeCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @TempDir
    Path m_Directory;

    @Test
    void retrievesEveryPageAndPersistsOrderedEpisodes() throws Exception {
        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(2)), 1, true));
        client.add(new Page<>(List.of(episode(1), episode(3)), 2, false));

        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog result = service(client, repository).synchronize(1);

        assertEquals(List.of(1, 2, 3), result.episodes().stream().map(AnimeEpisode::number).toList());
        assertEquals(2, result.pagesFetched());
        assertEquals(NOW, result.fetchedAt());
        assertTrue(result.snapshotComplete());
        assertEquals(result, repository.load(1).orElseThrow());
    }

    @Test
    void failedLaterPagePreservesPreviousCatalog() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(3600), 1, true);
        repository.save(original);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1), episode(2)), 1, true));
        client.failOnPage(2);

        assertThrows(IOException.class, () -> service(client, repository).synchronize(1));
        assertEquals(original, repository.load(1).orElseThrow());
    }

    @Test
    void duplicateEpisodesAcrossPagesAreRejected() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1)), 1, true));
        client.add(new Page<>(List.of(episode(1)), 2, false));

        assertThrows(IOException.class, () -> service(client, repository).synchronize(1));
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void emptyIntermediatePageIsRejected() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(), 1, true));

        assertThrows(IOException.class, () -> service(client, repository).synchronize(1));
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void emptyFinalPageAfterEarlierResultsIsRejected() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1)), 1, true));
        client.add(new Page<>(List.of(), 2, false));

        assertThrows(IOException.class, () -> service(client, repository).synchronize(1));
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void unexpectedPageNumberIsRejected() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1)), 7, false), 1);

        assertThrows(IOException.class, () -> service(client, repository).synchronize(1));
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void validEmptyCatalogIsSaved() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(), 1, false));

        EpisodeCatalog result = service(client, repository).synchronize(1);

        assertTrue(result.episodes().isEmpty());
        assertEquals(1, result.pagesFetched());
        assertEquals(result, repository.load(1).orElseThrow());
    }

    @Test
    void interruptedRetrievalNeverWritesPartialResults() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1)), 1, true));
        client.interruptOnPage(2);

        assertThrows(InterruptedException.class, () -> service(client, repository).synchronize(1));
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    void reportsEachPageBeforeItIsRequested() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1)), 1, true));
        client.add(new Page<>(List.of(episode(2)), 2, false));

        List<Integer> requestedPages = new ArrayList<>();
        service(client, repository).synchronize(1, requestedPages::add);

        assertEquals(List.of(1, 2), requestedPages);
    }

    @Test
    void freshCatalogLoadsWithoutCallingTenrai() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog cached = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(3600), 1, true);
        repository.save(cached);

        StubCatalogClient client = new StubCatalogClient();
        var result = service(client, repository).getOrRefresh(1, page -> {});

        assertEquals(EpisodeCatalogService.Source.CACHE, result.source());
        assertEquals(cached, result.catalog());
        assertEquals(0, client.m_Requests);
        assertEquals(0, client.m_Invalidations);
    }

    @Test
    void outdatedCatalogIsRefreshedAndSaved() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        repository.save(new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(172800), 1, true));

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1), episode(2)), 1, false));

        var result = service(client, repository).getOrRefresh(1, page -> {});

        assertEquals(EpisodeCatalogService.Source.NETWORK, result.source());
        assertEquals(2, repository.load(1).orElseThrow().episodes().size());
        assertEquals(1, client.m_Invalidations);
    }

    @Test
    void offlineRefreshReturnsSavedCatalogWithWarning() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog cached = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(172800), 1, true);
        repository.save(cached);

        StubCatalogClient client = new StubCatalogClient();
        client.failOnPage(1);

        var result = service(client, repository).getOrRefresh(1, page -> {});

        assertEquals(EpisodeCatalogService.Source.CACHE_FALLBACK, result.source());
        assertTrue(result.warning().contains("Failed to retrieve"));
        assertEquals(cached, result.catalog());
        assertEquals(cached, repository.load(1).orElseThrow());
    }

    @Test
    void forcedRefreshIgnoresFreshSnapshot() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        repository.save(new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(60), 1, true));

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(2)), 1, false));

        var result = service(client, repository).refresh(1, page -> {});

        assertEquals(EpisodeCatalogService.Source.NETWORK, result.source());
        assertEquals(2, result.catalog().episodes().get(0).number());
        assertEquals(1, client.m_Requests);
        assertEquals(1, client.m_Invalidations);
    }

    @Test
    void offlineWithoutSavedCatalogReportsFailure() {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);

        StubCatalogClient client = new StubCatalogClient();
        client.failOnPage(1);

        assertThrows(IOException.class, () -> service(client, repository).getOrRefresh(1, page -> {}));
    }

    @Test
    void staleSnapshotIsPublishedBeforeNetworkPages() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(172800), 1, true);
        repository.save(original);

        StubCatalogClient client = new StubCatalogClient();
        client.add(new Page<>(List.of(episode(1), episode(2)), 1, false));

        List<String> events = new ArrayList<>();
        var result = service(client, repository).getOrRefresh(1,
                cached -> {
                    assertEquals(original, cached);
                    events.add("cached");
                }, page -> events.add("page " + page));

        assertEquals(List.of("cached", "page 1"), events);
        assertEquals(EpisodeCatalogService.Source.NETWORK, result.source());
        assertEquals(2, result.catalog().episodes().size());
    }

    @Test
    void failedRefreshKeepsPreviouslyPublishedSnapshot() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(172800), 1, true);
        repository.save(original);

        StubCatalogClient client = new StubCatalogClient();
        client.failOnPage(1);

        List<EpisodeCatalog> published = new ArrayList<>();

        var result = service(client, repository).getOrRefresh(1, published::add, page -> {});

        assertEquals(List.of(original), published);
        assertEquals(EpisodeCatalogService.Source.CACHE_FALLBACK, result.source());
        assertEquals(original, result.catalog());
        assertEquals(original, repository.load(1).orElseThrow());
    }

    @Test
    void freshSnapshotDoesNotPublishStaleUpdate() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = new EpisodeCatalog(1, List.of(episode(1)), NOW.minusSeconds(100), 1, true);
        repository.save(original);

        StubCatalogClient client = new StubCatalogClient();
        List<EpisodeCatalog> published = new ArrayList<>();

        var result = service(client, repository).getOrRefresh(1, published::add, page -> {});

        assertTrue(published.isEmpty());
        assertEquals(EpisodeCatalogService.Source.CACHE, result.source());
        assertEquals(0, client.m_Requests);
    }

    @Test
    void loadCachedDoesNotContactCatalogProvider() throws Exception {
        EpisodeCatalogRepository repository = new JsonEpisodeCatalogRepository(m_Directory);
        EpisodeCatalog original = new EpisodeCatalog(1, List.of(episode(1)), NOW, 1, true);
        repository.save(original);

        StubCatalogClient client = new StubCatalogClient();

        assertEquals(original, service(client, repository).loadCached(1).orElseThrow());
        assertEquals(0, client.m_Requests);
    }

    private EpisodeCatalogService service(AnimeCatalogClient client, EpisodeCatalogRepository repository) {
        return new EpisodeCatalogService(client, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AnimeEpisode episode(int number) {
        return new AnimeEpisode(number, "Episode " + number, false, false);
    }

    private static final class StubCatalogClient implements AnimeCatalogClient {

        private final Map<Integer, Page<AnimeEpisode>> m_Pages = new HashMap<>();
        private int m_FailingPage = -1;
        private int m_InterruptingPage = -1;

        private int m_Requests;
        private int m_Invalidations;

        private void add(Page<AnimeEpisode> page) {
            m_Pages.put(page.number(), page);
        }

        private void add(Page<AnimeEpisode> page, int requestedPage) {
            m_Pages.put(requestedPage, page);
        }

        private void failOnPage(int page) {
            m_FailingPage = page;
        }

        private void interruptOnPage(int page) {
            m_InterruptingPage = page;
        }

        @Override
        public void invalidateEpisodeCache(int animeId) {
            m_Invalidations++;
        }

        @Override
        public Page<Anime> searchAnime(String query, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<AnimeEpisode> getEpisodes(int animeId, int page) throws IOException, InterruptedException {
            m_Requests++;

            if (page == m_FailingPage)
                throw new IOException("Simulated network failure");

            if (page == m_InterruptingPage)
                throw new InterruptedException("Simulated interruption");

            Page<AnimeEpisode> result = m_Pages.get(page);
            if (result == null)
                throw new IOException("Unexpected request for page " + page);

            return result;
        }

    }

}