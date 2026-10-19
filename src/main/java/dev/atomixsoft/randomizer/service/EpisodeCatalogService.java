package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.api.AnimeCatalogClient;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import dev.atomixsoft.randomizer.model.Page;
import dev.atomixsoft.randomizer.persistence.EpisodeCatalogRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class EpisodeCatalogService {

    private static final int MAX_PAGES = 1000;
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(24);

    public enum Source {
        CACHE, NETWORK, CACHE_FALLBACK
    }

    public record CatalogResult(EpisodeCatalog catalog, Source source, String warning) {

    }

    private final AnimeCatalogClient m_CatalogClient;
    private final EpisodeCatalogRepository m_Repository;
    private final Clock m_Clock;
    private final Duration m_MaxAge;

    public EpisodeCatalogService(AnimeCatalogClient catalogClient, EpisodeCatalogRepository repository) {
        this(catalogClient, repository, Clock.systemUTC(), DEFAULT_MAX_AGE);
    }

    public EpisodeCatalogService(AnimeCatalogClient catalogClient, EpisodeCatalogRepository repository, Clock clock) {
        this(catalogClient, repository, clock, DEFAULT_MAX_AGE);
    }

    public EpisodeCatalogService(AnimeCatalogClient catalogClient, EpisodeCatalogRepository repository, Clock clock, Duration maxAge) {
        m_CatalogClient = Objects.requireNonNull(catalogClient);
        m_Repository = Objects.requireNonNull(repository);
        m_Clock = Objects.requireNonNull(clock);
        m_MaxAge = Objects.requireNonNull(maxAge);

        if (maxAge.isZero() || maxAge.isNegative())
            throw new IllegalArgumentException("Catalog lifetime must be positive.");
    }

    public Optional<EpisodeCatalog> loadCached(int animeId) throws IOException {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");

        return m_Repository.load(animeId);
    }

    public CatalogResult getOrRefresh(int animeId, IntConsumer onPageRequested) throws IOException, InterruptedException {
        return getOrRefresh(animeId, catalog -> {}, onPageRequested);
    }

    public CatalogResult getOrRefresh(int animeId, Consumer<EpisodeCatalog> onStaleCatalog,
                                      IntConsumer onPageRequested) throws IOException, InterruptedException {
        return resolve(animeId, false, onStaleCatalog, onPageRequested);
    }

    public CatalogResult refresh(int animeId, IntConsumer onPageRequested) throws IOException, InterruptedException {
        return resolve(animeId, true, catalog -> {}, onPageRequested);
    }

    private CatalogResult resolve(int animeId, boolean force, Consumer<EpisodeCatalog> onStaleCatalog, IntConsumer onPageRequested) throws IOException, InterruptedException {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");

        Objects.requireNonNull(onStaleCatalog, "Cached catalog callback cannot be null.");
        Objects.requireNonNull(onPageRequested, "Progress callback cannot be null.");

        Optional<EpisodeCatalog> cached = loadCached(animeId);
        if (!force && cached.isPresent() && isFresh(cached.get()))
            return new CatalogResult(cached.get(), Source.CACHE, null);

        if (!force)
            cached.ifPresent(onStaleCatalog);

        try {
            return new CatalogResult(synchronize(animeId, onPageRequested), Source.NETWORK, null);
        } catch (IOException exception) {
            if (cached.isPresent())
                return new CatalogResult(cached.get(), Source.CACHE_FALLBACK, exception.getMessage());

            throw exception;
        }
    }

    private boolean isFresh(EpisodeCatalog catalog) {
        Duration age = Duration.between(catalog.fetchedAt(), m_Clock.instant());
        return !age.isNegative() && age.compareTo(m_MaxAge) < 0;
    }

    public EpisodeCatalog synchronize(int animeId) throws IOException, InterruptedException {
        return synchronize(animeId, page -> {});
    }

    public EpisodeCatalog synchronize(int animeId, IntConsumer onPageRequested) throws IOException, InterruptedException {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");

        Objects.requireNonNull(onPageRequested, "Progress callback cannot be null.");
        m_CatalogClient.invalidateEpisodeCache(animeId);

        TreeMap<Integer, AnimeEpisode> episodes = new TreeMap<>();

        for (int pageNumber = 1; pageNumber <= MAX_PAGES; pageNumber++) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Episode synchronization interrupted.");

            onPageRequested.accept(pageNumber);

            Page<AnimeEpisode> page;

            try {
                page = m_CatalogClient.getEpisodes(animeId, pageNumber);
            } catch (IOException exception) {
                throw new IOException("Failed to retrieve episode page " + pageNumber
                        + " for anime " + animeId + ".", exception);
            }

            if (page == null || page.number() != pageNumber)
                throw new IOException("Unexpected episode page " + pageNumber + " for anime " + animeId + ".");

            if (page.items().isEmpty() && (pageNumber > 1 || page.hasNextPage()))
                throw new IOException("Unexpected empty episode page " + pageNumber + " for anime " + animeId + ".");

            for (AnimeEpisode episode : page.items()) {
                if (episode == null || episode.number() <= 0
                        || episode.title() == null || episode.title().isBlank())
                    throw new IOException("Invalid episode on page " + pageNumber + " for anime " + animeId + ".");

                if (episodes.putIfAbsent(episode.number(), episode) != null)
                    throw new IOException("Duplicate episode " + episode.number()
                            + " while synchronizing anime " + animeId + ".");
            }

            if (!page.hasNextPage()) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Episode synchronization interrupted.");

                EpisodeCatalog catalog = new EpisodeCatalog(animeId,
                        new ArrayList<>(episodes.values()), m_Clock.instant(), pageNumber, true);

                m_Repository.save(catalog);
                return catalog;
            }
        }

        throw new IOException("Episode catalog for anime " + animeId + " exceeded " + MAX_PAGES + " pages.");
    }

}