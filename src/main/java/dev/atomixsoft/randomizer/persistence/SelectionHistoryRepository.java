package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.HistorySnapshot;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.model.SessionRecord;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface SelectionHistoryRepository {
    List<SelectionRecord> load() throws IOException;
    void append(SelectionRecord selection) throws IOException;
    HistorySnapshot snapshot() throws IOException;
    void createSession(SessionRecord session) throws IOException;
    void renameSession(String sessionId, String name) throws IOException;
    void undo(String selectionId, Instant changedAt) throws IOException;
    int resetAnime(int animeId, Instant changedAt) throws IOException;
    int resetAll(Instant changedAt) throws IOException;
    boolean hasBackup();
    void restoreBackup() throws IOException;
}
