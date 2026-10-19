package dev.atomixsoft.randomizer.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EpisodeCatalog(int animeId, List<AnimeEpisode> episodes, Instant fetchedAt, int pagesFetched, boolean snapshotComplete) {

    public EpisodeCatalog {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");

        Objects.requireNonNull(episodes, "Episodes cannot be null.");
        Objects.requireNonNull(fetchedAt, "Fetch timestamp cannot be null.");

        if (pagesFetched < 0 || (snapshotComplete && pagesFetched == 0))
            throw new IllegalArgumentException("Invalid number of fetched pages.");

        episodes = List.copyOf(episodes);
        Set<Integer> numbers = new HashSet<>();

        for (AnimeEpisode episode : episodes) {
            if (episode.number() <= 0 || episode.title() == null || episode.title().isBlank())
                throw new IllegalArgumentException("The catalog contains an invalid episode.");

            if (!numbers.add(episode.number()))
                throw new IllegalArgumentException("Duplicate episode number: " + episode.number());
        }
    }

}