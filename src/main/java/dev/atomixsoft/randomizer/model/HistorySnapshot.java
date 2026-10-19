package dev.atomixsoft.randomizer.model;

import java.util.List;

public record HistorySnapshot(List<SessionRecord> sessions, List<SelectionHistoryEntry> entries) {
    public HistorySnapshot {
        sessions = List.copyOf(sessions);
        entries = List.copyOf(entries);
    }

    public List<SelectionRecord> activeSelections() {
        return entries.stream().filter(SelectionHistoryEntry::active)
                .map(SelectionHistoryEntry::selection).toList();
    }
}
