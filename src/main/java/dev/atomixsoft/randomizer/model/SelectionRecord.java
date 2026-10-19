package dev.atomixsoft.randomizer.model;

import java.time.Instant;
import java.util.Objects;

public record SelectionRecord(String id, String sessionId, int animeId, String animeTitle, int episodeNumber, String episodeTitle, Instant selectedAt) {

    public SelectionRecord {
        if (id == null || id.isBlank() || sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("Selection and session identifiers are required.");

        if (animeId <= 0 || episodeNumber <= 0)
            throw new IllegalArgumentException("Anime and episode numbers must be positive.");

        if (animeTitle == null || animeTitle.isBlank() || episodeTitle == null || episodeTitle.isBlank())
            throw new IllegalArgumentException("Selection titles cannot be blank.");

        Objects.requireNonNull(selectedAt, "Selection time is required.");
    }

}
