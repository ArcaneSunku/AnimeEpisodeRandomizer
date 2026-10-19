package dev.atomixsoft.randomizer.model;

import java.time.Instant;
import java.util.Objects;

public record SessionRecord(String id, String name, Instant startedAt) {
    public SessionRecord {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Session identifier is required.");
        if (name == null || name.isBlank() || name.length() > 80)
            throw new IllegalArgumentException("Session name must contain between 1 and 80 characters.");
        Objects.requireNonNull(startedAt, "Session start time is required.");
        name = name.trim();
    }
}
