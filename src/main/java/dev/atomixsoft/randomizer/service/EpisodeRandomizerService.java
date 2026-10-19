package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.persistence.AnimeLibraryRepository;
import dev.atomixsoft.randomizer.persistence.EpisodeCatalogRepository;
import dev.atomixsoft.randomizer.persistence.SelectionHistoryRepository;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class EpisodeRandomizerService {
    public enum Weighting {
        PER_EPISODE, PER_ANIME
    }

    public record Request(List<Integer> animeIds, boolean excludeFiller, boolean excludeRecap,
                          Weighting weighting, String sessionId) {
        public Request {
            animeIds = List.copyOf(animeIds);
            if (animeIds.isEmpty() || new HashSet<>(animeIds).size() != animeIds.size())
                throw new IllegalArgumentException("Select one or more unique anime.");
            Objects.requireNonNull(weighting, "A selection weighting is required.");
            if (sessionId == null || sessionId.isBlank())
                throw new IllegalArgumentException("A session identifier is required.");
        }
    }

    public record Availability(int eligibleEpisodes, int totalEpisodes, int animeWithEligibleEpisodes) {
    }

    public static final class NoEligibleEpisodesException extends IOException {
        public NoEligibleEpisodesException() {
            super("No unselected episodes match these options. Choose another anime or adjust the filters.");
        }
    }

    private record Pool(Anime anime, List<AnimeEpisode> eligible) {
    }

    private record SelectionPool(List<Pool> pools, Availability availability) {
    }

    private final AnimeLibraryRepository m_Library;
    private final EpisodeCatalogRepository m_Catalogs;
    private final SelectionHistoryRepository m_History;
    private final RandomGenerator m_Random;
    private final Clock m_Clock;

    public EpisodeRandomizerService(AnimeLibraryRepository library, EpisodeCatalogRepository catalogs,
                                    SelectionHistoryRepository history) {
        this(library, catalogs, history, RandomGenerator.getDefault(), Clock.systemUTC());
    }

    public EpisodeRandomizerService(AnimeLibraryRepository library, EpisodeCatalogRepository catalogs,
                                    SelectionHistoryRepository history, RandomGenerator random, Clock clock) {
        m_Library = Objects.requireNonNull(library);
        m_Catalogs = Objects.requireNonNull(catalogs);
        m_History = Objects.requireNonNull(history);
        m_Random = Objects.requireNonNull(random);
        m_Clock = Objects.requireNonNull(clock);
    }

    public Availability availability(Request request) throws IOException {
        return buildPool(request).availability();
    }

    public synchronized SelectionRecord select(Request request) throws IOException {
        SelectionPool selectionPool = buildPool(request);
        if (selectionPool.availability().eligibleEpisodes() == 0)
            throw new NoEligibleEpisodesException();

        Pool chosen;
        AnimeEpisode episode;

        if (request.weighting() == Weighting.PER_ANIME) {
            List<Pool> available = selectionPool.pools().stream().filter(pool -> !pool.eligible().isEmpty()).toList();
            chosen = available.get(m_Random.nextInt(available.size()));
            episode = chosen.eligible().get(m_Random.nextInt(chosen.eligible().size()));
        } else {
            long index = m_Random.nextLong(selectionPool.availability().eligibleEpisodes());
            chosen = null;
            episode = null;

            for (Pool pool : selectionPool.pools()) {
                if (index < pool.eligible().size()) {
                    chosen = pool;
                    episode = pool.eligible().get((int) index);
                    break;
                }
                index -= pool.eligible().size();
            }
        }

        if (chosen == null || episode == null)
            throw new IllegalStateException("No eligible episode could be selected.");

        SelectionRecord selection = new SelectionRecord(UUID.randomUUID().toString(), request.sessionId(),
                chosen.anime().malId(), chosen.anime().title(), episode.number(), episode.title(), m_Clock.instant());

        m_History.append(selection);
        return selection;
    }

    private SelectionPool buildPool(Request request) throws IOException {
        Objects.requireNonNull(request);
        Map<Integer, Anime> library = new HashMap<>();
        for (Anime anime : m_Library.load())
            library.put(anime.malId(), anime);

        Set<Long> selected = new HashSet<>();
        for (SelectionRecord record : m_History.load())
            selected.add(episodeKey(record.animeId(), record.episodeNumber()));

        List<Pool> pools = new ArrayList<>();
        int total = 0;
        int available = 0;
        int animeAvailable = 0;

        for (int animeId : request.animeIds()) {
            Anime anime = library.get(animeId);
            if (anime == null)
                throw new IOException("Selected anime is no longer in your saved library (ID " + animeId + ").");

            EpisodeCatalog catalog = m_Catalogs.load(animeId).orElseThrow(() ->
                    new IOException("No local episodes for " + anime.title() + ". Open Anime Library and cache its episode list."));

            if (!catalog.snapshotComplete())
                throw new IOException("Episode catalog is incomplete for " + anime.title() + ".");

            List<AnimeEpisode> eligible = new ArrayList<>();
            total += catalog.episodes().size();

            for (AnimeEpisode episode : catalog.episodes()) {
                if ((request.excludeFiller() && episode.filler()) || (request.excludeRecap() && episode.recap()) ||
                        selected.contains(episodeKey(animeId, episode.number())))
                    continue;

                eligible.add(episode);
            }

            available += eligible.size();
            if (!eligible.isEmpty())
                animeAvailable++;

            pools.add(new Pool(anime, List.copyOf(eligible)));
        }

        return new SelectionPool(List.copyOf(pools), new Availability(available, total, animeAvailable));
    }

    private long episodeKey(int animeId, int episodeNumber) {
        return ((long) animeId << 32) | (episodeNumber & 0xffffffffL);
    }
}
