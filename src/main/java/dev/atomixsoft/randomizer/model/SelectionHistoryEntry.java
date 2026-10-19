package dev.atomixsoft.randomizer.model;

import java.time.Instant;
import java.util.Objects;

public record SelectionHistoryEntry(SelectionRecord selection, Status status, Instant changedAt) {
    public enum Status {
        ACTIVE, UNDONE, RESET
    }

    public SelectionHistoryEntry {
        Objects.requireNonNull(selection, "Selection is required.");
        Objects.requireNonNull(status, "History status is required.");
        if ((status == Status.ACTIVE) != (changedAt == null))
            throw new IllegalArgumentException("Only inactive selections require a change timestamp.");
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }
}
