package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.SelectionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public final class JsonSelectionHistoryRepositoryTest {
    @TempDir
    Path m_Directory;

    @Test
    void selectionsPersistAcrossRepositoryInstances() throws IOException {
        Path file = m_Directory.resolve("history.json");
        var repository = new JsonSelectionHistoryRepository(file);
        SelectionRecord record = record("one", 1);
        repository.append(record);
        assertEquals(record, new JsonSelectionHistoryRepository(file).load().get(0));
        assertEquals(1, repository.load().size());
    }

    @Test
    void duplicateEpisodesCannotBeWrittenTwice() throws IOException {
        var repository = new JsonSelectionHistoryRepository(m_Directory.resolve("history.json"));
        repository.append(record("one", 1));
        assertThrows(IOException.class, () -> repository.append(record("two", 1)));
        assertEquals(1, repository.load().size());
    }

    @Test
    void corruptedHistoryIsNotOverwritten() throws IOException {
        Path file = m_Directory.resolve("history.json");
        Files.writeString(file, "{invalid JSON");
        var repository = new JsonSelectionHistoryRepository(file);
        assertThrows(IOException.class, () -> repository.load());
        assertThrows(IOException.class, () -> repository.append(record("one", 1)));
        assertEquals("{invalid JSON", Files.readString(file));
    }

    @Test
    void missingHistoryIsTreatedAsEmpty() throws IOException {
        assertTrue(new JsonSelectionHistoryRepository(m_Directory.resolve("history.json")).load().isEmpty());
    }

    private SelectionRecord record(String id, int episode) {
        return new SelectionRecord(id, "session", 1, "Cowboy Bebop", episode, "Episode " + episode,
                Instant.parse("2026-10-19T12:00:00Z"));
    }
}
