package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.SelectionHistoryEntry;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.model.SessionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public final class SelectionHistoryManagementTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @TempDir
    Path m_Directory;

    @Test
    void undoPreservesAuditTrailAndAllowsEpisodeToBeSelectedAgain() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.append(selection("first", "session-1", 1, 12));
        repository.undo("first", NOW.plusSeconds(5));

        assertTrue(repository.load().isEmpty());
        var original = repository.snapshot().entries().get(0);
        assertEquals(SelectionHistoryEntry.Status.UNDONE, original.status());
        assertEquals(NOW.plusSeconds(5), original.changedAt());

        repository.append(selection("second", "session-1", 1, 12));
        assertEquals(1, repository.load().size());
        assertEquals(2, repository.snapshot().entries().size());
        assertThrows(IOException.class, () -> repository.undo("first", NOW.plusSeconds(10)));
    }

    @Test
    void resettingOneAnimeDoesNotChangeOtherAnime() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.append(selection("a", "session-1", 1, 12));
        repository.append(selection("b", "session-1", 1, 13));
        repository.append(selection("c", "session-1", 20, 1));

        assertEquals(2, repository.resetAnime(1, NOW.plusSeconds(15)));
        assertEquals(1, repository.load().size());
        assertEquals(20, repository.load().get(0).animeId());
        assertEquals(3, repository.snapshot().entries().size());
        assertEquals(0, repository.resetAnime(1, NOW.plusSeconds(20)));
    }

    @Test
    void resetAllRestoresEligibilityWithoutErasingRecords() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.append(selection("a", "session-1", 1, 1));
        repository.append(selection("b", "session-1", 20, 2));
        assertEquals(2, repository.resetAll(NOW.plusSeconds(20)));
        assertTrue(repository.load().isEmpty());
        assertEquals(2, repository.snapshot().entries().size());
        assertEquals(SelectionHistoryEntry.Status.RESET, repository.snapshot().entries().get(0).status());
    }

    @Test
    void namedSessionsSurviveReloadAndCanBeRenamed() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.renameSession("session-1", "Movie night");
        repository.append(selection("a", "session-1", 1, 1));

        var reopened = repository();
        assertEquals("Movie night", reopened.snapshot().sessions().get(0).name());
        assertEquals("session-1", reopened.snapshot().entries().get(0).selection().sessionId());
        assertThrows(IOException.class, () -> reopened.createSession(session("session-1")));
    }

    @Test
    void legacyHistoryIsUpgradedWithoutLosingSelections() throws Exception {
        Path file = m_Directory.resolve("history.json");
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "selections": [
                    {"id":"old","sessionId":"legacy-session","animeId":1,"animeTitle":"Cowboy Bebop",
                     "episodeNumber":12,"episodeTitle":"Jupiter Jazz","selectedAt":"2026-10-19T12:00:00Z"}
                  ]
                }
                """);
        var repository = repository();
        assertEquals(1, repository.load().size());
        assertEquals("legacy-session", repository.snapshot().sessions().get(0).id());

        repository.renameSession("legacy-session", "Original picks");
        assertEquals(2, new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(file.toFile()).path("schemaVersion").intValue());
        assertEquals("Original picks", repository().snapshot().sessions().get(0).name());
        assertEquals(1, repository().load().size());
    }

    @Test
    void damagedHistoryDoesNotGetOverwritten() throws Exception {
        Path file = m_Directory.resolve("history.json");
        Files.writeString(file, "{not json");
        var repository = repository();
        assertThrows(IOException.class, () -> repository.createSession(session("session-1")));
        assertEquals("{not json", Files.readString(file));
    }

    @Test
    void explicitBackupRecoveryRestoresPreviouslySavedVersion() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.append(selection("a", "session-1", 1, 1));
        repository.append(selection("b", "session-1", 1, 2));
        assertTrue(repository.hasBackup());
        assertEquals(2, repository.load().size());

        repository.restoreBackup();
        assertEquals(1, repository().load().size());
        assertEquals(1, repository().load().get(0).episodeNumber());
    }

    @Test
    void backupCanRecoverUnreadablePrimaryHistory() throws Exception {
        var repository = repository();
        repository.createSession(session("session-1"));
        repository.append(selection("a", "session-1", 1, 1));
        repository.append(selection("b", "session-1", 1, 2));

        Path file = m_Directory.resolve("history.json");
        Files.writeString(file, "{damaged");
        assertThrows(IOException.class, repository::snapshot);
        assertThrows(IOException.class, () -> repository.resetAll(NOW.plusSeconds(30)));
        assertEquals("{damaged", Files.readString(file));

        repository.restoreBackup();
        assertEquals(1, repository.load().size());
        assertEquals(1, repository.snapshot().entries().size());
    }

    @Test
    void multipleRepositoryInstancesRejectConflictingSelections() throws Exception {
        var first = repository();
        var second = repository();
        first.createSession(session("session-1"));
        first.append(selection("a", "session-1", 1, 1));
        assertThrows(IOException.class, () -> second.append(selection("b", "session-1", 1, 1)));
        assertEquals(1, second.load().size());
    }

    private JsonSelectionHistoryRepository repository() {
        return new JsonSelectionHistoryRepository(m_Directory.resolve("history.json"));
    }

    private SessionRecord session(String id) {
        return new SessionRecord(id, "Test session", NOW);
    }

    private SelectionRecord selection(String id, String sessionId, int animeId, int episodeNumber) {
        return new SelectionRecord(id, sessionId, animeId, "Anime " + animeId, episodeNumber,
                "Episode " + episodeNumber, NOW);
    }
}
