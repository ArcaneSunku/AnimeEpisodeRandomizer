package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.persistence.JsonSelectionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public final class SelectionHistoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @TempDir
    Path m_Directory;

    @Test
    void newSessionBecomesCurrentAndCanResumeAnOlderSession() throws Exception {
        var repository = new JsonSelectionHistoryRepository(m_Directory.resolve("history.json"));
        var service = new SelectionHistoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        var first = service.ensureSession();
        assertEquals(first.id(), service.ensureSession().id());
        var second = service.startSession("Watch party");
        assertEquals("Watch party", second.name());
        assertEquals(second.id(), service.currentSession().orElseThrow().id());

        service.useSession(first.id());
        assertEquals(first.id(), service.currentSession().orElseThrow().id());
        service.renameSession(first.id(), "Friday night");
        assertEquals("Friday night", service.currentSession().orElseThrow().name());
        assertEquals(2, service.snapshot().sessions().size());
    }

    @Test
    void undoAndResetUpdateActiveSessionCountAcrossInstances() throws Exception {
        var repository = new JsonSelectionHistoryRepository(m_Directory.resolve("history.json"));
        var service = new SelectionHistoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        var session = service.ensureSession();
        repository.append(new SelectionRecord("first", session.id(), 1, "Anime", 1, "Episode", NOW));
        repository.append(new SelectionRecord("second", session.id(), 1, "Anime", 2, "Episode", NOW));
        assertEquals(2, service.activeCount(session.id()));
        service.undo("first");
        assertEquals(1, service.activeCount(session.id()));
        assertEquals(1, service.resetAnime(1));
        assertEquals(0, service.activeCount(session.id()));
        assertEquals(2, service.snapshot().entries().size());
    }
}
